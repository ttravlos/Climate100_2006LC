@rem
@rem Gradle startup script for Windows
@rem Modified to recover gracefully from an invalid JAVA_HOME and to locate
@rem the JDK bundled with Android Studio.
@rem

@if "%DEBUG%" == "" @echo off
setlocal

set "DIRNAME=%~dp0"
if "%DIRNAME%" == "" set "DIRNAME=."
set "APP_BASE_NAME=%~n0"
set "APP_HOME=%DIRNAME%"

for %%i in ("%APP_HOME%") do set "APP_HOME=%%~fi"

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem --------------------------------------------------------------------------
@rem Locate Java.
@rem Search order:
@rem   1. Valid JAVA_HOME
@rem   2. Project-local .gradle\config.properties java.home entry
@rem   3. Android Studio bundled JBR/JRE in common install locations
@rem   4. java.exe available on PATH
@rem --------------------------------------------------------------------------

set "JAVA_EXE="

if defined JAVA_HOME (
    set "CANDIDATE_JAVA_HOME=%JAVA_HOME:"=%"
    if exist "%CANDIDATE_JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%CANDIDATE_JAVA_HOME%\bin\java.exe"
        goto execute
    )
    echo WARNING: JAVA_HOME is invalid and will be ignored:
    echo          %JAVA_HOME%
    echo.
)

@rem Android Studio may record the selected Gradle JDK here.
set "GRADLE_CONFIG=%APP_HOME%\.gradle\config.properties"
if exist "%GRADLE_CONFIG%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%GRADLE_CONFIG%") do (
        if /i "%%A"=="java.home" (
            set "CONFIG_JAVA_HOME=%%B"
        )
    )
)

if defined CONFIG_JAVA_HOME (
    set "CONFIG_JAVA_HOME=%CONFIG_JAVA_HOME:\\=\%"
    set "CONFIG_JAVA_HOME=%CONFIG_JAVA_HOME:\:=:%"
    if exist "%CONFIG_JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%CONFIG_JAVA_HOME%\bin\java.exe"
        goto execute
    )
)

for %%J in (
    "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
    "%ProgramFiles%\Android\Android Studio\jre\bin\java.exe"
    "%ProgramFiles%\Android\Android Studio Preview\jbr\bin\java.exe"
    "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe"
    "%LOCALAPPDATA%\Programs\Android Studio\jre\bin\java.exe"
) do (
    if exist "%%~J" (
        set "JAVA_EXE=%%~J"
        goto execute
    )
)

where java.exe >NUL 2>&1
if "%ERRORLEVEL%"=="0" (
    set "JAVA_EXE=java.exe"
    goto execute
)

echo.
echo ERROR: No usable Java installation was found.
echo.
echo Checked:
echo   - JAVA_HOME
echo   - .gradle\config.properties
echo   - Android Studio's bundled JBR/JRE
echo   - java.exe on PATH
echo.
echo Recommended fix:
echo   Set JAVA_HOME to the JDK used by Android Studio, usually:
echo   C:\Program Files\Android\Android Studio\jbr
echo.
goto fail

:execute
set "CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar"

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
if not ""=="%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
endlocal
