@echo off
rem Bob-Trainer starten: Doppelklick. Das Fenster schliesst sich gleich wieder; der Trainer laeuft im
rem Hintergrund weiter, sein Dashboard oeffnet sich im Browser. Ein zweiter Doppelklick oeffnet nur das Dashboard.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\start-windows.ps1"
if errorlevel 1 pause
