@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script, simplified for this repository.
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "BASEDIR=%~dp0"
if "%BASEDIR:~-1%"=="\" set "BASEDIR=%BASEDIR:~0,-1%"

set "WRAPPER_DIR=%BASEDIR%\.mvn\wrapper"
set "PROPS_FILE=%WRAPPER_DIR%\maven-wrapper.properties"
set "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"

if not exist "%PROPS_FILE%" (
  echo Missing %PROPS_FILE%
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS_FILE%") do (
  if "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
)

if not exist "%WRAPPER_JAR%" (
  echo Downloading Maven Wrapper jar...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$u='%WRAPPER_URL%';" ^
    "$o='%WRAPPER_JAR%';" ^
    "New-Item -ItemType Directory -Force -Path (Split-Path -Parent $o) | Out-Null;" ^
    "Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $o;"
  if errorlevel 1 (
    echo Failed to download Maven Wrapper jar from %WRAPPER_URL%
    exit /b 1
  )
)

set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java"

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%BASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %errorlevel%
