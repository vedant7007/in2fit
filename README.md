# IN2FIT

An Android health assistant that runs entirely on the phone. You say what you ate, in the
language you choose; on-device speech recognition and a local language model extract the foods;
public-domain USDA data supplies the numbers; a rules engine decides what to say. Point the
camera at a printed lab report, and later advice changes because of it. The demo build has no
internet permission, and the build fails if a dependency adds one.

Built for the iQOO Hackathon 2026. Licensed under Apache-2.0 (`LICENSE`); the third-party
models, data and their licences are in `docs/decisions/0005`.

The working name was Katori. It survives in the package (`io.github.vedant7007.katori`), in
class and file names, and in the older records, because the native symbol names encode the
package and that rename is deferred (`docs/decisions/0015`). Everything a person sees on the
phone says IN2FIT.

## Where to look

Every claim in this repository traces to a file a machine wrote: a build log, a test report or a
device report. Read in this order.

| file | what it is |
| --- | --- |
| `STATUS.md` | what is verified on hardware, what only on a laptop, what is not built, with the log behind each figure |
| `HANDOVER.md` | the audit of the project: every file, every rule and the bug behind it, what was found by measuring |
| `docs/decisions/` | 33 records; each states a choice, the evidence for it, and what would reverse it |
| `docs/screenshots/` | the app's screens, from an emulator; not a hardware claim |
| `docs/demo/` | the run of show for the four demo beats, and an audit of the submission deck against this repository |
| `docs/localisation/` | the Telugu string table's reviewer packet; every Telugu line is machine-drafted and marked so until a speaker confirms it |
| `docs/licences/` | licence texts filed verbatim |
| `docs/spec.md` | the original specification; `docs/decisions/0015` says where it is superseded |
| `COORDINATION.md` | the working log between the people building it |

## Building it

Windows, Android Studio's SDK, JDK 21. The scripts in `tools/` write their output to `logs/`,
and the logs are the evidence. On a fresh clone the order is 1, 2, 4, 3: the build refuses to
start until step 4 has fetched the speech runtime it needs, with its checksum verified.

    tools\1-setup-toolchain.bat      NDK and CMake into the SDK
    tools\2-bootstrap-gradle.bat     the Gradle wrapper
    tools\4-fetch-models.bat         model weights, the speech runtime, phoneme data; sha256 checked
    tools\3-build.bat                assembleDemoDebug, the unit tests, the APK size ledger
    tools\5-build-llama-android.bat  cross-compiles llama.cpp for the phone's CPU (arm64, dotprod, fp16)

`HANDOVER.md` §2 has every version pinned and why. Six people work in one worktree each
(`tools/new-worktree.ps1`) and land on `master` by fast-forward (`tools/land.ps1`).

## The rules the code keeps

- The model never computes a number. It extracts foods and phrases sentences; every figure comes
  from the database, and a guard rejects any number in generated text that was not in its input.
- A missing nutrient is unknown, never zero.
- Confidence is three bands, computed, never a percentage.
- The demo build's permissions are an allowlist of three, checked on the merged manifest at
  every build.
- A stub returns "not implemented", never a plausible value.
- A wrong food is the metric, not the match rate.

The reasons behind each are in `HANDOVER.md` §6.
