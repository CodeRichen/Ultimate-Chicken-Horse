@echo off
echo Starting Ultimate Chicken Horse Client...
echo.
echo Checking Java version...
java -version
echo.

REM Set library path for native DLLs
set PATH=%PATH%;%~dp0lib\natives

REM Run the game with native access enabled
java --module-path "lib\javafx" -cp "bin;lib\fxgl-21.1-uber.jar" --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -Djava.library.path=lib\natives GameClient

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo Error: Failed to start client!
    echo ========================================
    echo.
    echo Possible solutions:
    echo 1. Make sure Java 17 or higher is installed
    echo 2. Check if graphics drivers are up to date
    echo 3. Try running as administrator
    echo.
    pause
)
