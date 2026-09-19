@echo off
title Katori - cross-compile llama.cpp for Android arm64
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-llama-android.ps1"
echo.
echo Finished. See logs\llama-android-build.log
timeout /t 15
