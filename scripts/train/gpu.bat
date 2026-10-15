@echo off
rem Optional: PyTorch mit CUDA in die Trainings-Umgebung, damit das Deckungsmodell auf der
rem Grafikkarte lernt (gross: etwa 3 GB Download). Ohne laeuft alles mit numpy auf der CPU.
setlocal
cd /d "%~dp0..\.."
set "VENV=runs\train\venv"
if not exist "%VENV%\Scripts\python.exe" (
  echo Bitte zuerst einmal train.bat starten ^(richtet Python ein^).
  pause
  exit /b 1
)
"%VENV%\Scripts\python.exe" -m pip install --disable-pip-version-check torch --index-url https://download.pytorch.org/whl/cu128
"%VENV%\Scripts\python.exe" -c "import torch; print('PyTorch', torch.__version__, '- GPU:', torch.cuda.is_available() and torch.cuda.get_device_name(0))"
pause
