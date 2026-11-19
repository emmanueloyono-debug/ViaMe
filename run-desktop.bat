@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title ViaMe - Desktop Application

echo ================================================================
echo   Launching ViaMe Multi-Role Desktop Simulation (JavaFX)...
echo ================================================================

call :resolve_java
if errorlevel 1 exit /b 1

call :resolve_maven
if defined MVN_CMD (
    echo [INFO] Compiling with Maven...
    call "!MVN_CMD!" -q compile
    if errorlevel 1 (
        echo [WARN] Maven compile failed; falling back to javac...
        call :compile_with_javac
        if errorlevel 1 exit /b 1
    )
) else (
    if not exist "target\classes\com\viame\launcher\MainApp.class" (
        call :compile_with_javac
        if errorlevel 1 exit /b 1
    )
)

call :build_classpath
echo [INFO] Using Java: %JAVA_EXE%
"%JAVA_EXE%" -cp "%CP%" com.viame.launcher.MainApp
exit /b %ERRORLEVEL%

:resolve_java
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /f "delims=" %%J in ('where java 2^>nul') do (
        if not defined JAVA_EXE set "JAVA_EXE=%%J"
    )
)
if not defined JAVA_EXE (
    for /d %%D in ("%USERPROFILE%\.jdks\jbr-*") do (
        if not defined JAVA_EXE if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
        if not defined JAVA_EXE if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    echo [ERROR] Could not locate java.exe. Set JAVA_HOME or install JDK 17+.
    exit /b 1
)
for %%P in ("%JAVA_EXE%") do set "JAVA_BIN=%%~dpP"
for %%H in ("%JAVA_BIN%..") do (
    if not defined JAVA_HOME if exist "%%~fH\bin\javac.exe" set "JAVA_HOME=%%~fH"
)
if not defined JAVA_HOME (
    for /d %%D in ("%USERPROFILE%\.jdks\jbr-*") do (
        if not defined JAVA_HOME if exist "%%D\bin\javac.exe" set "JAVA_HOME=%%D"
    )
)
exit /b 0

:resolve_maven
set "MVN_CMD="
for /f "delims=" %%M in ('where mvn 2^>nul') do (
    if not defined MVN_CMD set "MVN_CMD=%%M"
)
if not defined MVN_CMD (
    for /d %%I in ("C:\Program Files\JetBrains\IntelliJ IDEA*") do (
        if not defined MVN_CMD if exist "%%I\plugins\maven\lib\maven3\bin\mvn.cmd" (
            set "MVN_CMD=%%I\plugins\maven\lib\maven3\bin\mvn.cmd"
        )
    )
)
exit /b 0

:build_classpath
set "M2_REPO=%USERPROFILE%\.m2\repository"
set "CP=target\classes"
for %%G in ("org\openjfx" "com\mysql" "org\mindrot" "com\google\protobuf") do (
    if exist "%M2_REPO%\%%~G" (
        for /r "%M2_REPO%\%%~G" %%F in (*.jar) do (
            echo "%%~nxF" | findstr /i /r "\-sources\.jar$ \-javadoc\.jar$" >nul || set "CP=!CP!;%%F"
        )
    )
)
exit /b 0

:compile_with_javac
set "JAVAC_EXE=%JAVA_BIN%javac.exe"
if not exist "%JAVAC_EXE%" (
    set "JAVAC_EXE="
    for /f "delims=" %%C in ('where javac 2^>nul') do (
        if not defined JAVAC_EXE set "JAVAC_EXE=%%C"
    )
)
if not defined JAVAC_EXE (
    for /d %%D in ("%USERPROFILE%\.jdks\jbr-*") do (
        if not defined JAVAC_EXE if exist "%%D\bin\javac.exe" set "JAVAC_EXE=%%D\bin\javac.exe"
    )
)
if not defined JAVAC_EXE (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
        if not defined JAVAC_EXE if exist "%%D\bin\javac.exe" set "JAVAC_EXE=%%D\bin\javac.exe"
    )
)
if not defined JAVAC_EXE (
    echo [ERROR] Could not locate javac.exe to compile sources.
    exit /b 1
)
call :build_classpath
if not exist "target\classes" mkdir "target\classes"
if exist "src\main\resources" xcopy /e /i /y /q "src\main\resources\*" "target\classes\" >nul
(
    for /r "src\main\java" %%S in (*.java) do (
        set "SRC=%%S"
        set "SRC=!SRC:\=/!"
        echo "!SRC!"
    )
) > "target\sources.txt"
"%JAVAC_EXE%" -encoding UTF-8 -cp "%CP%" -d "target\classes" "@target\sources.txt"
set "COMPILE_ERR=%ERRORLEVEL%"
del /q "target\sources.txt" 2>nul
exit /b %COMPILE_ERR%
