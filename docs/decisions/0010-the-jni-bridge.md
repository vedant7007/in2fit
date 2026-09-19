# 0010. The JNI bridge, and what can be claimed about it

Date: 19 September 2026. Status: accepted for the code, NOT for any behaviour claim.

## The claim boundary, first

**Nothing on this path has run on a phone.** The shim compiles, links against the four llama.cpp
libraries, and its three JNI symbols export under the exact names the Kotlin class expects. That
is the whole of what is known.

No figure for load time, tokens per second, memory, co-residency or output quality exists for this
app on any device, and none may be quoted until it has been read out of a log from the Realme 11.
The 9.89 tok/s from the desktop smoke test in `0006` is not a Katori number and never becomes one.

## Decision

**llama.cpp is not built by Gradle.** It is built once by `tools/5-build-llama-android.ps1` into
`app/src/main/jniLibs/arm64-v8a`, which is where Gradle packages the libraries from anyway.
Building it as a CMake subproject would add minutes to every build and tie the module to a large
vendored tree that is deliberately not committed. `app/src/main/cpp/CMakeLists.txt` builds only the
shim and fails at configure time, with a sentence saying which script to run, if a library is
missing. The alternative is a linker error nobody can read.

The libraries are gitignored at 53 MB. The headers beside them are committed, because the shim
does not compile without them and they are small.

**The native surface is as small as the two paths allow.** `nativeLoad`, `nativeGenerate`,
`nativeFree`. There is no chat session, no streaming callback and no general completion endpoint
reachable from Kotlin. A wider native surface would quietly become the third prompt path that the
`LlmEngine` contract exists to prevent, and it would be reachable without changing any interface
that goes to review.

**A prompt that does not fit the context window is REFUSED, not trimmed.** Trimming removes the
head of the prompt first, and the head is the system block: the rules telling the model not to
invent a number. Silently deleting those and then relying on the numeric guard to catch the
consequences is the wrong order. A prompt that does not fit is a bug to fix.

**Greedy sampling.** Extraction has to be reproducible for the schema tests to mean anything, and
the phrasing path is not improved by creative sampling; it writes a sentence about an already
decided result.

**Every call starts from a cleared KV cache.** No conversation state survives between calls, which
is what makes the same transcript produce the same items.

**CPU only.** `n_gpu_layers = 0`, and this build has no GPU backend compiled in. A GPU backend is a
separate decision that has to be measured on the device before it is taken, let alone claimed.

## What is testable without a phone, and is tested

Everything above the `LlamaRuntime` seam: prompt construction, the strict schema, the re-ask
budget, and the numeric guard. 37 tests across `NumericGuardTest` and `LlmEngineTest` run against
a runtime scripted to return truncated JSON, fenced JSON, unknown fields, string quantities,
runaway lists and fabricated calorie figures. That is the same seam as `FoodDbSource`, for the
same reason, and it means the part which cannot be tested yet is also the part with the least
logic in it.

## Verified in the container, read out of `logs/container-native.log`

    > Task :app:buildCMakeDebug[arm64-v8a]
    BUILD SUCCESSFUL

    lib/arm64-v8a/libkatori_llama.so      73,944
    lib/arm64-v8a/libllama.so          3,841,328  (compressed in the APK)
    NEEDED  libllama.so, libggml.so, libggml-base.so, libggml-cpu.so, liblog.so
    T  Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeLoad
    T  Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeGenerate
    T  Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeFree

The demo APK is 46 MB, arm64-v8a only, and its permissions are still exactly CAMERA,
RECORD_AUDIO and the platform receiver permission.

## The first thing to do when a phone is attached

Load the model, run the real extraction prompt, and read the result out of logcat. Until that
happens this file describes code that has never executed.
