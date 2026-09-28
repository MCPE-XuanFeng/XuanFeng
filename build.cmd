@echo off
setlocal
cd /d "%~dp0"

rem ============================================================
rem  XuanFeng - build debug APK and collect it into .\apks
rem  @author rpg636zjhi(3187735201)
rem  @link   https://github.com/MCPE-XuanFeng/XuanFeng
rem
rem  The Gradle build JVM is pinned to JDK 21 globally via
rem  %USERPROFILE%\.gradle\gradle.properties (org.gradle.java.home),
rem  but gradlew.bat still needs JAVA_HOME (or java on PATH) to
rem  start its client JVM - so this script locates a JDK itself.
rem ============================================================

echo ==================================================
echo   XuanFeng - build debug APK into .\apks
echo ==================================================
echo.

set "OUTDIR=apks"
set "APKSRC=app\build\outputs\apk\debug\app-debug.apk"
set "JDK_CANDIDATE=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"

rem ---------- 0. locate a JDK for the gradle client ----------
if defined JAVA_HOME goto :jdk_done
if not exist "%JDK_CANDIDATE%\bin\java.exe" goto :jdk_done
set "JAVA_HOME=%JDK_CANDIDATE%"
echo [jdk] JAVA_HOME was empty - using %JAVA_HOME%

:jdk_done
if defined JAVA_HOME goto :jdk_ok
where java >nul 2>&1
if errorlevel 1 goto :nojava

:jdk_ok
if not exist "gradlew.bat" goto :nowrapper

rem ---------- 1. build ----------
echo [1/3] gradlew.bat assembleDebug ...
echo.
call gradlew.bat assembleDebug
if errorlevel 1 goto :buildfailed
echo.

rem ---------- 2. locate APK ----------
echo [2/3] locate APK ...
if not exist "%APKSRC%" goto :noapk

rem ---------- 3. collect into .\apks ----------
echo [3/3] copy into .\%OUTDIR% ...
if not exist "%OUTDIR%" mkdir "%OUTDIR%"

set "STAMP="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%i"
if not defined STAMP set "STAMP=latest"

set "APKDST=%OUTDIR%\XuanFeng-%STAMP%.apk"
copy /y "%APKSRC%" "%APKDST%" >nul
if errorlevel 1 goto :fail
copy /y "%APKSRC%" "%OUTDIR%\XuanFeng-latest.apk" >nul

echo.
echo Done.
echo   %APKDST%
echo   %OUTDIR%\XuanFeng-latest.apk
for %%f in ("%APKDST%") do echo   size: %%~zf bytes
echo.
echo Install to a connected device with:
echo   adb install -r "%OUTDIR%\XuanFeng-latest.apk"
echo.
pause
exit /b 0

rem ---------- error paths ----------
:nojava
echo.
echo [ERROR] No JDK found.
echo   JAVA_HOME is not set, "%JDK_CANDIDATE%" does not exist,
echo   and there is no "java" on PATH.
echo.
echo   Install JDK 21 and either put it on PATH or point JAVA_HOME at it.
echo.
goto :fail

:nowrapper
echo [ERROR] gradlew.bat not found.
echo         Run this script from the project root folder.
goto :fail

:noapk
echo [ERROR] APK not found at "%APKSRC%"
echo         The build reported success but produced no APK. Check app\build\outputs\apk\.
goto :fail

:buildfailed
echo.
echo [ERROR] Build failed. Scroll up for the real cause.
echo.
echo   If it says: What went wrong: 25.0.3
echo   then Gradle picked up a too-new JDK. Ensure this line exists in
echo   %USERPROFILE%\.gradle\gradle.properties
echo       org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot
echo.
echo   If it says: could not resolve / Plugin not found
echo   then it is a network issue - check that dl.google.com is reachable.
echo.
goto :fail

:fail
echo.
pause
exit /b 1
