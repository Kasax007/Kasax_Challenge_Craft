@echo off
setlocal
set "REPO=C:\Users\lars6\IdeaProjects\Kasax_Challenge_Craft"
py "%~dp0bot_training_dashboard_server.py" --root "%REPO%"
if errorlevel 1 (
  echo.
  echo py launcher failed. Trying python...
  python "%~dp0bot_training_dashboard_server.py" --root "%REPO%"
)
pause
