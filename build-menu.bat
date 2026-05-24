@echo off
:: Vonix Server Utilities — Build Menu Launcher
:: Double-click this or run from command prompt

echo.
echo  Vonix Server Utilities Build Menu
echo  ──────────────────────────────────
echo.

python --version >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] Python 3 not found in PATH.
    echo  Download from https://python.org/downloads/
    echo.
    pause
    exit /b 1
)

python "%~dp0build_menu.py"
