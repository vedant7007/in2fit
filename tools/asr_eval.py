"""
Word and character error rate of the shipped ASR models, on the desktop, through the real runtime.

    python asr_eval.py synth <out-dir>            write the SYNTHETIC clips and their manifest
    python asr_eval.py wer   <manifest.csv> ...   transcribe every clip in the manifest(s), print WER/CER

Needs: pip install sherpa-onnx soundfile numpy   (plus piper-tts for `synth`; Windows for its English voices)
Models: data-sources/models/asr/indicconformer/, as tools/fetch-models.ps1 lays them out.

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


def recognizer(lang: str, threads: int = 4):
    import sherpa_onnx  # imported late so `synth` works without it

    model = MODELS / lang / "model.int8.onnx"
    tokens = MODELS / ("en/tokens.txt" if lang == "en" else "tokens.txt")
    for f in (model, tokens):
        if not f.is_file():
            sys.exit(f"missing {f}; run tools/4-fetch-models.bat")
    return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(model), tokens=str(tokens), num_threads=threads, sample_rate=16000, feature_dim=80,
        decoding_method="greedy_search",
    )


def transcribe(rec, wav: Path) -> tuple[str, list[str], float, float]:
    import soundfile as sf

    samples, sr = sf.read(wav, dtype="float32")
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    stream = rec.create_stream()
    stream.accept_waveform(sr, samples)
    t = time.perf_counter()
    rec.decode_stream(stream)
    return stream.result.text, list(stream.result.tokens), (time.perf_counter() - t) * 1000, len(samples) / sr


def cmd_wer(manifests: list[Path]) -> None:
    rows = []
    for m in manifests:
        with open(m, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                r["path"] = (m.parent / r["path"]).resolve()
                rows.append(r)
    if not rows:
        sys.exit("no rows")

    print(f"machine: {platform.processor() or platform.machine()}, {platform.system()}; sherpa-onnx desktop, 4 threads")
    print("DESKTOP TIMINGS. Not phone numbers. See the module docstring about free RAM.\n")

    per_lang = defaultdict(lambda: {"w_err": 0, "w_ref": 0, "c_err": 0, "c_ref": 0, "n": 0, "ms": 0.0, "audio": 0.0, "pieces": 0, "sources": set()})
    recs = {}
    for r in rows:
        lang = r["language"]
        if lang not in recs:
            t = time.perf_counter()
            recs[lang] = recognizer(lang)
            print(f"[{lang}] recognizer created in {time.perf_counter() - t:.2f}s")
        hyp, tokens, ms, secs = transcribe(recs[lang], r["path"])
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


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "synth":
        cmd_synth(Path(sys.argv[2]))
    elif len(sys.argv) >= 3 and sys.argv[1] == "wer":
        cmd_wer([Path(p) for p in sys.argv[2:]])
    else:
        sys.exit(__doc__)
