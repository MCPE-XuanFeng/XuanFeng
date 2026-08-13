@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: ============================================================
:: Proxy settings (for users behind a local proxy like Clash)
:: Set USE_PROXY=1 to route git pull/push through the proxy.
:: Change PROXY_ADDR if your proxy is on a different host/port.
:: ============================================================
set "USE_PROXY=1"
set "PROXY_ADDR=http://127.0.0.1:7897"

:: Switch to the folder this script lives in (project root),
:: so it works no matter where you double-click it from.
cd /d "%~dp0"

title XuanFeng - One-click upload to GitHub

echo ============================================
echo   XuanFeng - One-click upload to GitHub
echo ============================================

:: 1. Make sure we are inside a git repository
git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Not a git repository: %~dp0
    goto :end
)

:: 2. Find the current branch
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set "BRANCH=%%b"
echo Branch: %BRANCH%

:: 3. Make sure a remote named "origin" exists
set "REMOTE=origin"
git remote get-url %REMOTE% >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Remote "%REMOTE%" not found. Configure git remote first.
    goto :end
)

:: Build the -c proxy flags (empty when proxy is disabled)
set "PROXY_FLAGS="
if "%USE_PROXY%"=="1" (
    set "PROXY_FLAGS=-c http.proxy=%PROXY_ADDR% -c https.proxy=%PROXY_ADDR%"
    echo Using proxy: %PROXY_ADDR%
)

:: 4. Stage everything
echo [1/3] Staging changes ...
git add -A

:: 5. Commit only if there is something to commit
git diff --cached --quiet
if not errorlevel 1 (
    echo Working tree clean, nothing to commit.
    goto :sync
)

:: Build a timestamp (PowerShell first, fall back to wmic, then %date%)
set "TS="
for /f "delims=" %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmmss" 2^>nul') do set "TS=%%t"
if "%TS%"=="" (
    for /f "tokens=2 delims==." %%i in ('wmic os get localdatetime /format:list 2^>nul') do set "TS=%%i"
)
if "%TS%"=="" set "TS=%date% %time%"

set "MSG=chore: auto upload %TS%"
echo [2/3] Committing: %MSG%
git commit -m "%MSG%" 2>&1
if errorlevel 1 (
    echo [ERROR] Commit failed.
    goto :end
)

:sync
:: 6. Sync with remote (rebase) then push
echo [3/3] Syncing with %REMOTE%/%BRANCH% and pushing ...
git %PROXY_FLAGS% pull --rebase %REMOTE% %BRANCH% 2>&1
if errorlevel 1 (
    echo [WARN] Pull/rebase failed - possible conflict or no network. Aborting rebase.
    git rebase --abort >nul 2>&1
    goto :end
)
git %PROXY_FLAGS% push %REMOTE% %BRANCH% 2>&1
if errorlevel 1 (
    echo [ERROR] Push failed. Check network, SSL, proxy or credentials.
    echo [ERROR] If proxy is wrong, set USE_PROXY=0 at the top of this file.
    goto :end
)

echo.
echo [DONE] Uploaded to GitHub successfully.

:end
echo.
pause
