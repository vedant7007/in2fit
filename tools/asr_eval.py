"""
Word and character error rate of the shipped ASR models, on the desktop, through the real runtime.

    python asr_eval.py synth <out-dir>            write the SYNTHETIC clips and their manifest
    python asr_eval.py synth-mixed <out-dir>      write the SYNTHETIC code-mixed clips of 0031 (needs the Hindi voice)
    python asr_eval.py wer   <manifest.csv> ...   transcribe every clip in the manifest(s), print WER/CER
    python asr_eval.py wer --engine omnilingual <manifest.csv> ...
                                                  same, through Meta Omnilingual ASR CTC-300M (0031): one
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
    python asr_eval.py robustness <manifest.csv> [--engine ...]
                                                  the crowded-hall test: every clip of the manifest under many-voice
                                                  babble at several SNRs, under arm's-length and across-the-room
                                                  distance (attenuation + synthetic reverb), and both together;
                                                  prints exact n/N per condition, and runs the energy endpointer on
                                                  the same audio to show where it stops seeing the end of a sentence
    python asr_eval.py tail <manifest.csv>         push-to-talk's thumb question: cut each clip N ms after (or, negative, before) the last
                                                  word (N = -300..1000), clean and in +15 dB babble, and
                                                  print exact n/N per cut, so "let go a beat after the last word"
                                                  has a number for the beat
    python asr_eval.py manifest <recordings-dir> [set.csv]
                                                  (two presenters? name the files <name>_hi_01 .. for each, same folder;
                                                  `wer` then prints a by-speaker table against the same references)
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
(csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12, Apache-2.0; 0031).

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
                sys.exit(f"missing {f}; see docs/decisions/0031 for the source")
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

    def bucket():
        return {"w_err": 0, "w_ref": 0, "c_err": 0, "c_ref": 0, "n": 0, "exact": 0, "ms": 0.0, "audio": 0.0, "pieces": 0, "sources": set()}

    per_lang = defaultdict(bucket)
    per_speaker = defaultdict(bucket)   # (speaker, lang) for recorded rows: the second-presenter comparison
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
        targets = [per_lang[lang]]
        if r["source"].lower().startswith("recorded") and "_" in r["path"].stem:
            targets.append(per_speaker[(r["path"].stem.split("_")[0], lang)])
        for s in targets:
            s["w_err"] += w_err; s["w_ref"] += len(ref_w); s["c_err"] += c_err; s["c_ref"] += len(ref_c)
            s["n"] += 1; s["exact"] += int(w_err == 0); s["ms"] += ms; s["audio"] += secs; s["pieces"] += len(tokens); s["sources"].add(r["source"].upper())
        mark = "exact" if w_err == 0 else f"{w_err} word err, {c_err} char err"
        print(f"[{lang}] {r['source'].upper():9s} {r['path'].name:32s} {secs:4.1f}s audio  {ms:6.0f} ms  {len(tokens):3d} pieces  {mark}")
        print(f"      ref: {r['reference']}")
        print(f"      hyp: {hyp}")

    def table(title, rows):
        print(f"\n{title}")
        print(f"{'':22s} source            exact     WER      CER   pieces/s  decode ms/clip")
        for key, s in rows:
            wer = 100.0 * s["w_err"] / max(1, s["w_ref"])
            cer = 100.0 * s["c_err"] / max(1, s["c_ref"])
            print(f"{key:22s} {'+'.join(sorted(s['sources'])):16s} {s['exact']:3d}/{s['n']:<3d}  {wer:5.1f}%  {cer:5.1f}%    {s['pieces'] / s['audio']:5.1f}     {s['ms'] / s['n']:6.0f}")

    table("by language", sorted(per_lang.items()))
    if per_speaker:
        table("by speaker (recorded rows)", sorted(((f"{sp} / {lg}", v) for (sp, lg), v in per_speaker.items())))
    sources = {src for s in per_lang.values() for src in s["sources"]}
    if any("SYNTHETIC" in src for src in sources):
        print("\nSYNTHETIC rows are circular by construction and are not an accuracy claim. See the docstring.")
    if any(src.endswith("-DEMO") or "DEMO" in src for src in sources):
        print("DEMO SET: report this as 'n of the ten demo sentences', never as a WER figure standing alone. These are ten")
        print("sentences we selected, tuned to the recogniser after it misheard one of them; the number is the accuracy of")
        print("those sentences, on this audio, and says nothing about the system on arbitrary speech (0031).")
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
    """The spliced code-mixed clips of 0031: Piper te for Telugu, Piper hi for Hindi, a Windows voice for
    English, joined with 250 ms gaps. A speaker change mid-sentence is NOT how a person code-mixes, and
    the English parts have no Indian accent; the manifest says synthetic-mixed and 0031 says why they
    overstate the English problem. They exist so the tables in 0031 can be regenerated."""
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



# ---------------------------------------------------------------------------------------------
# The crowded hall. Everything measured before this was clean audio at the microphone.
# ---------------------------------------------------------------------------------------------

def _babble_pool(exclude_substrings):
    """Talkers for the babble: every synthetic clip on disk whose voice is NOT the target's. Piper te
    and the Windows English voices, never the Piper hi voice the hi demo rows are spoken by."""
    import numpy as np
    pool = []
    for d in ("synthetic", "synthetic-mixed", "synthetic-demo"):
        for f in sorted((REPO / "data-sources/asr-test-set" / d).glob("*.wav")):
            if any(x in f.name for x in exclude_substrings):
                continue
            x, sr = load_audio(f)
            if sr != 16000:
                x = np.interp(np.linspace(0, len(x) - 1, int(len(x) * 16000 / sr)), np.arange(len(x)), x).astype(np.float32)
            pool.append(x)
    if len(pool) < 6:
        sys.exit("not enough babble talkers on disk; run synth and synth-mixed first")
    return pool


def _babble(pool, n_samples, talkers, rng):
    """`talkers` voices at once, each a random clip at a random offset, looped to cover the length.
    Conversational level is set by the caller through the SNR; this returns unit-RMS babble."""
    import numpy as np
    out = np.zeros(n_samples, np.float32)
    for _ in range(talkers):
        track = np.zeros(n_samples, np.float32)
        pos = -int(rng.integers(0, 16000))
        while pos < n_samples:
            clip = pool[int(rng.integers(len(pool)))]
            clip = clip / (np.sqrt(np.mean(clip ** 2)) + 1e-9)
            a, b = max(pos, 0), min(pos + len(clip), n_samples)
            if b > a:
                track[a:b] += clip[a - pos:b - pos]
            pos += len(clip) + int(rng.integers(0, 8000))
        out += track
    return out / (np.sqrt(np.mean(out ** 2)) + 1e-9)


def _reverb(x, rt60_s, drr_db, rng):
    """Direct path plus an exponentially decaying noise tail: the textbook synthetic room. `drr_db` is the
    direct-to-reverberant ratio; +10 dB is a phone at the mouth, 0 dB arm's length, -5 dB across a hall."""
    import numpy as np
    n = int(rt60_s * 16000)
    t = np.arange(n) / 16000.0
    tail = rng.standard_normal(n).astype(np.float32) * np.exp(-6.9 * t / rt60_s)
    tail[:int(0.005 * 16000)] = 0                       # nothing arrives before the direct sound
    tail /= np.sqrt(np.sum(tail ** 2)) + 1e-9
    tail *= 10 ** (-drr_db / 20)
    h = np.zeros(n, np.float32); h[0] = 1.0; h += tail
    y = np.convolve(x, h)[: len(x)].astype(np.float32)
    return y


