@echo off
setlocal
cd /d "%~dp0"

rem ============================================================
rem  XuanFeng - one-click upload to GitHub
rem  @author rpg636zjhi(3187735201)
rem  @link   https://github.com/MCPE-XuanFeng/XuanFeng
rem
rem  Stages everything, commits if needed, then rebase-pulls and
rem  pushes the current branch. Double-click to run.
rem
rem  If GitHub is unreachable, start your local proxy (Clash etc.)
rem  and set USE_PROXY=1 below.
rem ============================================================

set "REMOTE_URL=https://github.com/MCPE-XuanFeng/XuanFeng.git"
set "BRANCH=main"

rem 0 = direct connection, 1 = go through the local proxy
set "USE_PROXY=0"
set "PROXY_ADDR=http://127.0.0.1:7897"

set "GITOPT="
if "%USE_PROXY%"=="1" set "GITOPT=-c http.proxy=%PROXY_ADDR% -c https.proxy=%PROXY_ADDR%"

echo ==================================================
echo   XuanFeng - one-click upload to GitHub
echo ==================================================
echo.

rem ---------- 0. git available, and inside a repo ----------
where git >nul 2>&1
if errorlevel 1 goto :nogitbin

git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 goto :nogit

echo Repo:   %REMOTE_URL%
echo Branch: %BRANCH%
echo.

rem ---------- 1. make sure origin points at the right repo ----------
set "CUR_URL="
for /f "delims=" %%u in ('git remote get-url origin 2^>nul') do set "CUR_URL=%%u"
if /i "%CUR_URL%"=="%REMOTE_URL%" goto :originok
echo [setup] origin is   "%CUR_URL%"
echo [setup] repointing to %REMOTE_URL%
git remote set-url origin "%REMOTE_URL%"
if errorlevel 1 goto :fail
echo.

:originok

rem ---------- 2. stage ----------
echo [1/4] staging changes ...
git add -A
if errorlevel 1 goto :fail

rem ---------- 3. commit only if something is staged ----------
git diff --cached --quiet
if not errorlevel 1 goto :nothing

set "STAMP="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmmss"`) do set "STAMP=%%i"
if not defined STAMP set "STAMP=auto"

echo [2/4] committing ...
git commit -m "chore: auto upload %STAMP%"
if errorlevel 1 goto :fail
goto :sync

:nothing
echo [2/4] nothing to commit - working tree is clean.

rem ---------- 4. pull --rebase then push ----------
:sync
echo [3/4] syncing with origin/%BRANCH% ...
git %GITOPT% pull --rebase origin %BRANCH%
if errorlevel 1 goto :pullfailed

echo [4/4] pushing ...
git %GITOPT% push origin HEAD:refs/heads/%BRANCH%
if errorlevel 1 goto :pushfailed

echo.
echo Uploaded to %REMOTE_URL%
echo.
pause
exit /b 0

rem ---------- error paths ----------
:nogitbin
echo [ERROR] git not found on PATH.
echo         Install Git for Windows, or run this from "Git Bash".
goto :fail

:nogit
echo [ERROR] Not a git repository.
echo         Run this script from the project root folder.
goto :fail

:pullfailed
echo.
echo [ERROR] pull --rebase failed.
echo   - Conflict: resolve it, or run "git rebase --abort" to undo.
echo   - Auth/network: see the message above, fix it and re-run.
echo.
goto :fail

:pushfailed
echo.
echo [ERROR] push failed.
echo   - Auth: MCPE-XuanFeng/XuanFeng needs a valid token or login.
echo     Run "git push" once manually to let Git Credential Manager sign you in.
echo   - Network: if GitHub is unreachable, start your local proxy and set
echo     USE_PROXY=1 at the top of this script.
echo   - Diverged: if the remote moved ahead, re-run - the rebase above will pick it up.
echo.
goto :fail

:fail
pause
exit /b 1
