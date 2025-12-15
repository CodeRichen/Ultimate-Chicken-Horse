import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

/**
 * 瓷磚地圖配置 - 管理瓷磚地圖數據的存儲和加載
 */
public class TilemapConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    private final int width;
    private final int height;
    private int[][] tileMap;
    private static final String TILEMAP_FILE = "tilemap_config.dat";
    
    public TilemapConfig(int width, int height) {
        this.width = width;
        this.height = height;
        this.tileMap = new int[height][width];
        
        // 初始化為空 (-1)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tileMap[y][x] = -1;
            }
        }
    }
    
    /**
     * 設置指定位置的瓷磚索引
     */
    public void setTile(int x, int y, int tileIndex) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            tileMap[y][x] = tileIndex;
        }
    }
    
    /**
     * 獲取指定位置的瓷磚索引
     */
    public int getTile(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return tileMap[y][x];
        }
        return -1;
    }
    
    /**
     * 清空所有瓷磚
     */
    public void clear() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tileMap[y][x] = -1;
            }
        }
    }
    
    /**
     * 獲取瓷磚地圖的副本
     */
    public int[][] getTileMapCopy() {
        int[][] copy = new int[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(tileMap[y], 0, copy[y], 0, width);
        }
        return copy;
    }
    
    /**
     * 設置整個瓷磚地圖
     */
    public void setTileMap(int[][] newTileMap) {
        if (newTileMap.length == height && newTileMap[0].length == width) {
            for (int y = 0; y < height; y++) {
                System.arraycopy(newTileMap[y], 0, tileMap[y], 0, width);
            }
        }
    }
    
    /**
     * 儲存瓷磚地圖到文件
     */
    public void save() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(TILEMAP_FILE))) {
            oos.writeInt(width);
            oos.writeInt(height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    oos.writeInt(tileMap[y][x]);
                }
            }
            System.out.println("瓷磚地圖已儲存到: " + TILEMAP_FILE);
        }
    }
    
    /**
     * 從文件加載瓷磚地圖
     */
    public void load() throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(TILEMAP_FILE))) {
            int savedWidth = ois.readInt();
            int savedHeight = ois.readInt();
            
            if (savedWidth != width || savedHeight != height) {
                throw new IOException("地圖尺寸不匹配: " + savedWidth + "x" + savedHeight + 
                                    " (預期: " + width + "x" + height + ")");
            }
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    tileMap[y][x] = ois.readInt();
                }
            }
            System.out.println("瓷磚地圖已從 " + TILEMAP_FILE + " 加載");
        }
    }
    
    /**
     * 獲取地圖寬度
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * 獲取地圖高度
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * 獲取統計信息
     */
    public String getStats() {
        int filledTiles = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tileMap[y][x] >= 0) {
                    filledTiles++;
                }
            }
        }
        return "瓷磚地圖: " + width + "x" + height + " (" + filledTiles + "/" + (width * height) + " 已填充)";
    }
}
