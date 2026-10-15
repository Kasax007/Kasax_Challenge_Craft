@echo off
rem Beendet das laufende Training sauber (die Server werden gestoppt, alles gespeichert).
cd /d "%~dp0..\.."
if not exist runs\train mkdir runs\train
type nul > runs\train\STOP
echo STOP gesetzt - das Training beendet sich in wenigen Sekunden.
timeout /t 5 >nul
