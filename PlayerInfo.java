import java.io.Serializable;

// 玩家位置訊息
class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    public String playerId;
    public String colorHex;
    public double x;
    public double y;
    public boolean isCrouching;
    public double scaleY;

    public PlayerInfo(String playerId, String colorHex, double x, double y, boolean isCrouching, double scaleY) {
        this.playerId = playerId;
        this.colorHex = colorHex;
        this.x = x;
        this.y = y;
        this.isCrouching = isCrouching;
        this.scaleY = scaleY;
    }
}

// 平台位置訊息
class PlatformInfo implements Serializable {
    private static final long serialVersionUID = 2L;
    public int platformId;  // 平台索引
    public double x;
    public double y;

    public PlatformInfo(int platformId, double x, double y) {
        this.platformId = platformId;
        this.x = x;
        this.y = y;
    }
}

// 初始化訊息
class InitMessage implements Serializable {
    private static final long serialVersionUID = 3L;
    public String playerId;
    public String colorHex;

    public InitMessage(String playerId, String colorHex) {
        this.playerId = playerId;
        this.colorHex = colorHex;
    }
}

// 斷線訊息
class DisconnectMessage implements Serializable {
    private static final long serialVersionUID = 4L;
    public String playerId;

    public DisconnectMessage(String playerId) {
        this.playerId = playerId;
    }
}