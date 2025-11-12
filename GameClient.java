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
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.geometry.Point2D;
import java.util.*;
import java.io.*;
import java.net.*;

/**
 * 多人平台遊戲客戶端
 * 三階段遊戲：選擇物件 -> 放置物件 -> 遊戲競賽
 */
public class GameClient extends GameApplication {

    // 遊戲實體
    private Entity player;
    private List<Entity> platformEntities = new ArrayList<>();
    private Map<String, Entity> otherPlayers = new HashMap<>();
    
    // 網路連線
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myPlayerId;
    private Color myColor = Color.RED;
    private volatile boolean connected = false;
    private volatile boolean running = true;
    
    // 遊戲狀態
    private GamePhase currentPhase = GamePhase.SELECTING;
    private List<GameObjectInfo> availableObjects = new ArrayList<>();
    private Integer selectedObjectId = null;
    private PlatformPlacement myPlacement = null;
    private Map<String, PlatformPlacement> otherPlacements = new HashMap<>();
    private Map<String, Integer> playerScores = new HashMap<>();
    
    // UI元素
    private Text phaseText;
    private Text timerText;
    private Text scoreText;
    private List<Entity> objectButtons = new ArrayList<>();
    private Entity previewPlatform = null;
    
    // 固定平台（起點和終點）
    private Entity startPlatform;
    private Entity endPlatform;
    
    // 遊戲計時
    private long gameStartTime = 0;
    private static final long GAME_DURATION = 60000;
    
