
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

public class GameClient extends GameApplication {

    private Entity player;

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
        FXGL.entityBuilder().at(0, 550).view(floor).buildAndAttach();

        // 平台
        FXGL.entityBuilder().at(200, 450).view(new Rectangle(100, 20, Color.DARKBLUE)).buildAndAttach();
        FXGL.entityBuilder().at(400, 350).view(new Rectangle(100, 20, Color.DARKBLUE)).buildAndAttach();

        // 角色
        player = FXGL.entityBuilder()
                .at(100, 500)
                .view(new Circle(20, Color.RED))
                .with(new PlayerControl())
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
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// 控制角色移動、跳躍、蹲下
class PlayerControl extends Component {
    private double speed = 5.0;
    private double velocityY = 0; // 垂直速度
    private double jumpStrength = 15.0; // 跳躍初始速度（增加這個值來提高跳躍高度）
    private double gravity = 0.8; // 重力加速度
    private double groundY = 500; // 地板 Y 座標
    private boolean onGround = true;

    @Override
    public void onUpdate(double tpf) {
        // 應用重力
        if (!onGround || velocityY < 0) {
            velocityY += gravity;
            entity.translateY(velocityY);
        }

        // 檢查是否著地
        if (entity.getY() >= groundY) {
            entity.setY(groundY);
            velocityY = 0;
            onGround = true;
        } else {
            onGround = false;
        }
    }

    public void moveLeft() {
        entity.translateX(-speed);
    }

    public void moveRight() {
        entity.translateX(speed);
    }

    public void jump() {
        if (onGround) {
            velocityY = -jumpStrength; // 給予向上的初速度
            onGround = false;
        }
    }

    public void crouch(boolean crouching) {
        entity.getTransformComponent().setScaleY(crouching ? 0.5 : 1.0);
    }
}