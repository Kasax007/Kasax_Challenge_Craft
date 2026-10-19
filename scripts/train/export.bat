@echo off
rem Copies the trained models and league state into the repo (src\main\resources\bob-models).
rem Then commit and push in IntelliJ: they become Bob's default and the start on other devices.
cd /d "%~dp0..\.."
if not exist src\main\resources\bob-models mkdir src\main\resources\bob-models
for %%f in (cover.json goal.json champion.json) do if exist runs\train\models\%%f copy /y runs\train\models\%%f src\main\resources\bob-models\ >nul
if exist runs\train\state.json copy /y runs\train\state.json src\main\resources\bob-models\ >nul
echo Kopiert nach src\main\resources\bob-models - jetzt in IntelliJ committen und pushen.
pause
