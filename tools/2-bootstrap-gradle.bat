@echo off
title Katori - bootstrap gradle and probe versions
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bootstrap-gradle.ps1"
echo.
echo Finished. Exit code %ERRORLEVEL%. See logs\gradle-bootstrap.log
timeout /t 15
