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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.net.*;

public class GameClient extends GameApplication {

    private Entity player;
    private List<Entity> platformEntities = new ArrayList<>();
    private Entity draggedPlatform = null;
    private Point2D dragOffset = Point2D.ZERO;
    private int draggedPlatformId = -1;
    
    // 網路相關
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myPlayerId;
    private Color myColor = Color.RED;
    private Map<String, Entity> otherPlayers = new HashMap<>();
    private boolean connected = false;
    
    // 平台同步相關
    private long lastPlatformSendTime = 0;
    private static final long PLATFORM_SYNC_INTERVAL = 50; // 50ms

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("FXGL Multiplayer Platformer");
    }

    @Override
    protected void initGame() {
        // 創建平台（順序很重要，用於ID對應）
        createPlatform(0, 550, 800, 50, Color.DARKGRAY);
        createPlatform(200, 450, 100, 20, Color.DARKBLUE);
        createPlatform(400, 350, 100, 20, Color.DARKBLUE);
        createPlatform(100, 250, 120, 20, Color.DARKGREEN);

        // 連接伺服器
        connectToServer();

        // 創建本地玩家（顏色會在連接後更新）
        player = FXGL.entityBuilder()
                .at(100, 500)
                .view(new Circle(20, myColor))
                .with(new PlayerControl(platformEntities))
                .buildAndAttach();

        // 啟動網路接收執行緒
        startNetworkThread();
        
        // 啟動位置發送執行緒
        startPositionSender();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            
            // 接收初始化訊息
            Object initObj = in.readObject();
            if (initObj instanceof InitMessage initMsg) {
                myPlayerId = initMsg.playerId;
                myColor = Color.web(initMsg.colorHex);
                connected = true;
                System.out.println("✅ Connected as " + myPlayerId + " with color " + initMsg.colorHex);
                
                // 更新玩家顏色
                if (player != null) {
                    Circle circle = (Circle) player.getViewComponent().getChildren().get(0);
                    circle.setFill(myColor);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to server: " + e.getMessage());
            connected = false;
        }
    }

    private void startNetworkThread() {
        new Thread(() -> {
            try {
                while (connected) {
                    Object obj = in.readObject();
                    
                    if (obj instanceof PlayerInfo info) {
                        // 更新其他玩家位置
                        javafx.application.Platform.runLater(() -> {
                            updateOtherPlayer(info);
                        });
                    } else if (obj instanceof DisconnectMessage disconnectMsg) {
                        // 移除離線玩家
                        javafx.application.Platform.runLater(() -> {
                            removeOtherPlayer(disconnectMsg.playerId);
                        });
                    } else if (obj instanceof PlatformInfo platformInfo) {
                        // 更新平台位置
                        javafx.application.Platform.runLater(() -> {
                            updatePlatform(platformInfo);
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Network thread error: " + e.getMessage());
                connected = false;
            }
        }).start();
    }

    private void startPositionSender() {
        new Thread(() -> {
            while (connected) {
                try {
                    if (player != null) {
                        PlayerControl pc = player.getComponent(PlayerControl.class);
                        PlayerInfo info = new PlayerInfo(
                            myPlayerId,
                            toHex(myColor),
                            player.getX(),
                            player.getY(),
                            pc.isCrouching(),
                            player.getTransformComponent().getScaleY()
                        );
                        out.writeObject(info);
                        out.flush();
                    }
                    Thread.sleep(50); // 每50ms發送一次
                } catch (Exception e) {
                    connected = false;
                    break;
                }
            }
        }).start();
    }

    private void updateOtherPlayer(PlayerInfo info) {
        Entity otherPlayer = otherPlayers.get(info.playerId);
        
        if (otherPlayer == null) {
            // 創建新玩家（使用平滑移動組件）
            Color playerColor = Color.web(info.colorHex);
            Circle circle = new Circle(20, playerColor);
            SmoothPlayerComponent smoothComponent = new SmoothPlayerComponent();
            otherPlayer = FXGL.entityBuilder()
                    .at(info.x, info.y)
                    .view(circle)
                    .with(smoothComponent)
                    .buildAndAttach();
            otherPlayers.put(info.playerId, otherPlayer);
            System.out.println("➕ New player joined: " + info.playerId);
        } else {
            // 使用平滑移動更新位置
            SmoothPlayerComponent smoothComponent = otherPlayer.getComponent(SmoothPlayerComponent.class);
            smoothComponent.setTargetPosition(info.x, info.y);
            smoothComponent.setTargetScaleY(info.scaleY);
        }
    }

    private void removeOtherPlayer(String playerId) {
        Entity player = otherPlayers.remove(playerId);
        if (player != null) {
            player.removeFromWorld();
            System.out.println("➖ Player left: " + playerId);
        }
    }

    private void updatePlatform(PlatformInfo info) {
        if (info.platformId >= 0 && info.platformId < platformEntities.size()) {
            Entity platform = platformEntities.get(info.platformId);
            // 只有在不是自己拖曳的平台時才更新
            if (platform != draggedPlatform) {
                platform.setPosition(info.x, info.y);
            }
        }
    }

    private void sendPlatformUpdate(int platformId, double x, double y) {
        if (!connected) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPlatformSendTime < PLATFORM_SYNC_INTERVAL) {
            return; // 限制發送頻率
        }
        lastPlatformSendTime = currentTime;
        
        try {
            PlatformInfo info = new PlatformInfo(platformId, x, y);
            out.writeObject(info);
            out.flush();
        } catch (Exception e) {
            System.err.println("Failed to send platform update: " + e.getMessage());
        }
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
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
                
                for (int i = 0; i < platformEntities.size(); i++) {
                    Entity platform = platformEntities.get(i);
                    PlatformComponent pc = platform.getComponent(PlatformComponent.class);
                    if (pc.containsPoint(mousePos.getX(), mousePos.getY())) {
                        draggedPlatform = platform;
                        draggedPlatformId = i;
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
                    double newX = mousePos.getX() - dragOffset.getX();
                    double newY = mousePos.getY() - dragOffset.getY();
                    
                    draggedPlatform.setPosition(newX, newY);
                    
                    // 發送平台位置更新
                    sendPlatformUpdate(draggedPlatformId, newX, newY);
                }
            }

            @Override
            protected void onActionEnd() {
                draggedPlatform = null;
                draggedPlatformId = -1;
            }
        }, MouseButton.PRIMARY);

        // 按 Q 退出
        FXGL.getInput().addAction(new UserAction("Quit") {
            @Override
            protected void onActionBegin() {
                cleanup();
                FXGL.getGameController().exit();
            }
        }, KeyCode.Q);
    }

    private void cleanup() {
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// ========== 平滑移動組件 ==========
class SmoothPlayerComponent extends Component {
    private Point2D targetPosition;
    private double targetScaleY = 1.0;
    private final double SMOOTHING = 0.3; // 平滑係數

    public SmoothPlayerComponent() {
        this.targetPosition = Point2D.ZERO;
    }

    public void setTargetPosition(double x, double y) {
        this.targetPosition = new Point2D(x, y);
    }

    public void setTargetScaleY(double scaleY) {
        this.targetScaleY = scaleY;
    }

    @Override
    public void onUpdate(double tpf) {
        if (targetPosition.equals(Point2D.ZERO)) {
            targetPosition = entity.getPosition();
            return;
        }

        // 使用線性插值平滑移動
        Point2D currentPos = entity.getPosition();
        double newX = currentPos.getX() + (targetPosition.getX() - currentPos.getX()) * SMOOTHING;
        double newY = currentPos.getY() + (targetPosition.getY() - currentPos.getY()) * SMOOTHING;
        
        entity.setPosition(newX, newY);

        // 平滑縮放
        double currentScaleY = entity.getTransformComponent().getScaleY();
        double newScaleY = currentScaleY + (targetScaleY - currentScaleY) * SMOOTHING;
        entity.getTransformComponent().setScaleY(newScaleY);
    }
}

// ========== 平台組件 ==========
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

    public CollisionInfo checkCollision(double playerX, double playerY, double radius, double velocityY) {
        double platformLeft = entity.getX();
        double platformRight = entity.getX() + width;
        double platformTop = entity.getY();
        double platformBottom = entity.getY() + height;

        double playerLeft = playerX - radius;
        double playerRight = playerX + radius;
        double playerTop = playerY - radius;
        double playerBottom = playerY + radius;

        boolean overlapping = !(playerRight < platformLeft || 
                                playerLeft > platformRight || 
                                playerBottom < platformTop || 
                                playerTop > platformBottom);

        if (!overlapping) {
            return new CollisionInfo(false, CollisionSide.NONE);
        }

        double overlapLeft = playerRight - platformLeft;
        double overlapRight = platformRight - playerLeft;
        double overlapTop = playerBottom - platformTop;
        double overlapBottom = platformBottom - playerTop;

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

// ========== 碰撞資訊 ==========
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

// ========== 玩家控制組件 ==========
class PlayerControl extends Component {
    private double speed = 5.0;
    private double velocityX = 0;
    private double velocityY = 0;
    private double jumpStrength = 15.0;
    private double gravity = 0.8;
    private boolean onGround = false;
    private boolean crouching = false;
    private List<Entity> platforms;
    private double playerRadius = 20;
    
    private final double LEFT_BOUNDARY = 20;
    private final double RIGHT_BOUNDARY = 780;
    private final double TOP_BOUNDARY = 20;

    public PlayerControl(List<Entity> platforms) {
        this.platforms = platforms;
    }

    @Override
    public void onUpdate(double tpf) {
        velocityY += gravity;
        
        entity.translateX(velocityX);
        entity.translateY(velocityY);
        
        onGround = false;
        
        for (Entity platform : platforms) {
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
            
            if (collision.collided) {
                switch (collision.side) {
                    case TOP:
                        entity.setY(platform.getY() - playerRadius);
                        velocityY = 0;
                        onGround = true;
                        break;
                    case BOTTOM:
                        entity.setY(platform.getY() + pc.getHeight() + playerRadius);
                        velocityY = 0;
                        break;
                    case LEFT:
                        entity.setX(platform.getX() - playerRadius);
                        velocityX = 0;
                        break;
                    case RIGHT:
                        entity.setX(platform.getX() + pc.getWidth() + playerRadius);
                        velocityX = 0;
                        break;
                }
            }
        }
        
        if (entity.getX() < LEFT_BOUNDARY) entity.setX(LEFT_BOUNDARY);
        if (entity.getX() > RIGHT_BOUNDARY) entity.setX(RIGHT_BOUNDARY);
        if (entity.getY() < TOP_BOUNDARY) {
            entity.setY(TOP_BOUNDARY);
            velocityY = 0;
        }
        
        if (entity.getY() > 600) {
            entity.setY(500);
            velocityY = 0;
            onGround = true;
        }
        
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
        this.crouching = crouching;
        entity.getTransformComponent().setScaleY(crouching ? 0.5 : 1.0);
    }
    
    public boolean isCrouching() {
        return crouching;
    }
}