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
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myPlayerId;
    private Color myColor = Color.RED;
    private Map<String, Entity> otherPlayers = new HashMap<>();
    private volatile boolean connected = false;
    private volatile boolean running = true;
    
    private Map<Integer, SmoothPlatformComponent> platformSmoothComponents = new HashMap<>();
    private Map<Integer, Point2D> myPlatformPositions = new HashMap<>();

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("FXGL Multiplayer Platformer");
    }

    @Override
    protected void initGame() {
        createPlatform(0, 550, 800, 50, Color.DARKGRAY);
        createPlatform(200, 450, 100, 20, Color.DARKBLUE);
        createPlatform(400, 350, 100, 20, Color.DARKBLUE);
        createPlatform(100, 250, 120, 20, Color.DARKGREEN);

        for (int i = 0; i < platformEntities.size(); i++) {
            SmoothPlatformComponent smoothComp = new SmoothPlatformComponent();
            platformEntities.get(i).addComponent(smoothComp);
            platformSmoothComponents.put(i, smoothComp);
            myPlatformPositions.put(i, platformEntities.get(i).getPosition());
        }

        connectToServer();

        player = FXGL.entityBuilder()
                .at(100, 500)
                .view(new Circle(20, myColor))
                .with(new PlayerControl(platformEntities))
                .buildAndAttach();

        startNetworkThread();
        startPositionSender();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 5000);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            
            Object initObj = in.readObject();
            if (initObj instanceof InitMessage initMsg) {
                myPlayerId = initMsg.playerId;
                myColor = Color.web(initMsg.colorHex);
                connected = true;
                System.out.println("Connected as " + myPlayerId + " with color " + initMsg.colorHex);
                
                if (player != null) {
                    Circle circle = (Circle) player.getViewComponent().getChildren().get(0);
                    circle.setFill(myColor);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            connected = false;
        }
    }

    private void startNetworkThread() {
        new Thread(() -> {
            try {
                while (running && connected) {
                    try {
                        Object obj = in.readObject();
                        
                        if (obj instanceof PlayerInfo info) {
                            javafx.application.Platform.runLater(() -> {
                                updateOtherPlayer(info);
                            });
                        } else if (obj instanceof DisconnectMessage disconnectMsg) {
                            javafx.application.Platform.runLater(() -> {
                                removeOtherPlayer(disconnectMsg.playerId);
                            });
                        } else if (obj instanceof PlatformInfo platformInfo) {
                            javafx.application.Platform.runLater(() -> {
                                updatePlatformSmooth(platformInfo);
                            });
                        }
                    } catch (EOFException | SocketException e) {
                        System.err.println("Connection lost");
                        break;
                    } catch (StreamCorruptedException e) {
                        System.err.println("Stream corrupted, reconnection needed");
                        break;
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Network thread error: " + e.getMessage());
                }
            } finally {
                connected = false;
            }
        }).start();
    }

    private void startPositionSender() {
        new Thread(() -> {
            while (running) {
                try {
                    if (connected && player != null) {
                        synchronized (out) {
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
                            out.reset();
                        }
                    }
                    Thread.sleep(50);
                } catch (Exception e) {
                    if (running) {
                        System.err.println("Position sender error: " + e.getMessage());
                        connected = false;
                    }
                    break;
                }
            }
        }).start();
    }

    private void updateOtherPlayer(PlayerInfo info) {
        Entity otherPlayer = otherPlayers.get(info.playerId);
        
        if (otherPlayer == null) {
            Color playerColor = Color.web(info.colorHex);
            Circle circle = new Circle(20, playerColor);
            SmoothPlayerComponent smoothComponent = new SmoothPlayerComponent();
            otherPlayer = FXGL.entityBuilder()
                    .at(info.x, info.y)
                    .view(circle)
                    .with(smoothComponent)
                    .buildAndAttach();
            otherPlayers.put(info.playerId, otherPlayer);
            System.out.println("New player joined: " + info.playerId);
        } else {
            SmoothPlayerComponent smoothComponent = otherPlayer.getComponent(SmoothPlayerComponent.class);
            smoothComponent.setTargetPosition(info.x, info.y);
            smoothComponent.setTargetScaleY(info.scaleY);
        }
    }

    private void removeOtherPlayer(String playerId) {
        Entity player = otherPlayers.remove(playerId);
        if (player != null) {
            player.removeFromWorld();
            System.out.println("Player left: " + playerId);
        }
    }

    private void updatePlatformSmooth(PlatformInfo info) {
        if (info.platformId >= 0 && info.platformId < platformEntities.size()) {
            Entity platform = platformEntities.get(info.platformId);
            
            if (draggedPlatformId == info.platformId) {
                return;
            }
            
            SmoothPlatformComponent smoothComp = platformSmoothComponents.get(info.platformId);
            if (smoothComp != null) {
                smoothComp.setTargetPosition(info.x, info.y);
                myPlatformPositions.put(info.platformId, new Point2D(info.x, info.y));
            }
        }
    }

    private void sendPlatformUpdate(int platformId, double x, double y) {
        if (!connected) return;
        
        try {
            synchronized (out) {
                PlatformInfo info = new PlatformInfo(platformId, x, y);
                out.writeObject(info);
                out.flush();
                out.reset();
            }
        } catch (Exception e) {
            System.err.println("Failed to send platform update: " + e.getMessage());
            connected = false;
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
                        
                        SmoothPlatformComponent smoothComp = platformSmoothComponents.get(i);
                        if (smoothComp != null) {
                            smoothComp.disable();
                        }
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
                    myPlatformPositions.put(draggedPlatformId, new Point2D(newX, newY));
                    
                    sendPlatformUpdate(draggedPlatformId, newX, newY);
                }
            }

            @Override
            protected void onActionEnd() {
                if (draggedPlatformId != -1) {
                    SmoothPlatformComponent smoothComp = platformSmoothComponents.get(draggedPlatformId);
                    if (smoothComp != null) {
                        Point2D finalPos = draggedPlatform.getPosition();
                        smoothComp.setTargetPosition(finalPos.getX(), finalPos.getY());
                        smoothComp.enable();
                    }
                }
                draggedPlatform = null;
                draggedPlatformId = -1;
            }
        }, MouseButton.PRIMARY);

        FXGL.getInput().addAction(new UserAction("Quit") {
            @Override
            protected void onActionBegin() {
                cleanup();
                FXGL.getGameController().exit();
            }
        }, KeyCode.Q);
    }

    private void cleanup() {
        running = false;
        connected = false;
        try {
            Thread.sleep(100);
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class SmoothPlayerComponent extends Component {
    private Point2D targetPosition;
    private double targetScaleY = 1.0;
    private final double SMOOTHING = 0.3;

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

        Point2D currentPos = entity.getPosition();
        double newX = currentPos.getX() + (targetPosition.getX() - currentPos.getX()) * SMOOTHING;
        double newY = currentPos.getY() + (targetPosition.getY() - currentPos.getY()) * SMOOTHING;
        
        entity.setPosition(newX, newY);

        double currentScaleY = entity.getTransformComponent().getScaleY();
        double newScaleY = currentScaleY + (targetScaleY - currentScaleY) * SMOOTHING;
        entity.getTransformComponent().setScaleY(newScaleY);
    }
}

class SmoothPlatformComponent extends Component {
    private Point2D targetPosition;
    private final double SMOOTHING = 0.25;
    private boolean initialized = false;
    private boolean enabled = true;

    public SmoothPlatformComponent() {
        this.targetPosition = Point2D.ZERO;
    }

    public void setTargetPosition(double x, double y) {
        this.targetPosition = new Point2D(x, y);
    }

    public void disable() {
        enabled = false;
    }

    public void enable() {
        enabled = true;
        targetPosition = entity.getPosition();
    }

    @Override
    public void onUpdate(double tpf) {
        if (!enabled) {
            return;
        }

        if (!initialized) {
            targetPosition = entity.getPosition();
            initialized = true;
            return;
        }

        if (targetPosition.equals(Point2D.ZERO)) {
            return;
        }

        Point2D currentPos = entity.getPosition();
        double distance = currentPos.distance(targetPosition);
        
        if (distance < 0.5) {
            entity.setPosition(targetPosition);
        } else {
            double newX = currentPos.getX() + (targetPosition.getX() - currentPos.getX()) * SMOOTHING;
            double newY = currentPos.getY() + (targetPosition.getY() - currentPos.getY()) * SMOOTHING;
            entity.setPosition(newX, newY);
        }
    }
}

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

        CollisionSide side = CollisionSide.NONE;
        
        if (minOverlap == overlapTop && velocityY >= 0) {
            side = CollisionSide.TOP;
        } else if (minOverlap == overlapBottom && velocityY <= 0) {
            side = CollisionSide.BOTTOM;
        } else if (minOverlap == overlapLeft && velocityY < 5) {
            side = CollisionSide.LEFT;
        } else if (minOverlap == overlapRight && velocityY < 5) {
            side = CollisionSide.RIGHT;
        }

        return new CollisionInfo(true, side);
    }
}

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
    private final double MAX_VELOCITY_Y = 20;

    public PlayerControl(List<Entity> platforms) {
        this.platforms = platforms;
    }

    @Override
    public void onUpdate(double tpf) {
        velocityY += gravity;
        
        if (velocityY > MAX_VELOCITY_Y) {
            velocityY = MAX_VELOCITY_Y;
        }
        if (velocityY < -MAX_VELOCITY_Y) {
            velocityY = -MAX_VELOCITY_Y;
        }
        
        double newX = entity.getX() + velocityX;
        double newY = entity.getY() + velocityY;
        
        entity.setX(newX);
        entity.setY(newY);
        
        onGround = false;
        
        for (Entity platform : platforms) {
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
            
            if (collision.collided) {
                switch (collision.side) {
                    case TOP:
                        double platformTop = platform.getY();
                        entity.setY(platformTop - playerRadius);
                        if (velocityY > 0) {
                            velocityY = 0;
                        }
                        onGround = true;
                        break;
                    case BOTTOM:
                        double platformBottom = platform.getY() + pc.getHeight();
                        entity.setY(platformBottom + playerRadius);
                        if (velocityY < 0) {
                            velocityY = 0;
                        }
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
        
        if (entity.getX() < LEFT_BOUNDARY) {
            entity.setX(LEFT_BOUNDARY);
            velocityX = 0;
        }
        if (entity.getX() > RIGHT_BOUNDARY) {
            entity.setX(RIGHT_BOUNDARY);
            velocityX = 0;
        }
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