    // 螢幕尺寸
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(SCREEN_WIDTH);
        settings.setHeight(SCREEN_HEIGHT);
        settings.setTitle("Multiplayer Platform Race");
        settings.setFullScreenAllowed(true);
        settings.setFullScreenFromStart(false);
    }

    @Override
    protected void initGame() {
        // 設置背景顏色
        FXGL.getGameScene().setBackgroundColor(Color.rgb(30, 30, 40));
        
        // 創建固定平台
        createFixedPlatforms();
        
        // 先創建玩家（初始隱藏）
        player = FXGL.entityBuilder()
                .at(100, 900)
                .view(new Circle(25, myColor))
                .with(new PlayerControl(platformEntities))
                .buildAndAttach();
        player.setVisible(false);
        
        // 創建UI
        createUI();
        
        // 連接伺服器（在玩家創建之後）
        connectToServer();
        
        // 啟動網路執行緒
        startNetworkThread();
        startPositionSender();
    }
    
    /**
     * 創建固定平台（起點和終點）
     */
    private void createFixedPlatforms() {
        // 起點平台（左下角）
        startPlatform = createPlatform(50, SCREEN_HEIGHT - 150, 200, 30, Color.GREEN);
        
        // 終點平台
        endPlatform = createPlatform(300, SCREEN_HEIGHT - 150, 200, 30, Color.GOLD);
        
        // 添加標籤
        Text startLabel = new Text("START");
        startLabel.setFont(Font.font(20));
        startLabel.setFill(Color.WHITE);
        FXGL.entityBuilder()
                .at(110, SCREEN_HEIGHT - 130)
                .view(startLabel)
                .buildAndAttach();
        
        Text endLabel = new Text("FINISH");
        endLabel.setFont(Font.font(20));
        endLabel.setFill(Color.WHITE);
        FXGL.entityBuilder()
                .at(360, SCREEN_HEIGHT - 130)
                .view(endLabel)
                .buildAndAttach();
    }
    
    /**
     * 創建UI元素
     */
    private void createUI() {
        // 階段提示文字
        phaseText = new Text("Phase: SELECTING");
        phaseText.setFont(Font.font(30));
        phaseText.setFill(Color.WHITE);
        FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 150, 50)
                .view(phaseText)
                .buildAndAttach();
        
        // 計時器文字
        timerText = new Text("");
        timerText.setFont(Font.font(25));
        timerText.setFill(Color.YELLOW);
        FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 50, 90)
                .view(timerText)
                .buildAndAttach();
        
        // 分數文字
        scoreText = new Text("Score: 0");
        scoreText.setFont(Font.font(20));
        scoreText.setFill(Color.CYAN);
        FXGL.entityBuilder()
                .at(50, 50)
                .view(scoreText)
                .buildAndAttach();
    }
    
    /**
     * 顯示可選擇的物件
     */
    private void displayObjectSelection() {
        // 清除舊的按鈕
        for (Entity btn : objectButtons) {
            btn.removeFromWorld();
        }
        objectButtons.clear();
        
        System.out.println("Displaying " + availableObjects.size() + " objects for selection");
        
        // 創建物件選擇按鈕（垂直排列在螢幕中央）
        int startY = 200;
        int spacing = 150;
        
        for (int i = 0; i < availableObjects.size(); i++) {
            GameObjectInfo obj = availableObjects.get(i);
            int yPos = startY + i * spacing;
            
            System.out.println("Object " + i + ": " + obj.width + "x" + obj.height + " at " + yPos);
            
            // 創建按鈕背景（更大更明顯）
            Rectangle btnBg = new Rectangle(400, 100, Color.rgb(80, 80, 100));
            btnBg.setStroke(Color.WHITE);
            btnBg.setStrokeWidth(2);
            Entity btnEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 200, yPos)
                    .view(btnBg)
                    .buildAndAttach();
            objectButtons.add(btnEntity);
            
            // 創建物件預覽
            Rectangle objRect = new Rectangle(obj.width, obj.height, Color.web(obj.color));
            objRect.setStroke(Color.YELLOW);
            objRect.setStrokeWidth(1);
            Entity objPreview = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - obj.width / 2, yPos + 50 - obj.height / 2)
                    .view(objRect)
                    .buildAndAttach();
            objectButtons.add(objPreview);
            
            // 創建序號標籤
            Text numLabel = new Text("Platform " + (i + 1));
            numLabel.setFont(Font.font(20));
            numLabel.setFill(Color.WHITE);
            Entity numEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 200 + 10, yPos + 25)
                    .view(numLabel)
                    .buildAndAttach();
            objectButtons.add(numEntity);
            
            // 創建尺寸標籤
            Text sizeLabel = new Text(obj.width + " x " + obj.height);
            sizeLabel.setFont(Font.font(18));
            sizeLabel.setFill(Color.LIGHTGREEN);
            Entity sizeEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 200 + 10, yPos + 80)
                    .view(sizeLabel)
                    .buildAndAttach();
            objectButtons.add(sizeEntity);
        }
    }
    
    /**
     * 清除物件選擇UI
     */
    private void clearObjectSelection() {
        System.out.println("Clearing " + objectButtons.size() + " selection UI elements");
        for (Entity btn : objectButtons) {
            btn.removeFromWorld();
        }
        objectButtons.clear();
    }
    
    /**
     * 連接伺服器
     */
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 5000);
            socket.setTcpNoDelay(true);
            
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            
            Object initObj = in.readObject();
            if (initObj instanceof InitMessage initMsg) {
                myPlayerId = initMsg.playerId;
                myColor = Color.web(initMsg.colorHex);
                connected = true;
                System.out.println("Connected as " + myPlayerId + " with color " + initMsg.colorHex);
                
                // 更新玩家顏色
                if (player != null && player.getViewComponent() != null && 
                    !player.getViewComponent().getChildren().isEmpty()) {
                    Circle circle = (Circle) player.getViewComponent().getChildren().get(0);
                    circle.setFill(myColor);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to connect: " + e.getMessage());
            e.printStackTrace();
            connected = false;
        }
    }
    
    /**
     * 網路接收執行緒
     */
    private void startNetworkThread() {
        new Thread(() -> {
            try {
                while (running && connected) {
                    Object obj = in.readObject();
                    
                    if (obj instanceof PhaseChangeMessage phaseMsg) {
                        javafx.application.Platform.runLater(() -> {
                            handlePhaseChange(phaseMsg.phase);
                        });
                    }
                    else if (obj instanceof ObjectListMessage objListMsg) {
                        javafx.application.Platform.runLater(() -> {
                            availableObjects = objListMsg.objects;
                            System.out.println("Received object list with " + availableObjects.size() + " objects");
                            if (currentPhase == GamePhase.SELECTING) {
                                displayObjectSelection();
                            }
                        });
                    }
                    else if (obj instanceof PlacementMessage placementMsg) {
                        javafx.application.Platform.runLater(() -> {
                            handlePlacement(placementMsg);
                        });
                    }
                    else if (obj instanceof PlayerInfo info) {
                        javafx.application.Platform.runLater(() -> {
                            updateOtherPlayer(info);
                        });
                    }
                    else if (obj instanceof DisconnectMessage disconnectMsg) {
                        javafx.application.Platform.runLater(() -> {
                            removeOtherPlayer(disconnectMsg.playerId);
                        });
                    }
                    else if (obj instanceof ScoreUpdateMessage scoreMsg) {
                        javafx.application.Platform.runLater(() -> {
                            playerScores = scoreMsg.scores;
                            updateScoreDisplay();
                        });
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Network error: " + e.getMessage());
                }
            } finally {
                connected = false;
            }
        }).start();
    }
    
    /**
     * 位置發送執行緒
     */
    private void startPositionSender() {
        new Thread(() -> {
            while (running) {
                try {
                    if (connected && currentPhase == GamePhase.PLAYING && player.isVisible()) {
                        synchronized (out) {
                            PlayerControl pc = player.getComponent(PlayerControl.class);
                            PlayerInfo info = new PlayerInfo(
                                myPlayerId, toHex(myColor),
                                player.getX(), player.getY(),
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
                        connected = false;
                    }
                    break;
                }
            }
        }).start();
    }
    
    /**
     * 處理階段變更
     */
    private void handlePhaseChange(GamePhase newPhase) {
        System.out.println("Phase changed to: " + newPhase);
        currentPhase = newPhase;
        
        switch (newPhase) {
            case SELECTING:
                phaseText.setText("Phase: SELECT YOUR PLATFORM");
                player.setVisible(false);
                selectedObjectId = null;
                myPlacement = null;
                
                // 清除所有動態平台
                for (int i = platformEntities.size() - 1; i >= 0; i--) {
                    Entity p = platformEntities.get(i);
                    if (p != startPlatform && p != endPlatform) {
                        p.removeFromWorld();
                        platformEntities.remove(i);
                    }
                }
                otherPlacements.clear();
                
                if (previewPlatform != null) {
                    previewPlatform.removeFromWorld();
                    previewPlatform = null;
                }
                
                // 確保顯示物件選擇（如果已經收到物件列表）
                if (!availableObjects.isEmpty()) {
                    System.out.println("Displaying objects immediately");
                    displayObjectSelection();
                } else {
                    System.out.println("Waiting for object list from server");
                }
                break;
                
            case PLACING:
                phaseText.setText("Phase: PLACE YOUR PLATFORM");
                clearObjectSelection();
                break;
                
            case PLAYING:
                phaseText.setText("Phase: RACE TO FINISH!");
                if (previewPlatform != null) {
                    previewPlatform.removeFromWorld();
                    previewPlatform = null;
                }
                
                // 創建所有玩家放置的平台
                for (Map.Entry<String, PlatformPlacement> entry : otherPlacements.entrySet()) {
                    PlatformPlacement p = entry.getValue();
                    createPlatform(p.x, p.y, p.width, p.height, Color.web(p.color));
                }
                
                // 創建自己的平台
                if (myPlacement != null) {
                    createPlatform(myPlacement.x, myPlacement.y, myPlacement.width, 
                                 myPlacement.height, Color.web(myPlacement.color));
                }
                
                player.setVisible(true);
                player.setPosition(100, 900);
                gameStartTime = System.currentTimeMillis();
                break;
        }
    }
    
    /**
     * 處理其他玩家放置的平台
     */
    private void handlePlacement(PlacementMessage msg) {
        otherPlacements.put(msg.playerId, msg.placement);
        
        // 如果在遊戲階段，創建所有玩家的平台（包括自己的）
        if (currentPhase == GamePhase.PLAYING) {
            PlatformPlacement p = msg.placement;
            createPlatform(p.x, p.y, p.width, p.height, Color.web(p.color));
        }
    }
    
    /**
     * 更新其他玩家位置
     */
    private void updateOtherPlayer(PlayerInfo info) {
        Entity otherPlayer = otherPlayers.get(info.playerId);
        
        if (otherPlayer == null && currentPhase == GamePhase.PLAYING) {
            Color playerColor = Color.web(info.colorHex);
            Circle circle = new Circle(25, playerColor);
            SmoothPlayerComponent smoothComponent = new SmoothPlayerComponent();
            otherPlayer = FXGL.entityBuilder()
                    .at(info.x, info.y)
                    .view(circle)
                    .with(smoothComponent)
                    .buildAndAttach();
            otherPlayers.put(info.playerId, otherPlayer);
        } else if (otherPlayer != null) {
            SmoothPlayerComponent smoothComponent = otherPlayer.getComponent(SmoothPlayerComponent.class);
            smoothComponent.setTargetPosition(info.x, info.y);
            smoothComponent.setTargetScaleY(info.scaleY);
        }
    }
    
    /**
     * 移除離線玩家
     */
    private void removeOtherPlayer(String playerId) {
        Entity player = otherPlayers.remove(playerId);
        if (player != null) {
            player.removeFromWorld();
        }
    }
    
    /**
     * 更新分數顯示
     */
    private void updateScoreDisplay() {
        Integer myScore = playerScores.getOrDefault(myPlayerId, 0);
        scoreText.setText("Score: " + myScore);
    }
    
    /**
     * 創建平台
     */
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
    
    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

    @Override
    protected void initInput() {
        // 玩家移動（僅在遊戲階段）
        FXGL.getInput().addAction(new UserAction("Move Left A") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).moveLeft();
                }
            }
        }, KeyCode.A);
        
        FXGL.getInput().addAction(new UserAction("Move Left Arrow") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).moveLeft();
                }
            }
        }, KeyCode.LEFT);

        FXGL.getInput().addAction(new UserAction("Move Right D") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).moveRight();
                }
            }
        }, KeyCode.D);
        
        FXGL.getInput().addAction(new UserAction("Move Right Arrow") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).moveRight();
                }
            }
        }, KeyCode.RIGHT);

        FXGL.getInput().addAction(new UserAction("Jump W") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).jump();
                }
            }
        }, KeyCode.W);
        
        FXGL.getInput().addAction(new UserAction("Jump Up") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).jump();
                }
            }
        }, KeyCode.UP);
        
        FXGL.getInput().addAction(new UserAction("Jump Space") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).jump();
                }
            }
        }, KeyCode.SPACE);

        FXGL.getInput().addAction(new UserAction("Crouch S") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).crouch(true);
                }
            }
            @Override
            protected void onActionEnd() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).crouch(false);
                }
            }
        }, KeyCode.S);
        
        FXGL.getInput().addAction(new UserAction("Crouch Down") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).crouch(true);
                }
            }
            @Override
            protected void onActionEnd() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
                    player.getComponent(PlayerControl.class).crouch(false);
                }
            }
        }, KeyCode.DOWN);

        // 滑鼠點擊（選擇或放置）
        FXGL.getInput().addAction(new UserAction("Click") {
            @Override
            protected void onActionBegin() {
                Point2D mousePos = FXGL.getInput().getMousePositionWorld();
                
                // 選擇階段：點擊選擇物件
                if (currentPhase == GamePhase.SELECTING && selectedObjectId == null) {
                    handleObjectSelection(mousePos);
                }
                // 放置階段：點擊放置物件
                else if (currentPhase == GamePhase.PLACING && myPlacement == null && selectedObjectId != null) {
                    handleObjectPlacement(mousePos);
                }
            }
        }, MouseButton.PRIMARY);
        
        // 退出
        FXGL.getInput().addAction(new UserAction("Quit") {
            @Override
            protected void onActionBegin() {
                cleanup();
                FXGL.getGameController().exit();
            }
        }, KeyCode.ESCAPE);
    }
    
    /**
     * 處理物件選擇
     */
    private void handleObjectSelection(Point2D mousePos) {
        int startY = 200;
        int spacing = 150;
        
        for (int i = 0; i < availableObjects.size(); i++) {
            GameObjectInfo obj = availableObjects.get(i);
            int yPos = startY + i * spacing;
            
            // 按鈕範圍（固定400x100）
            double btnLeft = SCREEN_WIDTH / 2 - 200.0;
            double btnRight = SCREEN_WIDTH / 2 + 200.0;
            double btnTop = yPos;
            double btnBottom = yPos + 100;
            
            System.out.println("Checking click at (" + mousePos.getX() + ", " + mousePos.getY() + 
                             ") against button " + i + " [" + btnLeft + "-" + btnRight + ", " + btnTop + "-" + btnBottom + "]");
            
            if (mousePos.getX() >= btnLeft && mousePos.getX() <= btnRight &&
                mousePos.getY() >= btnTop && mousePos.getY() <= btnBottom) {
                
                selectedObjectId = obj.id;
                System.out.println("Selected object: " + obj.id + " (Size: " + obj.width + "x" + obj.height + ")");
                
                // 發送選擇訊息給伺服器
                try {
                    synchronized (out) {
                        out.writeObject(new SelectionMessage(myPlayerId, obj.id));
                        out.flush();
                        out.reset();
                    }
                } catch (Exception e) {
                    System.err.println("Failed to send selection: " + e.getMessage());
                }
                
                // 清除選擇界面
                clearObjectSelection();
                
                // 顯示等待訊息
                phaseText.setText("Waiting for other players...");
                break;
            }
        }
    }
    
    /**
     * 處理物件放置
     */
    private void handleObjectPlacement(Point2D mousePos) {
        GameObjectInfo selectedObj = null;
        for (GameObjectInfo obj : availableObjects) {
            if (obj.id == selectedObjectId) {
                selectedObj = obj;
                break;
            }
        }
        
        if (selectedObj != null) {
            double x = mousePos.getX() - selectedObj.width / 2.0;
            double y = mousePos.getY() - selectedObj.height / 2.0;
            
            myPlacement = new PlatformPlacement(
                selectedObj.id, x, y,
                selectedObj.width, selectedObj.height,
                selectedObj.color
            );
            
            // 發送放置訊息給伺服器
            try {
                synchronized (out) {
                    out.writeObject(new PlacementMessage(myPlayerId, myPlacement));
                    out.flush();
                    out.reset();
                }
            } catch (Exception e) {
                System.err.println("Failed to send placement: " + e.getMessage());
            }
            
            // 創建預覽平台
            if (previewPlatform != null) {
                previewPlatform.removeFromWorld();
            }
            previewPlatform = createPlatform(x, y, selectedObj.width, selectedObj.height, 
                                            Color.web(selectedObj.color));
        }
    }
    
    @Override
    protected void onUpdate(double tpf) {
        // 更新計時器
        if (currentPhase == GamePhase.PLAYING && gameStartTime > 0) {
            long elapsed = System.currentTimeMillis() - gameStartTime;
            long remaining = GAME_DURATION - elapsed;
            
            if (remaining > 0) {
                timerText.setText("Time: " + (remaining / 1000) + "s");
            } else {
                timerText.setText("Time: 0s");
            }
        } else {
            timerText.setText("");
        }
        
        // 檢查玩家是否到達終點
        if (currentPhase == GamePhase.PLAYING && player.isVisible()) {
            double playerX = player.getX();
            double playerY = player.getY();
            double endX = endPlatform.getX();
            double endY = endPlatform.getY();
            
            // 檢查玩家是否在終點平台上
            if (playerX >= endX && playerX <= endX + 200 &&
                playerY >= endY - 50 && playerY <= endY + 30) {
                
                // 發送完成訊息
                try {
                    synchronized (out) {
                        out.writeObject(new FinishMessage(myPlayerId));
                        out.flush();
                        out.reset();
                    }
                    player.setVisible(false); // 隱藏玩家，表示已完成
                } catch (Exception e) {
                    System.err.println("Failed to send finish message: " + e.getMessage());
                }
            }
        }
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

// 平滑移動組件（其他玩家）
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

    /**
     * 檢查碰撞
     */
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

        // 計算重疊量
        double overlapLeft = playerRight - platformLeft;
        double overlapRight = platformRight - playerLeft;
        double overlapTop = playerBottom - platformTop;
        double overlapBottom = platformBottom - playerTop;

        double minOverlap = Math.min(Math.min(overlapLeft, overlapRight), 
                                     Math.min(overlapTop, overlapBottom));

        CollisionSide side = CollisionSide.NONE;
        
        // 根據最小重疊和速度方向判斷碰撞面
        if (minOverlap == overlapTop && velocityY >= 0) {
            side = CollisionSide.TOP;
        } else if (minOverlap == overlapBottom && velocityY <= 0) {
            side = CollisionSide.BOTTOM;
        } else if (minOverlap == overlapLeft && Math.abs(velocityY) < 5) {
            side = CollisionSide.LEFT;
        } else if (minOverlap == overlapRight && Math.abs(velocityY) < 5) {
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

// 玩家控制組件
class PlayerControl extends Component {
    private double speed = 8.0;
    private double velocityX = 0;
    private double velocityY = 0;
    private double jumpStrength = 20.0;
    private double gravity = 1.0;
    private boolean onGround = false;
    private boolean crouching = false;
    private List<Entity> platforms;
    private double playerRadius = 25;
    
    private final double LEFT_BOUNDARY = playerRadius;
    private final double RIGHT_BOUNDARY = 1920 - playerRadius;
    private final double TOP_BOUNDARY = playerRadius;
    private final double MAX_VELOCITY_Y = 25;

    public PlayerControl(List<Entity> platforms) {
        this.platforms = platforms;
    }

    @Override
    public void onUpdate(double tpf) {
        // 應用重力
        velocityY += gravity;
        
        // 限制最大速度
        if (velocityY > MAX_VELOCITY_Y) velocityY = MAX_VELOCITY_Y;
        if (velocityY < -MAX_VELOCITY_Y) velocityY = -MAX_VELOCITY_Y;
        
        // 更新位置
        entity.setX(entity.getX() + velocityX);
        entity.setY(entity.getY() + velocityY);
        
        onGround = false;
        
        // 檢查與所有平台的碰撞
        for (Entity platform : platforms) {
            // 確保平台有 PlatformComponent
            if (!platform.hasComponent(PlatformComponent.class)) {
                continue;
            }
            
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
            
            if (collision.collided) {
                switch (collision.side) {
                    case TOP:
                        entity.setY(platform.getY() - playerRadius);
                        if (velocityY > 0) velocityY = 0;
                        onGround = true;
                        break;
                    case BOTTOM:
                        entity.setY(platform.getY() + pc.getHeight() + playerRadius);
                        if (velocityY < 0) velocityY = 0;
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
        
        // 邊界檢查
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
        
        // 掉出螢幕底部則重置到起點
        if (entity.getY() > 1080) {
            entity.setPosition(100, 900);
            velocityY = 0;
            velocityX = 0;
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
        this.crouching = crouching;
        entity.getTransformComponent().setScaleY(crouching ? 0.5 : 1.0);
    }
    
    public boolean isCrouching() {
        return crouching;
    }
}