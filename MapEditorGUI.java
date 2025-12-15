import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * 圖形化地圖編輯器 - 使用拖曳方式創建平台
 */
public class MapEditorGUI extends Application {
    
    private Canvas canvas;
    private GraphicsContext gc;
    private MapConfig mapConfig;
    
    // 地圖尺寸(與GameClient一致)
    private static final double MAP_WIDTH = 4800;
    private static final double MAP_HEIGHT = 1080;  // 遊戲實際高度
    private static final double CANVAS_WIDTH = 1200;
    private static final double CANVAS_HEIGHT = 600;
    private static final double SCALE = CANVAS_WIDTH / MAP_WIDTH;
    
    // 繪製模式
    private String currentMode = "NORMAL";
    private boolean isDragging = false;
    private boolean isDraggingPlatform = false;
    private double dragStartX, dragStartY;
    private double dragEndX, dragEndY;
    private double platformDragOffsetX, platformDragOffsetY;
    
    // 選中的平台
    private MapPlatform selectedPlatform = null;
    private int selectedIndex = -1;
    
    // 歷史記錄 (用於 Undo)
    private List<List<MapPlatform>> history = new ArrayList<>();
    private int historyIndex = -1;
    private static final int MAX_HISTORY = 50;
    
    // UI 元素
    private Label statusLabel;
    private Label coordLabel;
    private ComboBox<String> typeCombo;
    private Spinner<Integer> widthSpinner;
    private Spinner<Integer> heightSpinner;
    private Spinner<Double> rotationSpinner;
    private TextField imagePathField;
    private Button browseImageButton;
    private String currentImagePath = null;
    private boolean lockImageSize = false;  // 選擇圖片時固定其原始尺寸
    private double lockedImageWidth = 0;
    private double lockedImageHeight = 0;
    
    // 圖片快取
    private Map<String, Image> imageCache = new HashMap<>();
    
    @Override
    public void start(Stage primaryStage) {
        mapConfig = new MapConfig();
        
        // 嘗試載入現有地圖
        try {
            mapConfig.load();
            System.out.println("已載入現有地圖配置");
        } catch (Exception e) {
            System.out.println("沒有找到現有地圖，從空白開始");
        }
        
        BorderPane root = new BorderPane();
        
        // 創建畫布
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        
        // 設置畫布事件
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        
        // 包裝畫布
        StackPane canvasPane = new StackPane(canvas);
        canvasPane.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #444; -fx-border-width: 2;");
        root.setCenter(canvasPane);
        
        // 創建工具欄
        VBox toolPanel = createToolPanel();
        root.setRight(toolPanel);
        
        // 創建底部狀態欄
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);
        
        // 初始繪製
        redraw();
        
        Scene scene = new Scene(root, 1600, 650);
        
