"""
Word and character error rate of the shipped ASR models, on the desktop, through the real runtime.

    python asr_eval.py synth <out-dir>            write the SYNTHETIC clips and their manifest
    python asr_eval.py synth-mixed <out-dir>      write the SYNTHETIC code-mixed clips of 0022 (needs the Hindi voice)
    python asr_eval.py wer   <manifest.csv> ...   transcribe every clip in the manifest(s), print WER/CER
    python asr_eval.py wer --engine omnilingual <manifest.csv> ...
                                                  same, through Meta Omnilingual ASR CTC-300M (0022): one
                                                  model for every row, and the language column is then a label
    python asr_eval.py synth-csv <set.csv> <out-dir>
                                                  synthesise an authored set (data-authoring/demo-utterance-set.csv:
                                                  columns id,language,spoken,reference,expected_foods; te and hi
                                                  through the Piper voices, en through a Windows voice) into clips
                                                  named <id>_<lang>.wav plus a manifest marked synthetic-demo
    python asr_eval.py sheet <recordings-dir>     write transcription-sheet.md for the rows of manifest.csv that
                                                  have no reference (the own-words items), with what the
                                                  recogniser heard pre-filled as a starting point; a person
                                                  writes the true words under each and runs `sheet-import`
    python asr_eval.py sheet-import <recordings-dir>
                                                  read the filled sheet back into manifest.csv
    python asr_eval.py manifest <recordings-dir> [set.csv]
                                                  write manifest.csv for a folder of REAL recordings named
                                                  <speaker>_<te|hi|en>_<nn>.<any audio ext>. Without set.csv the
                                                  numbers are the 13 sentences of data-authoring/asr-recording-script.md
                                                  (1-11 filled, 12-13 left blank for a person to transcribe); with
                                                  set.csv (e.g. data-authoring/demo-utterance-set.csv) the numbers
                                                  are that set's ids and the reference is its row for the file's
                                                  language. Non-WAV recordings get a 16 kHz WAV twin for the phone probe

Needs: pip install sherpa-onnx soundfile numpy   (plus piper-tts for `synth`; Windows for its English voices;
ffmpeg on PATH for phone recordings: libsndfile reads wav/flac/mp3/ogg, ffmpeg decodes m4a/aac/3gp)

Models: data-sources/models/asr/indicconformer/, as tools/fetch-models.ps1 lays them out, and for
--engine omnilingual data-sources/models/asr/candidates/omnilingual-300m-ctc-int8/{model.int8.onnx,tokens.txt}
(csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12, Apache-2.0; 0022).

THE MANIFEST is one CSV per test set, one row per clip:

    path,language,source,reference,expected_foods
    te_padma_rotis_pappu.wav,te,synthetic,నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను,roti;dal

`path` is relative to the manifest. `language` is te/hi/en and picks the model, because language is a
parameter of the engine and never detected. `source` is `synthetic` or `recorded`, and the report
prints it in capitals on every table, because a synthetic set is CIRCULAR BY CONSTRUCTION (0006, the
MatchRateTest precedent): the Telugu clips are Piper speaking, trained on the same AI4Bharat data
the recogniser was, and the English clips are a Windows voice with no Indian accent. Those numbers
say the model loads and is not garbage. They are not accuracy and they do not go on a slide.
`reference` is what was said, in the script the model emits. `expected_foods` is for the ON-DEVICE
extraction check (AsrDeviceTest), which this script cannot run: extraction needs the LLM and the
LLM runs on the phone.

When the recorded meal logs arrive (spec 18.3), they get a manifest with `source=recorded` and this
same command, and that is the number that replaces these.

WER is on whitespace-separated words after NFC normalisation, punctuation stripped, Latin lower-cased.
CER is on characters with spaces removed, which for an abugida is closer to what a food matcher sees:
one wrong vowel sign is one character, not a whole wrong word. Digits are NOT normalised to words:
"200" against "two hundred" counts as two errors here, and it is a real difference the LLM has to absorb.

Timing is printed per clip and labelled with the machine. It is a DESKTOP figure. On 20 Sep 2026 the
same clip decoded in 100 ms with 6 GB free and 3,000 ms with 450 MB free, so check free RAM before
believing a slow number, and never quote either as a phone number.
"""
from __future__ import annotations

