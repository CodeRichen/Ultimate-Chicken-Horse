 編譯 TilemapEditorGUI （瓷磚編輯器）
javac --module-path "D:\Tools\javafx-sdk-17.0.17\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics *.java

 執行 TilemapEditorGUI
java --module-path "D:\Tools\javafx-sdk-17.0.17\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics TilemapEditorGUI

 編譯並執行原始 GameClient （需要 fxgl jar）
 javac --module-path "D:\Tools\javafx-sdk-17.0.17\lib" -cp ".;fxgl-21.1-uber.jar" --add-modules javafx.controls,javafx.fxml *.java   
 java --module-path "D:\Tools\javafx-sdk-17.0.17\lib" -cp ".;fxgl-21.1-uber.jar" --add-modules javafx.controls,javafx.fxml GameClient

又或者
# 清除舊產物
Remove-Item *.class -ErrorAction SilentlyContinue
Remove-Item .\target\classes -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item .\bin\*.class -ErrorAction SilentlyContinue

# 重新編譯
javac --module-path "D:\Tools\javafx-sdk-17.0.17\lib" `
      -cp ".;fxgl-21.1-uber.jar" `
      --add-modules javafx.controls,javafx.fxml,javafx.graphics `
      *.java

# 執行
java -cp ".;fxgl-21.1-uber.jar" GameServer

java --module-path "D:\Tools\javafx-sdk-17.0.17\lib" `
     -cp ".;fxgl-21.1-uber.jar" `
     --add-modules javafx.controls,javafx.fxml,javafx.graphics `
     GameClient


技術差異:

Socket 連接 (TCP) - 你的遊戲使用的方式:

建立持久的雙向連接
可以即時傳送/接收資料
用於多人遊戲的即時通訊
需要 TCP tunnel
HTTP 連接 - 網頁使用的方式:

一次性請求/回應
適合瀏覽器訪問網頁
不適合即時遊戲
為什麼會出錯:

當你使用 ngrok http 5000 時:

ngrok 給你 https://xxx.ngrok-free.app
你的程式執行 new Socket("https://xxx.ngrok-free.app", 5000)
Socket 不認識 https:// 這個協議 → UnknownHostException
當你使用 ngrok tcp 5000 時:

ngrok 給你 tcp://2.tcp.ngrok.io:12345
你的程式執行 new Socket("2.tcp.ngrok.io", 12345)
Socket 可以直接連接 TCP → 成功!

# 進入 Ultimate-Chicken-Horse 目錄，編譯並執行 TilemapEditorGUI
cd Ultimate-Chicken-Horse
javac -cp "lib/*" *.java
java -cp "lib/*:." TilemapEditorGUI
javac --module-path "path/to/javafx-sdk/lib" --add-modules javafx.controls,javafx.graphics *.java