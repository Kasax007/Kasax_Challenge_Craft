@echo off
rem Bobs Trainings-Dashboard im Browser (http://127.0.0.1:8765): Stand, Start und Stopp.
setlocal
cd /d "%~dp0..\.."
set "VENV=runs\train\venv"
if not exist "%VENV%\Scripts\python.exe" (
  if not exist runs\train mkdir runs\train
  where py >nul 2>nul
  if not errorlevel 1 (py -3 -m venv "%VENV%") else (python -m venv "%VENV%")
)
"%VENV%\Scripts\python.exe" -m pip install --disable-pip-version-check -q -r scripts\train\requirements.txt
"%VENV%\Scripts\python.exe" scripts\train\dashboard.py
pause
