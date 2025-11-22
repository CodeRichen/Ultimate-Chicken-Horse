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
import java.util.AbstractMap;
/**
 * 多人平台遊戲客戶端（修正版）
 */
public class GameClient extends GameApplication {

    private Entity player;
    private List<Entity> platformEntities = new ArrayList<>();
    private Map<String, Entity> otherPlayers = new HashMap<>();
    private Entity middlePlatform;
    private List<Entity> deathZones = new ArrayList<>();
    private List<Entity> safeZones = new ArrayList<>(); 
    private boolean zonesCreated = false;  
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myPlayerId;
    private Color myColor = Color.RED;
    private volatile boolean connected = false;
    private volatile boolean running = true;
    
    private GamePhase currentPhase = GamePhase.SELECTING;
    private List<GameObjectInfo> availableObjects = new ArrayList<>();
    private Map<Integer, GameObjectInfo> availableObjMap = new HashMap<>();
    private Integer selectedObjectId = null;
    private PlatformPlacement myPlacement = null;
    private Map<String, PlatformPlacement> otherPlacements = new HashMap<>();
    private Map<String, PlatformPlacement> otherPreviewPlacements = new HashMap<>();
    private Map<String, Entity> otherPreviewEntities = new HashMap<>();
    private Map<String, Integer> playerScores = new HashMap<>();
    
    private Text phaseText;
    private Text timerText;
    private Text scoreText;
    private List<Entity> objectButtons = new ArrayList<>();
    private Entity previewPlatform = null;
    private Entity finishButton = null;
    private List<Entity> leaderboardEntities = new ArrayList<>();
    
    private boolean isDragging = false;
    private Point2D dragOffset = Point2D.ZERO;
    private double currentRotation = 0;
    private GameObjectInfo selectedObj = null;
    
    private Entity startPlatform;
    private Entity endPlatform;
    private Entity groundPlatform; // 底部大型平台防止玩家掉落
    
    private long gameStartTime = 0;
    private static final long GAME_DURATION = 120000;
    
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    
    private boolean hasFinished = false;
    private boolean hasFailed = false;
    private boolean mapPlatformsLoaded = false;  // 標記地圖平台是否已加載
    private double deathRecoveryTimer = 0;  // 死亡恢復計時器
    private static final double DEATH_RECOVERY_TIME = 2.0;  // 2秒後恢復
    private enum UIState {
        MENU,      // 主選單
        IN_ROOM,   // 在房間中
        PLAYING    // 遊戲中
    }




    private UIState uiState = UIState.MENU;
    private RoomInfo currentRoomInfo = null;
    private List<Entity> menuEntities = new ArrayList<>();
    private List<Entity> roomUIEntities = new ArrayList<>();
    private javafx.scene.control.TextField roomCodeInput;
    private double cameraOffsetX = 0;
    private static final int FINISH_X = 4000;  

