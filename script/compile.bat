@echo off

Rem set GRAALVM_HOME=C:\Users\IEUser\Downloads\graalvm-ce-java8-19.3.1
Rem set PATH=%PATH%;C:\Users\IEUser\bin

if "%GRAALVM_HOME%"=="" (
    echo Please set GRAALVM_HOME
    exit /b
)

set JAVA_HOME=%GRAALVM_HOME%
set PATH=%GRAALVM_HOME%\bin;%PATH%

set /P GRASP_VERSION=< resources\GRASP_VERSION
echo Building grasp %GRASP_VERSION%

if "%GRAALVM_HOME%"=="" (
echo Please set GRAALVM_HOME
exit /b
)

set PATH=%USERPROFILE%\deps.clj;%PATH%

if not exist "classes" mkdir classes
call deps -M:native -e "(compile 'grasp.native)"
deps -Spath -A:native > .classpath
set /P NATIVE_CLASSPATH=<.classpath

call %GRAALVM_HOME%\bin\gu.cmd install native-image

call %GRAALVM_HOME%\bin\native-image.cmd ^
  "-cp" "classes;%NATIVE_CLASSPATH%" ^
  "-H:Name=grasp" ^
  "-H:+ReportExceptionStackTraces" ^
  "--initialize-at-build-time" ^
  "-H:EnableURLProtocols=jar" ^
  "--report-unsupported-elements-at-runtime" ^
  "--verbose" ^
  "--no-fallback" ^
  "--no-server" ^
  "-J-Xmx3g" ^
  "grasp.native"

del .classpath

if %errorlevel% neq 0 exit /b %errorlevel%

echo Creating zip archive
jar -cMf grasp-%GRASP_VERSION%-windows-amd64.zip grasp.exe
