@echo off
REM 瓷磚地圖編輯器啟動檔案
REM 像 Unity Tilemap 一樣繪製地圖

setlocal enabledelayedexpansion

REM 尋找 Java 編譯器和運行環境
set JAVA_HOME=%JAVA_HOME%
if not defined JAVA_HOME (
    echo 未找到 JAVA_HOME 環境變量
    echo 請確保已安裝 Java 21 或更高版本
    pause
    exit /b 1
)

set JAVAC=%JAVA_HOME%\bin\javac.exe
set JAVA=%JAVA_HOME%\bin\java.exe

if not exist "%JAVAC%" (
    echo 未找到 Java 編譯器
    echo JAVA_HOME: %JAVA_HOME%
    pause
    exit /b 1
)

echo ========================================
echo 瓷磚地圖編輯器 - Tilemap Editor
echo ========================================
echo.

REM 編譯所有 Java 文件
echo 正在編譯源代碼...
"%JAVAC%" -encoding UTF-8 *.java
if errorlevel 1 (
    echo 編譯失敗！
    pause
    exit /b 1
)

echo 編譯成功！
echo.

REM 運行瓷磚地圖編輯器
echo 啟動瓷磚地圖編輯器...
echo.
"%JAVA%" TilemapEditorGUI

pause
