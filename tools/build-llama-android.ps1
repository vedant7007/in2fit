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
Log "--- configure for arm64-v8a, minSdk 26 ---"
& $cmake -B $bld -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE="$toolchain" `
  -DCMAKE_MAKE_PROGRAM="$ninja" `
  -DANDROID_ABI=arm64-v8a `
  -DANDROID_PLATFORM=android-26 `
  -DCMAKE_BUILD_TYPE=Release `
  -DLLAMA_CURL=OFF -DGGML_OPENMP=OFF `
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_SERVER=OFF `
  -DBUILD_SHARED_LIBS=ON 2>&1 | Select-Object -Last 12 | ForEach-Object { Log $_ }
Log "configure exit: $LASTEXITCODE"

Log "--- build libllama ---"
& $cmake --build $bld --target llama 2>&1 | Select-Object -Last 15 | ForEach-Object { Log $_ }
Log "build exit: $LASTEXITCODE"

Log "--- produced shared libraries ---"
Get-ChildItem $bld -Recurse -Filter *.so -ErrorAction SilentlyContinue | ForEach-Object {
  Log ("{0}  {1} bytes" -f $_.Name, $_.Length)
}
Pop-Location
Log "=== DONE ==="
