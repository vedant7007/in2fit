# 0003 - SDK root, JDK and toolchain

Status: accepted, verified on disk.

## Two SDK roots existed

| Root | Contents found |
| --- | --- |
| `C:\Users\vedan\AppData\Local\Android\Sdk` | build-tools 35.0.0 and 36.0.0, platforms android-36 and android-37.0, emulator, platform-tools, sources, system-images, skins, licenses |
| `C:\Users\vedan\Android\Sdk` | build-tools, cmdline-tools, platform-tools, platforms (android-36 only), licenses |

CHOSEN: `C:\Users\vedan\AppData\Local\Android\Sdk`.

Reasons. It is Android Studio's default location on Windows and carries the marks of the
install Studio actually manages: emulator, system images, sources and skins. It has
android-37.0, which the second root does not, and AGP 9 requires compileSdk 37. The
second root looks like a command-line install: its only unique asset is cmdline-tools.

`local.properties` sets `sdk.dir` to the chosen root. That file is gitignored because it
is machine-specific.

The NDK and CMake were installed INTO the chosen root using the `sdkmanager` binary from
the other root, via `--sdk_root`. Keeping two roots is still a hazard: install packages
with an explicit `--sdk_root`, or through Studio after confirming the path at the top of
the SDK Manager, or the NDK lands where the build cannot see it.

## Installed and verified

Verified by listing the directories on disk after the install, not by trusting the
installer's own output. See `logs/sdk-install.log`.

    ndk    28.2.13676358   (r28c)
    cmake  3.22.1

`ndkVersion` is pinned to 28.2.13676358 in `app/build.gradle.kts`, so another machine
cannot silently build against a different NDK.

## JDK

Available on this machine: Temurin 21.0.11, Android Studio's JBR 25.0.3, and a JDK 24.0.2.

CHOSEN: `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`, set as JAVA_HOME by
the build scripts.

Reason: 21 is an LTS that AGP and Gradle both support, and it avoids the JDK 24 and 25
toolchain surprises that come with running the build on a runtime newer than the build
tools target.

## How builds are run, and why

The shell available to this session is an isolated Linux VM with the project folder
mounted. It is not Windows, so it cannot run `gradlew` or `adb`. Android Studio and
terminals are also restricted to click-only control, so commands cannot be typed into
them.

So the build runs from authored scripts in `tools/`, launched by double-clicking the
`.bat` from Explorer, with every command's output redirected into `logs/`. The logs are
then read directly off disk. This is deliberately stronger than reading a screenshot of a
terminal: what gets reported is the file the build itself wrote.

Gradle writes UTF-16 on Windows, so `tools/build.ps1` also writes a `utf8-` copy of each
log. Read the `utf8-` copies.
