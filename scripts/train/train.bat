@echo off
rem Bobs Training starten. Es laeuft, bis stop.bat gestartet oder dieses Fenster geschlossen wird.
rem Beim ersten Mal: Python-Umgebung einrichten und den Rechner vermessen (15-30 Minuten).
setlocal
cd /d "%~dp0..\.."
set "VENV=runs\train\venv"
if not exist "%VENV%\Scripts\python.exe" (
  echo Erster Start: Python-Umgebung wird eingerichtet ...
  if not exist runs\train mkdir runs\train
  where py >nul 2>nul
  if not errorlevel 1 (
    py -3 -m venv "%VENV%"
  ) else (
    python -m venv "%VENV%"
  )
)
if not exist "%VENV%\Scripts\python.exe" (
  echo.
  echo Python 3 wurde nicht gefunden. Bitte Python 3.10 oder neuer von https://www.python.org installieren
  echo ^(beim Installieren "Add python.exe to PATH" anhaken^) und train.bat nochmal starten.
  pause
  exit /b 1
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
"%VENV%\Scripts\python.exe" scripts\train\bobtrain.py run %*
echo.
echo Training beendet. Bericht: runs\train\report.md
pause
