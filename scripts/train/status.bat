@echo off
rem Zeigt den Stand des Trainings; der ausfuehrliche Bericht steht in runs\train\report.md.
cd /d "%~dp0..\.."
if exist runs\train\venv\Scripts\python.exe (
  runs\train\venv\Scripts\python.exe scripts\train\bobtrain.py status
) else (
  python scripts\train\bobtrain.py status
)
pause
