@echo off
echo ========================================
echo Building Ultimate Chicken Horse Client
echo ========================================
echo.

REM Create release directory structure
echo Creating directory structure...
if not exist "release" mkdir release
if not exist "release\bin" mkdir release\bin
if not exist "release\lib" mkdir release\lib

REM Compile Java files
echo.
echo Compiling Java files...
javac --module-path "D:\Tools\javafx-sdk-17.0.17\lib" -cp ".;fxgl-21.1-uber.jar" --add-modules javafx.controls,javafx.fxml -d release\bin *.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed!
    pause
    exit /b 1
)

REM Copy required libraries
echo.
echo Copying libraries...
copy "fxgl-21.1-uber.jar" "release\lib\" >nul
if not exist "release\lib\javafx" mkdir "release\lib\javafx"
if not exist "release\lib\natives" mkdir "release\lib\natives"
xcopy /E /I /Y "D:\Tools\javafx-sdk-17.0.17\lib\*.jar" "release\lib\javafx\" >nul
xcopy /E /I /Y "D:\Tools\javafx-sdk-17.0.17\bin\*.dll" "release\lib\natives\" >nul

REM Copy map config if exists
if exist "map_config.dat" (
    echo Copying map configuration...
    copy "map_config.dat" "release\" >nul
)

REM Copy server config - use the one from project root
echo Copying server configuration...
if exist "server_config.txt" (
    copy "server_config.txt" "release\" >nul
) else (
    REM If no config exists, create default one
    (
    echo # Ultimate Chicken Horse - Server Configuration
    echo # 伺服器配置文件
    echo # 
    echo # 如果要連接到本地伺服器,使用:
    echo # SERVER_HOST=127.0.0.1
    echo # SERVER_PORT=5000
    echo #
    echo # 如果要連接到遠端伺服器 ^(使用 ngrok^),請填入 ngrok 提供的網址:
    echo # 例如: 4587b00fd592.ngrok-free.app
    echo # 注意: 只需要填入域名部分,不要包含 https:// 或 http://
    echo #
    echo # 使用範例:
    echo # SERVER_HOST=4587b00fd592.ngrok-free.app
    echo # SERVER_PORT=80
    echo.
    echo # 預設本地伺服器配置
    echo SERVER_HOST=127.0.0.1
    echo SERVER_PORT=5000
    ) > "release\server_config.txt"
)

REM Copy ngrok guide
echo Copying connection guide...
if exist "NGROK_GUIDE.txt" (
    copy "NGROK_GUIDE.txt" "release\" >nul
)

REM Create run script
echo.
echo Creating run script...
(
echo @echo off
echo echo Starting Ultimate Chicken Horse Client...
echo echo.
echo echo Checking Java version...
echo java -version
echo echo.
echo.
echo REM Set library path for native DLLs
echo set PATH=%%PATH%%;%%~dp0lib\natives
echo.
echo REM Run the game with native access enabled
echo java --module-path "lib\javafx" -cp "bin;lib\fxgl-21.1-uber.jar" --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -Djava.library.path=lib\natives GameClient
echo.
echo if %%ERRORLEVEL%% NEQ 0 ^(
echo     echo.
echo     echo ========================================
echo     echo Error: Failed to start client!
echo     echo ========================================
echo     echo.
echo     echo Possible solutions:
echo     echo 1. Make sure Java 17 or higher is installed
echo     echo 2. Check if graphics drivers are up to date
echo     echo 3. Try running as administrator
echo     echo.
echo     pause
echo ^)
) > "release\run_client.bat"

REM Create README
echo Creating README...
(
echo ========================================
echo Ultimate Chicken Horse - Client
echo ========================================
echo.
echo SYSTEM REQUIREMENTS:
echo - Java 17 or higher
echo - Windows 10/11 ^(64-bit^)
echo - Graphics card with DirectX support
echo.
echo IMPORTANT:
echo - Server must be running before starting client
echo - Default server: 127.0.0.1:12345
echo.
echo HOW TO RUN:
echo 1. Make sure the game server is running
echo 2. Double-click "run_client.bat"
echo 3. Or run from command line: run_client.bat
echo.
echo GAME CONTROLS:
echo - A / Left Arrow  : Move Left ^(or move camera when spectating^)
echo - D / Right Arrow : Move Right ^(or move camera when spectating^)
echo - W / Up / Space  : Jump
echo - S / Down        : Crouch
echo.
echo GAME MODES:
echo - Create Public Room  : Anyone can join
echo - Create Private Room : Need 4-digit code to join
echo - Join Random Room    : Join any public room
echo - Join with Code      : Enter 4-digit room code
echo.
echo GAMEPLAY:
echo 1. Select Phase - Choose an object from random selection
echo 2. Place Phase  - Place your object on the map ^(drag, rotate with mouse wheel^)
echo 3. Race Phase   - Race to the finish line!
echo.
echo SPECTATE MODE:
echo - After finishing or dying, use A/D to move camera
echo - Watch other players until round ends
echo - Leaderboard shows when all players finish
echo.
echo OBJECTS:
echo - Normal Platform : Gray, basic platform
echo - Death Platform  : Red, die when standing on top ^(recover after 2s^)
echo - Bounce Platform : Green, extra jump boost
echo - Moving Platform: Blue ^(horizontal^) / Purple ^(vertical^)
echo - Turret         : Yellow-Red gradient, shoots bullets
echo - Eraser         : Removes platforms in range
echo.
echo TROUBLESHOOTING:
echo - If game doesn't start, check Java version: java -version
echo - Connection refused? Make sure server is running first
echo - Graphics error? Update your graphics drivers
echo - Still not working? Try running as administrator
echo.
echo ========================================
echo HOW TO CONNECT TO REMOTE SERVER:
echo ========================================
echo.
echo METHOD 1: Using ngrok ^(Recommended for Internet play^)
echo.
echo 1. Server host runs: ngrok tcp 5000
echo 2. ngrok will show: tcp://2.tcp.ngrok.io:12345
echo 3. Edit server_config.txt and enter: 2.tcp.ngrok.io
echo 4. Edit port_config.txt and enter: 12345
echo 5. Run the game!
echo.
echo METHOD 2: Using Local Network
echo.
echo 1. Find server's local IP ^(ipconfig^)
echo 2. Edit server_config.txt and enter the IP
echo 3. Edit port_config.txt and enter: 5000
echo 4. Make sure firewall allows port 5000
echo.
echo METHOD 3: Using Public IP
echo.
echo 1. Configure port forwarding on router
echo 2. Edit server_config.txt with public IP
echo 3. Edit port_config.txt with forwarded port
echo.
echo ========================================
) > "release\README.txt"

echo.
echo ========================================
echo Build completed successfully!
echo ========================================
echo.
echo Release files are in the "release" folder:
echo - bin\          : Compiled .class files
echo - lib\          : Required libraries
echo - run_client.bat: Run the client
echo - README.txt    : User guide
echo.
pause
