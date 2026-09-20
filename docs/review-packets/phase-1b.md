# Review packet: Phase 1B

Katori, offline health assistant, iQOO Hackathon 2026. For a reviewer outside this codebase.
Repo: `<main tree>`, one commit, 43 files.

## What exists

An Android project that builds, and thirteen contract files with no implementations behind
them. AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.20, compileSdk 37, minSdk 26, arm64-v8a only,
NDK 28.2.13676358 pinned. Two product flavours: `demo` (no network permission, models
bundled) and `full` (network, models downloaded).

Verified from build logs on disk: `assembleDemoDebug` green, `testDemoDebugUnitTest` green
(2 tests), APK 34.06 MB, native libraries arm64-v8a only, merged `demo` manifest declares
CAMERA and RECORD_AUDIO and nothing else.

## Decided without asking

1. SDK root is `AppData\Local\Android\Sdk`, not the second root in the home directory. It
   has android-37.0 and the assets Studio manages. Decision 0003.
2. Moved from AGP 8.13.2 to AGP 9.4.1 rather than downgrading every androidx dependency to
   fit under AGP 8. Each step was driven by an error message naming its own requirement.
   Decision 0004.
3. Dropped the `org.jetbrains.kotlin.android` plugin, which AGP 9 refuses.
4. Every dependency version is resolved from the repository by a `probeVersions` Gradle
   task rather than typed from memory. Output in `logs/versions-resolved.log`.
5. Builds run from authored scripts launched via Explorer, with output redirected to
   `logs/`, because this session's shell is a Linux VM that cannot run `gradlew`, and
   Android Studio and terminals are click-only. Nothing is reported from a screenshot.

## The one finding worth a reviewer's attention

`verifyDemoDebugHasNoInternet` failed the build on a permission no file in this project
declares. `com.google.android.datatransport:transport-backend-cct`, a Google telemetry
uploader pulled in transitively by ML Kit, contributes INTERNET and ACCESS_NETWORK_STATE
to the merged manifest.

It would not have been caught by reading our source, and it would not have been caught in
rehearsal, because airplane mode blocks the traffic either way. It would have been caught
by a judge opening the manifest, which is exactly the proof the pitch leads with. Both
permissions are now removed in the `demo` source set with `tools:node="remove"`.

## Least confident in

1. Whether ML Kit initialises cleanly with its telemetry transport stripped of network
   permission. Untested; it surfaces when OCR is built, not before.
2. AGP 9.4.1 and Gradle 9.6.0 are very current. Adding KSP, Room's compiler and Hilt is
   the next step and is where a version conflict is most likely.
3. Nothing has run on a phone. Not one line. The APK has never been installed.
4. `ModelArbiter` and `RulesEngine` are the two contracts the demo rests on, and both are
   judged only on whether they compile.

## What I would attack to break this

- Install the APK on the Realme 11 and see whether ASR, the LLM and TTS can be resident at
  once. If they cannot, beat 1's spoken confirmation has to degrade to on-screen, and that
  is a product change, not a bug.
- Check whether any other dependency contributes a permission. Only INTERNET is currently
  asserted against; a reviewer should ask why the check is not a whitelist of every
  permitted permission instead.
- Add a dependency that pulls in INTERNET and confirm the build actually fails. The guard
  has fired once in anger, which is better than never, but it has not been re-tested since
  the fix.
- Read `NetworkIsolationTest` and note that it skips comment lines. A network call written
  on the same line as a trailing comment would slip through.