def _endpoint(x, frame_ms=20, calibration_ms=300, onset_ms=60, hangover_ms=700, max_wait_ms=8000,
              max_utt_ms=15000, threshold_over_floor=3.0, floor_min=0.004, floor_adapt=0.05):
    """A faithful port of EnergyEndpointer.feed(), so the VAD's behaviour in babble is measured with the
    same arithmetic the phone runs, not a lookalike. Returns (onset_ms or None, end_ms or None, reason)."""
    import numpy as np
    n = int(16000 * frame_ms / 1000)
    floor = -1.0; above = 0; below = 0; elapsed = 0; onset = None
    for i in range(len(x) // n):
        rms = float(np.sqrt(np.mean(x[i * n:(i + 1) * n] ** 2)))
        elapsed += frame_ms
        if floor < 0:
            floor = rms
        thr = max(floor_min, floor) * threshold_over_floor
        if onset is None:
            if rms > thr and elapsed > calibration_ms:
                above += frame_ms
                if above >= onset_ms:
                    onset = elapsed - above
            else:
                above = 0
                floor += (rms - floor) * floor_adapt
            if elapsed >= max_wait_ms:
                return None, None, "GAVE_UP: no onset within max_wait"
        else:
            below = 0 if rms > thr else below + frame_ms
            spoken = elapsed - onset
            if below >= hangover_ms:
                return onset, elapsed - hangover_ms, "hangover"
            if spoken >= max_utt_ms:
                return onset, elapsed, "MAX_UTTERANCE: never heard the end"
    return onset, None, "clip ended before the endpointer decided"


def cmd_robustness(manifest: Path, engine: str = "indicconformer") -> None:
    import numpy as np
    rng = np.random.default_rng(20260920)
    rows = list(csv.DictReader(open(manifest, encoding="utf-8", newline="")))
    if not rows:
        sys.exit("no rows")
    langs = {r["language"] for r in rows}
    exclude = ["_hi.wav", "hi_pratham", "mix_"] if "hi" in langs else ["_te.wav", "te_padma", "mix_"]
    pool = _babble_pool(exclude)
    clips = []
    for r in rows:
        x, sr = load_audio(manifest.parent / r["path"])
        if sr != 16000:
            x = np.interp(np.linspace(0, len(x) - 1, int(len(x) * 16000 / sr)), np.arange(len(x)), x).astype(np.float32)
        clips.append((r, x))
    recs = {lang: recognizer(lang, engine=engine) for lang in langs} if engine == "indicconformer" else {lang: recognizer(lang, engine=engine) for lang in langs}

    # (name, babble SNR dB or None, distance preset or None, burst). Distance presets: gain dB, DRR dB, RT60 s.
    # `burst` adds one louder talker (a laugh, a PA line) for 2 s starting 300 ms after the sentence ends,
    # 10 dB above the babble: the thing that keeps an open-listening endpointer from ever hearing the pause.
    DIST = {"close (at the mouth)": (0, 10, 0.3), "arm's length": (-12, 0, 0.6), "across the room": (-20, -5, 0.8)}
    conditions = [("clean, at the mouth", None, None, False)]
    conditions += [(f"babble SNR {snr:+d} dB, at the mouth", snr, None, False) for snr in (20, 15, 10, 5, 0)]
    conditions += [(f"babble SNR {snr:+d} dB + burst after the sentence", snr, None, True) for snr in (20, 15)]
    conditions += [(f"{d}, quiet room", None, d, False) for d in ("arm's length", "across the room")]
    conditions += [(f"arm's length + babble SNR {snr:+d} dB", snr, "arm's length", False) for snr in (15, 10, 5)]
    conditions += [("across the room + babble SNR +10 dB", 10, "across the room", False)]

    print(f"machine: {platform.processor() or platform.machine()}, {platform.system()}; engine: {engine}; {len(clips)} clips, {len(pool)} babble talker clips")
    print("SYNTHETIC HALL: babble is 12 other synthetic voices at once; reverb is a textbook exponential tail; the")
    print("presenter's voice is a Windows/Piper voice, not a person. Report as 'n of the ten' per condition, never as")
    print("a WER standing alone, and never as a claim about the hall itself: it says where THIS recogniser breaks (0031).\n")
    print(f"{'condition':46s} cut right  as heard   endpointer, open listening: no onset / clean end / ran on (mean end delay)")
    lead = int(2.0 * 16000)   # babble-only lead-in and 3 s tail, so the VAD has to find the sentence and its end
    tail = int(3.0 * 16000)
    for name, snr, dist, burst in conditions:
        exact = 0; w_err = 0; w_ref = 0; heard_exact = 0; no_onset = 0; ends = 0; ran_on = 0; delays = []
        for r, x in clips:
            speech = x.copy()
            if dist is not None:
                gain_db, drr, rt60 = DIST[dist]
                speech = _reverb(speech, rt60, drr, rng) * 10 ** (gain_db / 20)
            padded = np.concatenate([np.zeros(lead, np.float32), speech, np.zeros(tail, np.float32)])
            if snr is not None:
                # SNR is set against the speech as it reaches the microphone, so distance lowers it further only
                # through the gain applied above; the babble itself is at a fixed conversational level.
                b = _babble(pool, len(padded), talkers=12, rng=rng)
                s_rms = np.sqrt(np.mean(speech ** 2)) + 1e-9
                b_level = s_rms / 10 ** (snr / 20)
                padded = padded + b * b_level
                if burst:
                    start = lead + len(speech) + int(0.3 * 16000)
                    one = _babble(pool, 2 * 16000, talkers=1, rng=rng) * b_level * 10 ** (10 / 20)
                    padded[start:start + len(one)] += one[: max(0, min(len(one), len(padded) - start))]
            padded = np.clip(padded, -1, 1).astype(np.float32)
            # recogniser: on the sentence region only, the way a working endpointer would cut it
            region = padded[lead:lead + len(speech)]
            stream = recs[r["language"]].create_stream(); stream.accept_waveform(16000, region); recs[r["language"]].decode_stream(stream)
            hyp = stream.result.text
            ref_w, hyp_w = normalise(r["reference"]), normalise(hyp)
            e = edit_distance(ref_w, hyp_w); w_err += e; w_ref += len(ref_w); exact += int(e == 0)
            # endpointer: on the whole padded clip, babble lead-in included
            onset, end, why = _endpoint(padded)
            true_end_ms = (lead + len(speech)) * 1000 // 16000
            if onset is None:
                no_onset += 1
            elif why == "hangover":
                ends += 1; delays.append(end - true_end_ms)
            else:
                ran_on += 1
            # "as heard": what open listening would actually hand the recogniser. No onset = nothing at all;
            # otherwise the clip from 200 ms before the onset (the engine's pre-roll) to the detected end, or
            # to the end of the audio when it ran on. Scored exact-or-not against the same reference.
            if onset is not None:
                a = max(0, (onset - 200) * 16)
                b = (end + 700) * 16 if end is not None else len(padded)
                stream = recs[r["language"]].create_stream(); stream.accept_waveform(16000, padded[a:b]); recs[r["language"]].decode_stream(stream)
                heard_exact += int(edit_distance(ref_w, normalise(stream.result.text)) == 0)
        n = len(clips)
        delay = f"{np.mean(delays):+.0f} ms" if delays else "-"
        print(f"{name:46s} {exact:2d}/{n:<3d}      {heard_exact:2d}/{n:<3d}     no onset {no_onset}/{n}, clean end {ends}/{n}, ran on {ran_on}/{n} ({delay})")
    print("\n'cut right' = exact of N with the sentence cut at its true edges, the recogniser's own floor; 'as heard' = exact of N")
    print("on the clip the open-listening endpointer would actually hand over (nothing when it never started; the")
    print("sentence plus whatever hall it kept when it closed late; the tail it dropped when it closed early).")
    print("endpointer columns, open listening with 2 s of hall before the sentence and 3 s after: 'no onset' = the VAD")
    print("never started, the app says it heard nothing; 'clean end' = it closed on the 700 ms hangover, with the mean")
    print("delay of that close against the true end of speech (a positive delay is that much hall audio handed to the")
    print("recogniser along with the sentence); 'ran on' = it had not closed 3 s after the sentence ended (on the phone")
    print("it runs to the 15 s cap and hands all of it over). Recogniser columns are on the correctly cut sentence, so")
    print("they show the model's floor; the endpointer columns show whether open listening would hand it that sentence.")
    print("Push-to-talk removes the endpointer from the question entirely.")


def _last_word_end(x, frame=320, floor=0.01, hang_frames=10):
    """Index of the end of the last speech frame (a negative tail then cuts inside the last word): the last frame whose RMS is above `floor` on a full
    scale of 1, ignoring anything after a run of `hang_frames` quiet frames. TTS clips carry a quiet
    tail; a person's recording carries breath and room. Good enough to place a cut, which is all the
    thumb does."""
    import numpy as np
    rms = np.array([np.sqrt(np.mean(x[i:i + frame] ** 2)) for i in range(0, len(x) - frame, frame)])
    loud = np.where(rms > floor)[0]
    return int((loud[-1] + 1) * frame) if len(loud) else len(x)


def cmd_tail(manifest: Path, engine: str = "indicconformer") -> None:
    import numpy as np
    rng = np.random.default_rng(20260920)
    rows = list(csv.DictReader(open(manifest, encoding="utf-8", newline="")))
    langs = {r["language"] for r in rows}
    recs = {lang: recognizer(lang, engine=engine) for lang in langs}
    exclude = ["_hi.wav", "hi_pratham", "mix_"] if "hi" in langs else ["_te.wav", "te_padma", "mix_"]
    pool = _babble_pool(exclude)
    clips = []
    for r in rows:
        x, sr = load_audio(manifest.parent / r["path"])
        if sr != 16000:
            x = np.interp(np.linspace(0, len(x) - 1, int(len(x) * 16000 / sr)), np.arange(len(x)), x).astype(np.float32)
        clips.append((r, x, _last_word_end(x)))
    tails = [-300, -200, -100, 0, 150, 300, 600, 1000]
    print(f"{len(clips)} clips; the thumb lifts N ms after the last word; hi checkpoint; SYNTHETIC voice. Report as n of the ten.")
    print(f"{'tail after last word':22s} {'clean':>8s} {'+15 dB babble':>14s}")
    for tail in tails:
        exact = {"clean": 0, "babble": 0}
        for r, x, end in clips:
            ref_w = normalise(r["reference"])
            cut = x[: min(len(x), end + tail * 16)]
            for cond in ("clean", "babble"):
                y = cut
                if cond == "babble":
                    b = _babble(pool, len(cut), talkers=12, rng=rng)
                    y = cut + b * (np.sqrt(np.mean(x[:end] ** 2)) / 10 ** (15 / 20))
                y = np.clip(y, -1, 1).astype(np.float32)
                stream = recs[r["language"]].create_stream(); stream.accept_waveform(16000, y); recs[r["language"]].decode_stream(stream)
                exact[cond] += int(edit_distance(ref_w, normalise(stream.result.text)) == 0)
        print(f"{tail:>6d} ms{'':14s} {exact['clean']:>5d}/{len(clips):<2d} {exact['babble']:>10d}/{len(clips):<2d}")
    print("\nA cut at 0 ms is the thumb lifting on the last syllable. If a longer tail scores the same as 0, the")
    print("recogniser does not need the beat and the instruction is only about the presenter's own habit; if 0 loses")
    print("sentences that 300 or 600 recover, that many milliseconds is the beat, and the capture can add it as a")
    print("release tail so the presenter does not have to remember.")

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
        rows.append((name, lang, "recorded-demo" if set_csv is not None else "recorded", reference, foods))
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
    elif len(args) >= 2 and args[0] == "tail":
        cmd_tail(Path(args[1]), engine=engine)
    elif len(args) >= 2 and args[0] == "robustness":
        cmd_robustness(Path(args[1]), engine=engine)
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
