import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 所有遊戲訊息類別的定義
 * 注意：刪除 PlayerInfo.java 和 Messages.java，只保留這個檔案
 * 檔案名稱：SharedMessages.java
 */


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

// 物件資訊
class GameObjectInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    int id;
    int width;
    int height;
    String color;
    
    public GameObjectInfo(int id, int width, int height, String color) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.color = color;
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

// 放置訊息
class PlacementMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    PlatformPlacement placement;
    
    public PlacementMessage(String playerId, PlatformPlacement placement) {
        this.playerId = playerId;
        this.placement = placement;
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
    
    public FinishMessage(String playerId) {
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