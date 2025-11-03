import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.input.UserAction;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class GameClient extends GameApplication {

    private Entity player;
    private List<Platform> platforms = new ArrayList<>();

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("FXGL Platformer Example");
    }

    @Override
    protected void initGame() {
        // 地板
        Rectangle floor = new Rectangle(800, 50, Color.DARKGRAY);
        Entity floorEntity = FXGL.entityBuilder().at(0, 550).view(floor).buildAndAttach();
        platforms.add(new Platform(0, 550, 800, 50));

        // 平台1
        Entity platform1 = FXGL.entityBuilder().at(200, 450).view(new Rectangle(100, 20, Color.DARKBLUE)).buildAndAttach();
        platforms.add(new Platform(200, 450, 100, 20));

        // 平台2
        Entity platform2 = FXGL.entityBuilder().at(400, 350).view(new Rectangle(100, 20, Color.DARKBLUE)).buildAndAttach();
        platforms.add(new Platform(400, 350, 100, 20));

        // 角色
        player = FXGL.entityBuilder()
                .at(100, 500)
                .view(new Circle(20, Color.RED))
                .with(new PlayerControl(platforms))
                .buildAndAttach();
    }

    @Override
    protected void initInput() {
        FXGL.getInput().addAction(new UserAction("Move Left") {
            @Override
            protected void onAction() {
                player.getComponent(PlayerControl.class).moveLeft();
            }
        }, KeyCode.LEFT);

        FXGL.getInput().addAction(new UserAction("Move Right") {
            @Override
            protected void onAction() {
                player.getComponent(PlayerControl.class).moveRight();
            }
        }, KeyCode.RIGHT);

        FXGL.getInput().addAction(new UserAction("Jump") {
            @Override
            protected void onActionBegin() {
                player.getComponent(PlayerControl.class).jump();
            }
        }, KeyCode.UP);

        FXGL.getInput().addAction(new UserAction("Crouch") {
            @Override
            protected void onActionBegin() {
                player.getComponent(PlayerControl.class).crouch(true);
            }

            @Override
            protected void onActionEnd() {
                player.getComponent(PlayerControl.class).crouch(false);
            }
        }, KeyCode.DOWN);

        // 按 Q 退出
        FXGL.getInput().addAction(new UserAction("Quit") {
            @Override
            protected void onActionBegin() {
                FXGL.getGameController().exit();
            }
        }, KeyCode.Q);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// 平台資料結構
class Platform {
    double x, y, width, height;

    Platform(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    boolean isOnPlatform(double playerX, double playerY, double playerRadius) {
        // 檢查玩家的底部是否在平台上方
        double playerBottom = playerY + playerRadius;
        double playerLeft = playerX - playerRadius;
        double playerRight = playerX + playerRadius;

        return playerBottom >= y && playerBottom <= y + 10 &&
               playerRight > x && playerLeft < x + width;
    }
}

// 控制角色移動、跳躍、蹲下
class PlayerControl extends Component {
    private double speed = 5.0;
    private double velocityY = 0;
    private double jumpStrength = 15.0;
    private double gravity = 0.8;
    private boolean onGround = false;
    private List<Platform> platforms;
    private double playerRadius = 20;
    
    // 邊界限制
    private final double LEFT_BOUNDARY = 20;
    private final double RIGHT_BOUNDARY = 780;

    public PlayerControl(List<Platform> platforms) {
        this.platforms = platforms;
    }

    @Override
    public void onUpdate(double tpf) {
        // 應用重力
        velocityY += gravity;
        entity.translateY(velocityY);

        // 檢查是否站在任何平台上
        onGround = false;
        double playerX = entity.getX();
        double playerY = entity.getY();

        for (Platform platform : platforms) {
            if (platform.isOnPlatform(playerX, playerY, playerRadius)) {
                // 如果正在下落且碰到平台
                if (velocityY > 0) {
                    entity.setY(platform.y - playerRadius);
                    velocityY = 0;
                    onGround = true;
                    break;
                }
            }
        }

        // 防止掉出地圖底部（額外保護）
        if (entity.getY() > 600) {
            entity.setY(500);
            velocityY = 0;
            onGround = true;
        }
    }

    public void moveLeft() {
        double newX = entity.getX() - speed;
        if (newX >= LEFT_BOUNDARY) {
            entity.setX(newX);
        }
    }

    public void moveRight() {
        double newX = entity.getX() + speed;
        if (newX <= RIGHT_BOUNDARY) {
            entity.setX(newX);
        }
    }

    public void jump() {
        if (onGround) {
            velocityY = -jumpStrength;
            onGround = false;
        }
    }

    public void crouch(boolean crouching) {
        entity.getTransformComponent().setScaleY(crouching ? 0.5 : 1.0);
    }
}