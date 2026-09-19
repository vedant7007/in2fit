#!/usr/bin/env python3
"""Make a Piper voice loadable by sherpa-onnx, and trim espeak-ng-data to the languages we ship.

A voice from rhasspy/piper-voices is two files, `<voice>.onnx` and `<voice>.onnx.json`, and the
ONNX carries no metadata at all (read through onnxruntime: `custom_metadata_map {}`). sherpa-onnx
reads the voice's sample rate, speaker count and phonemiser from ONNX metadata and needs a
`tokens.txt` beside the model, so a voice straight from Hugging Face fails to load with a message
that looks like a broken model. This script does what sherpa-onnx's own
`scripts/piper/add_meta_data.py` does, with the same keys and the same tokens format, without the
`onnx` and `iso639` packages that script imports: stdlib only, like the other scripts in tools/.

The ONNX file is a protobuf `ModelProto`. Metadata is field 14, a repeated
`StringStringEntryProto` (key = field 1, value = field 2). Protobuf lets a message be rewritten
field by field at the top level without understanding the fields being copied, so this copies
every top-level field except 14 and then writes the new entries. The graph, tens of megabytes,
is copied as an opaque slice. The result is read back through onnxruntime by `smoke`, which is the
same reader sherpa-onnx uses, so the check is not the writer checking itself.

    python stamp_piper_voice.py stamp  <voice.onnx> <out-dir>
        writes <out-dir>/model.onnx (stamped copy), tokens.txt and a copy of the .onnx.json

    python stamp_piper_voice.py espeak <espeak-ng-data> <out-dir> en,te,hi
        copies the phoneme tables, lang/ and voices/, and only the named languages' dictionaries.
        The full directory is 18 MB across 355 files, of which the dictionaries for languages this
        app does not speak are 17 MB. `en` stays because espeak-ng loads the English voice at
        initialise time and switches to it for Latin-script words inside Telugu or Hindi text.

    python stamp_piper_voice.py smoke  <out-dir> <espeak-ng-data> <text> [wav-out]
        synthesises <text> through sherpa-onnx's Python binding on this machine and prints what
        came out. Desktop evidence that the stamped model, tokens and data directory work
        together; NOT a hardware claim.

    python stamp_piper_voice.py selftest
        round-trips the protobuf writer through the reader on a tiny hand-built ModelProto.
"""

import json
import shutil
import sys
from pathlib import Path

METADATA_FIELD = 14  # ModelProto.metadata_props
SHARED_ESPEAK_FILES = ("phontab", "phonindex", "phondata", "intonations")


# --- protobuf, the little we need ---------------------------------------------------------------

def _varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def _read_varint(buf: bytes, i: int):
    shift, n = 0, 0
    while True:
        b = buf[i]
        i += 1
        n |= (b & 0x7F) << shift
        if not b & 0x80:
            return n, i
        shift += 7


def _string_field(number: int, s: str) -> bytes:
    data = s.encode("utf-8")
    return _varint((number << 3) | 2) + _varint(len(data)) + data


def _entry(key: str, value: str) -> bytes:
    body = _string_field(1, key) + _string_field(2, value)
    return _varint((METADATA_FIELD << 3) | 2) + _varint(len(body)) + body


def _top_level_fields(buf: bytes):
    """Yield (field_number, wire_type, start, end) for every top-level field, in file order."""
    i, n = 0, len(buf)
    while i < n:
        start = i
        tag, i = _read_varint(buf, i)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            _, i = _read_varint(buf, i)
        elif wire == 1:
            i += 8
        elif wire == 2:
            length, i = _read_varint(buf, i)
            i += length
        elif wire == 5:
            i += 4
        else:
            raise ValueError(f"unsupported wire type {wire} at byte {start}; not an ONNX file?")
        yield number, wire, start, i


