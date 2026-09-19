# Cross-compiles llama.cpp for Android arm64 with the NDK, to verify the native build path
# the LlmEngine will use. It produces .so files. It does NOT run them: that needs hardware.
$ErrorActionPreference = 'Continue'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$log  = Join-Path $root 'logs\llama-android-build.log'
function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line; Add-Content -Path $log -Value $line -Encoding UTF8
}
Set-Content -Path $log -Value "=== llama.cpp android cross-compile $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$ndk = Join-Path $sdk 'ndk\28.2.13676358'
$cmake = Join-Path $sdk 'cmake\3.22.1\bin\cmake.exe'
$ninja = Join-Path $sdk 'cmake\3.22.1\bin\ninja.exe'
Log "ndk:   $ndk  exists=$(Test-Path $ndk)"
Log "cmake: $cmake exists=$(Test-Path $cmake)"
Log "ninja: $ninja exists=$(Test-Path $ninja)"
$toolchain = Join-Path $ndk 'build\cmake\android.toolchain.cmake'
Log "toolchain: $toolchain exists=$(Test-Path $toolchain)"
if (-not (Test-Path $toolchain)) { Log "FATAL: NDK toolchain file missing"; exit 2 }

$src = Join-Path $root 'data-sources\llama.cpp'
if (-not (Test-Path $src)) {
  Log "--- cloning llama.cpp ---"
  & git clone --depth 1 https://github.com/ggml-org/llama.cpp.git $src 2>&1 | Select-Object -Last 5 | ForEach-Object { Log $_ }
}
Push-Location $src
Log "commit: $(& git rev-parse --short HEAD)"

$bld = Join-Path $src 'build-android-arm64'
# THE TARGET ARCHITECTURE, AND WHY IT IS SPELLED OUT.
#
# ggml only applies an -march when GGML_CPU_ARM_ARCH is set, unless it is detecting the host
# (GGML_NATIVE) or building every variant (GGML_CPU_ALL_VARIANTS), and cross-compiling for
# Android does neither. Left unset, ARCH_FLAGS stays empty, every HAVE_* feature probe is
# compiled with no -march and fails, and the result is a baseline ARMv8.0 library using scalar
# fallbacks for exactly the Q4_K dot products that dominate inference.
#
# That is what shipped. Measured on the device it meant 207 prompt tokens at 11-15 tok/s and a
# 25 s extraction round trip against beat 1's 3.5 s budget.
#
# armv8.2-a+dotprod+fp16 is chosen to match the measured CPU, not guessed. See
# docs/decisions/0011. i8mm is deliberately ABSENT: the target has no i8mm, and compiling it in
# emits SMMLA instructions that raise SIGILL on the device rather than running slowly.
$armArch = 'armv8.2-a+dotprod+fp16'

# A fresh configure. check_cxx_source_compiles caches its results in CMakeCache.txt, so a stale
# cache from a build without -march keeps reporting the features as unsupported however the flags
# change. Deleting the build tree is the only way to make this reproducible.
if (Test-Path $bld) { Log "removing the previous build tree for a clean feature probe"; Remove-Item $bld -Recurse -Force }

Log "--- configure for arm64-v8a, minSdk 26, -march=$armArch ---"
& $cmake -B $bld -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE="$toolchain" `
  -DCMAKE_MAKE_PROGRAM="$ninja" `
  -DANDROID_ABI=arm64-v8a `
  -DANDROID_PLATFORM=android-26 `
  -DCMAKE_BUILD_TYPE=Release `
  -DGGML_CPU_ARM_ARCH="$armArch" `
  -DLLAMA_CURL=OFF -DGGML_OPENMP=OFF `
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_SERVER=OFF `
  -DBUILD_SHARED_LIBS=ON 2>&1 | ForEach-Object { Log $_ }
Log "configure exit: $LASTEXITCODE"

# The feature probes are the whole point of this change, so their results are read back out of
# the cache and logged rather than assumed from the flags having been passed.
Log "--- ARM feature probe results, read from CMakeCache.txt ---"
$cache = Join-Path $bld 'CMakeCache.txt'
foreach ($feat in 'HAVE_DOTPROD', 'HAVE_FP16_VECTOR_ARITHMETIC', 'HAVE_MATMUL_INT8', 'HAVE_SVE', 'HAVE_SME') {
    # ${feat} braces are required: "$feat:INTERNAL" parses as a SCOPED VARIABLE reference in
    # PowerShell, so the pattern silently becomes nonsense and every feature reads as absent.
    $line = Select-String -Path $cache -Pattern "^${feat}:INTERNAL=" -ErrorAction SilentlyContinue
    $val  = if ($line) { ($line.Line -split '=', 2)[1] } else { '<absent>' }
    $shown = if ([string]::IsNullOrWhiteSpace($val)) { 'NOT SET (feature unavailable or untested)' } else { $val }
    Log ("  {0,-30} {1}" -f $feat, $shown)
}

Log "--- build libllama ---"
& $cmake --build $bld --target llama 2>&1 | Select-Object -Last 15 | ForEach-Object { Log $_ }
Log "build exit: $LASTEXITCODE"

Log "--- produced shared libraries ---"
Get-ChildItem $bld -Recurse -Filter *.so -ErrorAction SilentlyContinue | ForEach-Object {
  Log ("{0}  {1} bytes" -f $_.Name, $_.Length)
}

# THE COPY STEP. Without it this script produced libraries in the llama.cpp build tree and left
# them there, while decision 0010, app/src/main/cpp/CMakeLists.txt and the commit that added the
# JNI bridge all stated it staged them into jniLibs. It did not. The four .so files in jniLibs
# had been put there by hand, so a fresh clone that followed the documented instruction hit
# "libllama.so is missing" at CMake configure time.
$jni = Join-Path $root 'app\src\main\jniLibs\arm64-v8a'
New-Item -ItemType Directory -Force -Path $jni | Out-Null
Log "--- staging into $jni ---"
$missing = $false
foreach ($name in 'libllama.so', 'libggml.so', 'libggml-base.so', 'libggml-cpu.so') {
    $built = Get-ChildItem $bld -Recurse -Filter $name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($built) {
        Copy-Item $built.FullName (Join-Path $jni $name) -Force
        Log ("  staged {0,-18} {1,12:N0} bytes" -f $name, $built.Length)
    } else {
        Log "  MISSING AFTER BUILD: $name"
        $missing = $true
    }
}
if ($missing) { Log 'FATAL: a library the JNI shim links against was not produced'; Pop-Location; exit 1 }

Pop-Location
Log "=== DONE. jniLibs is staged; rebuild the APK to pick the new libraries up ==="
