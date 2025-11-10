import java.io.Serializable;

public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String playerId;  // 唯一識別ID
    public String colorHex;  // 顏色 (十六進制)
    public double x, y;
    public boolean crouching;
    public double scaleY;

    public PlayerInfo(String playerId, String colorHex, double x, double y, boolean crouching, double scaleY) {
        this.playerId = playerId;
        this.colorHex = colorHex;
        this.x = x;
        this.y = y;
        this.crouching = crouching;
        this.scaleY = scaleY;
    }
    
    @Override
    public String toString() {
        return "Player[" + playerId + " " + colorHex + " at (" + x + "," + y + ")]";
    }
}