import csv
import platform
import sys
import tempfile
import time
import unicodedata
from collections import defaultdict
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parents[1]  # tools/ -> repo root
MODELS = REPO / "data-sources" / "models" / "asr" / "indicconformer"
PUNCT = set(".,!?;:\"'()[]{}।॥-–—")


def normalise(text: str) -> list[str]:
    text = unicodedata.normalize("NFC", text)
    text = "".join(" " if ch in PUNCT else ch for ch in text)
    return text.lower().split()


def edit_distance(a: list, b: list) -> int:
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def recognizer(lang: str, threads: int = 4, engine: str = "indicconformer"):
    import sherpa_onnx  # imported late so `synth` works without it

    if engine == "omnilingual":
        d = REPO / "data-sources" / "models" / "asr" / "candidates" / "omnilingual-300m-ctc-int8"
        for f in (d / "model.int8.onnx", d / "tokens.txt"):
            if not f.is_file():
                sys.exit(f"missing {f}; see docs/decisions/0022 for the source")
        return sherpa_onnx.OfflineRecognizer.from_omnilingual_asr_ctc(
            model=str(d / "model.int8.onnx"), tokens=str(d / "tokens.txt"), num_threads=threads,
        )
    model = MODELS / lang / "model.int8.onnx"
    tokens = MODELS / ("en/tokens.txt" if lang == "en" else "tokens.txt")
    for f in (model, tokens):
        if not f.is_file():
            sys.exit(f"missing {f}; run tools/4-fetch-models.bat")
    return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(model), tokens=str(tokens), num_threads=threads, sample_rate=16000, feature_dim=80,
        decoding_method="greedy_search",
    )


def load_audio(path: Path):
    """float32 mono samples and rate. libsndfile first; anything it cannot open (m4a, aac, 3gp) goes
    through ffmpeg, decoded to 16 kHz mono in memory, which is what a phone recorder hands us."""
    import numpy as np
    import soundfile as sf

    try:
        samples, sr = sf.read(path, dtype="float32")
        if samples.ndim > 1:
            samples = samples.mean(axis=1)
        return samples, sr
    except Exception:
        import subprocess
        out = subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", "16000", "-"],
            capture_output=True, check=True,
        ).stdout
        return np.frombuffer(out, dtype=np.float32), 16000


def transcribe(rec, wav: Path) -> tuple[str, list[str], float, float]:
    samples, sr = load_audio(wav)
    stream = rec.create_stream()
    stream.accept_waveform(sr, samples)
    t = time.perf_counter()
    rec.decode_stream(stream)
    return stream.result.text, list(stream.result.tokens), (time.perf_counter() - t) * 1000, len(samples) / sr


