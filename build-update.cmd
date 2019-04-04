@echo off
@setlocal
set JAVA_HOME=c:\src\downloads\Java\JDK\jdk-11.0.2
set GRADLE_VERSION=5.3.2
REM set APP_PATH=D:\src\AndroidStudioProjects\MyApplication3.5.Canary7
set APP_PATH=D:\src\AndroidStudioProjects\MyApp-Kotlin-TOT-LocalGradle
REM set STUDIO_PATH=D:\src\downloads\AndroidStudio\3.5 Canary 4\android-studio\bin\studio64.exe
REM set GRADLE_CACHE_LOCATION=C:\Users\rpaquay\.gradle\wrapper\dists\gradle-local-%GRADLE_VERSION%
set GRADLE_CACHE_LOCATION=%GRADLE_USER_HOME%\wrapper\dists\gradle-local-%GRADLE_VERSION%


echo ============================================================================
echo Building Gradle distribution
echo ============================================================================

call .\gradlew.bat install -Pgradle_installPath=".\gradle-local-%GRADLE_VERSION%"
IF %ERRORLEVEL% NEQ 0 (
  echo ***************************************************************************************
  echo ********************** ERROR Building Gradle distribution *****************************
  echo ***************************************************************************************
  goto end
)

call "%ProgramFiles%\7-Zip\7z.exe" u "gradle-local-%GRADLE_VERSION%.zip" ".\gradle-local-%GRADLE_VERSION%"

echo ============================================================================
echo Killing Gradle Daemon and stopping Studio
echo ============================================================================
call "%APP_PATH%\gradlew" --stop
call taskkill /im studio64.exe

:LOOP
tasklist | find /i "studio64.exe" >nul 2>&1
IF ERRORLEVEL 1 (
  GOTO CONTINUE
) ELSE (
  ECHO Studio is still running
  Timeout /T 2 /Nobreak
  GOTO LOOP
)
:CONTINUE

echo ============================================================================
echo Removing gradle distribution from cache
echo ============================================================================
mtdel /q %GRADLE_CACHE_LOCATION%
echo You can now restart Android Studio
REM @setlocal
REM set PATH=C:\WINDOWS\system32;C:\WINDOWS
REM start "%STUDIO_PATH%"
goto end

:end
@endlocal