    /**
     * 創建主選單UI
     */
    private void createMainMenu() {
        System.out.println("[CLIENT] Creating main menu");
        clearAllUI();
        
        // 標題
        Text title = new Text("PLATFORM RACE");
        title.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 60));
        title.setFill(Color.GOLD);
        Entity titleEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 250, 150)
                .view(title)
                .buildAndAttach();
        menuEntities.add(titleEntity);
        
        // 創建公共房間按鈕
        Rectangle createPublicBtn = new Rectangle(400, 70, Color.rgb(50, 150, 50));
        createPublicBtn.setStroke(Color.WHITE);
        createPublicBtn.setStrokeWidth(3);
        Entity createPublicEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 200, 300)
                .view(createPublicBtn)
                .buildAndAttach();
        menuEntities.add(createPublicEntity);
        
        Text createPublicText = new Text("CREATE PUBLIC ROOM");
        createPublicText.setFont(Font.font(24));
        createPublicText.setFill(Color.WHITE);
        Entity createPublicTextEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 150, 345)
                .view(createPublicText)
                .buildAndAttach();
        menuEntities.add(createPublicTextEntity);
        
        // 創建私人房間按鈕
        Rectangle createPrivateBtn = new Rectangle(400, 70, Color.rgb(50, 100, 150));
        createPrivateBtn.setStroke(Color.WHITE);
        createPrivateBtn.setStrokeWidth(3);
        Entity createPrivateEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 200, 400)
                .view(createPrivateBtn)
                .buildAndAttach();
        menuEntities.add(createPrivateEntity);
        
        Text createPrivateText = new Text("CREATE PRIVATE ROOM");
        createPrivateText.setFont(Font.font(24));
        createPrivateText.setFill(Color.WHITE);
        Entity createPrivateTextEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 155, 445)
                .view(createPrivateText)
                .buildAndAttach();
        menuEntities.add(createPrivateTextEntity);
        
        // 加入隨機公共房間按鈕
        Rectangle joinRandomBtn = new Rectangle(400, 70, Color.rgb(150, 50, 150));
        joinRandomBtn.setStroke(Color.WHITE);
        joinRandomBtn.setStrokeWidth(3);
        Entity joinRandomEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 200, 500)
                .view(joinRandomBtn)
                .buildAndAttach();
        menuEntities.add(joinRandomEntity);
        
        Text joinRandomText = new Text("JOIN RANDOM ROOM");
        joinRandomText.setFont(Font.font(24));
        joinRandomText.setFill(Color.WHITE);
        Entity joinRandomTextEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 140, 545)
                .view(joinRandomText)
                .buildAndAttach();
        menuEntities.add(joinRandomTextEntity);
        
        // 用房間碼加入按鈕
        Rectangle joinCodeBtn = new Rectangle(400, 70, Color.rgb(50, 50, 150));
        joinCodeBtn.setStroke(Color.WHITE);
        joinCodeBtn.setStrokeWidth(3);
        Entity joinCodeEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 200, 600)
                .view(joinCodeBtn)
                .buildAndAttach();
        menuEntities.add(joinCodeEntity);
        
        Text joinCodeText = new Text("JOIN WITH CODE");
        joinCodeText.setFont(Font.font(24));
        joinCodeText.setFill(Color.WHITE);
        Entity joinCodeTextEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 120, 645)
                .view(joinCodeText)
                .buildAndAttach();
        menuEntities.add(joinCodeTextEntity);
        
        // 房間代碼輸入框
        roomCodeInput = new javafx.scene.control.TextField();
        roomCodeInput.setPromptText("Enter 4-digit room code");
        roomCodeInput.setFont(Font.font(20));
        roomCodeInput.setPrefWidth(400);
        roomCodeInput.setPrefHeight(50);
        roomCodeInput.setLayoutX(SCREEN_WIDTH / 2 - 200);
        roomCodeInput.setLayoutY(700);
        roomCodeInput.setVisible(false);
        FXGL.getGameScene().addUINode(roomCodeInput);
        
        // 說明文字
        Text hint = new Text("Public: Join anyone | Private: Need room code");
        hint.setFont(Font.font(18));
        hint.setFill(Color.LIGHTGRAY);
        Entity hintEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 220, 800)
                .view(hint)
                .buildAndAttach();
        menuEntities.add(hintEntity);
        
        uiState = UIState.MENU;
    }
    /**
     * 創建房間UI
     */
    private void createRoomUI() {
        System.out.println("[CLIENT] Creating room UI");
        clearAllUI();
        
        if (currentRoomInfo == null) return;
        
        // 房間代碼顯示
        String roomType = currentRoomInfo.roomType == RoomType.PUBLIC ? "PUBLIC" : "PRIVATE";
        Text typeText = new Text("Type: " + roomType);
        typeText.setFont(Font.font(20));
        typeText.setFill(currentRoomInfo.roomType == RoomType.PUBLIC ? Color.LIGHTGREEN : Color.LIGHTSALMON);
        Entity typeEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 50, 130)
                .view(typeText)
                .buildAndAttach();
        roomUIEntities.add(typeEntity);
        Text roomCodeText = new Text("Room Code: " + currentRoomInfo.roomCode);
        roomCodeText.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 40));
        roomCodeText.setFill(Color.GOLD);
        Entity roomCodeEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 200, 100)
                .view(roomCodeText)
                .buildAndAttach();
        roomUIEntities.add(roomCodeEntity);
        
        // 回合資訊
        String roundInfo = currentRoomInfo.state == RoomState.PLAYING ? 
            "Round: " + currentRoomInfo.currentRound + "/" + currentRoomInfo.totalRounds :
            "Waiting to start...";
        
        Text roundText = new Text(roundInfo);
        roundText.setFont(Font.font(24));
        roundText.setFill(Color.CYAN);
        Entity roundEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 100, 150)
                .view(roundText)
                .buildAndAttach();
        roomUIEntities.add(roundEntity);
        
        // 玩家列表
        Text playersTitle = new Text("Players (" + currentRoomInfo.playerIds.size() + "/" + 
                                    currentRoomInfo.maxPlayers + "):");
        playersTitle.setFont(Font.font(28));
        playersTitle.setFill(Color.WHITE);
        Entity playersTitleEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 300, 250)
                .view(playersTitle)
                .buildAndAttach();
        roomUIEntities.add(playersTitleEntity);
        
        int yOffset = 300;
        for (String pid : currentRoomInfo.playerIds) {
            boolean isHost = pid.equals(currentRoomInfo.hostId);
            boolean isReady = currentRoomInfo.readyStatus.getOrDefault(pid, false);
            boolean isMe = pid.equals(myPlayerId);
            
            String playerText = (isMe ? "► " : "  ") + pid + 
                            (isHost ? " (HOST)" : "") + 
                            (isReady ? " ✓" : "");
            
            Text pText = new Text(playerText);
            pText.setFont(Font.font(22));
            pText.setFill(isMe ? Color.YELLOW : (isReady ? Color.GREEN : Color.WHITE));
            Entity pEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 280, yOffset)
                    .view(pText)
                    .buildAndAttach();
            roomUIEntities.add(pEntity);
            
            yOffset += 50;
        }
        
        // 準備按鈕（非房主）
        if (!myPlayerId.equals(currentRoomInfo.hostId) && currentRoomInfo.state == RoomState.WAITING) {
            boolean myReady = currentRoomInfo.readyStatus.getOrDefault(myPlayerId, false);
            Color btnColor = myReady ? Color.rgb(150, 150, 50) : Color.rgb(50, 150, 50);
            String btnText = myReady ? "CANCEL READY" : "READY";
            
            Rectangle readyBtn = new Rectangle(300, 70, btnColor);
            readyBtn.setStroke(Color.WHITE);
            readyBtn.setStrokeWidth(3);
            Entity readyBtnEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 150, 600)
                    .view(readyBtn)
                    .buildAndAttach();
            roomUIEntities.add(readyBtnEntity);
            
            Text readyText = new Text(btnText);
            readyText.setFont(Font.font(26));
            readyText.setFill(Color.WHITE);
            Entity readyTextEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - (btnText.length() * 8), 645)
                    .view(readyText)
                    .buildAndAttach();
            roomUIEntities.add(readyTextEntity);
        }
        
        // 開始遊戲按鈕（房主且所有人準備）
        if (myPlayerId.equals(currentRoomInfo.hostId) && currentRoomInfo.state == RoomState.WAITING) {
            boolean canStart = true;
                for (String pid : currentRoomInfo.playerIds) {
                    // 房主不需要準備
                    if (pid.equals(currentRoomInfo.hostId)) continue;
                    if (!currentRoomInfo.readyStatus.getOrDefault(pid, false)) {
                        canStart = false;
                        break;
                    }
                }

            
            Color btnColor = canStart ? Color.rgb(50, 200, 50) : Color.rgb(100, 100, 100);
            
            Rectangle startBtn = new Rectangle(300, 70, btnColor);
            startBtn.setStroke(Color.WHITE);
            startBtn.setStrokeWidth(3);
            Entity startBtnEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 150, 700)
                    .view(startBtn)
                    .buildAndAttach();
            roomUIEntities.add(startBtnEntity);
            
            Text startText = new Text("START GAME");
            startText.setFont(Font.font(26));
            startText.setFill(Color.WHITE);
            Entity startTextEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 100, 745)
                    .view(startText)
                    .buildAndAttach();
            roomUIEntities.add(startTextEntity);
            
            if (!canStart) {
                Text waitText = new Text("Waiting for all players to ready...");
                waitText.setFont(Font.font(18));
                waitText.setFill(Color.ORANGE);
                Entity waitEntity = FXGL.entityBuilder()
                        .at(SCREEN_WIDTH / 2 - 180, 790)
                        .view(waitText)
                        .buildAndAttach();
                roomUIEntities.add(waitEntity);
            }
        }
        
        // 離開房間按鈕
        Rectangle leaveBtn = new Rectangle(200, 50, Color.rgb(150, 50, 50));
        leaveBtn.setStroke(Color.WHITE);
        leaveBtn.setStrokeWidth(2);
        Entity leaveBtnEntity = FXGL.entityBuilder()
                .at(50, SCREEN_HEIGHT - 100)
                .view(leaveBtn)
                .buildAndAttach();
        roomUIEntities.add(leaveBtnEntity);
        
        Text leaveText = new Text("LEAVE");
        leaveText.setFont(Font.font(20));
        leaveText.setFill(Color.WHITE);
        Entity leaveTextEntity = FXGL.entityBuilder()
                .at(120, SCREEN_HEIGHT - 63)
                .view(leaveText)
                .buildAndAttach();
        roomUIEntities.add(leaveTextEntity);
        
        uiState = UIState.IN_ROOM;
    }

    /**
     * 清除所有UI
     */
    private void clearAllUI() {
        for (Entity e : menuEntities) {
            e.removeFromWorld();
        }
        menuEntities.clear();
        
        for (Entity e : roomUIEntities) {
            e.removeFromWorld();
        }
        roomUIEntities.clear();
        
        if (roomCodeInput != null) {
            roomCodeInput.setVisible(false);
        }
    }

    /**
     * 處理主選單點擊
     */
   private void handleMenuClick(Point2D mousePos) {
    double x = mousePos.getX();
    double y = mousePos.getY();
    
    int maxPlayers = 3;  // 預設3人房
    
    // 創建公共房間
    if (x >= SCREEN_WIDTH / 2 - 200 && x <= SCREEN_WIDTH / 2 + 200 &&
        y >= 300 && y <= 370) {
        
        try {
            synchronized (out) {
                out.writeObject(new CreateRoomRequest(maxPlayers, RoomType.PUBLIC));
                out.flush();
                out.reset();
            }
            System.out.println("[CLIENT] Sent create public room request");
        } catch (Exception e) {
            System.err.println("[CLIENT ERROR] Failed to create room: " + e.getMessage());
        }
    }
    // 創建私人房間
    else if (x >= SCREEN_WIDTH / 2 - 200 && x <= SCREEN_WIDTH / 2 + 200 &&
             y >= 400 && y <= 470) {
        
        try {
            synchronized (out) {
                out.writeObject(new CreateRoomRequest(maxPlayers, RoomType.PRIVATE));
                out.flush();
                out.reset();
            }
            System.out.println("[CLIENT] Sent create private room request");
        } catch (Exception e) {
            System.err.println("[CLIENT ERROR] Failed to create room: " + e.getMessage());
        }
    }
    // 加入隨機公共房間
    else if (x >= SCREEN_WIDTH / 2 - 200 && x <= SCREEN_WIDTH / 2 + 200 &&
             y >= 500 && y <= 570) {
        
        try {
            synchronized (out) {
                out.writeObject(new JoinRandomRoomRequest());
                out.flush();
                out.reset();
            }
            System.out.println("[CLIENT] Sent join random room request");
        } catch (Exception e) {
            System.err.println("[CLIENT ERROR] Failed to join random: " + e.getMessage());
        }
    }
    // 用房間碼加入
    else if (x >= SCREEN_WIDTH / 2 - 200 && x <= SCREEN_WIDTH / 2 + 200 &&
             y >= 600 && y <= 670) {
        
        roomCodeInput.setVisible(true);
        roomCodeInput.requestFocus();
    }
}

    /**
     * 處理房間UI點擊
     */
    private void handleRoomClick(Point2D mousePos) {
        if (currentRoomInfo == null) return;
        
        double x = mousePos.getX();
        double y = mousePos.getY();
        
        // 準備按鈕
        if (!myPlayerId.equals(currentRoomInfo.hostId) && currentRoomInfo.state == RoomState.WAITING) {
            if (x >= SCREEN_WIDTH / 2 - 150 && x <= SCREEN_WIDTH / 2 + 150 &&
                y >= 600 && y <= 670) {
                
                boolean currentReady = currentRoomInfo.readyStatus.getOrDefault(myPlayerId, false);
                
                try {
                    synchronized (out) {
                        out.writeObject(new PlayerReadyMessage(myPlayerId, !currentReady));
                        out.flush();
                        out.reset();
                    }
                    System.out.println("[CLIENT] Toggled ready status");
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to toggle ready: " + e.getMessage());
                }
            }
        }
        
        // 開始遊戲按鈕（房主）
        if (myPlayerId.equals(currentRoomInfo.hostId) && currentRoomInfo.state == RoomState.WAITING) {
            if (x >= SCREEN_WIDTH / 2 - 150 && x <= SCREEN_WIDTH / 2 + 150 &&
                y >= 700 && y <= 770) {
                
                try {
                    synchronized (out) {
                        out.writeObject(new StartGameRequest());
                        out.flush();
                        out.reset();
                    }
                    System.out.println("[CLIENT] Sent start game request");
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to start game: " + e.getMessage());
                }
            }
        }
        
        // 離開房間按鈕
        if (x >= 50 && x <= 250 && y >= SCREEN_HEIGHT - 100 && y <= SCREEN_HEIGHT - 50) {
            try {
                synchronized (out) {
                    out.writeObject(new LeaveRoomRequest());
                    out.flush();
                    out.reset();
                }
                currentRoomInfo = null;
                createMainMenu();
                System.out.println("[CLIENT] Left room");
            } catch (Exception e) {
                System.err.println("[CLIENT ERROR] Failed to leave room: " + e.getMessage());
            }
        }
    }

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
        FXGL.getGameScene().setBackgroundColor(Color.rgb(30, 30, 40));
        createFixedPlatforms();
        createMiddlePlatform();
        player = FXGL.entityBuilder()
                .at(100, 900)
                .view(new Circle(25, myColor))
                .with(new PlayerControl(platformEntities))
                .buildAndAttach();
        player.setVisible(false);
        createGameZones();
        createUI();
        connectToServer();
        startNetworkThread();
        startPositionSender();
        startPlacementPreviewSender();
        createMainMenu();
    }
     private void createMiddlePlatform() {
    // 中間固定平台
    double midX = SCREEN_WIDTH / 2 - 150;
    double midY = SCREEN_HEIGHT / 2;
    
    middlePlatform = createPlatform(midX, midY, 300, 30, Color.rgb(100, 100, 100));
    middlePlatform.setVisible(false);
}

