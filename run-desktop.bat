@echo off
title ViaMe — Desktop Application
echo ================================================================
echo   Launching ViaMe Multi-Role Desktop Simulation (JavaFX)...
echo ================================================================

start "ViaMe Desktop" powershell -NoProfile -ExecutionPolicy Bypass -Command "$m2 = '$env:USERPROFILE\.m2\repository'; $cp = (Get-ChildItem -Path $m2 -Recurse -Filter '*.jar' | ForEach-Object { $_.FullName }) -join ';'; & '$env:USERPROFILE\.jdks\jbr-21.0.11\bin\java.exe' -cp ($cp + ';target\classes') com.viame.launcher.MainApp"

