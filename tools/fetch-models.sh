#!/usr/bin/env bash
# Downloads only models whose licence permits redistribution in a shipped app.
# Deliberately EXCLUDED, see docs/decisions/0005:
#   - csukuangfj/sherpa-onnx-whisper-*  : no licence stated anywhere. No grant.
#   - willwade/mms-tts-* and facebook/mms-tts-*  : cc-by-nc-4.0, non-commercial.
#   - piper hi_IN-rohan : custom IITM licence, unread.
set -u
DEST="$1"
LOG="$2"
mkdir -p "$DEST"
exec >> "$LOG" 2>&1
echo "=== model fetch started $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="

get() { # url  relpath
  local url="$1" rel="$2" out="$DEST/$2"
  mkdir -p "$(dirname "$out")"
  if [ -s "$out" ]; then echo "SKIP  $rel (already $(wc -c < "$out") bytes)"; return; fi
  echo "GET   $rel"
  if curl -sS -L --fail --retry 3 --retry-delay 5 -o "$out.part" "$url"; then
    mv "$out.part" "$out"
    echo "OK    $rel  $(wc -c < "$out") bytes"
  else
    echo "FAIL  $rel"
    rm -f "$out.part"
  fi
}

B=https://huggingface.co
get "$B/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/te/model.int8.onnx" "asr/indicconformer/te/model.int8.onnx"
get "$B/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/hi/model.int8.onnx" "asr/indicconformer/hi/model.int8.onnx"
get "$B/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/en/model.int8.onnx" "asr/indicconformer/en/model.int8.onnx"
get "$B/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/tokens.txt"          "asr/indicconformer/tokens.txt"
get "$B/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/en/tokens.txt"       "asr/indicconformer/en/tokens.txt"
get "$B/rhasspy/piper-voices/resolve/main/te/te_IN/padmavathi/medium/te_IN-padmavathi-medium.onnx"      "tts/piper/te_IN-padmavathi-medium.onnx"
get "$B/rhasspy/piper-voices/resolve/main/te/te_IN/padmavathi/medium/te_IN-padmavathi-medium.onnx.json" "tts/piper/te_IN-padmavathi-medium.onnx.json"
get "$B/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf" "llm/qwen2.5-1.5b-instruct-q4_k_m.gguf"

echo "--- sha256 ---"
( cd "$DEST" && find . -type f ! -name '*.part' -print0 | xargs -0 sha256sum )
echo "--- sizes ---"
( cd "$DEST" && du -sh . && find . -type f ! -name '*.part' -printf '%s\t%p\n' | sort -rn )
echo "=== model fetch finished $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
