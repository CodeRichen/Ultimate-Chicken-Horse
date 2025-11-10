import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.input.UserAction;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.geometry.Point2D;
import java.util.ArrayList;
import java.util.List;

public class GameClient extends GameApplication {

    private Entity player;
    private List<Entity> platformEntities = new ArrayList<>();
    private Entity draggedPlatform = null;
    private Point2D dragOffset = Point2D.ZERO;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("FXGL Platformer - Draggable Platforms");
    }

    @Override
    protected void initGame() {
        // 地板
        Entity floor = createPlatform(0, 550, 800, 50, Color.DARKGRAY);
        
        // 平台1
        Entity platform1 = createPlatform(200, 450, 100, 20, Color.DARKBLUE);
        
        // 平台2
        Entity platform2 = createPlatform(400, 350, 100, 20, Color.DARKBLUE);
        
        // 平台3（額外平台）
        Entity platform3 = createPlatform(100, 250, 120, 20, Color.DARKGREEN);

        // 角色
        player = FXGL.entityBuilder()
                .at(100, 500)
                .view(new Circle(20, Color.RED))
                .with(new PlayerControl(platformEntities))
                .buildAndAttach();
    }

    private Entity createPlatform(double x, double y, double width, double height, Color color) {
        Rectangle rect = new Rectangle(width, height, color);
        Entity platform = FXGL.entityBuilder()
                .at(x, y)
                .view(rect)
                .with(new PlatformComponent(width, height))
                .buildAndAttach();
        
        platformEntities.add(platform);
        return platform;
    }

    @Override
    protected void initInput() {
        // 玩家控制
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

        // 滑鼠拖曳平台
        FXGL.getInput().addAction(new UserAction("Drag Platform") {
            @Override
            protected void onActionBegin() {
                Point2D mousePos = FXGL.getInput().getMousePositionWorld();
                
                // 檢查是否點擊到平台
                for (Entity platform : platformEntities) {
                    PlatformComponent pc = platform.getComponent(PlatformComponent.class);
                    if (pc.containsPoint(mousePos.getX(), mousePos.getY())) {
                        draggedPlatform = platform;
                        dragOffset = new Point2D(
                            mousePos.getX() - platform.getX(),
                            mousePos.getY() - platform.getY()
                        );
                        break;
                    }
                }
            }

            @Override
            protected void onAction() {
                if (draggedPlatform != null) {
                    Point2D mousePos = FXGL.getInput().getMousePositionWorld();
                    draggedPlatform.setPosition(
                        mousePos.getX() - dragOffset.getX(),
                        mousePos.getY() - dragOffset.getY()
                    );
                }
            }

            @Override
            protected void onActionEnd() {
                draggedPlatform = null;
            }
        }, MouseButton.PRIMARY);

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

// 平台組件
class PlatformComponent extends Component {
    private double width;
    private double height;

    public PlatformComponent(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public boolean containsPoint(double x, double y) {
        double left = entity.getX();
        double right = entity.getX() + width;
        double top = entity.getY();
        double bottom = entity.getY() + height;
        
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    // 檢查與圓形玩家的碰撞
    public CollisionInfo checkCollision(double playerX, double playerY, double radius, double velocityY) {
        double platformLeft = entity.getX();
        double platformRight = entity.getX() + width;
        double platformTop = entity.getY();
        double platformBottom = entity.getY() + height;

        // 玩家邊界
        double playerLeft = playerX - radius;
        double playerRight = playerX + radius;
        double playerTop = playerY - radius;
        double playerBottom = playerY + radius;

        // 檢查是否有重疊
        boolean overlapping = !(playerRight < platformLeft || 
                                playerLeft > platformRight || 
                                playerBottom < platformTop || 
                                playerTop > platformBottom);

        if (!overlapping) {
            return new CollisionInfo(false, CollisionSide.NONE);
        }

        // 計算重疊深度
        double overlapLeft = playerRight - platformLeft;
        double overlapRight = platformRight - playerLeft;
        double overlapTop = playerBottom - platformTop;
        double overlapBottom = platformBottom - playerTop;

        // 找出最小重疊（決定碰撞方向）
        double minOverlap = Math.min(Math.min(overlapLeft, overlapRight), 
                                     Math.min(overlapTop, overlapBottom));

        CollisionSide side;
        if (minOverlap == overlapTop && velocityY > 0) {
            side = CollisionSide.TOP;
        } else if (minOverlap == overlapBottom && velocityY < 0) {
            side = CollisionSide.BOTTOM;
        } else if (minOverlap == overlapLeft) {
            side = CollisionSide.LEFT;
        } else {
            side = CollisionSide.RIGHT;
        }

        return new CollisionInfo(true, side);
    }
}

// 碰撞信息
class CollisionInfo {
    boolean collided;
    CollisionSide side;

    CollisionInfo(boolean collided, CollisionSide side) {
        this.collided = collided;
        this.side = side;
    }
}

enum CollisionSide {
    NONE, TOP, BOTTOM, LEFT, RIGHT
}

// 控制角色移動、跳躍、蹲下
class PlayerControl extends Component {
    private double speed = 5.0;
    private double velocityX = 0;
    private double velocityY = 0;
    private double jumpStrength = 15.0;
    private double gravity = 0.8;
    private boolean onGround = false;
    private List<Entity> platforms;
    private double playerRadius = 20;
    
    // 邊界限制
    private final double LEFT_BOUNDARY = 20;
    private final double RIGHT_BOUNDARY = 780;
    private final double TOP_BOUNDARY = 20;

    public PlayerControl(List<Entity> platforms) {
        this.platforms = platforms;
    }

    @Override
    public void onUpdate(double tpf) {
        // 應用重力
        velocityY += gravity;
        
        // 保存原始位置
        double oldX = entity.getX();
        double oldY = entity.getY();
        
        // 嘗試移動
        entity.translateX(velocityX);
        entity.translateY(velocityY);
        
        // 重置標記
        onGround = false;
        
        // 檢查與所有平台的碰撞
        for (Entity platform : platforms) {
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
            
            if (collision.collided) {
                switch (collision.side) {
                    case TOP:
                        // 從上方碰撞（站在平台上）
                        entity.setY(platform.getY() - playerRadius);
                        velocityY = 0;
                        onGround = true;
                        break;
                        
                    case BOTTOM:
                        // 從下方碰撞（頭撞到平台）
                        entity.setY(platform.getY() + pc.getHeight() + playerRadius);
                        velocityY = 0;
                        break;
                        
                    case LEFT:
                        // 從左側碰撞
                        entity.setX(platform.getX() - playerRadius);
                        velocityX = 0;
                        break;
                        
                    case RIGHT:
                        // 從右側碰撞
                        entity.setX(platform.getX() + pc.getWidth() + playerRadius);
                        velocityX = 0;
                        break;
                }
            }
        }
        
        // 邊界檢查
        if (entity.getX() < LEFT_BOUNDARY) {
            entity.setX(LEFT_BOUNDARY);
        }
        if (entity.getX() > RIGHT_BOUNDARY) {
            entity.setX(RIGHT_BOUNDARY);
        }
        if (entity.getY() < TOP_BOUNDARY) {
            entity.setY(TOP_BOUNDARY);
            velocityY = 0;
        }
        
        // 防止掉出地圖底部
        if (entity.getY() > 600) {
            entity.setY(500);
            velocityY = 0;
            onGround = true;
        }
        
        // 重置水平速度
        velocityX = 0;
    }

    public void moveLeft() {
        velocityX = -speed;
    }

    public void moveRight() {
        velocityX = speed;
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