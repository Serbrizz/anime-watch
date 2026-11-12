@echo off
rem Apre la finestra degli script (scripts\gui.ps1) senza lasciare aperta una console. Si lancia anche con doppio clic.
start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0scripts\gui.ps1"
