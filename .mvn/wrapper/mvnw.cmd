@REM ----------------------------------------------------------------------------
@REM Maven Wrapper for Windows — 自动下载并使用指定版本 Maven
@REM ----------------------------------------------------------------------------

@echo off
setlocal enabledelayedexpansion

set "SAVED_PWD=%cd%"
set "PRG=%~f0"
set "PRG_DIR=%~dp0"

if exist "%PRG_DIR%maven-wrapper.properties" (
  for /f "usebackq tokens=1,* delims==" %%a in ("%PRG_DIR%maven-wrapper.properties") do (
    if "%%a"=="distributionUrl" set "DIST_URL=%%b"
    if "%%a"=="wrapperVersion" set "WRAPPER_VERSION=%%b"
  )
)

set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%DIST_URL:~-40%"
set "MAVEN_OPTS=-Xmx1024m %MAVEN_OPTS%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Downloading Maven %DIST_URL%...
  mkdir "%MAVEN_HOME%" 2>nul
  powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%MAVEN_HOME%\maven.zip'; Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%\tmp'; Move-Item -Path '%MAVEN_HOME%\tmp\*\*' -Destination '%MAVEN_HOME%'; Remove-Item -Recurse -Force '%MAVEN_HOME%\tmp','%MAVEN_HOME%\maven.zip'}"
  if %errorlevel% neq 0 (
    echo Error: Failed to download or extract Maven
    exit /b 1
  )
  echo Maven installed: %MAVEN_HOME%
)

set "M2_HOME=%MAVEN_HOME%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %errorlevel%
