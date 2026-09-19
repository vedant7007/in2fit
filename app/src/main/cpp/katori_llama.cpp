// The JNI shim between LlamaRuntime and llama.cpp.
//
// NOTHING IN THIS FILE HAS RUN ON A PHONE. It compiles; that is the only claim available until a
// device is attached. No statement about load time, speed, memory or output belongs anywhere near
// it until then.
//
// DESIGN. This is the smallest surface that supports the two paths in spec 11.5 and nothing else.
// There is no chat session, no streaming callback, no general completion endpoint reachable from
// Kotlin beyond `generate`. A wider native surface would quietly become the third prompt path the
// LlmEngine contract exists to prevent.
//
// THREADING. One handle is used by one thread at a time; the Kotlin side holds a ModelArbiter
// lease for the whole call. Nothing here is reentrant and nothing tries to be.
//
// MEMORY. The model and the context are owned by the handle and freed in `nativeFree`. Java
// strings are always released on every path, including the error paths, which is the failure mode
// that turns into an unexplained native leak three demos later.

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <mutex>
#include <chrono>

#include "llama.h"

#define LOG_TAG "katori-llama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

namespace {

struct Handle {
    llama_model   * model = nullptr;
    llama_context * ctx   = nullptr;
    const llama_vocab * vocab = nullptr;
    int32_t n_ctx = 0;
    std::mutex lock;