def cmd_wer(manifests: list[Path], engine: str = "indicconformer") -> None:
    rows = []
    for m in manifests:
        with open(m, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                r["path"] = (m.parent / r["path"]).resolve()
                rows.append(r)
    if not rows:
        sys.exit("no rows")

    print(f"machine: {platform.processor() or platform.machine()}, {platform.system()}; sherpa-onnx desktop, 4 threads; engine: {engine}")
    print("DESKTOP TIMINGS. Not phone numbers. See the module docstring about free RAM.\n")

    per_lang = defaultdict(lambda: {"w_err": 0, "w_ref": 0, "c_err": 0, "c_ref": 0, "n": 0, "ms": 0.0, "audio": 0.0, "pieces": 0, "sources": set()})
    recs = {}
    for r in rows:
        lang = r["language"]
        key = lang if engine == "indicconformer" else engine
        if key not in recs:
            t = time.perf_counter()
            recs[key] = recognizer(lang, engine=engine)
            print(f"[{key}] recognizer created in {time.perf_counter() - t:.2f}s")
        hyp, tokens, ms, secs = transcribe(recs[key], r["path"])
        if not r["reference"].strip():
            print(f"[{lang}] {r['source'].upper():9s} {r['path'].name:32s} {secs:4.1f}s audio  {ms:6.0f} ms  {len(tokens):3d} pieces  (no reference; not in WER)")
            print(f"      hyp: {hyp}")
            continue
        ref_w, hyp_w = normalise(r["reference"]), normalise(hyp)
        ref_c, hyp_c = list("".join(ref_w)), list("".join(hyp_w))
        w_err, c_err = edit_distance(ref_w, hyp_w), edit_distance(ref_c, hyp_c)
        s = per_lang[lang]
        s["w_err"] += w_err; s["w_ref"] += len(ref_w); s["c_err"] += c_err; s["c_ref"] += len(ref_c)
        s["n"] += 1; s["ms"] += ms; s["audio"] += secs; s["pieces"] += len(tokens); s["sources"].add(r["source"].upper())
        mark = "exact" if w_err == 0 else f"{w_err} word err, {c_err} char err"
        print(f"[{lang}] {r['source'].upper():9s} {r['path'].name:32s} {secs:4.1f}s audio  {ms:6.0f} ms  {len(tokens):3d} pieces  {mark}")
        print(f"      ref: {r['reference']}")
        print(f"      hyp: {hyp}")

    print("\nlang  source     clips   WER      CER    pieces/s  decode ms/clip")
    for lang, s in sorted(per_lang.items()):
        wer = 100.0 * s["w_err"] / max(1, s["w_ref"])
        cer = 100.0 * s["c_err"] / max(1, s["c_ref"])
        print(f"{lang:4s}  {'+'.join(sorted(s['sources'])):9s} {s['n']:5d}  {wer:5.1f}%  {cer:5.1f}%    {s['pieces'] / s['audio']:5.1f}     {s['ms'] / s['n']:6.0f}")
    if any("SYNTHETIC" in s["sources"] for s in per_lang.values()):
        print("\nSYNTHETIC rows are circular by construction and are not an accuracy claim. See the docstring.")
    print("extraction accuracy: NOT MEASURED HERE. It needs the LLM, which runs on the phone: AsrDeviceTest.")


def cmd_synth(out: Path) -> None:
    """The set used for the 20 Sep 2026 smoke test, regenerated. Windows voices for English, Piper for Telugu."""
    import wave

    out.mkdir(parents=True, exist_ok=True)
    rows = []

    # English: Windows SAPI, 16 kHz mono. No Indian accent on this machine, which is a stated weakness.
    if platform.system() == "Windows":
        import subprocess
        english = [
            ("en_zira_rotis", "Microsoft Zira Desktop", "I ate two rotis and dal for lunch", "roti;dal"),
            ("en_david_idli", "Microsoft David Desktop", "I had three idli and sambar for breakfast", "idli;sambar"),
            ("en_hazel_mixed", "Microsoft Hazel Desktop", "two rotis and pappu with a glass of milk", "roti;dal;milk"),
            ("en_zira_egg", "Microsoft Zira Desktop", "I drank 200 ml of milk and ate one boiled egg", "milk;egg"),
        ]
        ps = ["Add-Type -AssemblyName System.Speech",
              "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
              "$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)"]
        for name, voice, text, foods in english:
            ps.append(f"$s.SelectVoice('{voice}'); $s.SetOutputToWaveFile('{out / name}.wav', $fmt); $s.Speak('{text}'); $s.SetOutputToNull()")
            rows.append((f"{name}.wav", "en", "synthetic", text, foods))
        subprocess.run(["powershell", "-NoProfile", "-Command", "; ".join(ps)], check=True)

    # Telugu: the Piper voice the app ships for TTS. Same AI4Bharat data family as the recogniser,
    # so this is the OPTIMISTIC case, and the manifest says synthetic.
    from piper import PiperVoice
    voice = PiperVoice.load(str(REPO / "data-sources/models/tts/piper/te_IN-padmavathi-medium.onnx"))
    telugu = [
        ("te_padma_rotis_pappu", "నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను", "roti;dal"),
        ("te_padma_idli_sambar", "ఉదయం మూడు ఇడ్లీ సాంబార్ తిన్నాను", "idli;sambar"),
        ("te_padma_codemix", "టూ రోటీస్ అండ్ పప్పు తిన్నాను", "roti;dal"),
        ("te_padma_milk_egg", "ఒక గ్లాసు పాలు మరియు ఒక ఉడికించిన గుడ్డు తిన్నాను", "milk;egg"),
    ]
    for name, text, foods in telugu:
        with wave.open(str(out / f"{name}.wav"), "wb") as w:
            voice.synthesize_wav(text, w)
        rows.append((f"{name}.wav", "te", "synthetic", text, foods))

    with open(out / "manifest.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["path", "language", "source", "reference", "expected_foods"])
        w.writerows(rows)
    print(f"wrote {len(rows)} clips and manifest.csv to {out}")


def cmd_synth_mixed(out: Path) -> None:
    """The spliced code-mixed clips of 0022: Piper te for Telugu, Piper hi for Hindi, a Windows voice for
    English, joined with 250 ms gaps. A speaker change mid-sentence is NOT how a person code-mixes, and
    the English parts have no Indian accent; the manifest says synthetic-mixed and 0022 says why they
    overstate the English problem. They exist so the tables in 0022 can be regenerated."""
    import io
    import subprocess
    import wave

    import numpy as np
    from piper import PiperVoice

    out.mkdir(parents=True, exist_ok=True)
    voices = REPO / "data-sources/models/tts/piper"
    te = PiperVoice.load(str(voices / "te_IN-padmavathi-medium.onnx"))
    hi = PiperVoice.load(str(voices / "hi_IN-pratham-medium.onnx"))  # CC-BY-NC-SA-4.0, test audio only, 0005 register

    def resample(x, a, b):
        return x if a == b else np.interp(np.linspace(0, len(x) - 1, int(len(x) * b / a)), np.arange(len(x)), x).astype(np.float32)

    def piper(voice, text):
        buf = io.BytesIO()
        with wave.open(buf, "wb") as w:
            voice.synthesize_wav(text, w)
        buf.seek(0)
        with wave.open(buf, "rb") as w:
            sr = w.getframerate()
            x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        return resample(x, sr, 16000)

    def sapi(text, voice="Microsoft Zira Desktop"):
        p = out / "_tmp_en.wav"
        ps = ("Add-Type -AssemblyName System.Speech; $s=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
              "$fmt=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono); "
              f"$s.SelectVoice('{voice}'); $s.SetOutputToWaveFile('{p}',$fmt); $s.Speak('{text}'); $s.SetOutputToNull()")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True)
        with wave.open(str(p), "rb") as w:
            x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        p.unlink()
        return x

    gap = np.zeros(4000, np.float32)
    clips = [
        ("hi_pratham_roti_dal", "hi", [piper(hi, "मैंने दो रोटी और दाल खाई")], "मैंने दो रोटी और दाल खाई", "roti;dal"),
        ("hi_pratham_idli_sambar", "hi", [piper(hi, "सुबह तीन इडली और सांबर खाया")], "सुबह तीन इडली और सांबर खाया", "idli;sambar"),
        ("hi_pratham_hinglish", "hi", [piper(hi, "टू रोटी एंड दाल खाई")], "टू रोटी एंड दाल खाई", "roti;dal"),
        ("mix_te_frame_hi_words", "te", [piper(te, "నేను ఉదయం"), piper(hi, "दो रोटी और दाल"), piper(te, "తిన్నాను")], "నేను ఉదయం दो रोटी और दाल తిన్నాను", "roti;dal"),
        ("mix_hi_frame_te_words", "hi", [piper(hi, "मैंने सुबह"), piper(te, "రెండు ఇడ్లీ సాంబార్"), piper(hi, "खाया")], "मैंने सुबह రెండు ఇడ్లీ సాంబార్ खाया", "idli;sambar"),
        ("mix_te_frame_en_words", "te", [piper(te, "నేను"), sapi("two rotis and dal"), piper(te, "తిన్నాను")], "నేను two rotis and dal తిన్నాను", "roti;dal"),
        ("mix_hi_frame_en_words", "hi", [piper(hi, "मैंने"), sapi("one glass of milk"), piper(hi, "पिया")], "मैंने one glass of milk पिया", "milk"),
        ("mix_en_frame_te_words", "en", [sapi("for lunch I had"), piper(te, "పప్పు మరియు అన్నం")], "for lunch I had పప్పు మరియు అన్నం", "dal;rice"),
    ]
    rows = []
    for name, lang, parts, ref, foods in clips:
        x = np.concatenate(sum([[p, gap] for p in parts], [])[:-1])
        with wave.open(str(out / f"{name}.wav"), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(16000)
            w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
        rows.append((f"{name}.wav", lang, "synthetic-mixed", ref, foods))
    with open(out / "manifest.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["path", "language", "source", "reference", "expected_foods"])
        w.writerows(rows)
    print(f"wrote {len(rows)} clips and manifest.csv to {out}")

# The fixed sentences of data-authoring/asr-recording-script.md, by number. The reference is what the
# page asks the speaker to read, in the script the page prints it in; a code-mixed row (3, 7) therefore
# has Latin words inside an Indic sentence, and its WER is script-strict against an Indic-script model.
# Read CER on those rows. 12 and 13 are the speaker's own words and get a reference only when a person
# has transcribed the recording.
SCRIPT_SENTENCES = {
    1: ("te", "నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను", "roti;dal"),
    2: ("te", "ఉదయం మూడు ఇడ్లీ, సాంబార్, కొబ్బరి పచ్చడి తిన్నాను", "idli;sambar;coconut chutney"),
    3: ("te", "రాత్రి two rotis, dal fry, one glass milk తీసుకున్నాను", "roti;dal;milk"),
    4: ("te", "నాకు షుగర్ ఉంది, రాత్రి ఏం తినాలి?", ""),
    5: ("te", "నేను ఒకటి కాదు, రెండు దోసెలు తిన్నాను, పచ్చడితో", "dosa;chutney"),
    6: ("hi", "मैंने दो रोटी, दाल और थोड़ा चावल खाया", "roti;dal;rice"),
    7: ("hi", "सुबह one glass milk और दो boiled eggs लिए", "milk;egg"),
    8: ("hi", "मुझे iron कम है, क्या खाना चाहिए?", ""),
    9: ("en", "For lunch I had two rotis, some pappu and a katori of curd.", "roti;dal;curd"),
    10: ("en", "I drank 200 ml of milk and ate one boiled egg.", "milk;egg"),
    11: ("en", "I have anaemia, what should I eat for iron?", ""),
    12: (None, "", ""),
    13: (None, "", ""),
}


def _voices():
    """te and hi Piper voices plus a Windows English voice, each as text -> (float32 16 kHz mono)."""
    import io
    import subprocess
    import wave

    import numpy as np
    from piper import PiperVoice

    voices = REPO / "data-sources/models/tts/piper"
    piper_te = PiperVoice.load(str(voices / "te_IN-padmavathi-medium.onnx"))
    piper_hi = PiperVoice.load(str(voices / "hi_IN-pratham-medium.onnx"))  # CC-BY-NC-SA-4.0, test audio only

    def resample(x, a, b):
        return x if a == b else np.interp(np.linspace(0, len(x) - 1, int(len(x) * b / a)), np.arange(len(x)), x).astype(np.float32)

    def piper(voice):
        def say(text):
            buf = io.BytesIO()
            with wave.open(buf, "wb") as w:
                voice.synthesize_wav(text, w)
            buf.seek(0)
            with wave.open(buf, "rb") as w:
                sr = w.getframerate()
                x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
            return resample(x, sr, 16000)
        return say

    def sapi(text, voice="Microsoft Zira Desktop"):
        p = Path(tempfile.gettempdir()) / "asr_eval_sapi.wav"
        safe = text.replace("'", "''")
        ps = ("Add-Type -AssemblyName System.Speech; $s=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
              "$fmt=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono); "
              f"$s.SelectVoice('{voice}'); $s.SetOutputToWaveFile('{p}',$fmt); $s.Speak('{safe}'); $s.SetOutputToNull()")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True)
        with wave.open(str(p), "rb") as w:
            x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        p.unlink()
        return x

    return {"te": piper(piper_te), "hi": piper(piper_hi), "en": sapi}


def write_wav(path: Path, x) -> None:
    import wave

    import numpy as np

    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def cmd_synth_csv(csv_path: Path, out: Path) -> None:
    """One clip per row of an authored set. The Piper Hindi voice reads Latin letters as letters, so a
    Hinglish `spoken` text is voiced from `reference` (the Devanagari spelling of the same words); the
    English voice reads `spoken`. Synthetic, one voice per language, labelled synthetic-demo."""
    out.mkdir(parents=True, exist_ok=True)
    say = _voices()
    rows = []
    with open(csv_path, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(l for l in f if not l.startswith("#")):
            lang = r["language"]
            text = r["spoken"] if lang == "en" else r["reference"]
            name = f"{int(r['id']):02d}_{lang}.wav"
            write_wav(out / name, say[lang](text))
            rows.append((name, lang, "synthetic-demo", r["reference"], r.get("expected_foods", "")))
    with open(out / "manifest.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["path", "language", "source", "reference", "expected_foods"])
        w.writerows(rows)
    print(f"wrote {len(rows)} clips and manifest.csv to {out} (SYNTHETIC-DEMO: one voice per language, not the presenter)")


def cmd_sheet(folder: Path) -> None:
    """The own-words rows, with the recogniser's hypothesis under each so the transcriber corrects rather
    than types from nothing. Meant to be sent to the speaker the same evening: 'is this what you said?'"""
    manifest = folder / "manifest.csv"
    rows = list(csv.DictReader(open(manifest, encoding="utf-8", newline="")))
    todo = [r for r in rows if not r["reference"].strip()]
    if not todo:
        sys.exit("every row has a reference; nothing to transcribe")
    recs = {}
    lines = ["# Transcription sheet", "",
             "For each clip: play it, read what the recogniser heard, and write what was ACTUALLY said on the",
             "`said:` line, in the script of that language (Telugu in Telugu script, Hindi in Devanagari, English",
             "words as the speaker would write them). If the recogniser was right, copy its line. Then list the",
             "foods named, in English, separated by `;`, on the `foods:` line (leave empty for a question).",
             "Ask the speaker if unsure: they were there. Then run `python tools/asr_eval.py sheet-import <folder>`.", ""]
    for r in todo:
        lang = r["language"]
        if lang not in recs:
            recs[lang] = recognizer(lang)
        hyp, _, _, secs = transcribe(recs[lang], folder / r["path"])
        lines += [f"## {r['path']}  ({lang}, {secs:.1f}s)", f"heard: {hyp}", "said: ", "foods: ", ""]
    (folder / "transcription-sheet.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {folder / 'transcription-sheet.md'} with {len(todo)} clip(s) to transcribe")


def cmd_sheet_import(folder: Path) -> None:
    import re

    text = (folder / "transcription-sheet.md").read_text(encoding="utf-8")
    filled = {}
    for block in re.split(r"^## ", text, flags=re.M)[1:]:
        name = block.split()[0]
        said = re.search(r"^said:\s*(.*)$", block, flags=re.M)
        foods = re.search(r"^foods:\s*(.*)$", block, flags=re.M)
        if said and said.group(1).strip():
            filled[name] = (said.group(1).strip(), (foods.group(1).strip() if foods else ""))
    manifest = folder / "manifest.csv"
    rows = list(csv.DictReader(open(manifest, encoding="utf-8", newline="")))
    n = 0
    for r in rows:
        if r["path"] in filled and not r["reference"].strip():
            r["reference"], r["expected_foods"] = filled[r["path"]]
            n += 1
    with open(manifest, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["path", "language", "source", "reference", "expected_foods"])
        w.writeheader()
        w.writerows(rows)
    still = sum(1 for r in rows if not r["reference"].strip())
    print(f"imported {n} transcription(s) into {manifest}; {still} row(s) still without a reference")


def cmd_manifest(folder: Path, set_csv: Path | None = None) -> None:
    import re

    sentences = SCRIPT_SENTENCES
    if set_csv is not None:
        # (id, language) -> (language, reference, foods), from an authored set such as the demo one.
        sentences = {}
        with open(set_csv, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(l for l in f if not l.startswith("#")):
                sentences[(int(r["id"]), r["language"])] = (r["language"], r["reference"], r.get("expected_foods", ""))

    pattern = re.compile(r"^(?P<speaker>[^_]+)_(?P<lang>te|hi|en)_(?P<n>\d{1,2})\.(?P<ext>[A-Za-z0-9]+)$")
    rows, skipped = [], []
    for f in sorted(folder.iterdir()):
        m = pattern.match(f.name)
        if not m or f.suffix.lower() in (".csv", ".md", ".txt"):
            skipped.append(f.name)
            continue
        n = int(m["n"])
        lang = m["lang"]
        key = (n, lang) if set_csv is not None else n
        if key not in sentences:
            skipped.append(f.name)
            continue
        script_lang, reference, foods = sentences[key]
        if script_lang and script_lang != lang:
            print(f"note: {f.name} is sentence {n}, which the script prints as {script_lang}; keeping the file's {lang}")
        # The phone probe (AsrDeviceTest) reads 16-bit WAV only, so a phone recording gets a 16 kHz
        # mono WAV twin here and the manifest points at that; the folder then stages on the device as is.
        name = f.name
        if f.suffix.lower() != ".wav":
            import subprocess
            wav = f.with_suffix(".wav")
            if not wav.exists():
                subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(f), "-ac", "1", "-ar", "16000", "-sample_fmt", "s16", str(wav)], check=True)
            name = wav.name
        rows.append((name, lang, "recorded", reference, foods))
    with open(folder / "manifest.csv", "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["path", "language", "source", "reference", "expected_foods"])
        w.writerows(rows)
    speakers = sorted({r[0].split("_")[0] for r in rows})
    blank = sum(1 for r in rows if not r[3])
    print(f"wrote {len(rows)} rows for {len(speakers)} speaker(s) {speakers} to {folder / 'manifest.csv'}")
    print(f"{blank} own-words row(s) have no reference yet: transcribe them into the csv by hand before quoting WER on them")
    if skipped:
        print(f"skipped (name does not match <speaker>_<te|hi|en>_<nn>.<ext>): {skipped}")
    print(f"RECORDED, {len(speakers)} speaker(s): enough to tell whether it works at all, not an accuracy claim.")


if __name__ == "__main__":
    args = sys.argv[1:]
    engine = "indicconformer"
    if "--engine" in args:
        i = args.index("--engine")
        engine = args[i + 1]
        del args[i:i + 2]
    if len(args) >= 2 and args[0] == "synth":
        cmd_synth(Path(args[1]))
    elif len(args) >= 2 and args[0] == "synth-mixed":
        cmd_synth_mixed(Path(args[1]))
    elif len(args) >= 2 and args[0] == "wer":
        cmd_wer([Path(p) for p in args[1:]], engine=engine)
    elif len(args) >= 2 and args[0] == "manifest":
        cmd_manifest(Path(args[1]), Path(args[2]) if len(args) >= 3 else None)
    elif len(args) >= 3 and args[0] == "synth-csv":
        cmd_synth_csv(Path(args[1]), Path(args[2]))
    elif len(args) >= 2 and args[0] == "sheet":
        cmd_sheet(Path(args[1]))
    elif len(args) >= 2 and args[0] == "sheet-import":
        cmd_sheet_import(Path(args[1]))
    else:
        sys.exit(__doc__)
