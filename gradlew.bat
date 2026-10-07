@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" (
  java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)

set GRADLE_VERSION=8.11.1
set BOOT=%APP_HOME%.gradle-bootstrap
set DIST=%BOOT%\gradle-%GRADLE_VERSION%
set ZIP=%BOOT%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BOOT%" mkdir "%BOOT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if (!(Test-Path '%ZIP%')) { Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ZIP%' }; Expand-Archive -Force '%ZIP%' '%BOOT%'"
  if errorlevel 1 exit /b %ERRORLEVEL%
)
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
