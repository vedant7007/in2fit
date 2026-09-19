@echo off
title Katori - build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
echo.
echo Finished. See logs\build.log
timeout /t 15
