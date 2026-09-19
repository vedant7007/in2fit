@echo off
title Katori - install NDK and CMake
echo Installing the Android NDK and CMake. Output goes to logs\sdk-install.log
echo This is several GB and can take 20+ minutes. Leave this window open.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-toolchain.ps1"
echo.
echo Finished. Exit code %ERRORLEVEL%. See logs\sdk-install.log
timeout /t 20