private void createGameZones() {
    // 不再生成隨機死亡區和安全區
    // 所有區域都由地圖編輯器創建
    zonesCreated = true;
    System.out.println("[CLIENT] Game zones initialization complete (no random zones)");
}   

    private void createFixedPlatforms() {
    startPlatform = createPlatform(50, SCREEN_HEIGHT - 150, 200, 30, Color.GREEN);
    endPlatform = createPlatform(FINISH_X - 200, SCREEN_HEIGHT - 150, 200, 30, Color.GOLD); 
    
    Text startLabel = new Text("START");
    startLabel.setFont(Font.font(20));
    startLabel.setFill(Color.WHITE);
    Entity startLabelEntity = FXGL.entityBuilder()
            .at(110, SCREEN_HEIGHT - 130)
            .view(startLabel)
            .buildAndAttach();
    platformEntities.add(startLabelEntity);  // 加入列表以便管理
    
    Text endLabel = new Text("END");
    endLabel.setFont(Font.font(20));
    endLabel.setFill(Color.WHITE);
    Entity endLabelEntity = FXGL.entityBuilder()
            .at(SCREEN_WIDTH - 200, SCREEN_HEIGHT - 130)
            .view(endLabel)
            .buildAndAttach();
    platformEntities.add(endLabelEntity);  // 加入列表以便管理
    
    // 初始時隱藏
    startPlatform.setVisible(false);
    endPlatform.setVisible(false);
    startLabelEntity.setVisible(false);
    endLabelEntity.setVisible(false);
}
    
    private void createUI() {
        phaseText = new Text("Phase: SELECTING");
        phaseText.setFont(Font.font(30));
        phaseText.setFill(Color.WHITE);
        FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 150, 50)
                .view(phaseText)
                .buildAndAttach();
        
        timerText = new Text("");
        timerText.setFont(Font.font(25));
        timerText.setFill(Color.YELLOW);
        FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 50, 90)
                .view(timerText)
                .buildAndAttach();
        
        scoreText = new Text("Score: 0");
        scoreText.setFont(Font.font(20));
        scoreText.setFill(Color.CYAN);
        FXGL.entityBuilder()
                .at(50, 50)
                .view(scoreText)
                .buildAndAttach();
    }
    
    private void displayObjectSelection() {
        System.out.println("[CLIENT] Displaying object selection: " + availableObjects.size() + " objects");
        
        for (Entity btn : objectButtons) {
            btn.removeFromWorld();
        }
        objectButtons.clear();
        
        int startY = 200;
        int spacing = 150;
        
        for (int i = 0; i < availableObjects.size(); i++) {
            GameObjectInfo obj = availableObjects.get(i);
            int yPos = startY + i * spacing;
            
            Rectangle btnBg = new Rectangle(400, 100, Color.rgb(80, 80, 100));
            btnBg.setStroke(Color.WHITE);
            btnBg.setStrokeWidth(2);
            Entity btnEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 200, yPos)
                    .view(btnBg)
                    .buildAndAttach();
            objectButtons.add(btnEntity);
            
            // 如果是砲臺,添加游層效果
            if (obj.type == ObjectType.TURRET) {
                javafx.scene.shape.Rectangle turretBody = new javafx.scene.shape.Rectangle(obj.width, obj.height);
                javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.YELLOW),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.RED)
                );
                turretBody.setFill(gradient);
                turretBody.setStroke(javafx.scene.paint.Color.ORANGE);
                turretBody.setStrokeWidth(2);
                
                Entity objPreview = FXGL.entityBuilder()
                        .at(SCREEN_WIDTH / 2 - obj.width / 2, yPos + 50 - obj.height / 2)
                        .view(turretBody)
                        .buildAndAttach();
                objectButtons.add(objPreview);
            } else {
                Rectangle objRect = new Rectangle(obj.width, obj.height, Color.web(obj.color));
                objRect.setStroke(Color.YELLOW);
                objRect.setStrokeWidth(1);
                Entity objPreview = FXGL.entityBuilder()
                        .at(SCREEN_WIDTH / 2 - obj.width / 2, yPos + 50 - obj.height / 2)
                        .view(objRect)
                        .buildAndAttach();
                objectButtons.add(objPreview);
            }
            
                Text numLabel = new Text("Platform " + (i + 1) + " (" + obj.type + ")");
            numLabel.setFont(Font.font(20));
            numLabel.setFill(Color.WHITE);
            Entity numEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 200 + 10, yPos + 25)
                    .view(numLabel)
                    .buildAndAttach();
            objectButtons.add(numEntity);
            
            Text sizeLabel = new Text(obj.width + " x " + obj.height);
            sizeLabel.setFont(Font.font(18));
            sizeLabel.setFill(Color.LIGHTGREEN);
            Entity sizeEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 200 + 10, yPos + 80)
                    .view(sizeLabel)
                    .buildAndAttach();
            objectButtons.add(sizeEntity);
                // 用途描述
                String desc = getObjectDescription(obj.type);
                Text descText = new Text(desc);
                descText.setFont(Font.font(16));
                descText.setFill(Color.LIGHTGRAY);
                Entity descEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 + 10, yPos + 25)
                    .view(descText)
                    .buildAndAttach();
                objectButtons.add(descEntity);
        }
    }

            private String getObjectDescription(ObjectType type) {
            return switch(type) {
                case NORMAL -> "普通平台：可站立通行";
                case DEATH -> "死亡平台：站上去立即死亡";
                case ERASER -> "橡皮擦：清除範圍內的平台";
                case MOVING_H -> "水平移動：左右往返，需要掌握節奏";
                case MOVING_V -> "垂直移動：上下往返，注意時機";
                case BOUNCE -> "彈跳：踩上彈射提高高度";
                case TURRET -> "砲塔：定期射出子彈";
                default -> "普通平台";
            };
            }
    
    private void clearObjectSelection() {
        System.out.println("[CLIENT] Clearing object selection UI");
        for (Entity btn : objectButtons) {
            btn.removeFromWorld();
        }
        objectButtons.clear();
    }
    
    private Entity finishButtonText = null;
    private void showFinishButton() {
        System.out.println("[CLIENT] Showing finish button");
        if (finishButton != null) {
            finishButton.removeFromWorld();
        }
        if (finishButtonText != null) {
            finishButtonText.removeFromWorld();
        }
        
        Rectangle btnBg = new Rectangle(200, 60, Color.rgb(50, 200, 50));
        btnBg.setStroke(Color.WHITE);
        btnBg.setStrokeWidth(3);
        
        Text btnText = new Text("FINISH");
        btnText.setFont(Font.font(24));
        btnText.setFill(Color.WHITE);
        
        finishButton = FXGL.entityBuilder()
                .at(SCREEN_WIDTH - 250, SCREEN_HEIGHT - 100)
                .view(btnBg)
                .buildAndAttach();
        
        finishButtonText = FXGL.entityBuilder()
                .at(SCREEN_WIDTH - 200, SCREEN_HEIGHT - 62)
                .view(btnText)
                .buildAndAttach();
    }
    
    private void hideFinishButton() {
        System.out.println("[CLIENT] Hiding finish button");
        if (finishButton != null) {
            finishButton.removeFromWorld();
            finishButton = null;
        }
        if (finishButtonText != null) {
            finishButtonText.removeFromWorld();
            finishButtonText = null;
        }
    }
    
