@echo off
setlocal
cd /d "%~dp0"

rem ============================================================
rem  XuanFeng - tag a release and push it
rem  @author rpg636zjhi(3187735201)
rem  @link   https://github.com/MCPE-XuanFeng/XuanFeng
rem
rem  Pushing a v* tag triggers .github/workflows/release.yml, which builds
rem  the debug APK and publishes it as a GitHub Release.
rem
rem  Usage:
rem      release.cmd              tag = v + versionName from app/build.gradle
rem      release.cmd v1.0.8.0     tag = the name you pass
rem ============================================================

set "REMOTE_URL=https://github.com/MCPE-XuanFeng/XuanFeng.git"
set "WEB_URL=https://github.com/MCPE-XuanFeng/XuanFeng"
set "BRANCH=main"

rem 0 = direct connection, 1 = go through the local proxy
set "USE_PROXY=0"
set "PROXY_ADDR=http://127.0.0.1:7897"

set "GITOPT="
if "%USE_PROXY%"=="1" set "GITOPT=-c http.proxy=%PROXY_ADDR% -c https.proxy=%PROXY_ADDR%"

echo ==================================================
echo   XuanFeng - tag release and push
echo ==================================================
echo.

where git >nul 2>&1
if errorlevel 1 goto :nogitbin

git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 goto :nogit

rem ---------- 1. decide the tag name ----------
set "TAG=%~1"
if not "%TAG%"=="" goto :tagready

set "VER="
for /f "tokens=2" %%a in ('findstr /C:"versionName" app\build.gradle') do set "VER=%%a"
if not defined VER goto :noversion
set VER=%VER:"=%
if "%VER%"=="" goto :noversion
set "TAG=v%VER%"

:tagready
echo Tag:    %TAG%
echo Branch: %BRANCH%
echo.

rem ---------- 2. refuse to release a dirty tree ----------
git diff --quiet
if errorlevel 1 goto :dirty
git diff --cached --quiet
if errorlevel 1 goto :dirty

rem ---------- 3. create the tag if it is missing ----------
git rev-parse -q --verify "refs/tags/%TAG%" >nul 2>&1
if not errorlevel 1 goto :tagexists

echo [1/3] creating annotated tag %TAG% ...
git tag -a "%TAG%" -m "XuanFeng %TAG%"
if errorlevel 1 goto :fail
goto :push

:tagexists
echo [1/3] tag %TAG% already exists locally - reusing it.

rem ---------- 4. push branch + tag ----------
:push
echo [2/3] pushing %BRANCH% ...
git %GITOPT% push --follow-tags origin HEAD:refs/heads/%BRANCH%
if errorlevel 1 goto :pushfailed

echo [3/3] pushing tag %TAG% ...
git %GITOPT% push origin "refs/tags/%TAG%"
if errorlevel 1 goto :pushfailed

echo.
echo Done. GitHub Actions is building the APK now:
echo   %WEB_URL%/actions
echo Release will appear at:
echo   %WEB_URL%/releases
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

:noversion
echo [ERROR] Could not read versionName from app\build.gradle.
echo         Pass the tag explicitly, e.g.:  release.cmd v1.0.8.0
goto :fail

:dirty
echo [ERROR] Working tree has uncommitted changes.
echo         Commit and push them first - run upload.cmd - then release.
goto :fail

:pushfailed
echo.
echo [ERROR] push failed.
echo   - Auth: MCPE-XuanFeng/XuanFeng needs a valid token or login.
echo     Run "git push" once manually to let Git Credential Manager sign you in.
echo   - Network: if GitHub is unreachable, start your local proxy and set
echo     USE_PROXY=1 at the top of this script.
echo.
goto :fail

:fail
echo.
pause
exit /b 1
