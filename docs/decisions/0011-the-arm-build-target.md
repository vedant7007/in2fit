# 0011. The ARM build target, and the misbuild that hid behind "it compiles"

Date: 19 September 2026. Status: accepted.

## What was wrong

`libllama.so` shipped as a baseline ARMv8.0-A library on a CPU that advertises ARMv8.2 features,
so ggml used scalar fallbacks for exactly the quantized dot products that dominate inference.

It was invisible for the usual reason: it compiled, it linked, it exported its symbols, it loaded
on the phone and it produced correct output. Only the speed was wrong, and there was no earlier
number to compare against.

The mechanism, from `ggml/src/ggml-cpu/CMakeLists.txt`. An `-march` is applied from exactly three
places: host detection (`GGML_NATIVE`), the all-variants build (`GGML_CPU_ALL_VARIANTS`), or an
explicit `GGML_CPU_ARM_ARCH`. Cross-compiling for Android is none of those, and
`tools/build-llama-android.ps1` set none of them, so `ARCH_FLAGS` stayed empty. The feature
probes that follow are then compiled with no `-march` at all and every one of them fails:

    GGML_NATIVE:BOOL=OFF
    GGML_CPU_ALL_VARIANTS:BOOL=OFF
    HAVE_DOTPROD:INTERNAL=                  <- empty means the probe failed
    HAVE_FP16_VECTOR_ARITHMETIC:INTERNAL=
    HAVE_MATMUL_INT8:INTERNAL=

An empty `INTERNAL=` is a failed probe, not an untested one, and nothing in the build warns about
it.

## The target device, measured rather than assumed

    ro.soc.model    MT6835
    CPU part 0xd0b  x2   Cortex-A76
    CPU part 0xd05  x6   Cortex-A55
    Features        fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp
                    cpuid asimdrdm lrcpc dcpop asimddp

Two big cores and six little ones, ARMv8.2-A.

| Feature | In `/proc/cpuinfo` | Decision |
| --- | --- | --- |
| `asimddp`, dot product | yes | ENABLED as `+dotprod` |
| `asimdhp` + `fphp`, FP16 arithmetic | yes | ENABLED as `+fp16` |
| `i8mm`, int8 matrix multiply | **no** | EXCLUDED |
| `sve`, `sve2`, `sme`, `bf16` | no | excluded |

## Decision

    GGML_CPU_ARM_ARCH = armv8.2-a+dotprod+fp16

**`i8mm` IS DELIBERATELY ABSENT, AND MUST NOT BE ADDED TO MAKE A CHECKLIST GREEN.** It is an
ARMv8.6 feature; Cortex-A76 and A55 do not implement it. Compiling it in does not produce a
slower path, it emits `SMMLA` instructions that raise `SIGILL` the first time a quantized matmul
runs on this phone. `HAVE_MATMUL_INT8` staying empty after this change is the correct outcome and
is not a residual bug.

Anyone targeting a different phone re-reads `/proc/cpuinfo` on that phone and sets the arch from
what it actually reports. The flag is a property of the target, not of the project.

## Two consequences for the build script

**The build tree is deleted before configuring.** `check_cxx_source_compiles` caches its result in
`CMakeCache.txt`, so a cache written by a build without `-march` keeps reporting every feature as
unsupported no matter what flags are passed afterwards. Without the wipe this decision would
appear to have been applied and would have changed nothing.

**The probe results are read back out of the cache and logged.** Passing a flag is not evidence
that a feature was enabled. The script prints `HAVE_DOTPROD`, `HAVE_FP16_VECTOR_ARITHMETIC`,
`HAVE_MATMUL_INT8`, `HAVE_SVE` and `HAVE_SME` after configuring, so the log says what was actually
compiled in.

## And the copy step that was never there

The same script is documented in `0010`, in `app/src/main/cpp/CMakeLists.txt` and in the commit
that added the JNI bridge as staging its output into `app/src/main/jniLibs/arm64-v8a`. It did not.
It built the libraries into the llama.cpp build tree and listed them. The four `.so` files in
`jniLibs` had been placed there by hand, so a fresh clone following the documented instruction got
`libllama.so is missing from ...` at CMake configure time and no indication why.

The script now stages all four and fails loudly if any is absent after the build.

## What this does not claim

The rebuild's effect on the round trip is a measurement, recorded in `STATUS.md` against the
before figures, not a prediction. Enabling a feature the CPU has is correcting a misbuild; it is
not a guarantee that extraction fits inside beat 1's 3.5 s budget.
