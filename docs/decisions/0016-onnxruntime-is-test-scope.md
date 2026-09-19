# 0016. ONNX Runtime ships with the probe, not with the app, until something shipped calls it

Date: 20 September 2026. Status: accepted, measured.

## The question

`com.microsoft.onnxruntime:onnxruntime-android` was added in `747748a` as an `implementation`
dependency so that `HardwareProbeTest` could open the ASR and TTS models and MEASURE their resident
cost rather than estimate it (`0013`). That put it in every variant, including the `demo` build
that goes on stage, and nothing shipped calls it: the only reference in the tree is the probe.
`HANDOVER.md` §7 asked for a decision.

## What it costs, measured rather than assumed

Same tree, same toolchain, one line changed, `assembleDemoDebug` twice:

| `onnxruntime-android` scope | `app-demo-debug.apk` | log |
| --- | ---: | --- |
| `implementation` | 109,661,661 bytes | `logs/nila-ort-ab.log`, 01:23 |
| `androidTestImplementation` | 76,661,867 bytes | `logs/nila-ort-final.log`, 01:28 |

The difference is 32,999,794 bytes, which is `lib/arm64-v8a/libonnxruntime.so` (32,990,472) plus
`libonnxruntime4j_jni.so` (83,224), stored uncompressed as native libraries are. **Thirty-three
megabytes, not the "~20 MB" the handover estimated.**

Two things the handover said about this that measurement does not support:

- "The demo APK on disk is 75.6 MB, not the 46 MB `STATUS.md` reports", attributed to ONNX
  Runtime. The APK on disk carrying ONNX Runtime is 109.7 MB. The 75–77 MB APK never contained
  it. The 46 MB figure came from the cloud container (`0009`), which has no NDK and no `jniLibs`,
  so it is not comparable to a laptop build and its difference from 76.7 MB is not ONNX Runtime.
- Neither the 46 MB nor a 109 MB APK is recorded in any log; the only logged size is 34.5 MB in
  `logs/build.log` at 12:03 on 19 September, before the JNI bridge. The figures above are the
  first APK sizes in this project read off a build that was run for the purpose.

## Decision

**`androidTestImplementation`.** The probe still opens both ONNX sessions: the test APK carries
the library (`app-demo-debug-androidTest.apk`, 33,863,726 bytes, `libonnxruntime.so` inside it)
and instrumentation runs in the app's process with the test APK's native libraries on its path.
`assembleDemoDebugAndroidTest` succeeds with the change, so the probe compiles; whether it still
loads the sessions on the phone is a claim only Rao can make, and it is on the coordination log.

Reasons, in order of weight:

1. **The stage build ships nothing it does not call.** Thirty-three megabytes of runtime with no
   caller is the kind of thing a judge opening the APK would ask about, and the answer "for a
   test" is not a good one.
2. **It would collide with sherpa-onnx.** The ASR and TTS slices bring sherpa-onnx, whose Android
   artefact bundles its own `libonnxruntime.so`. Two copies of the same library at the same path
   is a packaging error at merge time. Whoever adds sherpa-onnx should not have to remember to
   remove this line first; with it in test scope, the collision surfaces in the test APK, where
   it can be resolved by dropping this dependency in favour of the bundled one.
3. **The permission allowlist is indifferent.** `[verify] demoDebug: permissions are exactly
   [CAMERA, RECORD_AUDIO, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION]` in both builds. ONNX Runtime
   contributed no permission either way; this was never a permission question.

## What this changes for the co-residency measurement

`0013` is open on a co-residency peak that moved 750 MB across a build change nobody expected to
cost memory. This is a build change. Where `libonnxruntime.so` is loaded from (test APK rather
than app APK) should not alter the process's resident set once the sessions exist, but "should
not" is precisely what `0013` refuses to write down as a finding. **The next probe run is on a
different build tag, and its co-residency row goes in the `0013` table as a new row, not as a
confirmation of the old one.**

## When this reverses

When shipped code opens an ONNX session. That is the ASR or TTS slice, and at that point the
runtime arrives with sherpa-onnx, and this line is removed rather than moved back.
