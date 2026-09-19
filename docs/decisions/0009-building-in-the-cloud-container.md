# 0009. Building and testing in the cloud container

Date: 19 September 2026. Status: accepted.

## Context

Every build so far ran on the Windows laptop through desktop control, because that is where the
Android SDK and NDK live. Desktop control expires after 30 minutes idle and has twice had to be
re-requested, once at a cost of about an hour. It is now also restricted to click-only for
terminals, so a shell command cannot be typed into PowerShell at all.

That makes the laptop a single point of failure for the one thing the work depends on most: being
able to run the tests and read the result.

## Decision

**JVM unit tests and the demo APK build now run in the cloud container.** Android SDK
command-line tools, platform `android-37.0` and build-tools 37.0.0 install there in a few
minutes, the JDK is already 21, and the network is fast. The project source is 245 KB as a
tarball, so moving it across is seconds.

The laptop stays the source of truth. Edits are made there, the archive is taken from there, and
results come back to it. The container is a build machine, not a second copy of the project.

**Verified in the container, read out of `logs/container-test3.log` and
`logs/container-assemble.log`:**

    66 tests, 0 failures
    app-demo-debug.apk, 36,231,457 bytes
    lib/arm64-v8a only
    [verify] demoDebug: permissions are exactly
             [CAMERA, RECORD_AUDIO, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION]

`aapt2 dump permissions` on the APK agrees, so the whitelist task and the shipped artefact tell
the same story. No INTERNET permission survives the merge.

## What this does NOT change

**A container build is not a hardware claim.** It is the same category as the emulator ruling: it
proves the code compiles, the tests pass and the manifest is clean. It says nothing about model
load time, inference speed, ASR accuracy, co-residency or anything else that needs the Realme 11.

The NDK path is untouched: `libllama.so` was cross-compiled separately and the container has no
NDK installed. When the JNI bridge lands, the native build goes back to the laptop unless the NDK
is installed here too.

## Consequences

- A test run no longer waits on desktop control, so an expired grant costs nothing on the path
  that matters most.
- Desktop control is still needed for anything touching the phone over USB, which is the whole
  of the hardware verification list.
