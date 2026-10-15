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
"%VENV%\Scripts\python.exe" scripts\train\bobtrain.py run %*
echo.
echo Training beendet. Bericht: runs\train\report.md
pause
