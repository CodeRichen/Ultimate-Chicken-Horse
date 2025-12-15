import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * 瓷磚地圖編輯器 - 支持導入圖像作為瓷磚集合，像 Unity Tilemap 一樣繪製地圖
 */
public class TilemapEditorGUI extends Application {
    
    private Canvas canvas;
    private Canvas tilesetCanvas;
    private GraphicsContext gc;
    private GraphicsContext tilesetGc;
    private TilemapConfig tilemapConfig;
    
    // 地圖設定
    private static final int TILES_WIDTH = 80;  // 水平瓷磚數
    private static final int TILES_HEIGHT = 18; // 垂直瓷磚數
    private static final int TILE_SIZE = 60;    // 每個瓷磚的像素大小
    private static final int CANVAS_WIDTH = TILES_WIDTH * TILE_SIZE;
    private static final int CANVAS_HEIGHT = TILES_HEIGHT * TILE_SIZE;
    
    // Tileset 相關
    private Image tilesetImage = null;
    private int tilesetTileWidth = 32;
    private int tilesetTileHeight = 32;
    private int tilesetColumns = 0;
    private List<Integer> selectedTileIndices = new ArrayList<>(); // 選中的瓷磚索引
    private int currentSelectedTile = -1; // 當前選中的單個瓷磚
    
    // 繪製模式
    private String currentTool = "DRAW";  // DRAW, ERASE, SELECT
    private boolean isDragging = false;
    private double dragStartX, dragStartY;
    
    // 選中的瓷磚地圖單元
    private int selectedMapTileX = -1;
    private int selectedMapTileY = -1;
    
    // 歷史記錄
    private List<int[][]> history = new ArrayList<>();
    private int historyIndex = -1;
    private static final int MAX_HISTORY = 50;
    
    // UI 元素
    private Label statusLabel;
    private Label coordLabel;
    private ComboBox<String> toolCombo;
    private Spinner<Integer> tileWidthSpinner;
    private Spinner<Integer> tileHeightSpinner;
    private Label tilesetInfoLabel;
    private ScrollPane tilesetScrollPane;
    
    @Override
    public void start(Stage primaryStage) {
        tilemapConfig = new TilemapConfig(TILES_WIDTH, TILES_HEIGHT);
        
        // 嘗試載入現有地圖
        try {
            tilemapConfig.load();
            System.out.println("已載入現有地圖配置");
        } catch (Exception e) {
            System.out.println("沒有找到現有地圖，從空白開始");
        }
        
        BorderPane root = new BorderPane();
        
        // 左側：瓷磚集合編輯器
        VBox leftPanel = createLeftPanel();
        root.setLeft(leftPanel);
        
        // 中心：地圖編輯區域
        VBox centerPanel = createCenterPanel();
        root.setCenter(centerPanel);
        
        // 右側：工具欄
        VBox toolPanel = createToolPanel();
        root.setRight(toolPanel);
        
        // 底部：狀態欄
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);
        
        // 初始繪製
        redrawMap();
        redrawTileset();
        
