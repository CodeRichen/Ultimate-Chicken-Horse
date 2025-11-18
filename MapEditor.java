import java.util.*;
import java.io.*;

/**
 * 地圖編輯器 - 命令行工具
 * 用於創建和編輯遊戲地圖配置
 */
public class MapEditor {
    private MapConfig mapConfig;
    private Scanner scanner;
    
    public MapEditor() {
        this.mapConfig = new MapConfig();
        this.scanner = new Scanner(System.in);
    }
    
    public void run() {
        System.out.println("=================================");
        System.out.println("   地圖編輯器 - Map Editor");
        System.out.println("=================================\n");
        
        // 嘗試加載現有地圖
        try {
            mapConfig.load();
            System.out.println("已加載現有地圖配置\n");
        } catch (Exception e) {
            System.out.println("沒有找到現有地圖，從空白開始\n");
        }
        
        boolean running = true;
        while (running) {
            showMenu();
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1" -> addPlatform();
                case "2" -> removePlatform();
                case "3" -> listPlatforms();
                case "4" -> clearMap();
                case "5" -> createDefault();
                case "6" -> saveMap();
                case "7" -> loadMap();
                case "8" -> {
                    System.out.println("退出編輯器");
                    running = false;
                }
                default -> System.out.println("無效的選擇，請重試\n");
            }
        }
        
        scanner.close();
    }
    
    private void showMenu() {
        System.out.println("\n===== 主選單 =====");
        System.out.println("1. 新增平台");
        System.out.println("2. 刪除平台");
        System.out.println("3. 列出所有平台");
        System.out.println("4. 清空地圖");
        System.out.println("5. 創建預設地圖");
        System.out.println("6. 儲存地圖");
        System.out.println("7. 載入地圖");
        System.out.println("8. 退出");
        System.out.print("\n請選擇 (1-8): ");
    }
    
    private void addPlatform() {
        System.out.println("\n=== 新增平台 ===");
        
        try {
            System.out.print("X 座標 (0-4800): ");
            double x = Double.parseDouble(scanner.nextLine().trim());
            
            System.out.print("Y 座標 (0-600): ");
            double y = Double.parseDouble(scanner.nextLine().trim());
            
            System.out.print("寬度 (50-300): ");
            int width = Integer.parseInt(scanner.nextLine().trim());
            
            System.out.print("高度 (15-50): ");
            int height = Integer.parseInt(scanner.nextLine().trim());
            
            System.out.println("\n平台類型:");
            System.out.println("1. NORMAL (普通平台)");
            System.out.println("2. DEATH (死亡平台)");
            System.out.println("3. BOUNCE (彈跳平台)");
            System.out.println("4. MOVING_H (水平移動)");
            System.out.println("5. MOVING_V (垂直移動)");
            System.out.print("選擇類型 (1-5): ");
            
            String typeChoice = scanner.nextLine().trim();
            String type;
            String color;
            
            switch (typeChoice) {
                case "1" -> {
                    type = "NORMAL";
                    color = "#8B4513";
                }
                case "2" -> {
                    type = "DEATH";
                    color = "#FF0000";
                }
                case "3" -> {
                    type = "BOUNCE";
                    color = "#00FF00";
                }
                case "4" -> {
                    type = "MOVING_H";
                    color = "#00AAFF";
                }
                case "5" -> {
                    type = "MOVING_V";
                    color = "#AA00FF";
                }
                default -> {
                    type = "NORMAL";
                    color = "#8B4513";
                }
            }
            
            System.out.print("旋轉角度 (0, 90, 180, 270): ");
            double rotation = Double.parseDouble(scanner.nextLine().trim());
            
            MapPlatform platform = new MapPlatform(x, y, width, height, color, rotation, type);
            mapConfig.addPlatform(platform);
            
            System.out.println("\n✓ 已新增平台: " + platform);
            
        } catch (NumberFormatException e) {
            System.out.println("✗ 輸入格式錯誤，請輸入有效的數字");
        }
    }
    
    private void removePlatform() {
        mapConfig.printPlatforms();
        
        if (mapConfig.getPlatforms().isEmpty()) {
            return;
        }
        
        System.out.print("請輸入要刪除的平台編號: ");
        try {
            int index = Integer.parseInt(scanner.nextLine().trim());
            mapConfig.removePlatform(index);
            System.out.println("✓ 已刪除平台 " + index);
        } catch (NumberFormatException e) {
            System.out.println("✗ 無效的編號");
        }
    }
    
    private void listPlatforms() {
        mapConfig.printPlatforms();
    }
    
    private void clearMap() {
        System.out.print("確定要清空地圖嗎? (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        
        if (confirm.equals("y") || confirm.equals("yes")) {
            mapConfig.clearPlatforms();
            System.out.println("✓ 地圖已清空");
        } else {
            System.out.println("取消清空");
        }
    }
    
    private void createDefault() {
        System.out.print("確定要創建預設地圖嗎? 這會覆蓋現有配置 (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        
        if (confirm.equals("y") || confirm.equals("yes")) {
            mapConfig.createDefaultMap();
            System.out.println("✓ 已創建預設地圖");
        } else {
            System.out.println("取消創建");
        }
    }
    
    private void saveMap() {
        try {
            mapConfig.save();
            System.out.println("✓ 地圖已儲存");
        } catch (IOException e) {
            System.out.println("✗ 儲存失敗: " + e.getMessage());
        }
    }
    
    private void loadMap() {
        try {
            mapConfig.load();
            System.out.println("✓ 地圖已載入");
            mapConfig.printPlatforms();
        } catch (Exception e) {
            System.out.println("✗ 載入失敗: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        MapEditor editor = new MapEditor();
        editor.run();
    }
}
