@echo off

setLocal EnableExtensions EnableDelayedExpansion

set NOZZLE_JAR="%~dp0..\lib\es-nozzle.jar"

if "x!JAVA_CMD!" == "x" set JAVA_CMD=java

rem remove quotes from around java commands
for /f "usebackq delims=" %%i in ('!JAVA_CMD!') do set JAVA_CMD=%%~i
goto :RUN

:RUN
:: We need to disable delayed expansion here because the %* variable
:: may contain bangs (as in test!).
setLocal DisableDelayedExpansion


"%JAVA_CMD%" -jar %NOZZLE_JAR% %*
goto :EOF


:EOF