def _parse_entry(payload: bytes) -> tuple:
    key = value = ""
    i = 0
    while i < len(payload):
        tag, i = _read_varint(payload, i)
        length, i = _read_varint(payload, i)
        text = payload[i:i + length].decode("utf-8")
        i += length
        if tag >> 3 == 1:
            key = text
        elif tag >> 3 == 2:
            value = text
    return key, value


def read_metadata(buf: bytes) -> dict:
    found = {}
    for number, wire, start, end in _top_level_fields(buf):
        if number == METADATA_FIELD and wire == 2:
            _, i = _read_varint(buf, start)
            length, i = _read_varint(buf, i)
            k, v = _parse_entry(buf[i:i + length])
            found[k] = v
    return found


def with_metadata(buf: bytes, meta: dict) -> bytes:
    """The same model with its metadata_props replaced by [meta]. Everything else is a byte copy."""
    out = bytearray()
    for number, _, start, end in _top_level_fields(buf):
        if number != METADATA_FIELD:
            out += buf[start:end]
    for k, v in meta.items():
        out += _entry(k, str(v))
    return bytes(out)


# --- the three jobs -----------------------------------------------------------------------------

def sherpa_metadata(config: dict) -> dict:
    """Exactly the keys sherpa-onnx's add_meta_data.py writes for an espeak Piper voice."""
    if config.get("phoneme_type") != "espeak":
        raise SystemExit(f"phoneme_type is {config.get('phoneme_type')!r}; only espeak voices are handled")
    sample_rate = config["audio"]["sample_rate"]
    if sample_rate == 22500:  # a known typo in some piper configs, corrected upstream the same way
        sample_rate = 22050
    return {
        "model_type": "vits",
        "comment": "piper",  # sherpa-onnx keys its Piper handling off this exact string
        "language": config["language"]["name_english"],
        "voice": config["espeak"]["voice"],
        "version": 1,
        "has_espeak": 1,
        "has_g2pw": 0,
        "n_speakers": config["num_speakers"],
        "sample_rate": sample_rate,
    }


def tokens_lines(config: dict):
    """One `<symbol> <id>` line per phoneme, in the config's order, the way sherpa-onnx writes it.

    A leading space symbol produces a line that begins with a space; sherpa-onnx's reader expects
    that and this must not strip it.
    """
    for symbol, ids in config["phoneme_id_map"].items():
        if symbol == "\n":
            continue
        yield f"{symbol} {ids[0] if isinstance(ids, list) else ids}\n"


def stamp(voice: Path, out_dir: Path) -> None:
    config_path = voice.with_name(voice.name + ".json")
    config = json.loads(config_path.read_text(encoding="utf-8"))
    buf = voice.read_bytes()
    before = read_metadata(buf)
    meta = sherpa_metadata(config)

    out_dir.mkdir(parents=True, exist_ok=True)
    model_out = out_dir / "model.onnx"
    model_out.write_bytes(with_metadata(buf, meta))
    tokens_out = out_dir / "tokens.txt"
    with tokens_out.open("w", encoding="utf-8", newline="\n") as f:
        f.writelines(tokens_lines(config))
    shutil.copyfile(config_path, out_dir / "model.onnx.json")

    after = read_metadata(model_out.read_bytes())
    if after != {k: str(v) for k, v in meta.items()}:
        raise SystemExit(f"read-back mismatch: wrote {meta}, read {after}")
    print(f"source     {voice}  {len(buf):,} bytes, metadata before: {before or '{}'}")
    print(f"model      {model_out}  {model_out.stat().st_size:,} bytes, metadata now: {after}")
    print(f"tokens     {tokens_out}  {sum(1 for _ in tokens_lines(config))} symbols")
    print(f"config     {out_dir / 'model.onnx.json'}")