    // Timings from the last generate call. They live here rather than being derived in Kotlin
    // because Kotlin cannot see how many tokens the prompt became or how many were produced, and
    // a tokens-per-second figure guessed from a character count is not a measurement.
    int64_t last_prompt_tokens = 0;
    int64_t last_eval_tokens   = 0;
    int64_t last_prompt_us     = 0;
    int64_t last_eval_us       = 0;
};

int64_t now_us() {
    return (int64_t) std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

std::once_flag g_backend_once;

// A Java string borrowed for the lifetime of the scope, released however the scope exits.
class JavaString {
public:
    JavaString(JNIEnv * env, jstring s) : env_(env), js_(s) {
        if (js_ != nullptr) chars_ = env_->GetStringUTFChars(js_, nullptr);
    }
    ~JavaString() {
        if (js_ != nullptr && chars_ != nullptr) env_->ReleaseStringUTFChars(js_, chars_);
    }
    JavaString(const JavaString &) = delete;
    JavaString & operator=(const JavaString &) = delete;

    const char * c_str() const { return chars_ == nullptr ? "" : chars_; }
    bool ok() const { return chars_ != nullptr; }

private:
    JNIEnv * env_;
    jstring  js_;
    const char * chars_ = nullptr;
};

void throwIllegalState(JNIEnv * env, const char * message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

// True when the tail of `text` ends with any of `stops`.
bool endsWithAny(const std::string & text, const std::vector<std::string> & stops) {
    for (const auto & s : stops) {
        if (!s.empty() && text.size() >= s.size() &&
            text.compare(text.size() - s.size(), s.size(), s) == 0) {
            return true;
        }
    }
    return false;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeLoad(
        JNIEnv * env, jobject, jstring modelPath, jint contextTokens, jint threads) {

    std::call_once(g_backend_once, []() { llama_backend_init(); });

    JavaString path(env, modelPath);
    if (!path.ok()) {
        throwIllegalState(env, "model path was null");
        return 0;
    }

    llama_model_params mparams = llama_model_default_params();
    // CPU only for now. A GPU backend is a separate decision that has to be measured on the
    // device before it is claimed, and this build has no GPU backend compiled in.
    mparams.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("model failed to load from %s", path.c_str());
        throwIllegalState(env, "the model file failed to load");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx         = (uint32_t) (contextTokens > 0 ? contextTokens : 2048);
    cparams.n_batch       = cparams.n_ctx;
    cparams.n_threads     = threads > 0 ? threads : 4;
    cparams.n_threads_batch = cparams.n_threads;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        llama_model_free(model);
        LOGE("context failed to initialise");
        throwIllegalState(env, "the model loaded but its context failed to initialise");
        return 0;
    }

    auto * h = new Handle();
    h->model = model;
    h->ctx   = ctx;
    h->vocab = llama_model_get_vocab(model);
    h->n_ctx = (int32_t) llama_n_ctx(ctx);
    LOGI("loaded, n_ctx=%d threads=%d", h->n_ctx, cparams.n_threads);
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT jstring JNICALL
Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jstring prompt, jint maxTokens, jobjectArray stops) {

    auto * h = reinterpret_cast<Handle *>(handle);
    if (h == nullptr) {
        throwIllegalState(env, "generate was called on a released handle");
        return nullptr;
    }
    std::lock_guard<std::mutex> guard(h->lock);

    JavaString text(env, prompt);
    if (!text.ok()) {
        throwIllegalState(env, "prompt was null");
        return nullptr;
    }

    std::vector<std::string> stopStrings;
    if (stops != nullptr) {
        const jsize n = env->GetArrayLength(stops);
        stopStrings.reserve((size_t) n);
        for (jsize i = 0; i < n; i++) {
            auto s = (jstring) env->GetObjectArrayElement(stops, i);
            // The borrow MUST be released before the local reference is deleted. Without the
            // inner scope, ~JavaString runs after DeleteLocalRef and calls ReleaseStringUTFChars
            // against a reference that has already been popped, which CheckJNI turns into a
            // SIGABRT on the first generate call. Found on the device; it aborts every time,
            // because both stop lists are non-empty.
            {
                JavaString borrowed(env, s);
                if (borrowed.ok()) stopStrings.emplace_back(borrowed.c_str());
            }
            env->DeleteLocalRef(s);
        }
    }

    // Tokenise. The two-call form is the documented one: a negative return is the required size.
    const int32_t promptLen = (int32_t) std::string(text.c_str()).size();
    int32_t needed = -llama_tokenize(h->vocab, text.c_str(), promptLen, nullptr, 0, true, true);
    if (needed <= 0) {
        throwIllegalState(env, "the prompt tokenised to nothing");
        return nullptr;
    }
    std::vector<llama_token> tokens((size_t) needed);
    const int32_t written = llama_tokenize(
            h->vocab, text.c_str(), promptLen, tokens.data(), needed, true, true);
    if (written < 0) {
        throwIllegalState(env, "the prompt failed to tokenise");
        return nullptr;
    }
    tokens.resize((size_t) written);

    // REFUSE rather than truncate. Silently dropping the head of a prompt removes the system
    // instructions first, which are the rules that stop the model inventing numbers. A prompt
    // that does not fit is a bug to fix, not an input to trim.
    if (written + maxTokens > h->n_ctx) {
        throwIllegalState(env, "the prompt plus its reply would not fit the context window");
        return nullptr;
    }

    // Each call starts from a clean slate. No conversation state survives between calls, which is
    // what makes the same transcript produce the same items.
    llama_memory_clear(llama_get_memory(h->ctx), true);

    // Greedy. Extraction has to be reproducible for the schema tests to mean anything, and the
    // phrasing path is not improved by creative sampling; it is a sentence about a decided result.
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string out;
    std::string error;
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());

    h->last_prompt_tokens = (int64_t) tokens.size();
    h->last_eval_tokens   = 0;
    h->last_prompt_us     = 0;
    h->last_eval_us       = 0;
    const int64_t t_start = now_us();
    int64_t t_prompt_done = 0;

    const int32_t budget = maxTokens > 0 ? maxTokens : 128;
    for (int32_t produced = 0; produced < budget; produced++) {
        if (llama_decode(h->ctx, batch) != 0) {
            error = "decode failed";
            break;
        }
        if (produced == 0) {
            // The first decode is the whole prompt. Everything after it is one token at a time,
            // and mixing the two into one average is how a prompt-bound number gets quoted as a
            // generation speed.
            t_prompt_done = now_us();
            h->last_prompt_us = t_prompt_done - t_start;
        }
        const llama_token id = llama_sampler_sample(sampler, h->ctx, -1);
        if (llama_vocab_is_eog(h->vocab, id)) break;
        llama_sampler_accept(sampler, id);

        char piece[256];
        const int32_t n = llama_token_to_piece(h->vocab, id, piece, (int32_t) sizeof(piece), 0, false);
        if (n < 0) {
            error = "a token could not be rendered";
            break;
        }
        out.append(piece, (size_t) n);
        h->last_eval_tokens++;
        h->last_eval_us = now_us() - (t_prompt_done > 0 ? t_prompt_done : t_start);
        if (endsWithAny(out, stopStrings)) {
            // Trim the stop sequence itself; the caller asked for what came before it.
            for (const auto & s : stopStrings) {
                if (out.size() >= s.size() && out.compare(out.size() - s.size(), s.size(), s) == 0) {
                    out.resize(out.size() - s.size());
                    break;
                }
            }
            break;
        }
        batch = llama_batch_get_one(const_cast<llama_token *>(&id), 1);
    }

    llama_sampler_free(sampler);

    if (!error.empty()) {
        LOGE("%s", error.c_str());
        throwIllegalState(env, error.c_str());
        return nullptr;
    }
    return env->NewStringUTF(out.c_str());
}

// [prompt tokens, eval tokens, prompt microseconds, eval microseconds] from the last generate.
JNIEXPORT jlongArray JNICALL
Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeLastTimings(
        JNIEnv * env, jobject, jlong handle) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (h == nullptr) return nullptr;
    jlong values[4];
    {
        std::lock_guard<std::mutex> guard(h->lock);
        values[0] = (jlong) h->last_prompt_tokens;
        values[1] = (jlong) h->last_eval_tokens;
        values[2] = (jlong) h->last_prompt_us;
        values[3] = (jlong) h->last_eval_us;
    }
    jlongArray out = env->NewLongArray(4);
    if (out != nullptr) env->SetLongArrayRegion(out, 0, 4, values);
    return out;
}

JNIEXPORT void JNICALL
Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (h == nullptr) return;
    {
        std::lock_guard<std::mutex> guard(h->lock);
        if (h->ctx   != nullptr) llama_free(h->ctx);
        if (h->model != nullptr) llama_model_free(h->model);
        h->ctx = nullptr;
        h->model = nullptr;
        h->vocab = nullptr;
    }
    delete h;
}

} // extern "C"
