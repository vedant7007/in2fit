# 0004 - AGP 9, Gradle 9.6, and a network permission that arrived on its own

Status: accepted, build verified green.

## The version line had to move, and the build said so

The first attempt used AGP 8.13.2, which was already in this machine's Gradle cache.
It failed with, verbatim from `logs/utf8-gradle-assemble.log`:

    Dependency 'androidx.core:core-ktx:1.19.0' requires Android Gradle plugin 9.1.0 or higher.
    This build currently uses Android Gradle plugin 8.13.2.

Two ways out: downgrade every androidx dependency until they all fit under AGP 8, or move
up. Moving up was chosen. The downgrade path has no single answer, since each library has
its own ceiling, and it would have meant guessing versions one at a time. android-37.0 was
already installed, which is what AGP 9 needs.

Final line, each step taken because the build named the requirement:

| | Version | How it was chosen |
| --- | --- | --- |
| AGP | 9.4.1 | Newest stable, resolved by the `probeVersions` task |
| Gradle | 9.6.0 | AGP 9.4.1 refused 9.0.0 and named 9.6.0 in the error |
| compileSdk | 37 | Required by AGP 9 |
| Kotlin | 2.2.20 | Compose plugin only; see below |
| JDK | 21 | Decision 0003 |

### The Kotlin plugin is gone

AGP 9 has Kotlin support built in and refuses the separate plugin, verbatim:

    The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.

So `org.jetbrains.kotlin.android` was removed from both build files, along with the
`kotlin { compilerOptions { } }` block it provided. `org.jetbrains.kotlin.plugin.compose`
is still applied. Anyone adding a Kotlin DSL block back will hit this same refusal.

### No version in this project was guessed

Every dependency version came from the `probeVersions` task resolving the newest stable
release from the real repository, and the answers are in `logs/versions-resolved.log`.
The task is kept in the root build file for the next unpinned dependency. KSP resolved to
2.2.20-2.0.4 and Hilt to 2.60.1; neither is applied yet.

## The finding that matters: INTERNET arrived without us asking

With everything compiling, `verifyDemoDebugHasNoInternet` failed the build:

    The demoDebug build declares INTERNET, which breaks the offline guarantee in spec 14.4.

No manifest in this project declares it. The manifest merger blame report names the
source:

    com.google.android.datatransport:transport-backend-cct:2.3.3
    AndroidManifest.xml:26:5-67  <uses-permission android:name="android.permission.INTERNET" />

That is a Google telemetry uploader, pulled in transitively by ML Kit. It also brought
ACCESS_NETWORK_STATE.

This is the exact failure the check was built for, and it is worth being blunt about what
would have happened otherwise. Nothing in our source would have shown it. Reviewing our
own manifests would have shown a clean build. The airplane-mode gesture on stage would
still have worked, because airplane mode blocks traffic regardless, so the problem would
never have surfaced during rehearsal. It would have surfaced if a judge opened the
manifest, which is precisely the proof spec 14.4 wants to lead with.

FIX: `app/src/demo/AndroidManifest.xml` removes both permissions with `tools:node="remove"`.
Removing the permission is the guarantee. A library with no INTERNET permission cannot
reach the network whatever its code attempts, so the telemetry uploader being linked in is
harmless in the demo build.

RESIDUAL RISK, RESOLVED 19 September 2026. When this record was written ML Kit's OCR had
not been run on hardware, and the open question was whether its initialiser hard-fails
when the telemetry transport cannot resolve a network. It does not. `HardwareProbeTest`
rendered `"Haemoglobin 9.8 g/dL"` to a bitmap on the realme RMX3780 in the demo build,
with the merged manifest holding exactly CAMERA, RECORD_AUDIO and the platform receiver
permission, and ML Kit recognised `"Haemoglobin 9.8g/dL"`, one block, figure and label
both read, `OCR USABLE true` (`logs/hw-report-run3.txt`, section "ML Kit OCR, demo build,
no INTERNET permission"; first seen in `logs/utf8-hw-probe-logcat.log`). Stripping the
transport's permission does not break recognition. Beat 3's camera path is safe on this
count.

WHY THE CHECK READS THE MERGED MANIFEST: this is the whole reason. A permission can enter
from a dependency without appearing anywhere in our own source, and only the merged result
shows it. A check that scanned our source files would have passed.

## Verified green

From `logs/`, read off disk rather than from a screen:

    assembleDemoDebug      BUILD SUCCESSFUL in 43s
    testDemoDebugUnitTest  BUILD SUCCESSFUL in 27s   (2 tests, 0 failures)
    APK                    app-demo-debug.apk, 34.06 MB
    merged demoDebug permissions: CAMERA, RECORD_AUDIO, and the platform's own
                                  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No INTERNET.