def trim_espeak(src: Path, dst: Path, langs: list) -> None:
    if dst.exists():
        shutil.rmtree(dst)
    dst.mkdir(parents=True)
    for name in SHARED_ESPEAK_FILES:
        shutil.copyfile(src / name, dst / name)
    for sub in ("lang", "voices"):
        shutil.copytree(src / sub, dst / sub)
    for lang in langs:
        dictionary = f"{lang}_dict"
        if not (src / dictionary).is_file():
            raise SystemExit(f"{src / dictionary} does not exist; espeak-ng has no dictionary for {lang!r}")
        shutil.copyfile(src / dictionary, dst / dictionary)
    files = [p for p in dst.rglob("*") if p.is_file()]
    print(f"espeak     {dst}  {len(files)} files, {sum(p.stat().st_size for p in files):,} bytes "
          f"(source: {sum(p.stat().st_size for p in src.rglob('*') if p.is_file()):,} bytes)")


def smoke(model_dir: Path, espeak_dir: Path, text: str, wav_out: Path = None) -> None:
    import time
    import sherpa_onnx  # pip install sherpa-onnx; the desktop build of the same runtime the AAR is

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(model_dir / "model.onnx"),
                tokens=str(model_dir / "tokens.txt"),
                data_dir=str(espeak_dir),
            ),
            num_threads=2,
        ),
    )
    if not config.validate():
        raise SystemExit("sherpa-onnx rejected the config; see its message above")
    t0 = time.perf_counter()
    tts = sherpa_onnx.OfflineTts(config)
    t1 = time.perf_counter()
    audio = tts.generate(text, sid=0, speed=1.0)
    t2 = time.perf_counter()
    n = len(audio.samples)
    seconds = n / audio.sample_rate if audio.sample_rate else 0.0
    peak = max((abs(s) for s in audio.samples), default=0.0)
    print(f"load       {t1 - t0:.2f} s   synth {t2 - t1:.2f} s for {seconds:.2f} s of audio "
          f"(RTF {((t2 - t1) / seconds) if seconds else float('inf'):.2f}), "
          f"{n} samples @ {audio.sample_rate} Hz, peak |sample| {peak:.3f}")
    if wav_out:
        sherpa_onnx.write_wave(str(wav_out), audio.samples, audio.sample_rate)
        print(f"wav        {wav_out}")
    if n == 0 or peak == 0.0:
        raise SystemExit("no audio came out; the voice, tokens or espeak data are not working together")


def selftest() -> None:
    # A hand-built ModelProto: ir_version=9 (field 1), producer_name (field 2), an empty graph
    # (field 7), one opset_import (field 8), and a stale metadata entry that must be dropped.
    tiny = (_varint(1 << 3) + _varint(9)
            + _string_field(2, "test")
            + _varint((7 << 3) | 2) + _varint(0)
            + _varint((8 << 3) | 2) + _varint(2) + _varint(2 << 3) + _varint(17)
            + _entry("stale", "yes"))
    assert read_metadata(tiny) == {"stale": "yes"}
    meta = {"model_type": "vits", "sample_rate": 22050, "voice": "te"}
    stamped = with_metadata(tiny, meta)
    assert read_metadata(stamped) == {"model_type": "vits", "sample_rate": "22050", "voice": "te"}, read_metadata(stamped)
    # Everything that was not metadata survives byte for byte, in order.
    assert stamped.startswith(tiny[: len(tiny) - len(_entry("stale", "yes"))])
    # Multi-byte varints, since a 63 MB graph length is one.
    assert _read_varint(_varint(63_516_050), 0) == (63_516_050, 4)
    # The tokens format keeps a leading space symbol intact.
    assert list(tokens_lines({"phoneme_id_map": {" ": [3], "\n": [9], "a": [14]}})) == ["  3\n", "a 14\n"]
    print("selftest   ok")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args[:1] == ["stamp"] and len(args) == 3:
        stamp(Path(args[1]), Path(args[2]))
    elif args[:1] == ["espeak"] and len(args) == 4:
        trim_espeak(Path(args[1]), Path(args[2]), args[3].split(","))
    elif args[:1] == ["smoke"] and len(args) in (4, 5):
        smoke(Path(args[1]), Path(args[2]), args[3], Path(args[4]) if len(args) == 5 else None)
    elif args == ["selftest"]:
        selftest()
    else:
        raise SystemExit(__doc__)
