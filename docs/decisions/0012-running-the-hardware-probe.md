# 0012. How the hardware probe is run, and one exit code that must not be trusted

Date: 19 September 2026. Status: accepted, operational.

Three rules, each of which cost a run to learn.

## 1. NEVER GATE ON `connectedAndroidTest`'s EXIT CODE

`:app:connectedDemoDebugAndroidTest` fails the build while reporting that every test passed.

Reduced to the smallest case that still shows it: one test, `d_mlKitOcrWithoutNetworkPermission`,
which needs no model and passes.

    TEST-RMX3780 - 15.xml        tests="1" failures="0" errors="0"
    index.html                   1 test, 0 failures, 100% successful
    test-result.pb               no error field, both at suite and device level
    test-result-exit-code.txt    1
    gradle                       "There were failing tests. See the report at ..."

The report it points at says 100% successful. The exit code is not derived from the test results
in this configuration.

Two explanations were checked and both are wrong. A stale `SIGABRT` left in the device log buffer
by an earlier crashed run: the per-test logcat files AGP captured contain no crash markers. Two
adb transports to the same phone colliding on the shared device-directory name `RMX3780 - 15`:
`device-info.pb` names only one device and the per-test logcat timestamps are contiguous.

**The underlying AGP defect has not been identified.** It is recorded as observed behaviour rather
than explained, because an invented explanation would be worse than an honest gap.

THE RULE. Anything that decides whether a hardware run passed reads the `failures` and `errors`
attributes of the JUnit XML under
`app/build/outputs/androidTest-results/connected/.../TEST-*.xml`. Never the task's exit code, in
either direction. An exit code that lies is worse than a red build, because a red build gets
investigated.

## 2. RUN THE PROBE WITH `am instrument`, NOT GRADLE

    adb -s <serial> shell am instrument -w -r \
      -e class io.github.vedant7007.katori.HardwareProbeTest \
      io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner

`connectedAndroidTest` uninstalls both APKs when it finishes. Uninstalling wipes
`/sdcard/Android/media/<pkg>/`, which is where the models are staged and where the probe writes
its report. So every gradle-driven run destroyed 1.29 GB of setup and the evidence it had just
produced, and the report could never be pulled afterwards: the first two runs reported
`adb: error: failed to stat remote object ... No such file or directory`, which read like a
path bug and was not one.

`am instrument` does not uninstall. On the first run done this way the report pulled cleanly,
3,275 bytes, and the models were still on the device afterwards.

Build and install with gradle (`assembleDemoDebug`, `assembleDemoDebugAndroidTest`,
`installDemoDebug`, plus `adb install -r -t -g` for the test APK). Run with `am instrument`.

## 3. HOLD THE SCREEN AWAKE FOR THE WHOLE RUN

    adb -s <serial> shell svc power stayon true

This phone runs Realme's `OplusHansManager`, which freezes app processes when the screen turns
off: `freeze uid: ... pkg: io.github.vedant7007.katori ... scene: LcdOff`. It froze a probe
mid-measurement. The process sat at 0.0% CPU with its resident set shrinking from 1.15 GB to
613 MB, and the row it was measuring came back as 207 prompt tokens in 191,673 ms, or 1.08 tok/s.

A frozen row is junk, not a finding, and it is not obviously junk if nobody is watching the
process while it runs. Set `stayon` before the run and clear it afterwards, and treat any row
wildly out of line with its neighbours as suspect until the freezer log is checked.

## Transport, while it stays this unreliable

USB on this setup re-enumerates every few minutes; `transport_id` climbed 1 → 2 → 4 → 5 → 6 in one
session, and a single `adb push` of the 1.04 GiB model died twice, at 18.3 s and at 43.4 s. Bulk
transfers go in chunks with per-chunk retry and an on-device `sha256sum` at the end. Control, the
install and the instrumentation run go over TCP (`adb tcpip 5555`, `adb connect <ip>:5555`), which
has not dropped.