        Scene scene = new Scene(root, 1400, 800);
        primaryStage.setTitle("瓷磚地圖編輯器 - 像 Unity Tilemap 一樣繪製");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // 設置鍵盤事件
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DELETE:
                case BACK_SPACE:
                    eraseTile();
                    break;
                case Z:
                    if (e.isControlDown()) {
                        undo();
                    }
                    break;
                case D:
                    if (!e.isControlDown()) {
                        toolCombo.setValue("繪製工具");
                    }
                    break;
                case E:
                    if (!e.isControlDown()) {
                        toolCombo.setValue("橡皮擦");
                    }
                    break;
            }
        });
    }
    
    private VBox createLeftPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(8));
        panel.setPrefWidth(220);
        panel.setStyle("-fx-background-color: #2b2b2b;");
        
        Label title = new Label("瓷磚集合 (Tileset)");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        Button importButton = new Button("📥 導入瓷磚圖像");
        importButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        importButton.setOnAction(e -> importTileset());
        
        HBox tilesetSizeBox = new HBox(5);
        Label sizeLabel = new Label("瓷磚大小:");
        sizeLabel.setStyle("-fx-text-fill: white;");
        tileWidthSpinner = new Spinner<>(8, 256, 32, 4);
        tileWidthSpinner.setEditable(true);
        tileHeightSpinner = new Spinner<>(8, 256, 32, 4);
        tileHeightSpinner.setEditable(true);
        tileWidthSpinner.valueProperty().addListener((obs, old, newVal) -> {
            tilesetTileWidth = newVal;
            recalculateTileset();
        });
        tileHeightSpinner.valueProperty().addListener((obs, old, newVal) -> {
            tilesetTileHeight = newVal;
            recalculateTileset();
        });
        
        tilesetSizeBox.getChildren().addAll(sizeLabel, new Label("寬:"), tileWidthSpinner, new Label("高:"), tileHeightSpinner);
        tilesetSizeBox.setStyle("-fx-text-fill: white;");
        
        tilesetInfoLabel = new Label("未導入瓷磚圖像");
        tilesetInfoLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        
        tilesetScrollPane = new ScrollPane();
        tilesetCanvas = new Canvas(200, 300);
        tilesetGc = tilesetCanvas.getGraphicsContext2D();
        tilesetCanvas.setStyle("-fx-background-color: #1e1e1e;");
        tilesetCanvas.setOnMousePressed(this::handleTilesetMousePressed);
        tilesetCanvas.setOnMouseReleased(this::handleTilesetMouseReleased);
        tilesetScrollPane.setContent(tilesetCanvas);
        tilesetScrollPane.setStyle("-fx-background-color: #1e1e1e;");
        
        panel.getChildren().addAll(
            title,
            importButton,
            new Separator(),
            tilesetSizeBox,
            tilesetInfoLabel,
            new Label("選擇瓷磚:") {{ setStyle("-fx-text-fill: white;"); }},
            tilesetScrollPane
        );
        VBox.setVgrow(tilesetScrollPane, Priority.ALWAYS);
        
        return panel;
    }
    
    private VBox createCenterPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #2b2b2b;");
        
        Label title = new Label("地圖編輯區域");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        // 地圖編輯區域
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        canvas.setStyle("-fx-background-color: #1e1e1e;");
        canvas.setOnMousePressed(this::handleMapMousePressed);
        canvas.setOnMouseDragged(this::handleMapMouseDragged);
        canvas.setOnMouseReleased(this::handleMapMouseReleased);
        canvas.setOnMouseMoved(this::handleMapMouseMoved);
        
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: #1e1e1e;");
        
        panel.getChildren().addAll(title, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        return panel;
    }
    
    private VBox createToolPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(8));
        panel.setPrefWidth(200);
        panel.setStyle("-fx-background-color: #3c3c3c;");
        
        Label title = new Label("工具");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        // 工具選擇
        Label toolLabel = new Label("當前工具:");
        toolLabel.setStyle("-fx-text-fill: white;");
        toolCombo = new ComboBox<>();
        toolCombo.getItems().addAll("繪製工具", "橡皮擦", "選擇工具");
        toolCombo.setValue("繪製工具");
        toolCombo.setMaxWidth(Double.MAX_VALUE);
        toolCombo.setOnAction(e -> {
            String selected = toolCombo.getValue();
            currentTool = selected.equals("繪製工具") ? "DRAW" : 
                         selected.equals("橡皮擦") ? "ERASE" : "SELECT";
            statusLabel.setText("工具: " + selected);
        });
        
        Separator sep1 = new Separator();
        
        // 操作說明
        Label instructionLabel = new Label("操作說明:");
        instructionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        
        TextArea instructions = new TextArea(
            """
            【瓷磚集合】
            • 點擊選擇單個瓷磚
            
            【地圖編輯】
            • 左鍵點擊繪製瓷磚
            • 左鍵拖曳繪製多個
            • Delete 橡皮擦
            • Ctrl+Z 撤銷
            • D 鍵快速切繪製
            • E 鍵快速切橡皮擦
            
            【座標系】
            • X: 0-4800 (寬)
            • Y: 0-1080 (高)"""
        );
        instructions.setEditable(false);
        instructions.setPrefRowCount(12);
        instructions.setWrapText(true);
        instructions.setStyle("-fx-control-inner-background: #2b2b2b; -fx-text-fill: #aaa; -fx-font-size: 11px;");
        
        Separator sep2 = new Separator();
        
        // 按鈕區
        Button saveButton = new Button("💾 儲存地圖");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        saveButton.setOnAction(e -> saveMap());
        
        Button loadButton = new Button("📂 載入地圖");
        loadButton.setMaxWidth(Double.MAX_VALUE);
        loadButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        loadButton.setOnAction(e -> loadMap());
        
        Button clearButton = new Button("🗑️ 清空地圖");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        clearButton.setOnAction(e -> clearMap());
        
        Button undoButton = new Button("↺ 撤銷 (Ctrl+Z)");
        undoButton.setMaxWidth(Double.MAX_VALUE);
        undoButton.setStyle("-fx-background-color: #607D8B; -fx-text-fill: white; -fx-font-weight: bold;");
        undoButton.setOnAction(e -> undo());
        
        panel.getChildren().addAll(
            title,
            toolLabel, toolCombo,
            sep1,
            instructionLabel, instructions,
            sep2,
            saveButton, loadButton, clearButton, undoButton
        );
        
        return panel;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setPadding(new Insets(5, 15, 5, 15));
        statusBar.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #444; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        
        statusLabel = new Label("就緒");
        statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        coordLabel = new Label("座標: (0, 0)");
        coordLabel.setStyle("-fx-text-fill: white;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label selectedTileLabel = new Label("已選瓷磚: -");
        selectedTileLabel.setStyle("-fx-text-fill: white;");
        
        statusBar.getChildren().addAll(statusLabel, coordLabel, spacer, selectedTileLabel);
        
        return statusBar;
    }
    
    private void importTileset() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("選擇瓷磚圖像");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("圖像文件", "*.png", "*.jpg", "*.bmp"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                tilesetImage = new Image(file.toURI().toString());
                tilesetColumns = (int)(tilesetImage.getWidth() / tilesetTileWidth);
                if (tilesetColumns <= 0) tilesetColumns = 1;
                
                tilesetInfoLabel.setText("""
                    寬: %.0f px | 高: %.0f px
                    瓷磚數: %d x %d = %d"""
                    .formatted(tilesetImage.getWidth(), tilesetImage.getHeight(),
                              tilesetColumns, 
                              (int)(tilesetImage.getHeight() / tilesetTileHeight),
                              tilesetColumns * (int)(tilesetImage.getHeight() / tilesetTileHeight)));
                
                statusLabel.setText("✓ 已導入瓷磚圖像");
                currentSelectedTile = 0;
                selectedTileIndices.clear();
                selectedTileIndices.add(0);
                redrawTileset();
                redrawMap();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("導入失敗");
                alert.setContentText("導入瓷磚圖像失敗: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }
    
    private void recalculateTileset() {
        if (tilesetImage != null) {
            tilesetColumns = (int)(tilesetImage.getWidth() / tilesetTileWidth);
            if (tilesetColumns <= 0) tilesetColumns = 1;
            redrawTileset();
        }
    }
    
    private void handleTilesetMousePressed(MouseEvent e) {
        if (tilesetImage == null) return;
        
        int tileX = (int)(e.getX() / tilesetTileWidth);
        int tileY = (int)(e.getY() / tilesetTileHeight);
        int tileIndex = tileY * tilesetColumns + tileX;
        int maxTiles = tilesetColumns * (int)(tilesetImage.getHeight() / tilesetTileHeight);
        
        if (tileIndex >= 0 && tileIndex < maxTiles) {
            currentSelectedTile = tileIndex;
            statusLabel.setText("已選瓷磚: #" + tileIndex);
            redrawTileset();
        }
    }
    
    private void handleTilesetMouseReleased(MouseEvent e) {
    }
    
    private void handleMapMousePressed(MouseEvent e) {
        if (currentSelectedTile < 0 && !"ERASE".equals(currentTool)) {
            statusLabel.setText("請先選擇瓷磚");
            return;
        }
        
        isDragging = true;
        dragStartX = e.getX();
        dragStartY = e.getY();
        
        int tileX = (int)(e.getX() / TILE_SIZE);
        int tileY = (int)(e.getY() / TILE_SIZE);
        
        if ("DRAW".equals(currentTool)) {
            placeTile(tileX, tileY, currentSelectedTile);
        } else if ("ERASE".equals(currentTool)) {
            eraseTile(tileX, tileY);
        }
        
        redrawMap();
    }
    
    private void handleMapMouseDragged(MouseEvent e) {
        if (!isDragging) return;
        
        int tileX = (int)(e.getX() / TILE_SIZE);
        int tileY = (int)(e.getY() / TILE_SIZE);
        
        if ("DRAW".equals(currentTool)) {
            placeTile(tileX, tileY, currentSelectedTile);
        } else if ("ERASE".equals(currentTool)) {
            eraseTile(tileX, tileY);
        }
        
        redrawMap();
    }
    
    private void handleMapMouseReleased(MouseEvent e) {
        if (isDragging) {
            isDragging = false;
            saveToHistory();
        }
    }
    
    private void handleMapMouseMoved(MouseEvent e) {
        int tileX = (int)(e.getX() / TILE_SIZE);
        int tileY = (int)(e.getY() / TILE_SIZE);
        
        // 計算實際遊戲座標
        double worldX = tileX * (4800.0 / TILES_WIDTH);
        double worldY = tileY * (1080.0 / TILES_HEIGHT);
        
        coordLabel.setText(String.format("座標: (%d, %d) [瓷磚: %d, %d]", 
                                         (int)worldX, (int)worldY, tileX, tileY));
    }
    
    private void placeTile(int tileX, int tileY, int tileIndex) {
        if (tileX >= 0 && tileX < TILES_WIDTH && tileY >= 0 && tileY < TILES_HEIGHT) {
            tilemapConfig.setTile(tileX, tileY, tileIndex);
        }
    }
    
    private void eraseTile(int tileX, int tileY) {
        if (tileX >= 0 && tileX < TILES_WIDTH && tileY >= 0 && tileY < TILES_HEIGHT) {
            tilemapConfig.setTile(tileX, tileY, -1);
        }
    }
    
    private void eraseTile() {
        if (selectedMapTileX >= 0 && selectedMapTileY >= 0) {
            eraseTile(selectedMapTileX, selectedMapTileY);
            redrawMap();
        }
    }
    
    private void saveToHistory() {
        int[][] snapshot = new int[TILES_HEIGHT][TILES_WIDTH];
        for (int y = 0; y < TILES_HEIGHT; y++) {
            for (int x = 0; x < TILES_WIDTH; x++) {
                snapshot[y][x] = tilemapConfig.getTile(x, y);
            }
        }
        
        while (historyIndex < history.size() - 1) {
            history.removeLast();
        }
        
        history.add(snapshot);
        historyIndex++;
        
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
            historyIndex--;
        }
    }
    
    private void undo() {
        if (historyIndex > 0) {
            historyIndex--;
            int[][] previousState = history.get(historyIndex);
            
            for (int y = 0; y < TILES_HEIGHT; y++) {
                for (int x = 0; x < TILES_WIDTH; x++) {
                    tilemapConfig.setTile(x, y, previousState[y][x]);
                }
            }
            
            redrawMap();
            statusLabel.setText("↶ 已撤銷");
        }
    }
    
    private void redrawMap() {
        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        
        // 繪製網格
        gc.setStroke(Color.rgb(60, 60, 60));
        gc.setLineWidth(0.5);
        for (int x = 0; x <= TILES_WIDTH; x++) {
            gc.strokeLine(x * TILE_SIZE, 0, x * TILE_SIZE, CANVAS_HEIGHT);
        }
        for (int y = 0; y <= TILES_HEIGHT; y++) {
            gc.strokeLine(0, y * TILE_SIZE, CANVAS_WIDTH, y * TILE_SIZE);
        }
        
        // 繪製瓷磚
        if (tilesetImage != null) {
            for (int y = 0; y < TILES_HEIGHT; y++) {
                for (int x = 0; x < TILES_WIDTH; x++) {
                    int tileIndex = tilemapConfig.getTile(x, y);
                    if (tileIndex >= 0) {
                        drawTile(x, y, tileIndex);
                    }
                }
            }
        }
    }
    
    private void drawTile(int gridX, int gridY, int tileIndex) {
        if (tilesetImage == null) return;
        
        int tilesetX = (tileIndex % tilesetColumns) * tilesetTileWidth;
        int tilesetY = (tileIndex / tilesetColumns) * tilesetTileHeight;
        
        int canvasX = gridX * TILE_SIZE;
        int canvasY = gridY * TILE_SIZE;
        
        gc.drawImage(tilesetImage,
                    tilesetX, tilesetY, tilesetTileWidth, tilesetTileHeight,
                    canvasX, canvasY, TILE_SIZE, TILE_SIZE);
    }
    
    private void redrawTileset() {
        if (tilesetImage == null) {
            tilesetGc.setFill(Color.rgb(30, 30, 30));
            tilesetGc.fillRect(0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());
            tilesetGc.setFill(Color.WHITE);
            tilesetGc.fillText("未導入瓷磚圖像", 20, 50);
            return;
        }
        
        // 調整 Canvas 大小
        int rows = (int)Math.ceil(tilesetImage.getHeight() / tilesetTileHeight);
        tilesetCanvas.setHeight(rows * tilesetTileHeight + 10);
        
        // 繪製瓷磚集合
        tilesetGc.setFill(Color.rgb(30, 30, 30));
        tilesetGc.fillRect(0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());
        
        // 繪製每個瓷磚
        for (int i = 0; i < tilesetColumns * rows; i++) {
            int tileX = (i % tilesetColumns) * tilesetTileWidth;
            int tileY = (i / tilesetColumns) * tilesetTileHeight;
            
            int srcX = (i % tilesetColumns) * tilesetTileWidth;
            int srcY = (i / tilesetColumns) * tilesetTileHeight;
            
            tilesetGc.drawImage(tilesetImage,
                               srcX, srcY, tilesetTileWidth, tilesetTileHeight,
                               tileX, tileY, tilesetTileWidth, tilesetTileHeight);
            
            // 如果是選中的瓷磚，繪製邊框
            if (i == currentSelectedTile) {
                tilesetGc.setStroke(Color.YELLOW);
                tilesetGc.setLineWidth(2);
                tilesetGc.strokeRect(tileX, tileY, tilesetTileWidth, tilesetTileHeight);
            }
            
            // 繪製網格線
            tilesetGc.setStroke(Color.rgb(50, 50, 50));
            tilesetGc.setLineWidth(0.5);
            tilesetGc.strokeRect(tileX, tileY, tilesetTileWidth, tilesetTileHeight);
        }
    }
    
    private void saveMap() {
        try {
            tilemapConfig.save();
            statusLabel.setText("✓ 地圖已儲存");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("儲存成功");
            alert.setContentText("瓷磚地圖已成功儲存！");
            alert.showAndWait();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("儲存失敗");
            alert.setContentText("儲存地圖時發生錯誤: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    private void loadMap() {
        try {
            tilemapConfig.load();
            redrawMap();
            statusLabel.setText("✓ 已載入地圖");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("載入成功");
            alert.setContentText("瓷磚地圖已成功載入！");
            alert.showAndWait();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("載入失敗");
            alert.setContentText("載入地圖時發生錯誤: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    private void clearMap() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("確認清空");
        confirm.setHeaderText("確定要清空地圖嗎?");
        confirm.setContentText("這將刪除所有瓷磚!");
        
        if (confirm.showAndWait().get() == ButtonType.OK) {
            tilemapConfig.clear();
            saveToHistory();
            redrawMap();
            statusLabel.setText("✓ 地圖已清空");
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
