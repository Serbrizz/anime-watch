@echo off
rem Avvia release.ps1 senza dover cambiare la ExecutionPolicy di PowerShell. Uso: release patch^|minor^|major
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" %*