        // 設置鍵盤事件
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DELETE:
                case BACK_SPACE:
                    deleteSelected();
                    break;
                case Z:
                    if (e.isControlDown()) {
                        undo();
                    }
                    break;
            }
        });
        
        primaryStage.setTitle("地圖編輯器 - 拖曳創建平台");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private VBox createToolPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(15));
        panel.setPrefWidth(350);
        panel.setStyle("-fx-background-color: #3c3c3c;");
        
        // 標題
        Label title = new Label("地圖編輯工具");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        // 平台類型選擇
        Label typeLabel = new Label("平台類型:");
        typeLabel.setStyle("-fx-text-fill: white;");
        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
            "NORMAL - 普通平台",
            "DEATH - 死亡平台",
            "BOUNCE - 彈跳平台",
            "MOVING_H - 水平移動",
            "MOVING_V - 垂直移動"
        );
        typeCombo.setValue("NORMAL - 普通平台");
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setOnAction(e -> updateMode());
        
        // 寬度設置
        Label widthLabel = new Label("寬度 (無限制):");
        widthLabel.setStyle("-fx-text-fill: white;");
        widthSpinner = new Spinner<>(10, 10000, 150, 10);
        widthSpinner.setEditable(true);
        widthSpinner.setMaxWidth(Double.MAX_VALUE);
        
        // 高度設置
        Label heightLabel = new Label("高度 (無限制):");
        heightLabel.setStyle("-fx-text-fill: white;");
        heightSpinner = new Spinner<>(10, 2000, 20, 5);
        heightSpinner.setEditable(true);
        heightSpinner.setMaxWidth(Double.MAX_VALUE);
        
        // 旋轉角度
        Label rotationLabel = new Label("旋轉角度:");
        rotationLabel.setStyle("-fx-text-fill: white;");
        rotationSpinner = new Spinner<>(0.0, 360.0, 0.0, 90.0);
        rotationSpinner.setEditable(true);
        rotationSpinner.setMaxWidth(Double.MAX_VALUE);
        
        // 圖片選擇
        Label imageLabel = new Label("平台圖片 (選填):");
        imageLabel.setStyle("-fx-text-fill: white;");
        
        HBox imageBox = new HBox(5);
        imagePathField = new TextField();
        imagePathField.setPromptText("選擇圖片或留空使用顏色");
        imagePathField.setEditable(false);
        imagePathField.setPrefWidth(200);
        
        browseImageButton = new Button("瀏覽...");
        browseImageButton.setOnAction(e -> browseImage());
        
        Button clearImageButton = new Button("清除");
        clearImageButton.setOnAction(e -> {
            currentImagePath = null;
            lockImageSize = false;
            lockedImageWidth = 0;
            lockedImageHeight = 0;
            imagePathField.clear();
            widthSpinner.setDisable(false);
            heightSpinner.setDisable(false);
            statusLabel.setText("已清除圖片，將使用顏色");
        });
        
        imageBox.getChildren().addAll(imagePathField, browseImageButton, clearImageButton);
        
        Separator sep1 = new Separator();
        
        // 操作說明
        Label instructionLabel = new Label("操作說明:");
        instructionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        
        TextArea instructions = new TextArea(
            """
            • 按住滑鼠左鍵拖曳矩形區域
              拖曳的範圍即為平台大小
            • 可選擇圖片作為平台材質
              支援 PNG, JPG, GIF, BMP 等格式
            • 點擊平台選中，拖曳移動
            • Delete/Backspace 刪除選中
            • Ctrl+Z 撤銷上一步
            • 起點(綠線): X=50
            • 終點(金線): X=4000
            • 可達範圍: 0-4800 x 0-700"""
        );
        instructions.setEditable(false);
        instructions.setPrefRowCount(9);
        instructions.setWrapText(true);
        instructions.setStyle("-fx-control-inner-background: #2b2b2b; -fx-text-fill: #aaa;");
        
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
        
        Button defaultButton = new Button("📋 創建預設地圖");
        defaultButton.setMaxWidth(Double.MAX_VALUE);
        defaultButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        defaultButton.setOnAction(e -> createDefaultMap());
        
        Button deleteButton = new Button("❌ 刪除選中平台");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> deleteSelected());
        
        Button undoButton = new Button("↺ 撤銷 (Ctrl+Z)");
        undoButton.setMaxWidth(Double.MAX_VALUE);
        undoButton.setStyle("-fx-background-color: #607D8B; -fx-text-fill: white; -fx-font-weight: bold;");
        undoButton.setOnAction(e -> undo());
        
        panel.getChildren().addAll(
            title,
            typeLabel, typeCombo,
            widthLabel, widthSpinner,
            heightLabel, heightSpinner,
            rotationLabel, rotationSpinner,
            imageLabel, imageBox,
            sep1,
            instructionLabel, instructions,
            sep2,
            saveButton, loadButton, clearButton, defaultButton, deleteButton, undoButton
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
        
        Label countLabel = new Label("平台數: 0");
        countLabel.setStyle("-fx-text-fill: white;");
        
        // 更新平台數量
        countLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
            () -> "平台數: " + mapConfig.getPlatforms().size(),
            javafx.collections.FXCollections.observableArrayList()
        ));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusBar.getChildren().addAll(statusLabel, coordLabel, spacer, countLabel);
        
        return statusBar;
    }
    
    private void handleMousePressed(MouseEvent e) {
        double worldX = e.getX() / SCALE;
        double worldY = e.getY() / SCALE;
        
        if (e.getButton() == MouseButton.PRIMARY) {
            // 左鍵：先嘗試選中平台
            boolean platformClicked = selectPlatform(worldX, worldY);
            
            if (platformClicked && selectedPlatform != null) {
                // 點擊到平台，準備拖曳移動
                isDraggingPlatform = true;
                platformDragOffsetX = worldX - selectedPlatform.x;
                platformDragOffsetY = worldY - selectedPlatform.y;
                statusLabel.setText("拖曳移動平台...");
            } else {
                // 沒有點擊到平台，開始拖曳創建新平台
                isDragging = true;
                dragStartX = worldX;
                dragStartY = worldY;
                dragEndX = worldX;
                dragEndY = worldY;
                selectedPlatform = null;
                selectedIndex = -1;
                statusLabel.setText("拖曳創建平台...");
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            // 右鍵：選擇平台
            selectPlatform(worldX, worldY);
        }
        
        redraw();
    }
    
    private void handleMouseDragged(MouseEvent e) {
        double worldX = e.getX() / SCALE;
        double worldY = e.getY() / SCALE;
        
        if (isDragging) {
            // 拖曳創建新平台
            dragEndX = worldX;
            dragEndY = worldY;
            redraw();
        } else if (isDraggingPlatform && selectedPlatform != null) {
            // 拖曳移動現有平台
            selectedPlatform.x = worldX - platformDragOffsetX;
            selectedPlatform.y = worldY - platformDragOffsetY;
            redraw();
        }
    }
    
    private void handleMouseReleased(MouseEvent e) {
        if (isDragging && e.getButton() == MouseButton.PRIMARY) {
            double worldX = e.getX() / SCALE;
            double worldY = e.getY() / SCALE;
            
            // 圖片模式：固定圖片原始尺寸
            if (lockImageSize && currentImagePath != null) {
                double x = Math.min(dragStartX, worldX);
                double y = Math.min(dragStartY, worldY);
                int w = (int)Math.round(lockedImageWidth);
                int h = (int)Math.round(lockedImageHeight);
                createPlatform(x, y, w, h);
                statusLabel.setText("✓ 已以圖片尺寸創建平台");
                isDragging = false;
                redraw();
                return;
            }

            double x = Math.min(dragStartX, worldX);
            double y = Math.min(dragStartY, worldY);
            double width = Math.abs(worldX - dragStartX);
            double height = Math.abs(worldY - dragStartY);
            
            // 最小尺寸限制
            if (width >= 30 && height >= 10) {
                createPlatform(x, y, (int)width, (int)height);
                statusLabel.setText("✓ 已創建平台");
            } else {
                statusLabel.setText("平台太小，請重新拖曳");
            }
            
            isDragging = false;
            redraw();
        } else if (isDraggingPlatform && e.getButton() == MouseButton.PRIMARY) {
            // 平台移動完成，保存到歷史
            saveToHistory();
            isDraggingPlatform = false;
            statusLabel.setText("✓ 平台已移動");
        }
    }
    
    private void handleMouseMoved(MouseEvent e) {
        double worldX = e.getX() / SCALE;
        double worldY = e.getY() / SCALE;
        coordLabel.setText("座標: (%.0f, %.0f)".formatted(worldX, worldY));
    }
    
    private void createPlatform(double x, double y, int width, int height) {
        String typeStr = typeCombo.getValue().split(" - ")[0];
        String color = getColorForType(typeStr);
        double rotation = rotationSpinner.getValue();
        
        int finalW = width;
        int finalH = height;
        if (lockImageSize && currentImagePath != null) {
            finalW = (int)Math.round(lockedImageWidth);
            finalH = (int)Math.round(lockedImageHeight);
        }
        
        MapPlatform platform = new MapPlatform(x, y, finalW, finalH, color, rotation, typeStr, currentImagePath);
        mapConfig.addPlatform(platform);
        
        widthSpinner.getValueFactory().setValue(finalW);
        heightSpinner.getValueFactory().setValue(finalH);
        
        saveToHistory();
        
        System.out.println("已創建平台: " + platform);
    }
    
    private boolean selectPlatform(double worldX, double worldY) {
        List<MapPlatform> platforms = mapConfig.getPlatforms();
        
        for (int i = platforms.size() - 1; i >= 0; i--) {
            MapPlatform p = platforms.get(i);
            if (worldX >= p.x && worldX <= p.x + p.width &&
                worldY >= p.y && worldY <= p.y + p.height) {
                selectedPlatform = p;
                selectedIndex = i;
                statusLabel.setText("已選中平台 #" + i);
                
                // 更新 UI 顯示選中平台的屬性
                typeCombo.setValue(p.type + " - " + getTypeDescription(p.type));
                widthSpinner.getValueFactory().setValue(p.width);
                heightSpinner.getValueFactory().setValue(p.height);
                rotationSpinner.getValueFactory().setValue(p.rotation);
                
                redraw();
                return true;
            }
        }
        
        // 未選中任何平台
        selectedPlatform = null;
        selectedIndex = -1;
        statusLabel.setText("未選中任何平台");
        redraw();
        return false;
    }
    
    private void deleteSelected() {
        if (selectedIndex >= 0) {
            mapConfig.removePlatform(selectedIndex);
            saveToHistory();
            statusLabel.setText("✓ 已刪除平台 #" + selectedIndex);
            selectedPlatform = null;
            selectedIndex = -1;
            redraw();
        } else {
            statusLabel.setText("請先選擇要刪除的平台");
        }
    }
    
    private void updateMode() {
        String selected = typeCombo.getValue();
        currentMode = selected.split(" - ")[0];
        statusLabel.setText("當前模式: " + currentMode);
    }
    
    private String getColorForType(String type) {
        return switch (type) {
            case "DEATH" -> "#FF0000";
            case "BOUNCE" -> "#00FF00";
            case "MOVING_H" -> "#00AAFF";
            case "MOVING_V" -> "#AA00FF";
            default -> "#8B4513";
        };
    }
    
    private String getTypeDescription(String type) {
        return switch (type) {
            case "DEATH" -> "死亡平台";
            case "BOUNCE" -> "彈跳平台";
            case "MOVING_H" -> "水平移動";
            case "MOVING_V" -> "垂直移動";
            default -> "普通平台";
        };
    }
    
    private void redraw() {
        // 清空畫布
        gc.setFill(Color.rgb(50, 50, 50));
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        
        // 繪製網格
        gc.setStroke(Color.rgb(70, 70, 70));
        gc.setLineWidth(1);
        for (int i = 0; i < CANVAS_WIDTH; i += 50) {
            gc.strokeLine(i, 0, i, CANVAS_HEIGHT);
        }
        for (int i = 0; i < CANVAS_HEIGHT; i += 50) {
            gc.strokeLine(0, i, CANVAS_WIDTH, i);
        }
        
        // 繪製可達範圍框(整個地圖範圍)
        gc.setStroke(Color.CYAN);
        gc.setLineWidth(3);
        gc.setLineDashes(10, 5);
        gc.strokeRect(0, 0, MAP_WIDTH * SCALE, MAP_HEIGHT * SCALE);
        gc.setLineDashes(0);
        
        // 繪製起點參考線 (X=50)
        gc.setStroke(Color.LIGHTGREEN);
        gc.setLineWidth(4);
        double startX = 50 * SCALE;
        gc.strokeLine(startX, 0, startX, CANVAS_HEIGHT);
        
        // 起點標籤和區域
        gc.setFill(Color.LIGHTGREEN);
        gc.fillText("起點 (X=50)", startX + 5, 20);
        gc.setFill(Color.rgb(144, 238, 144, 0.2));
        gc.fillRect(0, 0, startX, CANVAS_HEIGHT);
        
        // 繪製終點參考線 (X=4000)
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(4);
        double endX = 4000 * SCALE;
        gc.strokeLine(endX, 0, endX, CANVAS_HEIGHT);
        
        // 終點標籤和區域
        gc.setFill(Color.GOLD);
        gc.fillText("終點 (X=4000)", endX - 100, 20);
        gc.setFill(Color.rgb(255, 215, 0, 0.2));
        gc.fillRect(endX, 0, (MAP_WIDTH - 4000) * SCALE, CANVAS_HEIGHT);
        
        // 繪製地圖範圍標註
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 14));
        gc.fillText("可達範圍: 0 - 4800 (寬) × 0 - 700 (高)", 10, CANVAS_HEIGHT - 10);
        
        // 繪製所有平台
        List<MapPlatform> platforms = mapConfig.getPlatforms();
        for (int i = 0; i < platforms.size(); i++) {
            MapPlatform p = platforms.get(i);
            drawPlatform(p, i == selectedIndex);
        }
        
        // 繪製正在拖曳的矩形
        if (isDragging) {
            double baseX = Math.min(dragStartX, dragEndX);
            double baseY = Math.min(dragStartY, dragEndY);
            double w = lockImageSize ? lockedImageWidth : Math.abs(dragEndX - dragStartX);
            double h = lockImageSize ? lockedImageHeight : Math.abs(dragEndY - dragStartY);
            double x = baseX * SCALE;
            double y = baseY * SCALE;
            double wPx = w * SCALE;
            double hPx = h * SCALE;
            
            gc.setFill(Color.web(getColorForType(currentMode), 0.3));
            gc.fillRect(x, y, wPx, hPx);
            
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(2);
            gc.strokeRect(x, y, wPx, hPx);
            
            // 顯示尺寸
            gc.setFill(Color.WHITE);
            gc.fillText("%.0fx%.0f".formatted(w, h), x + 5, y + 15);
        }
    }
    
    private void drawPlatform(MapPlatform p, boolean selected) {
        double x = p.x * SCALE;
        double y = p.y * SCALE;
        double w = p.width * SCALE;
        double h = p.height * SCALE;
        
        // 保存當前變換
        gc.save();
        
        // 應用旋轉
        if (p.rotation != 0) {
            gc.translate(x + w/2, y + h/2);
            gc.rotate(p.rotation);
            gc.translate(-(x + w/2), -(y + h/2));
        }
        
        // 繪製平台 - 優先使用圖片，否則使用顏色
        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            Image img = loadImage(p.imagePath);
            if (img != null) {
                gc.drawImage(img, x, y, w, h);
            } else {
                // 圖片載入失敗，使用顏色
                gc.setFill(Color.web(p.color, 0.8));
                gc.fillRect(x, y, w, h);
            }
        } else {
            // 沒有圖片，使用顏色
            gc.setFill(Color.web(p.color, 0.8));
            gc.fillRect(x, y, w, h);
        }
        
        // 繪製邊框
        if (selected) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(3);
        } else {
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1);
        }
        gc.strokeRect(x, y, w, h);
        
        // 恢復變換
        gc.restore();
        
        // 繪製標籤（不旋轉）
        gc.setFill(Color.WHITE);
        String label = p.type;
        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            label += " 🖼️";
        }
        gc.fillText(label, x + 2, y + 12);
    }
    
    private void saveMap() {
        try {
            mapConfig.save();
            statusLabel.setText("✓ 地圖已儲存到 map_config.dat");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("儲存成功");
            alert.setHeaderText(null);
            alert.setContentText("地圖已成功儲存！\n共 " + mapConfig.getPlatforms().size() + " 個平台");
            alert.showAndWait();
        } catch (Exception e) {
            statusLabel.setText("✗ 儲存失敗: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("儲存失敗");
            alert.setHeaderText(null);
            alert.setContentText("儲存地圖時發生錯誤:\n" + e.getMessage());
            alert.showAndWait();
        }
    }
    
    private void loadMap() {
        try {
            mapConfig.load();
            selectedPlatform = null;
            selectedIndex = -1;
            redraw();
            statusLabel.setText("✓ 已載入地圖，共 " + mapConfig.getPlatforms().size() + " 個平台");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("載入成功");
            alert.setHeaderText(null);
            alert.setContentText("地圖已成功載入！\n共 " + mapConfig.getPlatforms().size() + " 個平台");
            alert.showAndWait();
        } catch (Exception e) {
            statusLabel.setText("✗ 載入失敗: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("載入失敗");
            alert.setHeaderText(null);
            alert.setContentText("載入地圖時發生錯誤:\n" + e.getMessage());
            alert.showAndWait();
        }
    }
    
    private void clearMap() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("確認清空");
        confirm.setHeaderText("確定要清空地圖嗎?");
        confirm.setContentText("這將刪除所有平台，無法復原！");
        
        if (confirm.showAndWait().get() == ButtonType.OK) {
            mapConfig.clearPlatforms();
            selectedPlatform = null;
            selectedIndex = -1;
            redraw();
            statusLabel.setText("✓ 地圖已清空");
        }
    }
    
    private void createDefaultMap() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("確認創建");
        confirm.setHeaderText("確定要創建預設地圖嗎?");
        confirm.setContentText("這將覆蓋現有配置！");
        
        if (confirm.showAndWait().get() == ButtonType.OK) {
            mapConfig.createDefaultMap();
            selectedPlatform = null;
            selectedIndex = -1;
            saveToHistory();
            redraw();
            statusLabel.setText("✓ 已創建預設地圖");
        }
    }
    
    // 歷史記錄管理
    private void saveToHistory() {
        // 創建當前狀態的深拷貝
        List<MapPlatform> snapshot = new ArrayList<>();
        for (MapPlatform p : mapConfig.getPlatforms()) {
            snapshot.add(new MapPlatform(p.x, p.y, p.width, p.height, p.color, p.rotation, p.type, p.imagePath));
        }
        
        // 如果不在歷史末尾，清除後續歷史
        while (historyIndex < history.size() - 1) {
            history.remove(history.size() - 1);
        }
        
        // 添加新狀態
        history.add(snapshot);
        historyIndex++;
        
        // 限制歷史記錄數量
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
            historyIndex--;
        }
    }
    
    private void undo() {
        if (historyIndex > 0) {
            historyIndex--;
            List<MapPlatform> previousState = history.get(historyIndex);
            
            // 恢復到之前的狀態
            mapConfig.clearPlatforms();
            for (MapPlatform p : previousState) {
                mapConfig.addPlatform(new MapPlatform(p.x, p.y, p.width, p.height, p.color, p.rotation, p.type, p.imagePath));
            }
            
            selectedPlatform = null;
            selectedIndex = -1;
            redraw();
            statusLabel.setText("↶ 已撤銷，回到步驟 " + historyIndex);
        } else {
            statusLabel.setText("✓ 已創建預設地圖");
        }
    }
    
    /**
     * 瀏覽並選擇圖片檔案
     */
    private void browseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("選擇平台圖片");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("圖片檔案", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("PNG 檔案", "*.png"),
            new FileChooser.ExtensionFilter("JPEG 檔案", "*.jpg", "*.jpeg"),
            new FileChooser.ExtensionFilter("所有檔案", "*.*")
        );
        
        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file != null && file.exists()) {
            currentImagePath = file.getAbsolutePath();
            Image img = loadImage(currentImagePath);
            if (img != null) {
                lockedImageWidth = img.getWidth();
                lockedImageHeight = img.getHeight();
                lockImageSize = true;
                imagePathField.setText(file.getName());
                widthSpinner.setDisable(true);
                heightSpinner.setDisable(true);
                widthSpinner.getValueFactory().setValue((int)Math.round(lockedImageWidth));
                heightSpinner.getValueFactory().setValue((int)Math.round(lockedImageHeight));
                statusLabel.setText("✓ 已選擇圖片並鎖定尺寸: " + file.getName());
            } else {
                statusLabel.setText("✗ 載入圖片失敗");
            }
        }
    }
    
    /**
     * 載入圖片並快取
     */
    private Image loadImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return null;
        }
        
        // 檢查快取
        if (imageCache.containsKey(imagePath)) {
            return imageCache.get(imagePath);
        }
        
        // 載入圖片
        try {
            File file = new File(imagePath);
            if (file.exists()) {
                Image image = new Image(new FileInputStream(file));
                imageCache.put(imagePath, image);
                return image;
            } else {
                System.err.println("圖片檔案不存在: " + imagePath);
                return null;
            }
        } catch (Exception e) {
            System.err.println("載入圖片失敗: " + imagePath + " - " + e.getMessage());
            return null;
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
