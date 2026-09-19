@echo off
title Katori - hardware probe
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0hardware-probe.ps1"
echo.
echo Finished. See logs\hw-report.txt and logs\hardware.log
timeout /t 20
