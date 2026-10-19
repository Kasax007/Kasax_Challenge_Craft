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
"%VENV%\Scripts\python.exe" -c "import numpy, psutil" >nul 2>nul
if errorlevel 1 (
  echo Pakete fehlen noch - Python-Umgebung wird neu eingerichtet ...
  rmdir /s /q "%VENV%"
  where py >nul 2>nul
  if not errorlevel 1 (py -3 -m venv "%VENV%") else (python -m venv "%VENV%")
  "%VENV%\Scripts\python.exe" -m pip install --disable-pip-version-check -r scripts\train\requirements.txt
)
"%VENV%\Scripts\python.exe" -c "import numpy, psutil" >nul 2>nul
if errorlevel 1 (
  echo.
  echo numpy/psutil liessen sich nicht installieren - die Meldung von pip steht oben.
  echo Haeufig: keine Internetverbindung, oder eine sehr neue Python-Version ohne fertige Pakete
  echo ^(dann Python 3.12 oder 3.13 von python.org installieren^).
  pause
  exit /b 1
)
"%VENV%\Scripts\python.exe" scripts\train\dashboard.py
pause
