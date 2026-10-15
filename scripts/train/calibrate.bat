@echo off
rem Vermisst den Rechner neu: wie viele Server und Arenen gleichzeitig laufen und wie schnell.
rem Ergebnis: runs\train\calibration.md (das Training richtet sich danach).
setlocal
cd /d "%~dp0..\.."
set "VENV=runs\train\venv"
if not exist "%VENV%\Scripts\python.exe" (
  echo Bitte zuerst einmal train.bat starten ^(richtet Python ein^).
  pause
  exit /b 1
)
"%VENV%\Scripts\python.exe" scripts\train\bobtrain.py calibrate %*
pause
