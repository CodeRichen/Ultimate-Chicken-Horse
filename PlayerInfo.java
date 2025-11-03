import java.io.Serializable;

public class PlayerInfo implements Serializable {
    public String name;
    public double x, y;
    public boolean crouching;

    public PlayerInfo(String name, double x, double y, boolean crouching) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.crouching = crouching;
    }
}
