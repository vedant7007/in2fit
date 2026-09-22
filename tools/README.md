# Tools

Scripts, not application code. The numbered `.bat` files are the path a stranger takes on a
fresh Windows clone, in order; each one is a thin wrapper over the `.ps1` beside it, so the
PowerShell file is the thing to read.

| run in this order | what it does |
| --- | --- |
| `1-setup-toolchain.bat` | checks the JDK, the Android SDK and the NDK, and says what is missing |
| `2-bootstrap-gradle.bat` | fetches the Gradle wrapper's distribution |
| `4-fetch-models.bat` | downloads the model weights, the sherpa-onnx AAR and the espeak data, and **verifies every sha256** before the build will accept them |
| `5-build-llama-android.bat` | builds the llama.cpp shared libraries for arm64 |
| `3-build.bat` | assembles the demo build and runs the checks |
| `6-hardware-probe.bat` | measures the phone: tokens per second, thermal state, what the device can carry |

The rest, by job:

- **the build and the phone** — `build.ps1`, `preflight.ps1`, `apk-size.ps1` (every APK size in this repository is a row in its log), `stage-models.ps1` (pushes 1.6 GB over a USB link that drops, in chunks, hash-checked), `cold-phone.ps1`, `measurement-pass.ps1`, `hardware-probe.ps1`
- **the data** — `build_food_db.py` (builds the shipped food database from the CSVs in `data-authoring/` and refuses to write one that fails its assertions), `render_lab_report_fixtures.py`, `stamp_piper_voice.py`
- **speech** — `asr_eval.py` (word error rate, foods heard, insertion analysis over the demo sentences)
- **localisation** — `make_review_queue.py`, `import_review_queue.py`
- **working in parallel** — `new-worktree.ps1`, `land.ps1` (rebase, then fast-forward `master`; nobody builds in the main checkout), `jvm-tests-standalone.sh`, `arjun-checks.ps1`
