import java.io.*;
import java.util.*;

/**
 * 地圖配置類 - 用於存儲和加載預設平台
 */
class MapPlatform implements Serializable {
    private static final long serialVersionUID = 1L;
    double x, y;
    int width, height;
    String color;
    double rotation;
    String type;  // "NORMAL", "DEATH", "BOUNCE", etc.
    
    public MapPlatform(double x, double y, int width, int height, String color, double rotation, String type) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
        this.rotation = rotation;
        this.type = type;
    }
    
    @Override
    public String toString() {
        return String.format("Platform[type=%s, pos=(%.0f,%.0f), size=%dx%d, rot=%.0f°]", 
                           type, x, y, width, height, rotation);
    }
}

public class MapConfig {
    private static final String MAP_FILE = "map_config.dat";
    private List<MapPlatform> platforms;
    
    public MapConfig() {
        this.platforms = new ArrayList<>();
    }
    
    public void addPlatform(MapPlatform platform) {
        platforms.add(platform);
    }
    
    public void removePlatform(int index) {
        if (index >= 0 && index < platforms.size()) {
            platforms.remove(index);
        }
    }
    
    public List<MapPlatform> getPlatforms() {
        return new ArrayList<>(platforms);
    }
    
    public void clearPlatforms() {
        platforms.clear();
    }
    
    public void save() throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(MAP_FILE))) {
            out.writeObject(platforms);
            System.out.println("[MAP] Saved " + platforms.size() + " platforms to " + MAP_FILE);
        }
    }
    
    @SuppressWarnings("unchecked")
    public void load() throws IOException, ClassNotFoundException {
        File file = new File(MAP_FILE);
        if (!file.exists()) {
            System.out.println("[MAP] No map file found, starting with empty map");
            return;
        }
        
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            platforms = (List<MapPlatform>) in.readObject();
            System.out.println("[MAP] Loaded " + platforms.size() + " platforms from " + MAP_FILE);
        }
    }
    
    public void printPlatforms() {
        System.out.println("\n=== Current Map Configuration ===");
        if (platforms.isEmpty()) {
            System.out.println("No platforms configured.");
        } else {
            for (int i = 0; i < platforms.size(); i++) {
                System.out.println(i + ": " + platforms.get(i));
            }
        }
        System.out.println("=================================\n");
    }
    
    // 創建預設地圖
    public void createDefaultMap() {
        clearPlatforms();
        
        // 左側起點附近的平台
        addPlatform(new MapPlatform(300, 450, 150, 20, "#8B4513", 0, "NORMAL"));
        addPlatform(new MapPlatform(550, 400, 120, 20, "#8B4513", 0, "NORMAL"));
        
        // 中間區域平台
        addPlatform(new MapPlatform(1000, 350, 200, 25, "#8B4513", 0, "NORMAL"));
        addPlatform(new MapPlatform(1400, 300, 150, 20, "#8B4513", 0, "NORMAL"));
        addPlatform(new MapPlatform(1800, 250, 180, 25, "#8B4513", 0, "NORMAL"));
        
        // 添加一些障礙
        addPlatform(new MapPlatform(1200, 400, 100, 20, "#FF0000", 0, "DEATH"));
        addPlatform(new MapPlatform(2200, 350, 120, 30, "#00FF00", 0, "BOUNCE"));
        
        // 右側區域平台
        addPlatform(new MapPlatform(2800, 300, 150, 20, "#8B4513", 0, "NORMAL"));
        addPlatform(new MapPlatform(3200, 350, 180, 25, "#8B4513", 0, "NORMAL"));
        addPlatform(new MapPlatform(3700, 300, 200, 20, "#8B4513", 0, "NORMAL"));
        
        // 終點前的挑戰
        addPlatform(new MapPlatform(4200, 400, 150, 20, "#8B4513", 0, "NORMAL"));
        
        System.out.println("[MAP] Created default map with " + platforms.size() + " platforms");
    }
}
