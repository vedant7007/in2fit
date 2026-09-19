@echo off
title Katori - fetch model weights
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fetch-models.ps1"
echo.
echo Finished. See logs\model-fetch.log
timeout /t 15
