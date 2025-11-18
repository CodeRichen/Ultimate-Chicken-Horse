import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 檔案名稱：SharedMessages.java
 */

// 遊戲階段枚舉
enum GamePhase {
    SELECTING,  // 選擇平台
    PLACING,    // 放置平台
    PLAYING     // 競賽階段
}
enum ObjectType {
    NORMAL,      // 普通平台
    DEATH,       // 死亡平台（紅色）
    ERASER,      // 橡皮擦
    MOVING_H,    // 水平移動平台
    MOVING_V,    // 垂直移動平台
    BOUNCE,      // 彈跳平台
    TURRET,      // 砲塔
    ROTATING     // 旋轉平台
}

// 初始化訊息
class InitMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    String colorHex;
    
    public InitMessage(String playerId, String colorHex) {
        this.playerId = playerId;
        this.colorHex = colorHex;
    }
}

// 階段變更訊息
class PhaseChangeMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    GamePhase phase;
    
    public PhaseChangeMessage(GamePhase phase) {
        this.phase = phase;
    }
}

// 隨機平台訊息
class RandomPlatformsMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    List<PlatformPlacement> randomPlatforms;
    
    public RandomPlatformsMessage(List<PlatformPlacement> randomPlatforms) {
        this.randomPlatforms = randomPlatforms;
    }
}

// 物件資訊
class GameObjectInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    int id;
    int width;
    int height;
    String color;
    ObjectType type;
    boolean selected;  // 是否已被選擇
    
    // 特殊屬性
    double moveSpeed;   // 移動速度
    double moveRange;   // 移動範圍
    double fireRate;    // 砲塔發射速率
    
    // 舊版建構子（向下相容）
    public GameObjectInfo(int id, int width, int height, String color) {
        this(id, width, height, color, ObjectType.NORMAL, 0, 0, 0);
    }
    
    // 完整建構子
    public GameObjectInfo(int id, int width, int height, String color, 
                         ObjectType type, double moveSpeed, double moveRange, 
                         double fireRate) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.color = color;
        this.type = type;
        this.selected = false;
        this.moveSpeed = moveSpeed;
        this.moveRange = moveRange;
        this.fireRate = fireRate;
    }
}
// 物件列表訊息
class ObjectListMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    List<GameObjectInfo> objects;
    
    public ObjectListMessage(List<GameObjectInfo> objects) {
        this.objects = objects;
    }
}

// 選擇訊息
class SelectionMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    int objectId;
    
    public SelectionMessage(String playerId, int objectId) {
        this.playerId = playerId;
        this.objectId = objectId;
    }
}

// 平台放置資訊（包含旋轉角度）
class PlatformPlacement implements Serializable {
    private static final long serialVersionUID = 1L;
    int id;
    double x;
    double y;
    int width;
    int height;
    String color;
    double rotation;  // 旋轉角度
    
    // 相容舊版本的建構子（沒有 rotation）
    public PlatformPlacement(int id, double x, double y, int width, int height, String color) {
        this(id, x, y, width, height, color, 0.0);
    }
    
    // 完整建構子（包含 rotation）
    public PlatformPlacement(int id, double x, double y, int width, int height, String color, double rotation) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
        this.rotation = rotation;
    }
}

// 放置訊息（包含是否確認）
class PlacementMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    PlatformPlacement placement;
    boolean confirmed;  // 是否確認放置（true）或只是預覽（false）
    
    public PlacementMessage(String playerId, PlatformPlacement placement, boolean confirmed) {
        this.playerId = playerId;
        this.placement = placement;
        this.confirmed = confirmed;
    }
}

// 玩家資訊
class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    String colorHex;
    double x;
    double y;
    boolean crouching;
    double scaleY;
    
    public PlayerInfo(String playerId, String colorHex, double x, double y, boolean crouching, double scaleY) {
        this.playerId = playerId;
        this.colorHex = colorHex;
        this.x = x;
        this.y = y;
        this.crouching = crouching;
        this.scaleY = scaleY;
    }
}

// 斷線訊息
class DisconnectMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    
    public DisconnectMessage(String playerId) {
        this.playerId = playerId;
    }
}

// 完成訊息
class FinishMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    long finishTime;  // 完成時間（毫秒）
    
    public FinishMessage(String playerId, long finishTime) {
        this.playerId = playerId;
        this.finishTime = finishTime;
    }
}

// 失敗訊息（掉出地圖）
class FailMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    
    public FailMessage(String playerId) {
        this.playerId = playerId;
    }
}

// 分數更新訊息
class ScoreUpdateMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    Map<String, Integer> scores;
    
    public ScoreUpdateMessage(Map<String, Integer> scores) {
        this.scores = scores;
    }
}

// 回合結束訊息（顯示排行榜）
class RoundEndMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    Map<String, Integer> roundScores;  // 本回合分數
    Map<String, Integer> totalScores;  // 總分
    List<String> finishOrder;  // 完成順序
    int currentRound;     
    int totalRounds; 
   public RoundEndMessage(Map<String, Integer> roundScores,
                       Map<String, Integer> totalScores,
                       List<String> finishOrder,
                       int currentRound,
                       int totalRounds) {
    this.roundScores = roundScores;
    this.totalScores = totalScores;
    this.finishOrder = finishOrder;
    this.currentRound = currentRound;
    this.totalRounds = totalRounds;
}

}