private void showLeaderboard(Map<String, Integer> roundScores, Map<String, Integer> totalScores, List<String> finishOrder) {
    System.out.println("[CLIENT] Showing leaderboard with " + finishOrder.size() + " players, currentRound=" + 
                      (currentRoomInfo != null ? currentRoomInfo.currentRound : "null"));
    
    hideLeaderboard();
    // 顯示排行榜時相機回到最左側
    cameraOffsetX = 0;
    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
    
    // 隱藏遊戲UI
    phaseText.setVisible(false);
    timerText.setVisible(false);
    scoreText.setVisible(false);
    
    // 背景
    Rectangle bg = new Rectangle(700, 600, Color.rgb(40, 40, 50, 0.95));
    bg.setStroke(Color.GOLD);
    bg.setStrokeWidth(4);
    
    Entity bgEntity = FXGL.entityBuilder()
            .at(SCREEN_WIDTH / 2 - 350, SCREEN_HEIGHT / 2 - 300)
            .view(bg)
            .buildAndAttach();
    leaderboardEntities.add(bgEntity);
    
    // 顯示回合資訊
    String roundInfo = "ROUND " + (currentRoomInfo != null ? currentRoomInfo.currentRound : "?") + 
                      " / " + (currentRoomInfo != null ? currentRoomInfo.totalRounds : "5") + 
                      " COMPLETE!";
    
    System.out.println("[CLIENT] Leaderboard title: " + roundInfo);
    
    Text title = new Text(roundInfo);
    title.setFont(Font.font(36));
    title.setFill(Color.GOLD);
    Entity titleEntity = FXGL.entityBuilder()
            .at(SCREEN_WIDTH / 2 - 200, SCREEN_HEIGHT / 2 - 240)
            .view(title)
            .buildAndAttach();
    leaderboardEntities.add(titleEntity);
    
    // 按總分排序玩家
    List<Map.Entry<String, Integer>> sortedPlayers = new ArrayList<>();
    for (String playerId : finishOrder) {
        sortedPlayers.add(new AbstractMap.SimpleEntry<>(playerId, totalScores.getOrDefault(playerId, 0)));
    }
    sortedPlayers.sort((a, b) -> b.getValue().compareTo(a.getValue())); // 從高到低排序
    
    // 顯示排序後的玩家
    int yOffset = -140;
    for (int i = 0; i < sortedPlayers.size(); i++) {
        String playerId = sortedPlayers.get(i).getKey();
        int roundScore = roundScores.getOrDefault(playerId, 0);
        int totalScore = totalScores.getOrDefault(playerId, 0);
        
        String rank = (i + 1) + ". ";
        String scoreInfo = playerId + " - Round: +" + roundScore + " | Total: " + totalScore;
        
        Text scoreText = new Text(rank + scoreInfo);
        scoreText.setFont(Font.font(22));
        
        // 只有自己是黃色，其他都是白色
        if (playerId.equals(myPlayerId)) {
            scoreText.setFill(Color.YELLOW);
            scoreText.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 24));
        } else {
            scoreText.setFill(Color.WHITE);
        }
        
        Entity scoreEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 300, SCREEN_HEIGHT / 2 + yOffset)
                .view(scoreText)
                .buildAndAttach();
        leaderboardEntities.add(scoreEntity);
        
        yOffset += 45;
    }
    
    // 提示文字
    boolean isLastRound = currentRoomInfo != null && 
                         currentRoomInfo.currentRound >= currentRoomInfo.totalRounds;
    String hintMsg = isLastRound ? "Returning to room..." : "Next round starting soon...";
    
    Text hint = new Text(hintMsg);
    hint.setFont(Font.font(20));
    hint.setFill(Color.LIGHTGRAY);
    Entity hintEntity = FXGL.entityBuilder()
            .at(SCREEN_WIDTH / 2 - 150, SCREEN_HEIGHT / 2 + 230)
            .view(hint)
            .buildAndAttach();
    leaderboardEntities.add(hintEntity);
    
    System.out.println("[CLIENT] Leaderboard created with " + leaderboardEntities.size() + " entities");
}
private void hideLeaderboard() {
    System.out.println("[CLIENT] Hiding leaderboard, entities: " + leaderboardEntities.size());
    
    for (Entity entity : leaderboardEntities) {
        if (entity != null && entity.isActive()) {  // 加上檢查！
            entity.removeFromWorld();
        }
    }
    leaderboardEntities.clear();
    
    System.out.println("[CLIENT] Leaderboard cleared");
}
    
    private void connectToServer() {
        try {
            System.out.println("[CLIENT] Connecting to server...");
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
                System.out.println("[CLIENT] Connected as " + myPlayerId + " with color " + initMsg.colorHex);
                
                if (player != null && player.getViewComponent() != null && 
                    !player.getViewComponent().getChildren().isEmpty()) {
                    Circle circle = (Circle) player.getViewComponent().getChildren().get(0);
                    circle.setFill(myColor);
                }
            }
        } catch (Exception e) {
            System.err.println("[CLIENT ERROR] Failed to connect: " + e.getMessage());
            e.printStackTrace();
            connected = false;
        }
    }
    
    private void startNetworkThread() {
        new Thread(() -> {
            try {
                while (running && connected) {
                    Object obj = in.readObject();
                    
                    if (obj instanceof PhaseChangeMessage phaseMsg) {
                        System.out.println("[CLIENT] Received phase change: " + phaseMsg.phase);
                        javafx.application.Platform.runLater(() -> {
                            handlePhaseChange(phaseMsg.phase);
                        });
                    }
                    else if (obj instanceof RandomPlatformsMessage mapMsg) {
                        System.out.println("[CLIENT] Received map platforms: " + mapMsg.randomPlatforms.size());
                        javafx.application.Platform.runLater(() -> {
                            // 游戲重新開始時重置地圖加載狀態
                            if (mapPlatformsLoaded) {
                                System.out.println("[CLIENT] Game restarting, clearing old map platforms");
                                // 清除舊地圖平台
                                List<Entity> toRemove = new ArrayList<>();
                                for (Entity platform : platformEntities) {
                                    // 保留起點、終點、地板和標籤
                                    if (platform != startPlatform && platform != endPlatform && 
                                        platform != groundPlatform && platform != middlePlatform &&
                                        platformEntities.indexOf(platform) > 3) {
                                        toRemove.add(platform);
                                    }
                                }
                                for (Entity e : toRemove) {
                                    e.removeFromWorld();
                                    platformEntities.remove(e);
                                }
                                mapPlatformsLoaded = false;
                                System.out.println("[CLIENT] Old map cleared, ready for new map");
                            }
                            handleMapPlatforms(mapMsg.randomPlatforms);
                        });
                    }
                    else if (obj instanceof ObjectListMessage objListMsg) {
                        System.out.println("[CLIENT] Received object list: " + objListMsg.objects.size() + " objects");
                        javafx.application.Platform.runLater(() -> {
                            availableObjects = objListMsg.objects;
                            availableObjMap.clear();
                            for (GameObjectInfo gi : availableObjects) {
                                availableObjMap.put(gi.id, gi);
                            }
                            if (currentPhase == GamePhase.SELECTING) {
                                displayObjectSelection();
                            }
                        });
                    }
                    else if (obj instanceof PlacementMessage placementMsg) {
                        System.out.println("[CLIENT] Received placement from " + placementMsg.playerId + 
                                         " confirmed=" + placementMsg.confirmed);
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
                        System.out.println("[CLIENT] Player disconnected: " + disconnectMsg.playerId);
                        javafx.application.Platform.runLater(() -> {
                            removeOtherPlayer(disconnectMsg.playerId);
                        });
                    }
                    else if (obj instanceof ScoreUpdateMessage scoreMsg) {
                        javafx.application.Platform.runLater(() -> {
                            // 如果是新遊戲，清除舊分數
                            if (scoreMsg.scores.isEmpty() || 
                                (currentRoomInfo != null && currentRoomInfo.currentRound == 1)) {
                                System.out.println("[CLIENT] Resetting scores for new game");
                                playerScores.clear();
                            }
                            playerScores.putAll(scoreMsg.scores);
                            updateScoreDisplay();
                        });
                    }
                 
                           else if (obj instanceof RoundEndMessage roundEndMsg) {
    System.out.println("[CLIENT] Round ended! Round: " + roundEndMsg.currentRound);
    javafx.application.Platform.runLater(() -> {
        // 更新房間資訊的回合數
        if (currentRoomInfo != null) {
            currentRoomInfo.currentRound = roundEndMsg.currentRound;
            currentRoomInfo.totalRounds = roundEndMsg.totalRounds;
        }
        
        // 相機回到最左邊
        cameraOffsetX = 0;
        FXGL.getGameScene().getViewport().setX(0);
        
        showLeaderboard(roundEndMsg.roundScores, roundEndMsg.totalScores, 
                       roundEndMsg.finishOrder);
    });
    
}      
                    else if (obj instanceof CreateRoomResponse createResp) {
                        javafx.application.Platform.runLater(() -> {
                            if (createResp.success) {
                                System.out.println("[CLIENT] Room created: " + createResp.roomCode);
                                // 等待 RoomUpdateMessage
                            } else {
                                System.err.println("[CLIENT] Failed to create room: " + createResp.message);
                                // 可以顯示錯誤訊息
                            }
                        });
                    }
                    else if (obj instanceof JoinRoomResponse joinResp) {
                        javafx.application.Platform.runLater(() -> {
                            if (joinResp.success) {
                                System.out.println("[CLIENT] Joined room successfully");
                                currentRoomInfo = joinResp.roomInfo;
                                createRoomUI();
                            } else {
                                System.err.println("[CLIENT] Failed to join room: " + joinResp.message);
                                // 可以顯示錯誤訊息
                            }
                        });
                    }
                    else if (obj instanceof RoomUpdateMessage roomUpdate) {
                        javafx.application.Platform.runLater(() -> {
                            System.out.println("[CLIENT] Room updated");
                            currentRoomInfo = roomUpdate.roomInfo;
                            
                            // 如果在選單狀態，切換到房間UI
                            if (uiState == UIState.MENU) {
                                createRoomUI();
                            }
                            // 如果在房間狀態，更新房間UI
                            else if (uiState == UIState.IN_ROOM) {
                                createRoomUI();
                            }
                        });
                    }
                    else if (obj instanceof ReturnToRoomMessage returnMsg) {
                        javafx.application.Platform.runLater(() -> {
                            System.out.println("[CLIENT] Returning to room: " + returnMsg.message);
                            
                            // 清理遊戲狀態
                            player.setVisible(false);
                            hasFinished = false;
                            hasFailed = false;
                            deathRecoveryTimer = 0;  // 重置死亡恢復計時器
                            hideLeaderboard();
                            clearObjectSelection();
                            hideFinishButton();
                            
                            // 清除所有平台(包括起點和終點)
                            for (Entity platform : platformEntities) {
                                platform.removeFromWorld();
                            }
                            platformEntities.clear();
                            
                            // 清除所有死亡區
                            for (Entity zone : deathZones) {
                                zone.removeFromWorld();
                            }
                            deathZones.clear();
                            
                            // 重置地圖加載狀態,以便下一回合重新加載地圖平台
                            mapPlatformsLoaded = false;
                            System.out.println("[CLIENT] Reset mapPlatformsLoaded to allow reloading in next round");
                            
                            // 清除所有子彈
                            FXGL.getGameWorld().getEntitiesByComponent(BulletComponent.class).forEach(Entity::removeFromWorld);
                            
                            // 重新創建起點和終點平台
                            startPlatform = createPlatform(50, SCREEN_HEIGHT - 150, 200, 30, Color.GREEN);
                            endPlatform = createPlatform(FINISH_X - 200, SCREEN_HEIGHT - 150, 200, 30, Color.GOLD);
                            startPlatform.setVisible(false);
                            endPlatform.setVisible(false);
                            platformEntities.add(startPlatform);
                            platformEntities.add(endPlatform);
                            
                            // 清除其他玩家
                            for (Entity otherPlayer : otherPlayers.values()) {
                                otherPlayer.removeFromWorld();
                            }
                            otherPlayers.clear();
                            
                            // 返回房間UI
                            createRoomUI();
                            
                            // 顯示訊息
                            Text msgText = new Text(returnMsg.message);
                            msgText.setFont(Font.font(24));
                            msgText.setFill(Color.YELLOW);
                            Entity msgEntity = FXGL.entityBuilder()
                                    .at(SCREEN_WIDTH / 2 - 300, SCREEN_HEIGHT / 2)
                                    .view(msgText)
                                    .buildAndAttach();
                            roomUIEntities.add(msgEntity);
                        });
                    }
                                
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[CLIENT ERROR] Network error: " + e.getMessage());
                    e.printStackTrace();
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
                    // 只在遊戲中且玩家可見時發送位置
                    if (connected && uiState == UIState.PLAYING && 
                        player != null && player.isVisible() ) {
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
                        System.err.println("[CLIENT ERROR] Position sender error: " + e.getMessage());
                        connected = false;
                    }
                    break;
                }
            }
        }).start();
    }
    
    private void startPlacementPreviewSender() {
        new Thread(() -> {
            while (running) {
                try {
                    if (connected && uiState == UIState.PLAYING && 
                        previewPlatform != null && myPlacement == null && selectedObj != null) {
                        
                        synchronized (out) {
                            PlatformPlacement preview = new PlatformPlacement(
                                selectedObj.id,
                                previewPlatform.getX(),
                                previewPlatform.getY(),
                                selectedObj.width,
                                selectedObj.height,
                                selectedObj.color,
                                currentRotation
                            );
                            out.writeObject(new PlacementMessage(myPlayerId, preview, false));
                            out.flush();
                            out.reset();
                        }
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    if (running) {
                        System.err.println("[CLIENT ERROR] Preview sender error: " + e.getMessage());
                        connected = false;
                    }
                    break;
                }
            }
        }).start();
    }
    
private void handlePhaseChange(GamePhase newPhase) {
    System.out.println("[CLIENT] Phase changed to: " + newPhase);
    currentPhase = newPhase;
    
    // 切換到遊戲UI狀態
    if (uiState != UIState.PLAYING) {
        uiState = UIState.PLAYING;
        clearAllUI();
    }
    
    switch (newPhase) {
        case SELECTING:
            hideLeaderboard();
            phaseText.setText("Phase: SELECT YOUR PLATFORM");
            phaseText.setVisible(true);
            player.setVisible(false);
            // 進入選擇階段重置鏡頭
            cameraOffsetX = 0;
            FXGL.getGameScene().getViewport().setX(0);
            // 隱藏起點和終點平台
            startPlatform.setVisible(false);
            endPlatform.setVisible(false);
            if (platformEntities.size() > 2) {
                platformEntities.get(2).setVisible(false); // START label
                platformEntities.get(3).setVisible(false); // FINISH label
            }
      
            selectedObjectId = null;
            selectedObj = null;
            myPlacement = null;
            currentRotation = 0;
            isDragging = false;
            hasFinished = false;
            hasFailed = false;
            hideFinishButton();
            
            if (previewPlatform != null) {
                previewPlatform.removeFromWorld();
                previewPlatform = null;
            }
            
            for (Entity preview : otherPreviewEntities.values()) {
                preview.removeFromWorld();
            }
            otherPreviewEntities.clear();
            otherPreviewPlacements.clear();
            
            // 清除其他玩家實體
            for (Entity otherPlayer : otherPlayers.values()) {
                otherPlayer.removeFromWorld();
            }
            otherPlayers.clear();
            

            
            if (!availableObjects.isEmpty()) {
                displayObjectSelection();
            }
            break;
            
        case PLACING:
            phaseText.setText("Phase: DRAG & ROTATE (Q/E 90°), THEN FINISH");
            phaseText.setVisible(true);
            clearObjectSelection();
            if (previewPlatform != null && myPlacement == null) {
                showFinishButton();
                System.out.println("[CLIENT] Can now drag and rotate platform");
            } else {
                System.out.println("[CLIENT ERROR] No preview platform in PLACING phase!");
            }
            break;
            
        case PLAYING:
            phaseText.setText("Phase: RACE TO FINISH!");
            phaseText.setVisible(true);
            timerText.setVisible(true);
            scoreText.setVisible(false);
            hideFinishButton();
            isDragging = false;

            startPlatform.setVisible(true);
            endPlatform.setVisible(true);
            middlePlatform.setVisible(true);
            if (platformEntities.size() > 2) {
                platformEntities.get(2).setVisible(true); // START label
                platformEntities.get(3).setVisible(true); // FINISH label
            }
            
            // 不顯示隨機 death zones 和 safe zones，它們已經不存在
            // 所有障礙物都來自地圖編輯器
            
            if (previewPlatform != null) {
                previewPlatform.removeFromWorld();
                previewPlatform = null;
            }
            
            for (Entity preview : otherPreviewEntities.values()) {
                preview.removeFromWorld();
            }
            otherPreviewEntities.clear();
            
            // 創建所有玩家本回合放置的物件（依照物件類型建立行為）
            System.out.println("[CLIENT] ========== PLAYING Phase Objects ==========");
            System.out.println("[CLIENT] Total map platforms loaded: " + (mapPlatformsLoaded ? "Yes" : "No"));
            System.out.println("[CLIENT] Current platformEntities count: " + platformEntities.size());
            System.out.println("[CLIENT] Player placed objects to create:");
            System.out.println("[CLIENT]   - My placement: " + (myPlacement != null));
            System.out.println("[CLIENT]   - Other placements: " + otherPlacements.size());
            
            for (Map.Entry<String, PlatformPlacement> entry : otherPlacements.entrySet()) {
                PlatformPlacement p = entry.getValue();
                System.out.println("[CLIENT] Creating OTHER player object: player=" + entry.getKey() + 
                                 ", id=" + p.id + ", at (" + p.x + "," + p.y + "), rotation=" + p.rotation);
                createPlacedObject(p);
            }
            if (myPlacement != null) {
                System.out.println("[CLIENT] Creating MY object: id=" + myPlacement.id + 
                                 ", at (" + myPlacement.x + "," + myPlacement.y + "), rotation=" + myPlacement.rotation);
                createPlacedObject(myPlacement);
            }
            
            System.out.println("[CLIENT] After creating placed objects, platformEntities count: " + platformEntities.size());
            System.out.println("[CLIENT] =============================================");
            
            otherPlacements.clear();
            
            player.setVisible(true);
            player.setPosition(100, 900);
            player.getComponent(PlayerControl.class).reset();
            gameStartTime = System.currentTimeMillis();
            break;
    }
}    
    private void handlePlacement(PlacementMessage msg) {
        if (msg.confirmed) {
            System.out.println("[CLIENT] Confirmed placement from " + msg.playerId);
            otherPlacements.put(msg.playerId, msg.placement);
            
            Entity preview = otherPreviewEntities.remove(msg.playerId);
            if (preview != null) {
                javafx.scene.shape.Rectangle rect = (javafx.scene.shape.Rectangle) preview.getViewComponent().getChildren().get(0);
                rect.setOpacity(0.3);  // 更透明
                rect.setStroke(Color.GREEN);  // 綠色邊框
                rect.setStrokeWidth(3);
            }
            otherPreviewPlacements.remove(msg.playerId);
        } else {
            otherPreviewPlacements.put(msg.playerId, msg.placement);
            updateOtherPreview(msg.playerId, msg.placement);
        }
    }
    
    private void handleMapPlatforms(List<PlatformPlacement> mapPlatforms) {
        // 只在遊戲開始時加載地圖平台一次
        if (mapPlatformsLoaded) {
            System.out.println("[CLIENT] Map platforms already loaded, skipping");
            return;
        }
        
        System.out.println("[CLIENT] Loading map with " + mapPlatforms.size() + " platforms");
        for (PlatformPlacement placement : mapPlatforms) {
            createPlacedObject(placement);
        }
        mapPlatformsLoaded = true;
        System.out.println("[CLIENT] Map platforms loaded successfully");
    }
    
    private void updateOtherPreview(String playerId, PlatformPlacement placement) {
        Entity preview = otherPreviewEntities.get(playerId);
        
        if (preview == null) {
            Rectangle rect = new Rectangle(placement.width, placement.height, Color.web(placement.color));
            rect.setOpacity(0.5);
            rect.setStroke(Color.CYAN);
            rect.setStrokeWidth(2);
            
            preview = FXGL.entityBuilder()
                    .at(placement.x, placement.y)
                    .view(rect)
                    .buildAndAttach();
            
            otherPreviewEntities.put(playerId, preview);
        }
        
        preview.setPosition(placement.x, placement.y);
        preview.setRotation(placement.rotation);
    }
    
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
            System.out.println("[CLIENT] Created other player: " + info.playerId);
        } else if (otherPlayer != null) {
            SmoothPlayerComponent smoothComponent = otherPlayer.getComponent(SmoothPlayerComponent.class);
            smoothComponent.setTargetPosition(info.x, info.y);
            smoothComponent.setTargetScaleY(info.scaleY);
        }
    }
    
    private void removeOtherPlayer(String playerId) {
        Entity player = otherPlayers.remove(playerId);
        if (player != null) {
            player.removeFromWorld();
            System.out.println("[CLIENT] Removed other player: " + playerId);
        }
    }
    
    private void updateScoreDisplay() {
        Integer myScore = playerScores.getOrDefault(myPlayerId, 0);
        scoreText.setText("Score: " + myScore);
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
    
    private Entity createPlatformWithRotation(double x, double y, double width, double height, Color color, double rotation) {
        Rectangle rect = new Rectangle(width, height, color);
        Entity platform = FXGL.entityBuilder()
                .at(x, y)
                .view(rect)
                .with(new PlatformComponent(width, height))
                .buildAndAttach();
        platform.setRotation(rotation);
        platformEntities.add(platform);
        return platform;
    }

    private Entity createPlacedObject(PlatformPlacement p) {
        GameObjectInfo info = availableObjMap.getOrDefault(p.id, null);
        Color color = Color.web(p.color);
        Rectangle rect = new Rectangle(p.width, p.height, color);
        Entity e;
        
        // 詳細日誌：顯示正在創建的物件
        System.out.println("[CLIENT] Creating object: id=" + p.id + ", color=" + p.color + 
                         ", pos=(" + p.x + "," + p.y + "), size=" + p.width + "x" + p.height + 
                         ", hasInfo=" + (info != null) + (info != null ? ", type=" + info.type : ""));
        
        // 如果沒有 info，說明這是地圖平台，根據顏色決定類型
        if (info == null) {
            System.out.println("[CLIENT] No info found, treating as map platform with color: " + p.color);
            
            // 根據顏色判斷平台類型
            if (p.color.equalsIgnoreCase("#FF0000") || p.color.equalsIgnoreCase("FF0000")) {
                // 紅色 = 死亡區
                System.out.println("[CLIENT] Creating DEATH platform from map");
                rect.setFill(Color.RED);  // 確保紅色
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new DeathZoneComponent(p.width, p.height))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                deathZones.add(e);
                platformEntities.add(e);
                System.out.println("[CLIENT] Added death zone to list, total: " + deathZones.size());
                return e;
            } else if (p.color.equalsIgnoreCase("#00FF00")) {
                // 綠色 = 彈跳
                System.out.println("[CLIENT] Creating BOUNCE platform from map");
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new BouncePlatformComponent())
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            } else if (p.color.equalsIgnoreCase("#00AAFF")) {
                // 藍色 = 水平移動
                System.out.println("[CLIENT] Creating MOVING_H platform from map");
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(true, 1.0, 100))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            } else if (p.color.equalsIgnoreCase("#AA00FF")) {
                // 紫色 = 垂直移動
                System.out.println("[CLIENT] Creating MOVING_V platform from map");
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(false, 1.0, 100))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            } else {
                // 預設為普通平台
                System.out.println("[CLIENT] Creating NORMAL platform from map");
                return createPlatformWithRotation(p.x, p.y, p.width, p.height, color, p.rotation);
            }
        }
        
        // 有 info，說明是玩家選擇的物件
        System.out.println("[CLIENT] Creating player-selected object of type: " + info.type);
        switch (info.type) {
            case MOVING_H: {
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(true, Math.max(0.5, info.moveSpeed), Math.max(50, info.moveRange)))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case MOVING_V: {
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(false, Math.max(0.5, info.moveSpeed), Math.max(50, info.moveRange)))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case BOUNCE: {
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new BouncePlatformComponent())
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case TURRET: {
                // Turret 可供玩家站立：同時具有 PlatformComponent 與 TurretComponent
                javafx.scene.shape.Rectangle turretBody = new javafx.scene.shape.Rectangle(p.width, p.height);
                javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.YELLOW),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.RED)
                );
                turretBody.setFill(gradient);
                turretBody.setStroke(javafx.scene.paint.Color.ORANGE);
                turretBody.setStrokeWidth(2);
                
                e = FXGL.entityBuilder()
                    .at(p.x, p.y)
                    .view(turretBody)
                    .with(new PlatformComponent(p.width, p.height))
                    .with(new TurretComponent(Math.max(0.8, info.fireRate)))
                    .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case DEATH: {
                // 死亡區也要可以踩，同時具有死亡檢測
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(rect)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new DeathZoneComponent(p.width, p.height))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                deathZones.add(e);
                platformEntities.add(e);  // 加入平台列表，可以踩
                return e;
            }
            case ERASER: {
                // ERASER 不顯示方塊，清除範圍內的所有平台和死亡區
                double eraserLeft = p.x;
                double eraserRight = p.x + p.width;
                double eraserTop = p.y;
                double eraserBottom = p.y + p.height;
                
                System.out.println("[CLIENT] ERASER at (" + p.x + "," + p.y + ") size " + p.width + "x" + p.height);
                
                List<Entity> toRemove = new ArrayList<>();
                
                // 檢查platformEntities中與橡皮擦重疊的平台(碰到邊緣就算)
                for (Entity platform : platformEntities) {
                    double platformLeft = platform.getX();
                    double platformRight = platform.getX() + platform.getWidth();
                    double platformTop = platform.getY();
                    double platformBottom = platform.getY() + platform.getHeight();
                    
                    // AABB 碰撞檢測 - 只要有任何重疊就清除
                    boolean overlaps = !(eraserRight < platformLeft || 
                                        eraserLeft > platformRight || 
                                        eraserBottom < platformTop || 
                                        eraserTop > platformBottom);
                    
                    if (overlaps) {
                        toRemove.add(platform);
                        System.out.println("[CLIENT] ERASER marked platform at (" + platform.getX() + "," + platform.getY() + ") for removal");
                    }
                }
                
                // 移除所有標記的平台
                for (Entity entity : toRemove) {
                    entity.removeFromWorld();
                    platformEntities.remove(entity);
                    deathZones.remove(entity);  // 如果是死亡區也要移除
                }
                
                System.out.println("[CLIENT] ERASER cleared " + toRemove.size() + " platforms/zones");
                return null;
            }
            case NORMAL:
            default:
                return createPlatformWithRotation(p.x, p.y, p.width, p.height, color, p.rotation);
        }
    }
    
    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

    @Override
    protected void initInput() {
        FXGL.getInput().addAction(new UserAction("Move Left A") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveLeft();
                } else if (currentPhase == GamePhase.PLAYING && (hasFinished || hasFailed)) {
                    // 已完成/死亡的玩家可以移動攝影機觀戰
                    cameraOffsetX = Math.max(0, cameraOffsetX - 120);
                    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
                } else if ((currentPhase == GamePhase.PLACING || currentPhase == GamePhase.SELECTING) && myPlacement == null) {
                    cameraOffsetX = Math.max(0, cameraOffsetX - 120);
                    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
                }
            }
        }, KeyCode.A);
        FXGL.getInput().addAction(new UserAction("Submit Room Code") {
    @Override
    protected void onActionBegin() {
        if (uiState == UIState.MENU && roomCodeInput != null && roomCodeInput.isVisible()) {
            String code = roomCodeInput.getText().trim();
            
            if (code.length() == 4 && code.matches("\\d{4}")) {
                try {
                    synchronized (out) {
                        out.writeObject(new JoinRoomRequest(code));
                        out.flush();
                        out.reset();
                    }
                    System.out.println("[CLIENT] Sent join room request: " + code);
                    roomCodeInput.setVisible(false);
                    roomCodeInput.clear();
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to join room: " + e.getMessage());
                }
            } else {
                System.err.println("[CLIENT] Invalid room code: " + code);
            }
        }
    }
}, KeyCode.ENTER);
        FXGL.getInput().addAction(new UserAction("Move Left Arrow") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveLeft();
                }
            }
        }, KeyCode.LEFT);

        FXGL.getInput().addAction(new UserAction("Move Right D") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveRight();
                } else if (currentPhase == GamePhase.PLAYING && (hasFinished || hasFailed)) {
                    // 已完成/死亡的玩家可以移動攝影機觀戰
                    double maxOffset = Math.max(0, FINISH_X - SCREEN_WIDTH);
                    cameraOffsetX = Math.min(maxOffset, cameraOffsetX + 120);
                    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
                } else if ((currentPhase == GamePhase.PLACING || currentPhase == GamePhase.SELECTING) && myPlacement == null) {
                    double maxOffset = Math.max(0, FINISH_X - SCREEN_WIDTH);
                    cameraOffsetX = Math.min(maxOffset, cameraOffsetX + 120);
                    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
                }
            }
        }, KeyCode.D);
        
        FXGL.getInput().addAction(new UserAction("Move Right Arrow") {
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveRight();
                }
            }
        }, KeyCode.RIGHT);

        FXGL.getInput().addAction(new UserAction("Jump W") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).jump();
                }
            }
        }, KeyCode.W);
        
        FXGL.getInput().addAction(new UserAction("Jump Up") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).jump();
                }
            }
        }, KeyCode.UP);
        
        FXGL.getInput().addAction(new UserAction("Jump Space") {
            @Override
            protected void onActionBegin() {
                if (player.isVisible() && !hasFinished && !hasFailed) {
                    if (currentPhase == GamePhase.PLAYING) {
                        player.getComponent(PlayerControl.class).jump();
                    }
                }
            }
        }, KeyCode.SPACE);

        FXGL.getInput().addAction(new UserAction("Crouch S") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).crouch(true);
                }
            }
            @Override
            protected void onActionEnd() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).crouch(false);
                }
            }
        }, KeyCode.S);
        
        FXGL.getInput().addAction(new UserAction("Crouch Down") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).crouch(true);
                }
            }
            @Override
            protected void onActionEnd() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).crouch(false);
                }
            }
        }, KeyCode.DOWN);

        // 旋轉改為每次90度(砲臺和死亡平台可以旋轉)
        FXGL.getInput().addAction(new UserAction("Rotate Left") {
            @Override
            protected void onActionBegin() {
                if ((currentPhase == GamePhase.PLACING || currentPhase == GamePhase.SELECTING) && 
                    previewPlatform != null && myPlacement == null && 
                    selectedObj != null && (selectedObj.type == ObjectType.TURRET || selectedObj.type == ObjectType.DEATH)) {
                    currentRotation -= 90;
                    previewPlatform.getTransformComponent().setRotationOrigin(new Point2D(previewPlatform.getWidth() / 2.0, previewPlatform.getHeight() / 2.0));
                    previewPlatform.setRotation(currentRotation);
                    System.out.println("[CLIENT] Rotated left to " + currentRotation + "°");
                }
            }
        }, KeyCode.Q);
        
        FXGL.getInput().addAction(new UserAction("Rotate Right") {
            @Override
            protected void onActionBegin() {
                if ((currentPhase == GamePhase.PLACING || currentPhase == GamePhase.SELECTING) && 
                    previewPlatform != null && myPlacement == null && 
                    selectedObj != null && (selectedObj.type == ObjectType.TURRET || selectedObj.type == ObjectType.DEATH)) {
                    currentRotation += 90;
                    previewPlatform.getTransformComponent().setRotationOrigin(new Point2D(previewPlatform.getWidth() / 2.0, previewPlatform.getHeight() / 2.0));
                    previewPlatform.setRotation(currentRotation);
                    System.out.println("[CLIENT] Rotated right to " + currentRotation + "°");
                }
            }
        }, KeyCode.E);

        FXGL.getInput().addAction(new UserAction("Click") {
            @Override
            protected void onActionBegin() {
                Point2D mousePos = FXGL.getInput().getMousePositionWorld();

                if (uiState == UIState.MENU) {
                    handleMenuClick(mousePos);
                    return;
                } else if (uiState == UIState.IN_ROOM && currentRoomInfo != null && 
                        currentRoomInfo.state == RoomState.WAITING) {
                    handleRoomClick(mousePos);
                    return;
                }
                mousePos = FXGL.getInput().getMousePositionWorld();
                
                if (currentPhase == GamePhase.SELECTING && selectedObjectId == null) {
                    handleObjectSelection(mousePos);
                }
                else if ((currentPhase == GamePhase.PLACING || currentPhase == GamePhase.SELECTING) && myPlacement == null) {
                    // 檢查是否點擊 Finish 按鈕
                    if (finishButton != null && previewPlatform != null) {
                        double btnX = SCREEN_WIDTH - 250;
                        double btnY = SCREEN_HEIGHT - 100;
                        if (mousePos.getX() >= btnX && mousePos.getX() <= btnX + 200 &&
                            mousePos.getY() >= btnY && mousePos.getY() <= btnY + 60) {
                            System.out.println("[CLIENT] Finish button clicked!");
                            confirmPlacement();
                            return;
                        }
                    }
                    
                    // 開始拖曳預覽平台
                    if (previewPlatform != null) {
                        isDragging = true;
                        dragOffset = new Point2D(
                            mousePos.getX() - previewPlatform.getX(),
                            mousePos.getY() - previewPlatform.getY()
                        );
                        System.out.println("[CLIENT] Started dragging platform");
                    }
                }
            }
            
            @Override
            protected void onActionEnd() {
                if (isDragging) {
                    System.out.println("[CLIENT] Stopped dragging platform");
                    isDragging = false;
                }
            }
        }, MouseButton.PRIMARY);
        
        FXGL.getInput().addAction(new UserAction("Quit") {
            @Override
            protected void onActionBegin() {
                cleanup();
                FXGL.getGameController().exit();
            }
        }, KeyCode.ESCAPE);
    }
    
    private void handleObjectSelection(Point2D mousePos) {
        int startY = 200;
        int spacing = 150;
        
        for (int i = 0; i < availableObjects.size(); i++) {
            GameObjectInfo obj = availableObjects.get(i);
            int yPos = startY + i * spacing;
            
            double btnLeft = SCREEN_WIDTH / 2 - 200.0;
            double btnRight = SCREEN_WIDTH / 2 + 200.0;
            double btnTop = yPos;
            double btnBottom = yPos + 100;
            
            if (mousePos.getX() >= btnLeft && mousePos.getX() <= btnRight &&
                mousePos.getY() >= btnTop && mousePos.getY() <= btnBottom) {
                 // 檢查物件是否已被選
        if (obj.selected) {
            System.out.println("[CLIENT ERROR] Object " + obj.id + " already selected!");
            continue;  // 跳過已被選的物件
        }
                selectedObjectId = obj.id;
                selectedObj = obj;
                System.out.println("[CLIENT] Selected object " + obj.id);
                
                // 降低被選物件的透明度
                int btnIndex = i * 5;  // 每個物件有5個UI元素
                if (btnIndex < objectButtons.size()) {
                    for (int j = 0; j < 5 && (btnIndex + j) < objectButtons.size(); j++) {
                        Entity uiElement = objectButtons.get(btnIndex + j);
                        uiElement.getViewComponent().setOpacity(0.3);
                    }
                }
                
                try {
                    synchronized (out) {
                        out.writeObject(new SelectionMessage(myPlayerId, obj.id));
                        out.flush();
                        out.reset();
                    }
                    System.out.println("[CLIENT] Sent selection to server");
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to send selection: " + e.getMessage());
                    e.printStackTrace();
                }
                
                clearObjectSelection();
                createPreviewPlatform();

                // 立即允許本地玩家進入放置流程（即使其他人尚未完成選擇）
                currentPhase = GamePhase.PLACING;
                phaseText.setText("Phase: DRAG & ROTATE (Q/E 90°), THEN FINISH");
                showFinishButton();
                break;
            }
        }
    }
    
    private void createPreviewPlatform() {
        if (selectedObj != null && previewPlatform == null) {
            double x = SCREEN_WIDTH / 2 - selectedObj.width / 2.0;
            double y = SCREEN_HEIGHT / 2 - selectedObj.height / 2.0;
            
            // 如果是砲臺,添加游層效果
            if (selectedObj.type == ObjectType.TURRET) {
                javafx.scene.shape.Rectangle turretBody = new javafx.scene.shape.Rectangle(
                    selectedObj.width, selectedObj.height);
                javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.YELLOW),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.RED)
                );
                turretBody.setFill(gradient);
                turretBody.setStroke(javafx.scene.paint.Color.ORANGE);
                turretBody.setStrokeWidth(3);
                
                previewPlatform = FXGL.entityBuilder()
                        .at(x, y)
                        .view(turretBody)
                        .buildAndAttach();
            } else {
                Rectangle rect = new Rectangle(selectedObj.width, selectedObj.height, Color.web(selectedObj.color));
                rect.setStroke(Color.YELLOW);
                rect.setStrokeWidth(3);
                
                previewPlatform = FXGL.entityBuilder()
                        .at(x, y)
                        .view(rect)
                        .buildAndAttach();
            }
            
            currentRotation = 0;
            System.out.println("[CLIENT] Created preview platform");
        }
    }
    
    private void confirmPlacement() {
        if (selectedObj != null && previewPlatform != null && myPlacement == null) {
            myPlacement = new PlatformPlacement(
                selectedObj.id, 
                previewPlatform.getX(), 
                previewPlatform.getY(),
                selectedObj.width, 
                selectedObj.height,
                selectedObj.color,
                currentRotation
            );
            
            System.out.println("[CLIENT] Confirming placement at (" + myPlacement.x + "," + 
                             myPlacement.y + ") rotation=" + currentRotation);
            
            try {
                synchronized (out) {
                    out.writeObject(new PlacementMessage(myPlayerId, myPlacement, true));
                    out.flush();
                    out.reset();
                }
                System.out.println("[CLIENT] Sent confirmed placement to server");
            } catch (Exception e) {
                System.err.println("[CLIENT ERROR] Failed to send placement: " + e.getMessage());
                e.printStackTrace();
            }
            
            phaseText.setText("Waiting for other players...");
            hideFinishButton();
            isDragging = false;
            // 放置完成後鏡頭歸零
            cameraOffsetX = 0;
            FXGL.getGameScene().getViewport().setX(0);
        } else {
            System.err.println("[CLIENT ERROR] Cannot confirm: selectedObj=" + selectedObj + 
                             " previewPlatform=" + previewPlatform + " myPlacement=" + myPlacement);
        }
    }
    
    @Override
    protected void onUpdate(double tpf) {
        // 處理拖曳（允許在本地 PLACING 或 SELECTING 狀態拖曳）
        if (isDragging && previewPlatform != null && myPlacement == null) {
            Point2D mousePos = FXGL.getInput().getMousePositionWorld();
            previewPlatform.setPosition(
                mousePos.getX() - dragOffset.getX(),
                mousePos.getY() - dragOffset.getY()
            );
        }
        if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
            // 只在玩家還在遊戲中時，攝影機跟隨玩家
            double targetCameraX = player.getX() - SCREEN_WIDTH / 3.0;
            double maxOffset = Math.max(0, FINISH_X - SCREEN_WIDTH);
            cameraOffsetX = Math.max(0, Math.min(maxOffset, targetCameraX));
            FXGL.getGameScene().getViewport().setX(cameraOffsetX);
        }
        // 如果玩家已完成或死亡，保持當前攝影機位置，允許手動移動(A/D鍵)


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
        // ✅ 子彈碰撞檢測（修正版）
    if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
        // 檢查子彈碰撞
        List<Entity> allEntities = FXGL.getGameWorld().getEntities();
        for (Entity e : allEntities) {
            if (e.hasComponent(BulletComponent.class)) {
                BulletComponent bullet = e.getComponent(BulletComponent.class);
                if (bullet.checkHit(player.getX(), player.getY(), 25)) {
                    // 被子彈擊中 - 發送失敗訊息
                    try {
                        synchronized (out) {
                            out.writeObject(new FailMessage(myPlayerId));
                            out.flush();
                            out.reset();
                        }
                        hasFailed = true;
                        player.setVisible(false);
                        // 禁用玩家移動,但允許觀戰
                        player.getComponent(PlayerControl.class).setEnabled(false);
                        System.out.println("[CLIENT] Hit by bullet! Can spectate with A/D");
                    } catch (Exception ex) {
                        System.err.println("[CLIENT ERROR] Failed to send fail message: " + ex.getMessage());
                    }
                    break;
                }
            }
        }
    }
        // 檢查是否到達終點或死亡
    if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished) {
        double playerX = player.getX();
        double playerY = player.getY();
        
        // 處理死亡恢復計時器
        if (deathRecoveryTimer > 0) {
            deathRecoveryTimer -= tpf;
            if (deathRecoveryTimer <= 0) {
                // 恢復正常外觀,但不重置hasFailed(本回合已失敗)
                player.setScaleY(1.0);
                deathRecoveryTimer = 0;
                System.out.println("[CLIENT] Player recovered from death (visual only, still failed this round)!");
            }
        }
        
        // 檢查是否站在死亡平台上(只在未處於死亡恢復狀態時檢查)
        if (deathRecoveryTimer <= 0 && !hasFailed) {
            PlayerControl pc = player.getComponent(PlayerControl.class);
            boolean isStandingOnDeathPlatform = false;
            
            // 檢查玩家是否站在死亡平台上
            for (Entity zone : deathZones) {
                if (!zone.isVisible()) continue;
                
                // 計算玩家底部和平台頂部的距離
                double playerBottom = playerY + 25;  // 玩家半徑
                double platformTop = zone.getY();
                double platformLeft = zone.getX();
                double platformRight = zone.getX() + (zone.hasComponent(DeathZoneComponent.class) ? 
                                                     zone.getComponent(DeathZoneComponent.class).width : 0);
                
                // 檢查玩家是否在平台上方範圍內,且底部接近平台頂部
                boolean isOnTopOfPlatform = (playerX > platformLeft && playerX < platformRight &&
                                            Math.abs(playerBottom - platformTop) < 5 &&
                                            pc.velocityY >= 0);  // 向下移動或靜止
                
                if (isOnTopOfPlatform) {
                    isStandingOnDeathPlatform = true;
                    System.out.println("[CLIENT] Standing on death platform at (" + zone.getX() + "," + zone.getY() + ")!");
                    break;
                }
            }
            
            if (isStandingOnDeathPlatform) {
                try {
                    System.out.println("[CLIENT] Sending FailMessage to server for player: " + myPlayerId);
                    synchronized (out) {
                        out.writeObject(new FailMessage(myPlayerId));
                        out.flush();
                        out.reset();
                    }
                    hasFailed = true;
                    // 玩家變扁,並開始恢復計時
                    player.setScaleY(0.2);
                    deathRecoveryTimer = 2.0;  // 2秒後恢復
                    // 禁用玩家移動,但允許觀戰
                    player.getComponent(PlayerControl.class).setEnabled(false);
                    System.out.println("[CLIENT] Death triggered! Sent FailMessage, will recover visually in 2 seconds, can spectate with A/D");
                    System.out.println("[CLIENT] Current phase: " + currentPhase + ", connected: " + connected);
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to send fail message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // 檢查終點
        double endX = endPlatform.getX();
        double endY = endPlatform.getY();
        
        if (playerX >= endX && playerX <= endX + 200 &&
            playerY >= endY - 50 && playerY <= endY + 30) {
            
            try {
                long finishTime = System.currentTimeMillis() - gameStartTime;
                synchronized (out) {
                    out.writeObject(new FinishMessage(myPlayerId, finishTime));
                    out.flush();
                    out.reset();
                }
                hasFinished = true;
                
                // 禁用玩家移動,但不移動攝影機(回合結束時統一移動)
                player.getComponent(PlayerControl.class).setEnabled(false);
                
                System.out.println("[CLIENT] Reached finish! Time: " + finishTime + "ms, player disabled, can now spectate with A/D");
            } catch (Exception e) {
                System.err.println("[CLIENT ERROR] Failed to send finish message: " + e.getMessage());
            }
        }
        
        // 檢查掉出地圖(上下邊界)
        if (playerY > SCREEN_HEIGHT + 100 || playerY < -100) {
            try {
                synchronized (out) {
                    out.writeObject(new FailMessage(myPlayerId));
                    out.flush();
                    out.reset();
                }
                hasFailed = true;
                player.setVisible(false);
                // 禁用玩家移動,但允許觀戰
                player.getComponent(PlayerControl.class).setEnabled(false);
                System.out.println("[CLIENT] Failed - fell off map (y=" + playerY + "), can spectate with A/D");
            } catch (Exception e) {
                System.err.println("[CLIENT ERROR] Failed to send fail message: " + e.getMessage());
            }
        }
    }
    }
    
    private void cleanup() {
        System.out.println("[CLIENT] Cleaning up...");
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

/**
 * 修復後的碰撞檢測 - 放在 GameClient.java 中的 PlatformComponent 類別
 */
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
     * 修復後的碰撞檢測 - 支持旋轉平台
     */
    public CollisionInfo checkCollision(double playerX, double playerY, double radius, double velocityY) {
        // 如果平台有旋轉，使用旋轉碰撞檢測
        if (Math.abs(entity.getRotation()) > 0.1) {
            return checkRotatedCollision(playerX, playerY, radius, velocityY);
        }
        
        // 無旋轉的平台使用原本的 AABB 碰撞檢測
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
        } else if (minOverlap == overlapLeft) {
            side = CollisionSide.LEFT;
        } else if (minOverlap == overlapRight) {
            side = CollisionSide.RIGHT;
        }

        return new CollisionInfo(true, side);
    }
    
    /**
     * 旋轉平台的碰撞檢測（修復版本）
     * 使用圓形與旋轉矩形的精確碰撞檢測
     */
    private CollisionInfo checkRotatedCollision(double playerX, double playerY, double radius, double velocityY) {
    // 新方法：使用局部座標 + 單純頂面判定，避免高速旋轉穿透
    double angleDeg = entity.getRotation();
    double centerX = entity.getX() + width / 2.0;
    double centerY = entity.getY() + height / 2.0;
    double angle = Math.toRadians(-angleDeg);

    // 玩家底部點（更貼近踩踏感）
    double playerBottomX = playerX;
    double playerBottomY = playerY + radius * 0.6; // 若半徑 25，採底部偏上些減少誤判

    double dx = playerBottomX - centerX;
    double dy = playerBottomY - centerY;
    double localX = dx * Math.cos(angle) - dy * Math.sin(angle);
    double localY = dx * Math.sin(angle) + dy * Math.cos(angle);

    double halfW = width / 2.0;
    double halfH = height / 2.0;

    // 是否在矩形投影範圍內（含少量邊界緩衝）
    double padding = 4.0;
    boolean insideProj = localX >= -halfW - padding && localX <= halfW + padding &&
                         localY >= -halfH - padding && localY <= halfH + padding;
    if (!insideProj) {
        return new CollisionInfo(false, CollisionSide.NONE);
    }

    // 僅處理頂面著地：玩家局部座標需要在頂面上方且正在下落
    boolean falling = velocityY >= 0;
    boolean aboveTopSurface = localY <= -halfH + 6; // 距離頂面容忍值
    if (falling && aboveTopSurface) {
        return new CollisionInfo(true, CollisionSide.TOP);
    }

    // 其餘僅當作側面/底面簡化：不改變 Y，但阻擋 X（此處不細分，回傳一般碰撞）
    return new CollisionInfo(true, CollisionSide.NONE);
    }
}

/**
 * 修復後的玩家控制 - 放在 GameClient.java 中
 */
class PlayerControl extends Component {
    private double speed = 8.0;
    private double velocityX = 0;
    public double velocityY = 0;  // 改為public以便死亡檢測使用
    private double jumpStrength = 20.0;
    private double gravity = 1.0;
    private boolean onGround = false;
    private boolean crouching = false;
    private List<Entity> platforms;
    private double playerRadius = 25;
    private boolean enabled = true;  // 控制玩家是否可移動
    
    private final double LEFT_BOUNDARY = playerRadius;
    private final double RIGHT_BOUNDARY = 5000 - playerRadius; // 使用整個關卡寬度 FINISH_X
    private final double TOP_BOUNDARY = playerRadius;
    private final double MAX_VELOCITY_Y = 25;

    public PlayerControl(List<Entity> platforms) {
        this.platforms = platforms;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            velocityX = 0;
            velocityY = 0;
        }
    }
    
    public void reset() {
        velocityX = 0;
        velocityY = 0;
        onGround = false;
        crouching = false;
        enabled = true;
    }
   @Override
public void onUpdate(double tpf) {
    if (!enabled) return;  // 如果禁用，不處理任何移動
    
    velocityY += gravity;
    
    if (velocityY > MAX_VELOCITY_Y) velocityY = MAX_VELOCITY_Y;
    if (velocityY < -MAX_VELOCITY_Y) velocityY = -MAX_VELOCITY_Y;
    
    // 先移動X
    double oldX = entity.getX();
    entity.setX(entity.getX() + velocityX);
    
    // 檢查X方向碰撞
    boolean xCollision = false;
    for (Entity platform : platforms) {
        if (!platform.hasComponent(PlatformComponent.class)) continue;
        
        PlatformComponent pc = platform.getComponent(PlatformComponent.class);
        CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
        
        if (collision.collided && (collision.side == CollisionSide.LEFT || collision.side == CollisionSide.RIGHT)) {
            entity.setX(oldX);  // 恢復到舊位置
            velocityX = 0;
            xCollision = true;
            break;
        }
    }
    
    // 再移動Y
    double oldY = entity.getY();
    entity.setY(entity.getY() + velocityY);
    
    onGround = false;
    
    // 檢查Y方向碰撞
  for (Entity platform : platforms) {
        if (!platform.hasComponent(PlatformComponent.class)) continue;
        
        PlatformComponent pc = platform.getComponent(PlatformComponent.class);
        CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
        
        if (collision.collided) {
            switch (collision.side) {
                case TOP:
                    // 站立在平台上
                    entity.setY(platform.getY() - playerRadius);
                    
                    if (velocityY > 0) velocityY = 0;
                    onGround = true;
                    
                    // 在這裡添加彈跳平台檢測
                    if (platform.hasComponent(BouncePlatformComponent.class)) {
                        BouncePlatformComponent bounce = platform.getComponent(BouncePlatformComponent.class);
                        velocityY = -bounce.getBounceStrength();
                        onGround = false;  // 彈跳時離地
                    }
                    break;
                    
                case BOTTOM:
                    entity.setY(oldY);
                    if (velocityY < 0) velocityY = 0;
                    break;
                    
                case LEFT:
                case RIGHT:
                    if (!xCollision) {
                        entity.setX(oldX);
                        velocityX = 0;
                    }
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
// 移動平台組件
class MovingPlatformComponent extends Component {
    private double startX, startY;
    private double moveSpeed, moveRange;
    private boolean horizontal;
    private double elapsed = 0;
    
    public MovingPlatformComponent(boolean horizontal, double speed, double range) {
        this.horizontal = horizontal;
        this.moveSpeed = speed;
        this.moveRange = range;
    }
    
    @Override
    public void onAdded() {
        startX = entity.getX();
        startY = entity.getY();
    }
    
    @Override
    public void onUpdate(double tpf) {
        elapsed += tpf;
        double offset = Math.sin(elapsed * moveSpeed) * moveRange / 2;
        
        if (horizontal) {
            entity.setX(startX + offset);
        } else {
            entity.setY(startY + offset);
        }
    }
}

// 彈跳平台組件
class BouncePlatformComponent extends Component {
    private static final double BOUNCE_STRENGTH = 30.0;
    
    public double getBounceStrength() {
        return BOUNCE_STRENGTH;
    }
}

// 砲塔組件
class TurretComponent extends Component {
    private double fireRate;
    private double timeSinceLastShot = 0;
    private List<Entity> bullets = new ArrayList<>();
    
    public TurretComponent(double fireRate) {
        this.fireRate = fireRate;
    }
    
    @Override
    public void onUpdate(double tpf) {
        timeSinceLastShot += tpf;
        
        if (timeSinceLastShot >= fireRate) {
            fireBullet();
            timeSinceLastShot = 0;
        }
    }
    
    private void fireBullet() {
        Circle bullet = new Circle(8, Color.ORANGE);
        bullet.setStroke(Color.RED);
        bullet.setStrokeWidth(2);
        
        int rot = ((int)Math.round(entity.getRotation()) % 360 + 360) % 360;
        double dx = 1, dy = 0; // 0° 射向右
        double offsetX = entity.getWidth()/2, offsetY = entity.getHeight()/2;
        
        if (rot >= 45 && rot < 135) { 
            dx = 0; dy = -1;  // 90° 射向上
            offsetY = 0;
        }
        else if (rot >= 135 && rot < 225) { 
            dx = -1; dy = 0;  // 180° 射向左
            offsetX = 0;
        }
        else if (rot >= 225 && rot < 315) { 
            dx = 0; dy = 1;  // 270° 射向下
            offsetY = entity.getHeight();
        }
        else {
            // 0° 射向右
            offsetX = entity.getWidth();
        }
        
        Entity bulletEntity = FXGL.entityBuilder()
            .at(entity.getX() + offsetX, entity.getY() + offsetY)
            .view(bullet)
            .with(new BulletComponent(dx, dy))
            .buildAndAttach();
        bullets.add(bulletEntity);
    }
}

// 子彈組件
class BulletComponent extends Component {
    private double speed = 300;
    private double dirX, dirY;
    public BulletComponent(double dirX, double dirY) {
        double len = Math.sqrt(dirX*dirX + dirY*dirY);
        if (len == 0) { this.dirX = 1; this.dirY = 0; }
        else { this.dirX = dirX/len; this.dirY = dirY/len; }
    }
    @Override
    public void onUpdate(double tpf) {
        entity.translateX(dirX * speed * tpf);
        entity.translateY(dirY * speed * tpf);
        int appH = FXGL.getAppHeight();
        if (entity.getX() < -200 || entity.getX() > 10000 || entity.getY() < -200 || entity.getY() > appH + 200) {
            entity.removeFromWorld();
        }
    }
    public boolean checkHit(double playerX, double playerY, double radius) {
        double dx = entity.getX() - playerX;
        double dy = entity.getY() - playerY;
        return Math.sqrt(dx*dx + dy*dy) < radius + 5;
    }
}

// 死亡區域組件
class DeathZoneComponent extends Component {
    public double width, height;  // 改為public以便死亡檢測使用
    
    public DeathZoneComponent(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    // 檢查玩家是否與死亡區重疊(碰撞檢測)
    public boolean checkCollision(double playerX, double playerY, double playerRadius) {
        double zoneLeft = entity.getX();
        double zoneRight = entity.getX() + width;
        double zoneTop = entity.getY();
        double zoneBottom = entity.getY() + height;
        
        // 使用更寬鬆的檢測 - 玩家中心點在死亡區內或非常接近
        // 方法1: 簡單的矩形重疊檢測
        boolean simpleOverlap = (playerX + playerRadius > zoneLeft && 
                                 playerX - playerRadius < zoneRight && 
                                 playerY + playerRadius > zoneTop && 
                                 playerY - playerRadius < zoneBottom);
        
        if (simpleOverlap) {
            return true;
        }
        
        // 方法2: 圓形與矩形的精確碰撞
        double closestX = Math.max(zoneLeft, Math.min(playerX, zoneRight));
        double closestY = Math.max(zoneTop, Math.min(playerY, zoneBottom));
        
        double distanceX = playerX - closestX;
        double distanceY = playerY - closestY;
        double distanceSquared = distanceX * distanceX + distanceY * distanceY;
        
        return distanceSquared < (playerRadius * playerRadius);
    }
}