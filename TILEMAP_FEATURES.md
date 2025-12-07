# 🎨 瓷磚地圖編輯器 (Tilemap Editor)

## ✨ 新功能概述

你的遊戲項目現在包含一個完整的 **瓷磚地圖編輯器**，功能類似於 **Unity 的 Tilemap**！

### 核心特性

✅ **導入瓷磚圖像**
- 支持 PNG、JPG、BMP 等圖像格式
- 自動識別瓷磚網格
- 可調整瓷磚尺寸

✅ **繪製地圖**
- 像 Unity Tilemap 一樣在網格上繪製
- 支持點擊和拖曳繪製
- 實時網格預覽

✅ **編輯工具**
- 繪製工具 (Draw)
- 橡皮擦工具 (Erase)
- 選擇工具 (Select)

✅ **撤銷/重做**
- 最多 50 步撤銷歷史
- Ctrl+Z 快速撤銷

✅ **保存/加載**
- 以二進制格式保存地圖數據
- 完全持久化

## 🚀 快速開始

### 運行編輯器

**方法 1：運行批處理檔案**
```batch
啟動瓷磚地圖編輯器.bat
```

**方法 2：Maven 運行**
```bash
mvn exec:java -Dexec.mainClass="TilemapEditorGUI"
```

## 📂 新增文件

| 文件 | 說明 |
|------|------|
| `TilemapEditorGUI.java` | 主編輯器 UI 和邏輯 |
| `TilemapConfig.java` | 地圖數據管理和序列化 |
| `啟動瓷磚地圖編輯器.bat` | Windows 啟動腳本 |
| `TILEMAP_EDITOR_README.md` | 詳細使用說明 |

## 🎮 使用流程

### 1️⃣ 導入瓷磚
```
點擊「📥 導入瓷磚圖像」→ 選擇你的瓷磚集合圖片
```

### 2️⃣ 調整瓷磚大小
```
根據你的圖片調整「瓷磚大小」（默認 32x32）
```

### 3️⃣ 選擇瓷磚
```
在左側面板點擊想要的瓷磚
```

### 4️⃣ 繪製地圖
```
在中央區域左鍵點擊或拖曳繪製瓷磚
```

### 5️⃣ 保存地圖
```
點擊「💾 儲存地圖」→ 生成 tilemap_config.dat
```

## ⌨️ 快速鍵

| 快速鍵 | 功能 |
|--------|------|
| **D** | 切換到繪製工具 |
| **E** | 切換到橡皮擦 |
| **Delete** | 擦除瓷磚 |
| **Ctrl+Z** | 撤銷 |

## 📐 地圖規格

- **瓷磚網格**: 80 × 18 個瓷磚
- **遊戲座標**: 4800 × 1080 像素
- **每個瓷磚**: 60 × 60 像素（顯示）
- **最大撤銷**: 50 步

## 🎨 編輯器界面

```
┌─────────────────────────────────────────────┐
│ 瓷磚集合          │  地圖編輯區域  │  工具  │
│  (左側)           │   (中央)       │ (右側) │
│                   │                │        │
│ • 導入圖像        │  顯示瓷磚地圖  │ • 工具 │
│ • 瓷磚尺寸        │  拖曳繪製      │ • 快速 │
│ • 瓷磚網格        │  網格顯示      │ • 說明 │
│                   │                │ • 按鈕 │
└─────────────────────────────────────────────┘
```

## 💾 保存格式

地圖保存為 `tilemap_config.dat`，包含：
- 地圖寬度和高度
- 每個瓷磚的索引值（-1 表示空）

```java
// 數據格式
[width: int] [height: int] [tile[0][0]: int] [tile[0][1]: int] ...
```

## 🔄 與遊戲集成

### 在遊戲中加載瓷磚地圖

```java
// 1. 加載地圖配置
TilemapConfig tilemapConfig = new TilemapConfig(80, 18);
tilemapConfig.load();

// 2. 獲取瓷磚數據
int tileIndex = tilemapConfig.getTile(x, y);

// 3. 根據索引渲染對應瓷磚
// 使用你的瓷磚圖像和索引來顯示瓷磚
```

## 📊 功能對比

| 功能 | Tilemap Editor | 原平台編輯器 |
|------|---|---|
| 導入圖像 | ✅ | ❌ |
| 瓷磚式編輯 | ✅ | ❌ |
| 網格繪製 | ✅ | ✅ |
| 撤銷功能 | ✅ | ✅ |
| 保存/加載 | ✅ | ✅ |
| 旋轉支持 | ❌ | ✅ |
| 多種平台類型 | ❌ | ✅ |

## 🎯 後續改進建議

- [ ] 添加圖層支持
- [ ] 支持旋轉和翻轉
- [ ] 支持動畫瓷磚
- [ ] 導出為 JSON/CSV 格式
- [ ] 支持瓷磚碰撞框設置
- [ ] 實時預覽遊戲視角

## 📝 技術細節

### 編譯和運行

```bash
# 編譯
mvn clean compile

# 運行
mvn exec:java -Dexec.mainClass="TilemapEditorGUI"

# 或直接
java -cp target/classes TilemapEditorGUI
```

### 依賴

- Java 21+
- JavaFX 17.0.12+
- FXGL 17.3+

## 🐛 常見問題

**Q: 如何更改地圖大小?**
A: 編輯 `TilemapEditorGUI.java` 中的常數：
```java
private static final int TILES_WIDTH = 80;   // 改這個
private static final int TILES_HEIGHT = 18;  // 改這個
```

**Q: 如何導出地圖？**
A: 當前保存為二進制文件 `tilemap_config.dat`。可以添加 JSON 導出功能。

**Q: 支持多少瓷磚？**
A: 取決於你的圖像大小。例如 512x512 的圖像（32x32 瓷磚）可以包含 256 個不同的瓷磚。

## 📚 相關文檔

- [詳細使用說明](TILEMAP_EDITOR_README.md)
- [MapEditorGUI 源碼](MapEditorGUI.java)（原平台編輯器）
- [GameClient 源碼](GameClient.java)（遊戲客戶端）

---

**🎉 祝你使用愉快！創作出精彩的瓷磚地圖吧！**
