import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.input.UserAction;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
/**
 * 多人平台遊戲客戶端（修正版）
 */
public class GameClient extends GameApplication {

    private static String SERVER_HOST = "127.0.0.1";
    private static int SERVER_PORT = 12345;
    
    private Entity player;
    private javafx.scene.text.Text lastCreatedNameText = null;  // 用於傳遞 nameText 給 PlayerControl
    private javafx.scene.Node lastCreatedBodyNode = null;  // 用於傳遞 bodyNode 給 PlayerControl
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
    private Text fpsText;
    private double fpsCounter = 0;
    private int frameCount = 0;
    private List<Entity> objectButtons = new ArrayList<>();
    private Entity previewPlatform = null;
    private Entity finishButton = null;
    private List<Entity> leaderboardEntities = new ArrayList<>();
    private Map<String, Image> platformImageCache = new HashMap<>();
    private Pane hudPane = null;           // 固定在螢幕上的 HUD (phase/timer/score)
    private Pane selectionPane = null;     // 放置/選擇 UI 內容
    private ScrollPane selectionScroll = null;         // 滾動容器顯示平台列表（延遲建立避免 Toolkit 未初始化）
    private Pane finishPane = null;        // 完成按鈕 UI
    private Pane leaderboardPane = null;   // 排行榜 UI，固定在螢幕上
    
    private boolean isDragging = false;
    private Point2D dragOffset = Point2D.ZERO;
    private double currentRotation = 0;
    private GameObjectInfo selectedObj = null;
    
    private Entity startPlatform;
    private Entity endPlatform;
    private Entity flagEntity;  // finish platform 上方的旗幟
    private Entity groundPlatform; // 底部大型平台防止玩家掉落
    private List<Entity> backgroundLayersLeft = new ArrayList<>();   // 視差背景（左圖塊）
    private List<Entity> backgroundLayersRight = new ArrayList<>();  // 視差背景（右圖塊）
    
    private long gameStartTime = 0;
    private static final long GAME_DURATION = 120000;
    
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    // 視差速度（前景→背景）
    private static final double[] PARALLAX_FACTORS = {0.9, 0.7, 0.5, 0.3, 0.1};
    
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
    
    // 角色選擇
    private int selectedCharacter = 1; // 1, 2, or 3
    private Map<String, Integer> playerCharacters = new HashMap<>(); // playerId -> character index
    private Pane characterSelectionPane = null;
    private boolean characterSelectionDone = false;  

    // 依房間中的順序回傳玩家顯示名稱（Player 1/2/...），若無資料則回傳原 playerId
    private String getPlayerLabel(RoomInfo info, String playerId) {
        if (info == null || info.playerOrder == null) return playerId;
        Integer order = info.playerOrder.get(playerId);
        return (order != null) ? ("Player " + order) : playerId;
    }
    
    // 載入角色圖片
    private javafx.scene.image.Image loadCharacterImage(int index) {
        try {
            return new javafx.scene.image.Image("file:map picture/player" + index + ".png");
        } catch (Exception e) {
            System.err.println("[CLIENT] Failed to load character " + index + ": " + e.getMessage());
            return null;
        }
    }
    
    // 顯示角色選擇UI
    private void showCharacterSelection() {
        System.out.println("[CLIENT] Showing character selection");
        if (characterSelectionPane == null) {
            characterSelectionPane = new javafx.scene.layout.Pane();
        }
        characterSelectionPane.getChildren().clear();
        characterSelectionPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");
        
        // 背景
        javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle(SCREEN_WIDTH, SCREEN_HEIGHT, javafx.scene.paint.Color.rgb(40, 40, 50, 0.8));
        characterSelectionPane.getChildren().add(bg);
        
        // 標題
        javafx.scene.text.Text title = new javafx.scene.text.Text("Select Your Character");
        title.setFont(javafx.scene.text.Font.font(48));
        title.setFill(javafx.scene.paint.Color.GOLD);
        title.setLayoutX(SCREEN_WIDTH / 2.0 - 250);
        title.setLayoutY(100);
        characterSelectionPane.getChildren().add(title);
        
        // 三個角色選項
        int startX = 200;
        int spacing = 500;
        for (int i = 1; i <= 3; i++) {
            int idx = i;
            javafx.scene.image.Image charImg = loadCharacterImage(i);
            int x = startX + (i - 1) * spacing;
            
            // 角色圖片容器
            javafx.scene.layout.VBox charBox = new javafx.scene.layout.VBox(10);
            charBox.setLayoutX(x);
            charBox.setLayoutY(250);
            charBox.setStyle("-fx-alignment: center;");
            
            // 顯示角色圖片或虛擬框
            javafx.scene.Node charView;
            if (charImg != null) {
                javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView(charImg);
                imgView.setFitWidth(150);
                imgView.setFitHeight(150);
                imgView.setPreserveRatio(true);
                charView = imgView;
            } else {
                javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(150, 150, javafx.scene.paint.Color.GRAY);
                rect.setStroke(javafx.scene.paint.Color.WHITE);
                rect.setStrokeWidth(2);
                charView = rect;
            }
            charBox.getChildren().add(charView);
            
            // 選擇按鈕
            javafx.scene.shape.Rectangle selectBtn = new javafx.scene.shape.Rectangle(150, 50, javafx.scene.paint.Color.rgb(50, 150, 50));
            selectBtn.setStroke(javafx.scene.paint.Color.WHITE);
            selectBtn.setStrokeWidth(2);
            selectBtn.setOnMouseClicked(e -> selectCharacter(idx));
            selectBtn.setOnMouseEntered(e -> selectBtn.setFill(javafx.scene.paint.Color.rgb(100, 200, 100)));
            selectBtn.setOnMouseExited(e -> selectBtn.setFill(javafx.scene.paint.Color.rgb(50, 150, 50)));
            charBox.getChildren().add(selectBtn);
            
            javafx.scene.text.Text btnText = new javafx.scene.text.Text("Character " + i);
            btnText.setFill(javafx.scene.paint.Color.WHITE);
            btnText.setFont(javafx.scene.text.Font.font(18));
            btnText.setMouseTransparent(true);
            charBox.getChildren().add(btnText);
            
            characterSelectionPane.getChildren().add(charBox);
        }
        
        FXGL.getGameScene().addUINode(characterSelectionPane);
    }
    
    private void selectCharacter(int index) {
        System.out.println("[CLIENT] Selected character " + index);
        selectedCharacter = index;
        playerCharacters.put(myPlayerId, index);
        characterSelectionDone = true;
        if (characterSelectionPane != null) {
            characterSelectionPane.setVisible(false);
            FXGL.getGameScene().removeUINode(characterSelectionPane);
        }
        
        // 更新玩家實體的視圖（顯示選擇的角色）
        if (player != null) {
            String myLabel = getPlayerLabel(currentRoomInfo, myPlayerId);
            javafx.scene.Node newView = buildPlayerView(myLabel, myColor, myPlayerId);
            player.getViewComponent().clearChildren();
            player.getViewComponent().addChild(newView);
            System.out.println("[CLIENT] Updated player view with character " + index);

            // 同步更新動畫組件的角色幀來源
            PlayerAnimationComponent anim = player.getComponentOptional(PlayerAnimationComponent.class).orElse(null);
            if (anim != null) {
                anim.setCharacterIndex(index);
                anim.refreshImageViewFromEntity();
                player.setVisible(true); // 確保玩家可見
            }
        }
        
        // 發送給伺服器
        try {
            synchronized (out) {
                out.writeObject(new CharacterSelectionMessage(myPlayerId, index));
                out.flush();
                out.reset();
            }
        } catch (Exception e) {
            System.err.println("[CLIENT ERROR] Failed to send character selection: " + e.getMessage());
        }
        
        // 進行遊戲（角色選擇完成後立即開始 PLAYING 阶段）
        System.out.println("[CLIENT] Character selection complete, starting game...");
        handlePhaseChange(GamePhase.PLAYING);
    }

    // 建立玩家的視覺（身體 + 名字標籤置於頭上）
    private javafx.scene.Node buildPlayerView(String label, Color bodyColor, String playerId) {
        // 根据玩家选择的角色显示对应的图片
        javafx.scene.Node body;
        int characterIndex = 1;
        
        // playerId 可能为 null（在 initGame 时），所以需要处理
        if (playerId != null && playerId.equals(myPlayerId)) {
            characterIndex = selectedCharacter;
        } else if (playerId != null) {
            characterIndex = playerCharacters.getOrDefault(playerId, 1);
        } else {
            characterIndex = selectedCharacter;  // 默认使用自己选择的角色
        }
        
        javafx.scene.image.Image charImg = loadCharacterImage(characterIndex);
        if (charImg != null) {
            javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView(charImg);
            imgView.setFitWidth(128);
            imgView.setFitHeight(128);
            imgView.setPreserveRatio(true);
            // 圖片以原點為中心對齊
            imgView.setTranslateX(-64);
            imgView.setTranslateY(-64);
            body = imgView;
        } else {
            // 如果图片加载失败，回退到圆形
            body = new Circle(64, bodyColor);
        }

        Text name = new Text(label);
        // 強制純白文字，移除描邊避免整體變黑
        name.setFill(Color.WHITE);
        name.setStroke(null);
        name.setStyle("-fx-fill: white; -fx-stroke: transparent;");
        name.setTranslateY(-90); // 玩家預計 128x128，標籤放在頭頂之上
        name.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
            name.setTranslateX(-newB.getWidth() / 2.0);
        });
        
        lastCreatedNameText = name;  // 存儲 nameText 供稍後使用
        lastCreatedBodyNode = body;  // 存儲 bodyNode 供稍後使用

        javafx.scene.Group group = new javafx.scene.Group(body, name);
        return group;
    }

    // 將現有玩家實體的標籤文字更新為指定字串
    private void updatePlayerLabel(Entity playerEntity, String label) {
        if (playerEntity == null) return;
        for (javafx.scene.Node node : playerEntity.getViewComponent().getChildren()) {
            if (node instanceof Text t) {
                t.setText(label);
                t.setFill(Color.WHITE);
                t.setStroke(null);
                t.setStyle("-fx-fill: white; -fx-stroke: transparent;");
                return;
            }
            if (node instanceof javafx.scene.Group g) {
                for (javafx.scene.Node inner : g.getChildren()) {
                    if (inner instanceof Text t2) {
                        t2.setText(label);
                        t2.setFill(Color.WHITE);
                        t2.setStroke(null);
                        t2.setStyle("-fx-fill: white; -fx-stroke: transparent;");
                        return;
                    }
                }
            }
        }
    }

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
            String label = getPlayerLabel(currentRoomInfo, pid);
            
            String playerText = (isMe ? "► " : "  ") + label + 
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
        settings.setManualResizeEnabled(true);  // 允許手動調整窗口大小
        settings.setPreserveResizeRatio(true);  // 保持窗口的寬高比
        settings.setScaleAffectedOnResize(true); // 窗口縮放時遊戲畫面也跟著縮放
    }

    @Override
    protected void initGame() {
        FXGL.getGameScene().setBackgroundColor(Color.rgb(30, 30, 40));
        
        // 添加 5 個重疊背景圖層（每層使用兩個圖塊確保全螢幕覆蓋）
        String[] bgFiles = {"background1.png", "background2.png", "background3.png", 
                           "background4.png", "background5.png"};
        int tileWidth = SCREEN_WIDTH;   // 每個圖塊等於螢幕寬度
        int tileHeight = SCREEN_HEIGHT; // 每個圖塊等於螢幕高度
        backgroundLayersLeft.clear();
        backgroundLayersRight.clear();

        for (int i = 0; i < 5; i++) {
            try {
                Image bgImage = new Image("file:map picture/" + bgFiles[i]);

                // 左圖塊（使用原始圖片大小，不縮放）
                ImageView leftView = new ImageView(bgImage);
                Entity leftEntity = FXGL.entityBuilder()
                    .at(0, 0)
                    .view(leftView)
                    .zIndex(-1000 - i)  // background1 最前，background5 最後
                    .buildAndAttach();
                backgroundLayersLeft.add(leftEntity);

                // 右圖塊（緊接著左圖塊右側，用於無縫覆蓋；原始大小）
                ImageView rightView = new ImageView(bgImage);
                Entity rightEntity = FXGL.entityBuilder()
                    .at(tileWidth, 0)
                    .view(rightView)
                    .zIndex(-1000 - i)
                    .buildAndAttach();
                backgroundLayersRight.add(rightEntity);
            } catch (Exception e) {
                System.err.println("[CLIENT] Failed to load background " + bgFiles[i] + ": " + e.getMessage());
            }
        }
        
        createFixedPlatforms();
        // createMiddlePlatform();  // 註解掉灰色中間平台
        String myLabel = getPlayerLabel(currentRoomInfo, myPlayerId != null ? myPlayerId : "Me");
        
        // 準備玩家 View
        javafx.scene.Node playerView = buildPlayerView(myLabel, myColor, myPlayerId);
        int charIndex = selectedCharacter;
        
        // 建立玩家動畫和控制組件
        PlayerAnimationComponent animComp = new PlayerAnimationComponent(charIndex);
        PlayerControl playerCtrl = new PlayerControl(platformEntities);
        playerCtrl.setAnimationComponent(animComp);
        playerCtrl.setNameText(lastCreatedNameText);  // 設置 nameText 參考以便翻轉時不翻轉名字
        playerCtrl.setBodyNode(lastCreatedBodyNode);  // 設置 bodyNode 參考以便翻轉
        
        player = FXGL.entityBuilder()
            .at(100, 900)
            .view(playerView)
            .with(animComp)
            .with(playerCtrl)
            .buildAndAttach();
        
        updatePlayerLabel(player, myLabel);
        player.setVisible(false);
        createGameZones();
        ensureUIPanes();
        // 將 UI pane 加到 UI 層，確保不受攝影機影響
        if (!FXGL.getGameScene().getUINodes().contains(hudPane)) {
            FXGL.getGameScene().addUINode(hudPane);
        }
        // 配置可滾動的選擇列表
        setupSelectionScroll();
        if (!FXGL.getGameScene().getUINodes().contains(selectionScroll)) {
            FXGL.getGameScene().addUINode(selectionScroll);
        }
        if (selectionScroll != null) {
            selectionScroll.setVisible(false);
            selectionScroll.setMouseTransparent(true);
        }
        if (!FXGL.getGameScene().getUINodes().contains(finishPane)) {
            FXGL.getGameScene().addUINode(finishPane);
        }
        if (!FXGL.getGameScene().getUINodes().contains(leaderboardPane)) {
            FXGL.getGameScene().addUINode(leaderboardPane);
        }
        createUI();
        
        // 先連接伺服器
        connectToServer();
        
        // 只在連接成功後才啟動網路線程和選單
        if (connected) {
            startNetworkThread();
            startPositionSender();
            startPlacementPreviewSender();
            createMainMenu();
        } else {
            // 連接失敗,顯示錯誤訊息
            showConnectionError();
        }
    }
     // private void createMiddlePlatform() {
    // // 中間固定平台
    // double midX = SCREEN_WIDTH / 2 - 150;
    // double midY = SCREEN_HEIGHT / 2;
    // 
    // middlePlatform = createPlatform(midX, midY, 300, 30, Color.rgb(100, 100, 100));
    // middlePlatform.setVisible(false);
// }

private void createGameZones() {
    // 不再生成隨機死亡區和安全區
    // 所有區域都由地圖編輯器創建
    zonesCreated = true;
    System.out.println("[CLIENT] Game zones initialization complete (no random zones)");
}   

    private void createFixedPlatforms() {
    startPlatform = createPlatform(50, SCREEN_HEIGHT - 150, 200, 30, Color.GREEN, "map picture/start.png");
    endPlatform = createPlatform(FINISH_X - 200, SCREEN_HEIGHT - 150, 200, 30, Color.GOLD, "map picture/finish.png"); 
    
    // 在 finish 平台上方添加旗幟（無碰撞）
    Image flagImage = loadPlatformImage("map picture/flag.png");
    if (flagImage != null) {
        ImageView flagView = new ImageView(flagImage);
        flagView.setFitWidth(100);
        flagView.setFitHeight(100);
        flagView.setPreserveRatio(false);
        
        flagEntity = FXGL.entityBuilder()
                .at(FINISH_X - 150, SCREEN_HEIGHT - 150 - 100)  // 在 finish 平台上方 100 像素
                .view(flagView)
                .buildAndAttach();  // 不添加 PlatformComponent，所以沒有碰撞
        flagEntity.setVisible(false);  // 初始時隱藏
    }
    
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
            .at(FINISH_X - 130, SCREEN_HEIGHT - 130)
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
        hudPane.getChildren().clear();

        phaseText = new Text("Phase: SELECTING");
        phaseText.setFont(Font.font(30));
        phaseText.setFill(Color.WHITE);
        phaseText.setLayoutX(SCREEN_WIDTH / 2.0 - 150);
        phaseText.setLayoutY(50);
        hudPane.getChildren().add(phaseText);
        
        timerText = new Text("");
        timerText.setFont(Font.font(25));
        timerText.setFill(Color.YELLOW);
        timerText.setLayoutX(SCREEN_WIDTH / 2.0 - 50);
        timerText.setLayoutY(90);
        hudPane.getChildren().add(timerText);
        
        scoreText = new Text("Score: 0");
        scoreText.setFont(Font.font(20));
        scoreText.setFill(Color.CYAN);
        scoreText.setLayoutX(50);
        scoreText.setLayoutY(50);
        hudPane.getChildren().add(scoreText);
        
        fpsText = new Text("FPS: 0");
        fpsText.setFont(Font.font(20));
        fpsText.setFill(Color.LIME);
        fpsText.setLayoutX(SCREEN_WIDTH - 150);  // 右上角
        fpsText.setLayoutY(50);
        hudPane.getChildren().add(fpsText);
        }

    // 確保各 UI Pane 只在 JavaFX 初始化後建立，避免 Toolkit not initialized
    private void ensureUIPanes() {
        if (hudPane == null) hudPane = new Pane();
        if (selectionPane == null) selectionPane = new Pane();
        if (finishPane == null) finishPane = new Pane();
        if (leaderboardPane == null) leaderboardPane = new Pane();
    }

    // 設定選擇清單的滾動容器，防止清單超出螢幕
    private void setupSelectionScroll() {
        if (selectionScroll == null) {
            selectionScroll = new ScrollPane();
        }
        selectionScroll.setContent(selectionPane);
        selectionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        selectionScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        selectionScroll.setPannable(true);
        selectionScroll.setFitToWidth(true);
        selectionScroll.setPrefViewportWidth(SCREEN_WIDTH);
        selectionScroll.setPrefViewportHeight(SCREEN_HEIGHT - 150);
        selectionScroll.setLayoutX(0);
        selectionScroll.setLayoutY(100);
        selectionScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    }
    
    private void displayObjectSelection() {
        System.out.println("[CLIENT] Displaying object selection: " + availableObjects.size() + " objects");
        for (GameObjectInfo obj : availableObjects) {
            System.out.println("[CLIENT]   - " + obj.type + " (" + obj.width + "x" + obj.height + "), imagePath=" + obj.imagePath);
        }
        if (selectionScroll != null) {
            selectionScroll.setVisible(true);
            selectionScroll.setMouseTransparent(false);
        }
        
        selectionPane.getChildren().clear();
        int startY = 40;
        int spacing = 150;

        for (int i = 0; i < availableObjects.size(); i++) {
            GameObjectInfo obj = availableObjects.get(i);
            int yPos = startY + i * spacing;
            double baseX = SCREEN_WIDTH / 2.0 - 200;

            Rectangle btnBg = new Rectangle(400, 100, Color.rgb(80, 80, 100));
            btnBg.setStroke(Color.WHITE);
            btnBg.setStrokeWidth(2);
            btnBg.setLayoutX(baseX);
            btnBg.setLayoutY(yPos);
            selectionPane.getChildren().add(btnBg);

            // 顯示平台預覽（優先圖片，次之顏色或漸層）
            if (obj.imagePath != null && !obj.imagePath.isBlank()) {
                Image img = loadPlatformImage(obj.imagePath);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    if (obj.type == ObjectType.ERASER) {
                        // 將 ERASER 圖片縮小並置中到 400x100 的按鈕框內
                        double boxOuterW = 400;
                        double boxOuterH = 100;
                        double boxPadding = 10; // 內邊距
                        double boxW = boxOuterW - boxPadding * 2;
                        double boxH = boxOuterH - boxPadding * 2;
                        iv.setPreserveRatio(true);
                        iv.setFitHeight(boxH);
                        
                        // 依據原圖比例計算顯示寬度，置中顯示
                        double imgW = img.getWidth();
                        double imgH = img.getHeight();
                        double displayedW = boxH * (imgW / imgH);
                        if (displayedW > boxW) {
                            // 若寬度超過盒子寬，以寬度為主重新計算高度
                            displayedW = boxW;
                            double displayedH = boxW * (imgH / imgW);
                            iv.setFitHeight(displayedH);
                        }
                        double buttonLeftX = (SCREEN_WIDTH / 2.0) - (boxOuterW / 2.0);
                        double contentLeftX = buttonLeftX + (boxOuterW - displayedW) / 2.0;
                        double contentTopY = yPos + (boxOuterH - boxH) / 2.0; // 垂直置中（以盒子高度為主）
                        iv.setLayoutX(contentLeftX);
                        iv.setLayoutY(contentTopY);
                    } else if (obj.type == ObjectType.TURRET) {
                        // TURRET 圖片顯示為原始大小，置中
                        iv.setPreserveRatio(true);
                        iv.setFitWidth(obj.width);
                        double centerX = SCREEN_WIDTH / 2.0 - obj.width / 2.0;
                        double centerY = yPos + 50 - obj.height / 2.0;
                        iv.setLayoutX(centerX);
                        iv.setLayoutY(centerY);
                    } else if (obj.type == ObjectType.ROTATING) {
                        // ROTATING 圖片平放顯示
                        iv.setPreserveRatio(false);
                        iv.setFitWidth(obj.width);
                        iv.setFitHeight(obj.height);
                        double centerX = SCREEN_WIDTH / 2.0 - obj.width / 2.0;
                        double centerY = yPos + 50 - obj.height / 2.0;
                        iv.setLayoutX(centerX);
                        iv.setLayoutY(centerY);
                    } else {
                        iv.setFitWidth(obj.width);
                        iv.setFitHeight(obj.height);
                        iv.setPreserveRatio(false);
                        iv.setLayoutX(SCREEN_WIDTH / 2.0 - obj.width / 2.0);
                        iv.setLayoutY(yPos + 50 - obj.height / 2.0);
                    }
                    selectionPane.getChildren().add(iv);
                } else {
                    // 圖片載入失敗，回退到顏色
                    Rectangle objRect = new Rectangle(obj.width, obj.height, Color.web(obj.color));
                    objRect.setStroke(Color.YELLOW);
                    objRect.setStrokeWidth(1);
                    objRect.setLayoutX(SCREEN_WIDTH / 2.0 - obj.width / 2.0);
                    objRect.setLayoutY(yPos + 50 - obj.height / 2.0);
                    selectionPane.getChildren().add(objRect);
                }
            } else if (obj.type == ObjectType.TURRET) {
                javafx.scene.shape.Rectangle turretBody = new javafx.scene.shape.Rectangle(obj.width, obj.height);
                javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.YELLOW),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.RED)
                );
                turretBody.setFill(gradient);
                turretBody.setStroke(javafx.scene.paint.Color.ORANGE);
                turretBody.setStrokeWidth(2);
                turretBody.setLayoutX(SCREEN_WIDTH / 2.0 - obj.width / 2.0);
                turretBody.setLayoutY(yPos + 50 - obj.height / 2.0);
                selectionPane.getChildren().add(turretBody);
            } else if (obj.type == ObjectType.ROTATING) {
                javafx.scene.shape.Rectangle rotatingRect = new javafx.scene.shape.Rectangle(obj.width, obj.height);
                javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.web("#89C2FF")),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.web("#5E60CE"))
                );
                rotatingRect.setFill(gradient);
                rotatingRect.setStroke(javafx.scene.paint.Color.WHITE);
                rotatingRect.setStrokeWidth(2);
                rotatingRect.setLayoutX(SCREEN_WIDTH / 2.0 - obj.width / 2.0);
                rotatingRect.setLayoutY(yPos + 50 - obj.height / 2.0);
                selectionPane.getChildren().add(rotatingRect);
            } else {
                Rectangle objRect = new Rectangle(obj.width, obj.height, Color.web(obj.color));
                objRect.setStroke(Color.YELLOW);
                objRect.setStrokeWidth(1);
                objRect.setLayoutX(SCREEN_WIDTH / 2.0 - obj.width / 2.0);
                objRect.setLayoutY(yPos + 50 - obj.height / 2.0);
                selectionPane.getChildren().add(objRect);
            }

            Text numLabel = new Text("Platform " + (i + 1) + " (" + obj.type + ")");
            numLabel.setFont(Font.font(20));
            numLabel.setFill(Color.WHITE);
            numLabel.setLayoutX(baseX + 10);
            numLabel.setLayoutY(yPos + 25);
            selectionPane.getChildren().add(numLabel);

            Text sizeLabel = new Text(obj.width + " x " + obj.height);
            sizeLabel.setFont(Font.font(18));
            sizeLabel.setFill(Color.LIGHTGREEN);
            sizeLabel.setLayoutX(baseX + 10);
            sizeLabel.setLayoutY(yPos + 80);
            selectionPane.getChildren().add(sizeLabel);

            String desc = getObjectDescription(obj.type);
            Text descText = new Text(desc);
            descText.setFont(Font.font(16));
            descText.setFill(Color.LIGHTGRAY);
            descText.setLayoutX(baseX + 210);
            descText.setLayoutY(yPos + 25);
            selectionPane.getChildren().add(descText);
        }

        // 根據項目數量調整容器高度，讓 ScrollPane 可滾動
        double totalHeight = startY + availableObjects.size() * spacing + 200;
        selectionPane.setMinHeight(totalHeight);
        selectionPane.setPrefHeight(totalHeight);
        selectionPane.setMaxHeight(totalHeight);
        selectionPane.setPrefWidth(SCREEN_WIDTH);

        // 直接在 Pane 上處理點擊，避免 UI 節點阻擋 FXGL input
        selectionPane.setOnMouseClicked(evt -> {
            handleObjectSelectionAtContent(evt.getX(), evt.getY());
        });
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
                case ROTATING -> "旋轉：平台持續旋轉，踩點要抓時機";
                default -> "普通平台";
            };
            }
    
    private void clearObjectSelection() {
        System.out.println("[CLIENT] Clearing object selection UI");
        selectionPane.getChildren().clear();
        if (selectionScroll != null) {
            selectionScroll.setVisible(false);
            selectionScroll.setMouseTransparent(true);
        }
    }
    
    private Entity finishButtonText = null;
    private void showFinishButton() {
        System.out.println("[CLIENT] Showing finish button");
        finishPane.getChildren().clear();

        Rectangle btnBg = new Rectangle(200, 60, Color.rgb(50, 200, 50));
        btnBg.setStroke(Color.WHITE);
        btnBg.setStrokeWidth(3);
        btnBg.setLayoutX(SCREEN_WIDTH - 250);
        btnBg.setLayoutY(SCREEN_HEIGHT - 100);

        Text btnText = new Text("FINISH");
        btnText.setFont(Font.font(24));
        btnText.setFill(Color.WHITE);
        btnText.setLayoutX(SCREEN_WIDTH - 200);
        btnText.setLayoutY(SCREEN_HEIGHT - 62);

        finishPane.getChildren().addAll(btnBg, btnText);
    }
    
    private void hideFinishButton() {
        System.out.println("[CLIENT] Hiding finish button");
        finishPane.getChildren().clear();
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
    bg.setLayoutX(SCREEN_WIDTH / 2.0 - 350);
    bg.setLayoutY(SCREEN_HEIGHT / 2.0 - 300);
    leaderboardPane.getChildren().add(bg);
    
    // 顯示回合資訊
    String roundInfo = "ROUND " + (currentRoomInfo != null ? currentRoomInfo.currentRound : "?") + 
                      " / " + (currentRoomInfo != null ? currentRoomInfo.totalRounds : "5") + 
                      " COMPLETE!";
    
    System.out.println("[CLIENT] Leaderboard title: " + roundInfo);
    
    Text title = new Text(roundInfo);
    title.setFont(Font.font(36));
    title.setFill(Color.GOLD);
    title.setLayoutX(SCREEN_WIDTH / 2.0 - 200);
    title.setLayoutY(SCREEN_HEIGHT / 2.0 - 240);
    leaderboardPane.getChildren().add(title);
    
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
        String scoreInfo = getPlayerLabel(currentRoomInfo, playerId) + " - Round: +" + roundScore + " | Total: " + totalScore;
        
        Text scoreText = new Text(rank + scoreInfo);
        scoreText.setFont(Font.font(22));
        
        // 只有自己是黃色，其他都是白色
        if (playerId.equals(myPlayerId)) {
            scoreText.setFill(Color.YELLOW);
            scoreText.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 24));
        } else {
            scoreText.setFill(Color.WHITE);
        }
        
        scoreText.setLayoutX(SCREEN_WIDTH / 2.0 - 300);
        scoreText.setLayoutY(SCREEN_HEIGHT / 2.0 + yOffset);
        leaderboardPane.getChildren().add(scoreText);
        
        yOffset += 45;
    }
    
    // 提示文字
    boolean isLastRound = currentRoomInfo != null && 
                         currentRoomInfo.currentRound >= currentRoomInfo.totalRounds;
    String hintMsg = isLastRound ? "Returning to room..." : "Next round starting soon...";
    
    Text hint = new Text(hintMsg);
    hint.setFont(Font.font(20));
    hint.setFill(Color.LIGHTGRAY);
    hint.setLayoutX(SCREEN_WIDTH / 2.0 - 150);
    hint.setLayoutY(SCREEN_HEIGHT / 2.0 + 230);
    leaderboardPane.getChildren().add(hint);
    
    System.out.println("[CLIENT] Leaderboard created with " + leaderboardPane.getChildren().size() + " nodes");
}
private void hideLeaderboard() {
    System.out.println("[CLIENT] Hiding leaderboard");
    leaderboardPane.getChildren().clear();
    System.out.println("[CLIENT] Leaderboard cleared");
}
    
    // 讀取伺服器配置
    private void loadServerConfig() {
        try {
            File configFile = new File("server_config.txt");
            if (configFile.exists()) {
                System.out.println("[CLIENT] Loading server config from server_config.txt...");
                try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        // 跳過註釋和空行
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        
                        // 解析 KEY=VALUE 格式
                        if (line.contains("=")) {
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                String key = parts[0].trim();
                                String value = parts[1].trim();
                                
                                if (key.equals("SERVER_HOST")) {
                                    SERVER_HOST = value;
                                    System.out.println("[CLIENT] Loaded SERVER_HOST: " + SERVER_HOST);
                                } else if (key.equals("SERVER_PORT")) {
                                    try {
                                        SERVER_PORT = Integer.parseInt(value);
                                        System.out.println("[CLIENT] Loaded SERVER_PORT: " + SERVER_PORT);
                                    } catch (NumberFormatException e) {
                                        System.err.println("[CLIENT ERROR] Invalid port number: " + value);
                                    }
                                }
                            }
                        }
                    }
                }
                System.out.println("========================================");
                System.out.println("[CLIENT] Configuration loaded:");
                System.out.println("  Server: " + SERVER_HOST);
                System.out.println("  Port: " + SERVER_PORT);
                if (SERVER_HOST.equals("127.0.0.1") || SERVER_HOST.equals("localhost")) {
                    System.out.println("  Mode: Local Server (本機伺服器)");
                } else {
                    System.out.println("  Mode: Remote Server (遠端伺服器)");
                }
                System.out.println("========================================");
            } else {
                System.out.println("[CLIENT] server_config.txt not found, using defaults");
                System.out.println("========================================");
                System.out.println("[CLIENT] Default configuration:");
                System.out.println("  Server: " + SERVER_HOST);
                System.out.println("  Port: " + SERVER_PORT);
                System.out.println("  Mode: Local Server (本機伺服器)");
                System.out.println("========================================");
            }
        } catch (Exception e) {
            System.err.println("[CLIENT ERROR] Failed to load config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void connectToServer() {
        loadServerConfig();  // 先讀取配置
        int maxRetries = 5;
        int attempt = 0;
        int backoffMs = 1000; // 初始重試間隔 1 秒
        Exception lastError = null;

        while (attempt < maxRetries && !connected) {
            attempt++;
            try {
                System.out.println("[CLIENT] Connecting to server (attempt " + attempt + "/" + maxRetries + ") at " + SERVER_HOST + ":" + SERVER_PORT + "...");

                socket = new Socket();
                socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 5000);
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
                        Circle circle = (Circle) player.getViewComponent().getChildren().getFirst();
                        circle.setFill(myColor);
                    }
                }
            } catch (Exception e) {
                lastError = e;
                System.err.println("[CLIENT ERROR] Connect attempt " + attempt + " failed: " + e.getMessage());
                try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                backoffMs = Math.min(backoffMs * 2, 8000); // 指數回退，最多 8 秒
            }
        }

        if (!connected) {
            System.err.println("[CLIENT ERROR] Unable to connect after " + maxRetries + " attempts. Last error: " + (lastError != null ? lastError.getMessage() : "unknown"));
        }
    }
    
    private void showConnectionError() {
        // 清空所有UI
        clearAllUI();
        
        // 顯示錯誤訊息
        Text errorTitle = new Text("連接伺服器失敗");
        errorTitle.setFont(Font.font(40));
        errorTitle.setFill(Color.RED);
        Entity titleEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 200, SCREEN_HEIGHT / 2 - 150)
                .view(errorTitle)
                .buildAndAttach();
        menuEntities.add(titleEntity);
        
        Text errorMsg = new Text("無法連接到: " + SERVER_HOST + ":" + SERVER_PORT);
        errorMsg.setFont(Font.font(24));
        errorMsg.setFill(Color.YELLOW);
        Entity msgEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 250, SCREEN_HEIGHT / 2 - 80)
                .view(errorMsg)
                .buildAndAttach();
        menuEntities.add(msgEntity);
        
        Text hint1 = new Text("請確認:");
        hint1.setFont(Font.font(20));
        hint1.setFill(Color.WHITE);
        Entity hint1Entity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 250, SCREEN_HEIGHT / 2)
                .view(hint1)
                .buildAndAttach();
        menuEntities.add(hint1Entity);
        
        Text hint2 = new Text("1. 伺服器是否已啟動");
        hint2.setFont(Font.font(18));
        hint2.setFill(Color.LIGHTGRAY);
        Entity hint2Entity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 250, SCREEN_HEIGHT / 2 + 40)
                .view(hint2)
                .buildAndAttach();
        menuEntities.add(hint2Entity);
        
        Text hint3 = new Text("2. server_config.txt 配置是否正確");
        hint3.setFont(Font.font(18));
        hint3.setFill(Color.LIGHTGRAY);
        Entity hint3Entity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 250, SCREEN_HEIGHT / 2 + 80)
                .view(hint3)
                .buildAndAttach();
        menuEntities.add(hint3Entity);
        
        Text hint4 = new Text("3. 防火牆是否允許連線");
        hint4.setFont(Font.font(18));
        hint4.setFill(Color.LIGHTGRAY);
        Entity hint4Entity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 250, SCREEN_HEIGHT / 2 + 120)
                .view(hint4)
                .buildAndAttach();
        menuEntities.add(hint4Entity);
        
        Text exitHint = new Text("按 ESC 退出遊戲");
        exitHint.setFont(Font.font(20));
        exitHint.setFill(Color.ORANGE);
        Entity exitEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 100, SCREEN_HEIGHT / 2 + 200)
                .view(exitHint)
                .buildAndAttach();
        menuEntities.add(exitEntity);
    }
    
    private void startNetworkThread() {
        new Thread(() -> {
            try {
                while (running && connected) {
                    Object obj = in.readObject();
                    
                    if (obj instanceof PhaseChangeMessage phaseMsg) {
                        System.out.println("[CLIENT] Received phase change: " + phaseMsg.phase);
                        javafx.application.Platform.runLater(() -> {
                            if (phaseMsg.phase == GamePhase.PLAYING && !characterSelectionDone) {
                                // 遊戲開始前先顯示角色選擇
                                showCharacterSelection();
                            } else {
                                handlePhaseChange(phaseMsg.phase);
                            }
                        });
                    }
                    else if (obj instanceof CharacterSelectionMessage charMsg) {
                        System.out.println("[CLIENT] Received character selection from " + charMsg.playerId + ": " + charMsg.characterIndex);
                        javafx.application.Platform.runLater(() -> {
                            playerCharacters.put(charMsg.playerId, charMsg.characterIndex);
                            System.out.println("[CLIENT] Updated playerCharacters map for " + charMsg.playerId + " -> " + charMsg.characterIndex);
                            
                            // 如果該玩家的實體已經存在，更新其視圖以顯示正確的角色
                            Entity existingPlayer = otherPlayers.get(charMsg.playerId);
                            if (existingPlayer != null) {
                                System.out.println("[CLIENT] Found existing player entity for " + charMsg.playerId + ", updating view...");
                                String label = getPlayerLabel(currentRoomInfo, charMsg.playerId);
                                Color playerColor = Color.RED; // 預設顏色
                                
                                // 嘗試從視圖中獲取當前顏色
                                for (javafx.scene.Node node : existingPlayer.getViewComponent().getChildren()) {
                                    if (node instanceof javafx.scene.Group group) {
                                        for (javafx.scene.Node inner : group.getChildren()) {
                                            if (inner instanceof Circle innerCircle) {
                                                playerColor = (Color) innerCircle.getFill();
                                                System.out.println("[CLIENT] Extracted color from Circle: " + playerColor);
                                                break;
                                            }
                                        }
                                        break;
                                    } else if (node instanceof Circle circle) {
                                        playerColor = (Color) circle.getFill();
                                        System.out.println("[CLIENT] Extracted color from Circle: " + playerColor);
                                        break;
                                    }
                                }
                                
                                javafx.scene.Node newView = buildPlayerView(label, playerColor, charMsg.playerId);
                                existingPlayer.getViewComponent().clearChildren();
                                existingPlayer.getViewComponent().addChild(newView);
                                System.out.println("[CLIENT] Rebuilt player view with new character");
                                
                                // 更新動畫組件的角色索引
                                PlayerAnimationComponent anim = existingPlayer.getComponentOptional(PlayerAnimationComponent.class).orElse(null);
                                if (anim != null) {
                                    anim.setCharacterIndex(charMsg.characterIndex);
                                    anim.refreshImageViewFromEntity();
                                    System.out.println("[CLIENT] Updated animation component to character " + charMsg.characterIndex);
                                }
                                
                                System.out.println("[CLIENT] Successfully updated existing player " + charMsg.playerId + " to character " + charMsg.characterIndex);
                            } else {
                                System.out.println("[CLIENT] No existing player entity found for " + charMsg.playerId + ", will use character " + charMsg.characterIndex + " when entity is created");
                            }
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
                            // 更新場上玩家的頭頂名稱
                            if (player != null) {
                                String label = getPlayerLabel(currentRoomInfo, myPlayerId);
                                updatePlayerLabel(player, label);
                            }
                            for (Map.Entry<String, Entity> entry : otherPlayers.entrySet()) {
                                String pid = entry.getKey();
                                Entity ent = entry.getValue();
                                String label = getPlayerLabel(currentRoomInfo, pid);
                                updatePlayerLabel(ent, label);
                            }
                            
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
                            startPlatform = createPlatform(50, SCREEN_HEIGHT - 150, 200, 30, Color.GREEN, "map picture/start.png");
                            endPlatform = createPlatform(FINISH_X - 200, SCREEN_HEIGHT - 150, 200, 30, Color.GOLD, "map picture/finish.png");
                            startPlatform.setVisible(false);
                            endPlatform.setVisible(false);
                            platformEntities.add(startPlatform);
                            platformEntities.add(endPlatform);
                            
                            // 在 finish 平台上方添加旗幟（無碰撞）
                            Image flagImage = loadPlatformImage("map picture/flag.png");
                            if (flagImage != null) {
                                ImageView flagView = new ImageView(flagImage);
                                flagView.setFitWidth(100);
                                flagView.setFitHeight(100);
                                flagView.setPreserveRatio(false);
                                
                                flagEntity = FXGL.entityBuilder()
                                        .at(FINISH_X - 150, SCREEN_HEIGHT - 150 - 100)
                                        .view(flagView)
                                        .buildAndAttach();
                                flagEntity.setVisible(false);  // 初始時隱藏
                            }
                            
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
                    // 在遊戲中時發送位置（不論可見與否，以便傳送死亡動畫狀態）
                    if (connected && uiState == UIState.PLAYING && player != null) {
                        synchronized (out) {
                            PlayerControl pc = player.getComponent(PlayerControl.class);
                            PlayerAnimationComponent animComp = player.getComponent(PlayerAnimationComponent.class);
                            String currentAnimState = (animComp != null) ? animComp.getState() : "idle";
                            // 從 ImageView 獲取正確的 scaleX（1 或 -1），而不是從 Entity Transform
                            double actualScaleX = 1.0;
                            if (animComp != null && animComp.getImageView() != null) {
                                actualScaleX = animComp.getImageView().getScaleX();
                            }
                            PlayerInfo info = new PlayerInfo(
                                myPlayerId, toHex(myColor),
                                player.getX(), player.getY(),
                                pc.isCrouching(),
                                actualScaleX,  // 使用 ImageView 的 scaleX
                                player.getTransformComponent().getScaleY(),
                                currentAnimState
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
                                currentRotation,
                                selectedObj.imagePath
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
            if (flagEntity != null) {
                flagEntity.setVisible(false);  // 隱藏旗幟
            }
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
            if (flagEntity != null) {
                flagEntity.setVisible(true);  // 顯示旗幟
            }
            // middlePlatform.setVisible(true);  // 註解掉灰色中間平台
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
                javafx.scene.shape.Rectangle rect = (javafx.scene.shape.Rectangle) preview.getViewComponent().getChildren().getFirst();
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
            String label = getPlayerLabel(currentRoomInfo, info.playerId);
            
            int charIdx = playerCharacters.getOrDefault(info.playerId, 1);
            System.out.println("[CLIENT] Creating other player " + info.playerId + " with character index: " + charIdx + " (from playerCharacters map)");
            
            javafx.scene.Node view = buildPlayerView(label, playerColor, info.playerId);
            
            PlayerAnimationComponent animComp = new PlayerAnimationComponent(charIdx);
            SmoothPlayerComponent smoothComponent = new SmoothPlayerComponent();
            
            otherPlayer = FXGL.entityBuilder()
                    .at(info.x, info.y)
                    .view(view)
                    .with(animComp)
                    .with(smoothComponent)
                    .buildAndAttach();
            
            // 立即設置初始的翻轉狀態
            smoothComponent.setTargetScaleX(info.scaleX);
            smoothComponent.setTargetScaleY(info.scaleY);
            animComp.setFlipX(info.scaleX);  // 直接設置初始翻轉
            System.out.println("[CLIENT] Set initial scaleX=" + info.scaleX + " for other player " + info.playerId);
            
            updatePlayerLabel(otherPlayer, label);
            otherPlayers.put(info.playerId, otherPlayer);
            System.out.println("[CLIENT] Created other player: " + info.playerId + " with character " + charIdx);
        } else if (otherPlayer != null) {
            SmoothPlayerComponent smoothComponent = otherPlayer.getComponent(SmoothPlayerComponent.class);
            smoothComponent.setTargetPosition(info.x, info.y);
            smoothComponent.setTargetScaleX(info.scaleX);  // 更新水平縮放（翻轉）
            smoothComponent.setTargetScaleY(info.scaleY);
            
            System.out.println("[CLIENT] Updated other player " + info.playerId + " scaleX=" + info.scaleX);
            
            // 更新動畫狀態
            PlayerAnimationComponent animComp = otherPlayer.getComponent(PlayerAnimationComponent.class);
            if (animComp != null && info.animationState != null && !info.animationState.isEmpty()) {
                animComp.setState(info.animationState);
            }
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
    
    private Entity createPlatform(double x, double y, double width, double height, Color color, String imagePath) {
        javafx.scene.Node view;
        if (imagePath != null && !imagePath.isBlank()) {
            Image img = loadPlatformImage(imagePath);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(width);
                iv.setFitHeight(height);
                iv.setPreserveRatio(false);
                view = iv;
            } else {
                view = new Rectangle(width, height, color);
            }
        } else {
            view = new Rectangle(width, height, color);
        }
        
        Entity platform = FXGL.entityBuilder()
                .at(x, y)
                .view(view)
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
    
    private Entity createPlatformWithRotation(double x, double y, double width, double height, Color color, double rotation, javafx.scene.Node view) {
        Entity platform = FXGL.entityBuilder()
                .at(x, y)
                .view(view)
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
        
        // 如果沒有 info，說明這是地圖平台（地圖編輯器產生），根據顏色決定類型並支援圖片材質
        if (info == null) {
            System.out.println("[CLIENT] No info found, treating as map platform with color: " + p.color +
                               (p.imagePath != null ? (", image=" + p.imagePath) : ""));
            
            // 根據顏色判斷平台類型
            if (p.color.equalsIgnoreCase("#FF0000") || p.color.equalsIgnoreCase("FF0000")) {
                // 紅色 = 死亡區
                System.out.println("[CLIENT] Creating DEATH platform from map");
                Node view = buildPlatformView(p, Color.RED);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
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
                Node view = buildPlatformView(p, Color.LIME);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new BouncePlatformComponent())
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            } else if (p.color.equalsIgnoreCase("#00AAFF")) {
                // 藍色 = 水平移動
                System.out.println("[CLIENT] Creating MOVING_H platform from map");
                Node view = buildPlatformView(p, Color.web("#00AAFF"));
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(true, 1.0, 100))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            } else if (p.color.equalsIgnoreCase("#AA00FF")) {
                // 紫色 = 垂直移動
                System.out.println("[CLIENT] Creating MOVING_V platform from map");
                Node view = buildPlatformView(p, Color.web("#AA00FF"));
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(false, 1.0, 100))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            } else {
                // 預設為普通平台
                System.out.println("[CLIENT] Creating NORMAL platform from map");
                Node view = buildPlatformView(p, color);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
        }
        
        // 有 info，說明是玩家選擇的物件
        System.out.println("[CLIENT] Creating player-selected object of type: " + info.type);
        switch (info.type) {
            case MOVING_H: {
                Node view = buildPlatformView(p, color);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(true, Math.max(0.5, info.moveSpeed), Math.max(50, info.moveRange)))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case MOVING_V: {
                Node view = buildPlatformView(p, color);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new MovingPlatformComponent(false, Math.max(0.5, info.moveSpeed), Math.max(50, info.moveRange)))
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case BOUNCE: {
                Node view = buildPlatformView(p, color);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new BouncePlatformComponent())
                        .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case ROTATING: {
                Node view = buildPlatformView(p, color);
                double rotationSpeed = info.moveSpeed > 0 ? info.moveSpeed : 60.0;
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
                        .with(new PlatformComponent(p.width, p.height))
                        .with(new RotatingPlatformComponent(rotationSpeed))
                        .buildAndAttach();
                e.getTransformComponent().setRotationOrigin(new Point2D(p.width / 2.0, p.height / 2.0));
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case TURRET: {
                // Turret 優先使用圖片，如果沒有則使用漸層
                javafx.scene.Node turretView;
                if (p.imagePath != null && !p.imagePath.isBlank()) {
                    Image img = loadPlatformImage(p.imagePath);
                    if (img != null) {
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(p.width);
                        iv.setFitHeight(p.height);
                        iv.setPreserveRatio(false);
                        turretView = iv;
                    } else {
                        // 圖片載入失敗，使用漸層
                        javafx.scene.shape.Rectangle turretBody = new javafx.scene.shape.Rectangle(p.width, p.height);
                        javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                            0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                            new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.YELLOW),
                            new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.RED)
                        );
                        turretBody.setFill(gradient);
                        turretBody.setStroke(javafx.scene.paint.Color.ORANGE);
                        turretBody.setStrokeWidth(2);
                        turretView = turretBody;
                    }
                } else {
                    // 無圖片路徑，使用漸層
                    javafx.scene.shape.Rectangle turretBody = new javafx.scene.shape.Rectangle(p.width, p.height);
                    javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                        0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                        new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.YELLOW),
                        new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.RED)
                    );
                    turretBody.setFill(gradient);
                    turretBody.setStroke(javafx.scene.paint.Color.ORANGE);
                    turretBody.setStrokeWidth(2);
                    turretView = turretBody;
                }
                
                e = FXGL.entityBuilder()
                    .at(p.x, p.y)
                    .view(turretView)
                    .with(new PlatformComponent(p.width, p.height))
                    .with(new TurretComponent(Math.max(0.8, info.fireRate)))
                    .buildAndAttach();
                e.setRotation(p.rotation);
                platformEntities.add(e);
                return e;
            }
            case DEATH: {
                // 死亡區也要可以踩，同時具有死亡檢測
                Node view = buildPlatformView(p, color);
                e = FXGL.entityBuilder()
                        .at(p.x, p.y)
                        .view(view)
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
                    // 保護起點和終點平台
                    if (platform == startPlatform || platform == endPlatform) {
                        continue;
                    }
                    
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
                    entity.setVisible(false);  // 先設為不可見
                    entity.removeFromWorld();  // 然後從世界移除
                    platformEntities.remove(entity);
                    deathZones.remove(entity);  // 如果是死亡區也要移除
                }
                
                System.out.println("[CLIENT] ERASER cleared " + toRemove.size() + " platforms/zones");
                return null;
            }
            case NORMAL:
            default: {
                Node view = buildPlatformView(p, color);
                return createPlatformWithRotation(p.x, p.y, p.width, p.height, color, p.rotation, view);
            }
        }
    }
    
    // 建立平台的視圖：若有自訂圖片則使用圖片，否則使用顏色矩形
    private Node buildPlatformView(PlatformPlacement p, Color fallbackColor) {
        if (p.imagePath != null && !p.imagePath.isBlank()) {
            // 檢查是否為瓷磚格式: "imagePath|srcX,srcY,srcWidth,srcHeight"
            if (p.imagePath.contains("|")) {
                String[] parts = p.imagePath.split("\\|");
                if (parts.length == 2) {
                    String actualPath = parts[0];
                    String[] coords = parts[1].split(",");
                    if (coords.length == 4) {
                        try {
                            int srcX = Integer.parseInt(coords[0]);
                            int srcY = Integer.parseInt(coords[1]);
                            int srcWidth = Integer.parseInt(coords[2]);
                            int srcHeight = Integer.parseInt(coords[3]);
                            
                            System.out.println("[CLIENT] Loading tile from: " + actualPath + 
                                             " at (" + srcX + "," + srcY + ") size " + srcWidth + "x" + srcHeight);
                            
                            Image img = loadPlatformImage(actualPath);
                            if (img != null) {
                                System.out.println("[CLIENT] Image loaded successfully, size: " + 
                                                 img.getWidth() + "x" + img.getHeight());
                                // 使用 WritableImage 從源圖片裁剪瓷磚
                                javafx.scene.image.PixelReader pixelReader = img.getPixelReader();
                                javafx.scene.image.WritableImage tileImage = new javafx.scene.image.WritableImage(
                                    pixelReader, srcX, srcY, srcWidth, srcHeight);
                                
                                ImageView iv = new ImageView(tileImage);
                                iv.setFitWidth(p.width);
                                iv.setFitHeight(p.height);
                                iv.setPreserveRatio(false);
                                System.out.println("[CLIENT] Tile view created successfully");
                                return iv;
                            } else {
                                System.out.println("[CLIENT] Failed to load image: " + actualPath);
                            }
                        } catch (Exception e) {
                            System.out.println("[CLIENT] Failed to create tile view: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("[CLIENT] Invalid coord format, expected 4 parts but got: " + coords.length);
                    }
                } else {
                    System.out.println("[CLIENT] Invalid imagePath format, expected 2 parts but got: " + parts.length);
                }
            } else {
                // 普通圖片路徑
                Image img = loadPlatformImage(p.imagePath);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(p.width);
                    iv.setFitHeight(p.height);
                    iv.setPreserveRatio(false);
                    return iv;
                }
            }
        }
        System.out.println("[CLIENT] Using fallback color for platform at (" + p.x + "," + p.y + ")");
        return new Rectangle(p.width, p.height, fallbackColor);
    }

    // 載入並快取平台圖片，避免重複 IO
    private Image loadPlatformImage(String path) {
        try {
            File file = new File(path);
            String key = file.getAbsolutePath();
            if (platformImageCache.containsKey(key)) {
                return platformImageCache.get(key);
            }
            if (!file.exists()) {
                System.out.println("[CLIENT] Image file not found: " + path);
                return null;
            }
            Image img = new Image(file.toURI().toString());
            if (img.isError()) {
                System.out.println("[CLIENT] Failed to load image: " + path);
                return null;
            }
            platformImageCache.put(key, img);
            return img;
        } catch (Exception ex) {
            System.out.println("[CLIENT] Error loading image (fallback to color): " + ex.getMessage());
            return null;
        }
    }
    
    private String toHex(Color color) {
        return "#%02X%02X%02X".formatted(
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255));
    }

    @Override
    protected void initInput() {
        FXGL.getInput().addAction(new UserAction("Move Left A") {
            @Override
            protected void onActionBegin() {
                if (player != null && currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).setLeftHeld(true);
                }
            }
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveLeft();
                    player.getComponent(PlayerControl.class).setLeftHeld(true);
                } else if (currentPhase == GamePhase.PLAYING && (hasFinished || hasFailed)) {
                    // 已完成/死亡的玩家可以移動攝影機觀戰
                    cameraOffsetX = Math.max(0, cameraOffsetX - 120);
                    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
                } else if ((currentPhase == GamePhase.PLACING || currentPhase == GamePhase.SELECTING) && myPlacement == null) {
                    cameraOffsetX = Math.max(0, cameraOffsetX - 120);
                    FXGL.getGameScene().getViewport().setX(cameraOffsetX);
                }
            }
            @Override
            protected void onActionEnd() {
                if (player != null) {
                    player.getComponent(PlayerControl.class).setLeftHeld(false);
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
            protected void onActionBegin() {
                if (player != null && currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).setLeftHeld(true);
                }
            }
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveLeft();
                }
            }
            @Override
            protected void onActionEnd() {
                if (player != null) {
                    player.getComponent(PlayerControl.class).setLeftHeld(false);
                }
            }
        }, KeyCode.LEFT);

        FXGL.getInput().addAction(new UserAction("Move Right D") {
            @Override
            protected void onActionBegin() {
                if (player != null && currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).setRightHeld(true);
                }
            }
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveRight();
                    player.getComponent(PlayerControl.class).setRightHeld(true);
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
            @Override
            protected void onActionEnd() {
                if (player != null) {
                    player.getComponent(PlayerControl.class).setRightHeld(false);
                }
            }
        }, KeyCode.D);
        
        FXGL.getInput().addAction(new UserAction("Move Right Arrow") {
            @Override
            protected void onActionBegin() {
                if (player != null && currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).setRightHeld(true);
                }
            }
            @Override
            protected void onAction() {
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).moveRight();
                }
            }
            @Override
            protected void onActionEnd() {
                if (player != null) {
                    player.getComponent(PlayerControl.class).setRightHeld(false);
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
                    selectedObj != null && (selectedObj.type == ObjectType.TURRET || selectedObj.type == ObjectType.DEATH || selectedObj.type == ObjectType.ROTATING)) {
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
                    selectedObj != null && (selectedObj.type == ObjectType.TURRET || selectedObj.type == ObjectType.DEATH || selectedObj.type == ObjectType.ROTATING)) {
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
                    // 檢查是否點擊 Finish 按鈕（UI 座標，不受鏡頭影響）
                    if (previewPlatform != null && !finishPane.getChildren().isEmpty()) {
                        Point2D uiPos = FXGL.getInput().getMousePositionUI();
                        double btnX = SCREEN_WIDTH - 250;
                        double btnY = SCREEN_HEIGHT - 100;
                        if (uiPos != null && uiPos.getX() >= btnX && uiPos.getX() <= btnX + 200 &&
                            uiPos.getY() >= btnY && uiPos.getY() <= btnY + 60) {
                            System.out.println("[CLIENT] Finish button clicked!");
                            confirmPlacement();
                            return;
                        }
                    }
                    
                    // 開始拖曳預覽平台（世界座標）
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
        // 使用 UI 座標並考慮 ScrollPane 的滾動偏移
        Point2D uiPos = FXGL.getInput().getMousePositionUI();
        if (uiPos == null) return;

        int startY = 40;
        int spacing = 150;

        double viewportH = selectionScroll.getViewportBounds().getHeight();
        double contentH = selectionPane.getHeight();
        double scrollOffset = selectionScroll.getVvalue() * Math.max(0, contentH - viewportH);

        double xInContent = uiPos.getX() - selectionScroll.getLayoutX();
        double yInContent = uiPos.getY() - selectionScroll.getLayoutY() + scrollOffset;
        handleObjectSelectionAtContent(xInContent, yInContent);
    }

    // 直接使用內容座標（已考慮滾動）判斷點擊
    private void handleObjectSelectionAtContent(double xInContent, double yInContent) {
        int startY = 40;
        int spacing = 150;
        
        for (int i = 0; i < availableObjects.size(); i++) {
            GameObjectInfo obj = availableObjects.get(i);
            int yPos = startY + i * spacing;
            
            double btnLeft = SCREEN_WIDTH / 2 - 200.0;
            double btnRight = SCREEN_WIDTH / 2 + 200.0;
            double btnTop = yPos;
            double btnBottom = yPos + 100;
            
            if (xInContent >= btnLeft && xInContent <= btnRight &&
                yInContent >= btnTop && yInContent <= btnBottom) {
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
            
            // 優先顯示圖片，如果有的話
            javafx.scene.Node viewNode;
            if (selectedObj.imagePath != null && !selectedObj.imagePath.isBlank()) {
                Image img = loadPlatformImage(selectedObj.imagePath);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(selectedObj.width);
                    iv.setFitHeight(selectedObj.height);
                    iv.setPreserveRatio(false);
                    viewNode = iv;
                } else {
                    // 圖片載入失敗，回退到顏色
                    Rectangle rect = new Rectangle(selectedObj.width, selectedObj.height, Color.web(selectedObj.color));
                    rect.setStroke(Color.YELLOW);
                    rect.setStrokeWidth(3);
                    viewNode = rect;
                }
            } else if (selectedObj.type == ObjectType.TURRET) {
                // 如果是砲臺,添加漸層效果
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
                viewNode = turretBody;
            } else if (selectedObj.type == ObjectType.ROTATING) {
                javafx.scene.shape.Rectangle rotatingRect = new javafx.scene.shape.Rectangle(
                    selectedObj.width, selectedObj.height);
                javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.web("#89C2FF")),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.web("#5E60CE"))
                );
                rotatingRect.setFill(gradient);
                rotatingRect.setStroke(javafx.scene.paint.Color.WHITE);
                rotatingRect.setStrokeWidth(3);
                rotatingRect.setRotate(-10);
                viewNode = rotatingRect;
            } else {
                // 一般平台，顯示顏色
                Rectangle rect = new Rectangle(selectedObj.width, selectedObj.height, Color.web(selectedObj.color));
                rect.setStroke(Color.YELLOW);
                rect.setStrokeWidth(3);
                viewNode = rect;
            }
            
            previewPlatform = FXGL.entityBuilder()
                    .at(x, y)
                    .view(viewNode)
                    .buildAndAttach();
            
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
                currentRotation,
                selectedObj.imagePath
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
            
            // 檢查房間是否只有一個玩家，如果是就不用等待
            if (currentRoomInfo != null && currentRoomInfo.playerIds.size() > 1) {
                phaseText.setText("Waiting for other players...");
            } else {
                phaseText.setText("Phase: RACE TO FINISH!");
            }
            hideFinishButton();
            isDragging = false;
            
            // 移除預覽平台
            if (previewPlatform != null) {
                previewPlatform.removeFromWorld();
                previewPlatform = null;
            }
            
            // 放置完成後鏡頭歸零
            cameraOffsetX = 0;
            FXGL.getGameScene().getViewport().setX(0);
        } else {
            System.err.println("[CLIENT ERROR] Cannot confirm: selectedObj=" + selectedObj + 
                             " previewPlatform=" + previewPlatform + " myPlacement=" + myPlacement);
        }
    }

    /**
     * 更新視差背景位置（根據攝影機偏移）
     * 確保背景始終填滿整個可視區域
     */
    private void updateParallaxBackgrounds() {
        if (backgroundLayersLeft.isEmpty() || backgroundLayersRight.isEmpty()) return;
        
        // 視差係數使用常數設定
        double tileW = SCREEN_WIDTH;
        
        for (int i = 0; i < PARALLAX_FACTORS.length; i++) {
            double factor = PARALLAX_FACTORS[i];
            double parallaxOffset = cameraOffsetX * factor;
            
            // 計算螢幕座標的偏移，確保以兩個圖塊無縫覆蓋整個螢幕
            double offsetOnScreen = - (parallaxOffset % tileW);
            if (offsetOnScreen > 0) {
                offsetOnScreen -= tileW; // 保持在 [-tileW, 0]
            }
            
            double leftWorldX = cameraOffsetX + offsetOnScreen;
            double rightWorldX = leftWorldX + tileW;
            
            // 更新對應圖層的左右圖塊位置
            Entity leftEntity = backgroundLayersLeft.get(i);
            Entity rightEntity = backgroundLayersRight.get(i);
            leftEntity.setX(leftWorldX);
            rightEntity.setX(rightWorldX);
        }
    }
    
    @Override
    protected void onUpdate(double tpf) {
        // FPS 計算
        frameCount++;
        fpsCounter += tpf;
        if (fpsCounter >= 1.0) {
            if (fpsText != null) {
                fpsText.setText("FPS: " + frameCount);
            }
            frameCount = 0;
            fpsCounter = 0;
        }
        
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
        
        // 更新視差背景位置
        updateParallaxBackgrounds();
        
        // FPS 計數器隨著攝影機移動（跟著玩家）
        if (fpsText != null) {
            double fpsLayoutX = SCREEN_WIDTH - 150;
            fpsText.setLayoutX(fpsLayoutX);
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
                        // 播放死亡動畫
                        PlayerAnimationComponent animComp = player.getComponent(PlayerAnimationComponent.class);
                        if (animComp != null) {
                            animComp.setState("death");
                            System.out.println("[CLIENT] Death animation triggered (bullet hit)!");
                        }
                        // 開始恢復計時
                        deathRecoveryTimer = 2.0;
                        // 禁用玩家移動,但允許觀戰
                        player.getComponent(PlayerControl.class).setEnabled(false);
                        System.out.println("[CLIENT] Hit by bullet! Playing death animation, can spectate with A/D");
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
                // 恢復計時器結束（玩家保持在death動畫最後一幀）
                deathRecoveryTimer = 0;
                System.out.println("[CLIENT] Player death animation complete (still failed this round)!");
            }
        }
        
        // 檢查是否站在死亡平台上(只在未處於死亡恢復狀態時檢查)
        if (deathRecoveryTimer <= 0 && !hasFailed) {
            PlayerControl pc = player.getComponent(PlayerControl.class);
            boolean isStandingOnDeathPlatform = false;
            
            // 檢查玩家是否站在死亡平台上
            for (Entity zone : deathZones) {
                if (!zone.isVisible()) continue;
                
                // 使用矩形碰撞：玩家中心/半寬半高，平台寬高
                double halfW = pc.getCurrentHalfWidth();
                double halfH = pc.getCurrentHalfHeight();
                double playerBottom = playerY + halfH;
                double platformTop = zone.getY();
                double platformLeft = zone.getX();
                double platformRight = zone.getX() + (zone.hasComponent(DeathZoneComponent.class) ? 
                                                     zone.getComponent(DeathZoneComponent.class).width : 0);
                
                boolean horizontallyInside = (playerX + halfW > platformLeft && playerX - halfW < platformRight);
                boolean touchingTop = Math.abs(playerBottom - platformTop) < 5 && pc.velocityY >= 0;
                
                if (horizontallyInside && touchingTop) {
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
                    // 播放死亡動畫
                    PlayerAnimationComponent animComp = player.getComponent(PlayerAnimationComponent.class);
                    if (animComp != null) {
                        animComp.setState("death");
                        System.out.println("[CLIENT] Death animation triggered!");
                    }
                    // 開始恢復計時（不壓扁玩家）
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
        
        // 只有當玩家在地面上且在終點平台的正上方時才觸發完成
        PlayerControl playerControl = player.getComponent(PlayerControl.class);
        if (playerControl.isOnGround() &&
            playerX >= endX && playerX <= endX + 200 &&
            playerY >= endY - 100 && playerY <= endY) {
            
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
                // 播放死亡動畫
                PlayerAnimationComponent animComp = player.getComponent(PlayerAnimationComponent.class);
                if (animComp != null) {
                    animComp.setState("death");
                    System.out.println("[CLIENT] Death animation triggered (fell off map)!");
                }
                // 開始恢復計時
                deathRecoveryTimer = 2.0;
                // 禁用玩家移動,但允許觀戰
                player.getComponent(PlayerControl.class).setEnabled(false);
                System.out.println("[CLIENT] Failed - fell off map (y=" + playerY + "), playing death animation, can spectate with A/D");
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
    private double targetScaleX = 1.0;
    private double targetScaleY = 1.0;
    private final double SMOOTHING = 0.3;

    public SmoothPlayerComponent() {
        this.targetPosition = Point2D.ZERO;
    }

    public void setTargetPosition(double x, double y) {
        this.targetPosition = new Point2D(x, y);
    }

    public void setTargetScaleX(double scaleX) {
        this.targetScaleX = scaleX;
        System.out.println("[SMOOTH COMPONENT] setTargetScaleX: " + scaleX);
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

        // 直接設置 ImageView 的翻轉（不通過 Entity Transform）
        // 因為 Entity Transform 的 scaleX 會影響所有子節點，造成視覺問題
        PlayerAnimationComponent animComp = entity.getComponentOptional(PlayerAnimationComponent.class).orElse(null);
        if (animComp != null) {
            animComp.setFlipX(targetScaleX);  // 直接設置 ImageView 的 scaleX
        }

        // 平滑更新垂直縮放（下蹲）
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
     * 碰撞檢測：玩家使用寬/高的一半 (半寬、半高)，平台為矩形。
     */
    public CollisionInfo checkCollision(double playerX, double playerY, double halfW, double halfH, double velocityY) {
        // 如果平台有旋轉，使用旋轉碰撞（仍以近似圓形 radius 處理）
        if (Math.abs(entity.getRotation()) > 0.1) {
            double radius = Math.max(halfW, halfH);
            return checkRotatedCollision(playerX, playerY, radius, velocityY);
        }
        
        // 無旋轉：AABB vs AABB
        double platformLeft = entity.getX();
        double platformRight = entity.getX() + width;
        double platformTop = entity.getY();
        double platformBottom = entity.getY() + height;

        double playerLeft = playerX - halfW;
        double playerRight = playerX + halfW;
        double playerTop = playerY - halfH;
        double playerBottom = playerY + halfH;

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
    // 精確：圓 vs 旋轉矩形，回傳最小滲透方向，並偏好從上方落地
    double angleDeg = entity.getRotation();
    double centerX = entity.getX() + width / 2.0;
    double centerY = entity.getY() + height / 2.0;
    double angle = Math.toRadians(-angleDeg);

    // 取玩家圓心，轉換到平台局部坐標
    double dx = playerX - centerX;
    double dy = playerY - centerY;
    double localX = dx * Math.cos(angle) - dy * Math.sin(angle);
    double localY = dx * Math.sin(angle) + dy * Math.cos(angle);
    
    // 計算矩形半寬半高
    double halfW = width / 2.0;
    double halfH = height / 2.0;
    
    // 檢查玩家圓心是否在矩形內（考慮圓半徑）
    if (Math.abs(localX) > halfW + radius || Math.abs(localY) > halfH + radius) {
        return new CollisionInfo(false, CollisionSide.NONE);
    }
    
    double overlapX = halfW - Math.abs(localX);
    double overlapY = halfH - Math.abs(localY);

    CollisionSide side;
    boolean falling = velocityY >= 0;
    // 若玩家在矩形上半部且落下，優先當作頂面著陸
    if (falling && localY <= 0 && overlapY <= overlapX + 4) {
        side = CollisionSide.TOP;
    } else if (overlapX < overlapY) {
        side = (localX < 0) ? CollisionSide.LEFT : CollisionSide.RIGHT;
    } else {
        side = (localY < 0) ? CollisionSide.TOP : CollisionSide.BOTTOM;
    }

    return new CollisionInfo(true, side);
    }
}

/**
 * 修復後的玩家控制 - 放在 GameClient.java 中
 */
class PlayerControl extends Component {
    private double speed = 420.0;  // 減低移動靈敏度：最大水平速度降低到 420 px/s
    private double velocityX = 0;
    public double velocityY = 0;  // 改為public以便死亡檢測使用
    private double jumpStrength = 650.0;  // 改為 pixels/second (增加跳躍力度至 650，讓跳躍更有力不飄)
    private double gravity = 1400.0;  // 增加重力，使下落更明顯
    private double gravityUp = 1000.0;  // 上升時使用較輕的重力
    // 水平加速度，用於平滑按鍵響應（降低靈敏度）
    private double horizontalAccel = 3600.0; // px/s^2
    // Fixed timestep / accumulator to keep physics deterministic across different FPS/GPU
    private double physicsAccumulator = 0.0;
    private static final double FIXED_DT = 1.0 / 60.0; // 60 Hz physics
    private static final double MAX_TPF = 0.1; // clamp very large frame times
    private boolean onGround = false;
    private boolean crouching = false;
    private List<Entity> platforms;
    // 碰撞盒的一半尺寸（寬 40，高 112 -> 半寬 20，半高 56）
    private double playerHalfWidth = 20;
    private double playerHalfHeight = 56;
    private boolean enabled = true;  // 控制玩家是否可移動
    private PlayerAnimationComponent animComponent = null;  // 動畫組件參考
    private int facingDirection = 1;  // 1 = 右邊，-1 = 左邊，用於 flip 邏輯
    private javafx.scene.text.Text nameText = null;  // 玩家名字文本參考，用於逆向翻轉
    private javafx.scene.Node bodyNode = null;  // 身體節點（ImageView 或 Circle），用於翻轉
    private javafx.scene.image.ImageView imageView = null;  // ImageView 引用，用於翻轉角色圖像
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private double walkGraceTimer = 0.0;  // 短暫保持 walk 狀態的緩衝，避免切回 idle 閃爍
    
    private final double MAX_VELOCITY_Y = 700.0;  // 改為 pixels/second (增加至 700，配合更強的重力)

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
        facingDirection = 1;  // 重置為向右
        
        // 重置 ImageView 翻轉
        if (imageView != null) {
            imageView.setScaleX(1);  // 恢復正常
        }
    }

    public double getCurrentHalfWidth() {
        double scale = Math.max(entity.getTransformComponent().getScaleX(), entity.getTransformComponent().getScaleY());
        return playerHalfWidth * scale;
    }

    public double getCurrentHalfHeight() {
        double scale = Math.max(entity.getTransformComponent().getScaleX(), entity.getTransformComponent().getScaleY());
        return playerHalfHeight * scale;
    }
   @Override
public void onUpdate(double tpf) {
    if (!enabled) return;  // 如果禁用，不處理任何移動

    // 保護極端 tpf（避免暫停後大跳躍）
    if (tpf > MAX_TPF) tpf = MAX_TPF;

    physicsAccumulator += tpf;

    // 針對不同硬體的差異，使用固定物理時間步，確保重力/跳躍一致
    while (physicsAccumulator >= FIXED_DT) {
        physicsStep(FIXED_DT);
        physicsAccumulator -= FIXED_DT;
    }

    // 更新動畫與 UI 相關（使用原始 tpf 使動畫平滑）
    if (animComponent != null) {
        boolean horizontalInput = leftHeld || rightHeld;

        if (horizontalInput) {
            walkGraceTimer = 0.0;
        } else {
            walkGraceTimer += tpf;
        }

        if (!onGround) {
            if (velocityY < 0) animComponent.setState("jump"); else animComponent.setState("fall");
        } else if (horizontalInput || walkGraceTimer < 0.15) {
            animComponent.setState("walk");
        } else {
            animComponent.setState("idle");
        }
    }
}

    /**
     * 執行一個固定時間步的物理更新
     */
    private void physicsStep(double dt) {
        // horizontal input 驅動（使用加速度平滑，降低靈敏度）
        double inputX = 0;
        if (leftHeld) inputX -= 1;
        if (rightHeld) inputX += 1;
        
        // 根據實際移動方向更新翻轉（只在有單向輸入時更新）
        if (inputX < 0) {
            setFacing(-1);  // 面向左邊
        } else if (inputX > 0) {
            setFacing(1);  // 面向右邊
        }
        // 如果 inputX == 0（沒有按鍵或同時按左右），保持當前朝向不變
        
        double targetVx = inputX * speed;
        double maxDelta = horizontalAccel * dt;
        double delta = targetVx - velocityX;
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        velocityX += delta;

        // 計算當前重力（以固定步長套用）
        double currentGravity;
        if (onGround && velocityY >= 0) {
            currentGravity = gravity * 0.3; // 著陸時較輕
        } else if (velocityY < 0) {
            currentGravity = gravityUp; // 上升時較輕
        } else {
            currentGravity = gravity; // 下落時較重
        }

        velocityY += currentGravity * dt;
        if (velocityY > MAX_VELOCITY_Y) velocityY = MAX_VELOCITY_Y;
        if (velocityY < -MAX_VELOCITY_Y) velocityY = -MAX_VELOCITY_Y;

        // X 移動與碰撞
        double halfW = getCurrentHalfWidth();
        double halfH = getCurrentHalfHeight();
        double oldX = entity.getX();
        entity.setX(entity.getX() + velocityX * dt);

        boolean xCollision = false;
        for (Entity platform : platforms) {
            if (!platform.hasComponent(PlatformComponent.class)) continue;
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), halfW, halfH, velocityY);
            if (collision.collided && (collision.side == CollisionSide.LEFT || collision.side == CollisionSide.RIGHT)) {
                entity.setX(oldX);
                velocityX = 0;
                xCollision = true;
                break;
            }
        }

        // Y 移動與碰撞
        double oldY = entity.getY();
        entity.setY(entity.getY() + velocityY * dt);
        onGround = false;

        for (Entity platform : platforms) {
            if (!platform.hasComponent(PlatformComponent.class)) continue;
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), halfW, halfH, velocityY);
            if (collision.collided) {
                switch (collision.side) {
                    case TOP -> {
                        entity.setY(platform.getY() - halfH);
                        if (velocityY > 0) velocityY = 0;
                        onGround = true;

                        if (platform.hasComponent(MovingPlatformComponent.class)) {
                            MovingPlatformComponent mp = platform.getComponent(MovingPlatformComponent.class);
                            boolean playerIdle = Math.abs(velocityX) < 1e-3;
                            if (playerIdle) {
                                double carry = 0.6;
                                entity.setX(entity.getX() + mp.getDeltaX() * carry);
                                entity.setY(entity.getY() + mp.getDeltaY() * carry);
                            }
                        }

                        if (platform.hasComponent(BouncePlatformComponent.class)) {
                            BouncePlatformComponent bounce = platform.getComponent(BouncePlatformComponent.class);
                            velocityY = -bounce.getBounceStrength();
                            onGround = false;
                        }
                    }
                    case BOTTOM -> {
                        entity.setY(oldY);
                        if (velocityY < 0) velocityY = 0;
                    }
                    case LEFT, RIGHT -> {
                        if (!xCollision) {
                            entity.setX(oldX);
                            velocityX = 0;
                        }
                    }
                }
            }
        }

        // 邊界檢查
        double leftBoundary = halfW;
        double rightBoundary = 5000 - halfW;
        double topBoundary = halfH;
        if (entity.getX() < leftBoundary) { entity.setX(leftBoundary); velocityX = 0; }
        if (entity.getX() > rightBoundary) { entity.setX(rightBoundary); velocityX = 0; }
        if (entity.getY() < topBoundary) { entity.setY(topBoundary); velocityY = 0; }
    }

    public void moveLeft() {
        velocityX = -speed;
        setFacing(-1);  // 面向左邊
    }
    public void moveRight() {
        velocityX = speed;
        setFacing(1);  // 面向右邊
    }

    public void setLeftHeld(boolean held) {
        leftHeld = held;
    }

    public void setRightHeld(boolean held) {
        rightHeld = held;
    }
    
    private void setFacing(int direction) {
        if (facingDirection != direction) {
            facingDirection = direction;
            // 只改變 ImageView 的 scaleX，不改變整個 entity
            // 這樣 nameText 就不會受到影響
            // 每次都從 animationComponent 獲取最新的 imageView
            if (animComponent != null) {
                animComponent.setFlipX(direction);  // 通過動畫組件設置翻轉
                System.out.println("[PLAYER CONTROL] Set facing to " + (direction == 1 ? "RIGHT" : "LEFT") + " via animComponent");
            } else {
                ImageView currentImageView = imageView;
                if (currentImageView != null) {
                    currentImageView.setScaleX(direction);
                    System.out.println("[PLAYER CONTROL] Set facing to " + (direction == 1 ? "RIGHT" : "LEFT") + ", scaleX=" + currentImageView.getScaleX());
                } else {
                    System.err.println("[PLAYER CONTROL] Cannot set facing: imageView is null!");
                }
            }
        }
    }
    
    public int getFacingDirection() {
        return facingDirection;
    }

    public void jump() {
        if (onGround) {
            velocityY = -jumpStrength;
            onGround = false;
        }
    }
    
    public void setAnimationComponent(PlayerAnimationComponent animComp) {
        this.animComponent = animComp;
        // 從動畫組件獲取 ImageView 引用
        if (animComp != null) {
            this.imageView = animComp.getImageView();
        }
    }
    
    public void setNameText(javafx.scene.text.Text nameText) {
        this.nameText = nameText;
    }
    
    public void setBodyNode(javafx.scene.Node bodyNode) {
        this.bodyNode = bodyNode;
    }

    public void crouch(boolean crouching) {
        this.crouching = crouching;
        entity.getTransformComponent().setScaleY(crouching ? 0.5 : 1.0);
    }
    
    public boolean isCrouching() {
        return crouching;
    }
    
    public boolean isOnGround() {
        return onGround;
    }
}
// 移動平台組件
class MovingPlatformComponent extends Component {
    private double startX, startY;
    private double moveSpeed, moveRange;
    private boolean horizontal;
    private double elapsed = 0;
    private double lastX, lastY;
    private double deltaX, deltaY;
    
    public MovingPlatformComponent(boolean horizontal, double speed, double range) {
        this.horizontal = horizontal;
        this.moveSpeed = speed;
        this.moveRange = range;
    }
    
    @Override
    public void onAdded() {
        startX = entity.getX();
        startY = entity.getY();
        lastX = startX;
        lastY = startY;
    }
    
    @Override
    public void onUpdate(double tpf) {
        elapsed += tpf;
        double offset = Math.sin(elapsed * moveSpeed) * moveRange / 2;
        double newX = horizontal ? startX + offset : entity.getX();
        double newY = horizontal ? entity.getY() : startY + offset;
        
        entity.setX(newX);
        entity.setY(newY);

        deltaX = entity.getX() - lastX;
        deltaY = entity.getY() - lastY;
        lastX = entity.getX();
        lastY = entity.getY();
    }

    public double getDeltaX() { return deltaX; }
    public double getDeltaY() { return deltaY; }
    public boolean isHorizontal() { return horizontal; }
}

// 旋轉平台組件
class RotatingPlatformComponent extends Component {
    private final double rotationSpeedDeg;

    public RotatingPlatformComponent(double rotationSpeedDeg) {
        this.rotationSpeedDeg = rotationSpeedDeg;
    }

    @Override
    public void onAdded() {
        entity.getTransformComponent().setRotationOrigin(new Point2D(entity.getWidth() / 2.0, entity.getHeight() / 2.0));
    }

    @Override
    public void onUpdate(double tpf) {
        entity.rotateBy(rotationSpeedDeg * tpf);
    }
}

// 彈跳平台組件
class BouncePlatformComponent extends Component {
    private static final double BOUNCE_STRENGTH = 800.0;  // pixels/second，增加彈跳力度
    
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

        // 以平台碰撞尺寸的中心作為發射點，避免視覺上從左上角出彈
        double w = entity.hasComponent(PlatformComponent.class)
            ? entity.getComponent(PlatformComponent.class).getWidth()
            : entity.getWidth();
        double h = entity.hasComponent(PlatformComponent.class)
            ? entity.getComponent(PlatformComponent.class).getHeight()
            : entity.getHeight();

        double cx = entity.getX() + w / 2.0;
        double cy = entity.getY() + h / 2.0;

        Entity bulletEntity = FXGL.entityBuilder()
            .at(cx, cy)
            .view(bullet)
            .with(new BulletComponent(-1, 0)) // 一律往左射出
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

// 碰撞方向枚舉
enum CollisionSide {
    NONE, TOP, BOTTOM, LEFT, RIGHT
}

// 碰撞資訊類別
class CollisionInfo {
    boolean collided;
    CollisionSide side;
    
    public CollisionInfo(boolean collided, CollisionSide side) {
        this.collided = collided;
        this.side = side;
    }
}

// 玩家動畫組件
class PlayerAnimationComponent extends Component {
    private int characterIndex;  // 1, 2, 3
    private String currentState = "idle";  // idle, walk, jump, fall, death
    private List<Image> idleFrames = new ArrayList<>();
    private List<Image> walkFrames = new ArrayList<>();
    private List<Image> jumpFrames = new ArrayList<>();
    private List<Image> fallFrames = new ArrayList<>();
    private List<Image> deathFrames = new ArrayList<>();
    private int currentFrame = 0;
    private double frameTimer = 0;
    // 調整幀間隔：將 idle 動畫改為更慢的速度，避免太快看不清
    // 0.20秒/幀 ≈ 5 FPS，比較容易辨識
    private double frameInterval = 0.20;
    private double walkFrameInterval = 0.12;  // 稍快一點的行走幀率
    private double jumpFrameInterval = 0.20;  // jump 幀速率
    private double fallFrameInterval = 0.20;  // fall 幀速率
    private double deathFrameInterval = 0.15;  // death 幀速率（稍快以顯示死亡動畫）
    // idle 動畫只循環 1~8，跳過第 0 幀（空白的 player1.png）
    private ImageView imageView;
    private double currentFlipX = 1.0;  // 記住當前的翻轉狀態
    
    public PlayerAnimationComponent(int characterIndex) {
        this.characterIndex = characterIndex;
        System.out.println("[ANIMATION] Creating PlayerAnimationComponent for player" + characterIndex);
    }
    
    @Override
    public void onAdded() {
        super.onAdded();
        // 從 Entity 的 ViewComponent 中提取 ImageView
        try {
            javafx.scene.Node viewNode = entity.getViewComponent().getChildren().get(0);
            System.out.println("[ANIMATION] ViewComponent child 0 type: " + (viewNode != null ? viewNode.getClass().getName() : "NULL"));
            
            if (viewNode instanceof javafx.scene.Group g) {
                System.out.println("[ANIMATION] Found Group with " + g.getChildren().size() + " children");
                for (javafx.scene.Node child : g.getChildren()) {
                    System.out.println("[ANIMATION]   - Child type: " + child.getClass().getName());
                    if (child instanceof ImageView iv) {
                        imageView = iv;
                        System.out.println("[ANIMATION] Found ImageView in Group! Size: " + iv.getFitWidth() + "x" + iv.getFitHeight());
                        break;
                    }
                }
            } else if (viewNode instanceof ImageView iv) {
                imageView = iv;
                System.out.println("[ANIMATION] Found direct ImageView! Size: " + iv.getFitWidth() + "x" + iv.getFitHeight());
            }
        } catch (Exception e) {
            System.err.println("[ANIMATION] Failed to extract ImageView: " + e.getMessage());
            e.printStackTrace();
        }
        
        if (imageView != null) {
            // 確保 ImageView 的初始設定正確
            imageView.setFitWidth(128);
            imageView.setFitHeight(128);
            imageView.setPreserveRatio(true);
            imageView.setTranslateX(-64);
            imageView.setTranslateY(-64);
            imageView.setScaleX(1);  // 初始面向右邊
            System.out.println("[ANIMATION] ImageView initialized: " + imageView.getFitWidth() + "x" + imageView.getFitHeight() + ", facing RIGHT (scaleX=1)");
            
            loadIdleAnimation();
            loadWalkAnimation();
            loadJumpAnimation();
            loadFallAnimation();
            loadDeathAnimation();
        } else {
            System.err.println("[ANIMATION] ImageView is still NULL after onAdded!");
        }
    }
    
    private void loadIdleAnimation() {
        idleFrames.clear();
        // 加載 player{characterIndex}/idle/ 中的所有圖片
        for (int i = 1; i <= 9; i++) {  // idle 有 9 幀
            try {
                String path = "file:map picture/player" + characterIndex + "/idle/player" + i + ".png";
                Image img = new Image(path);
                idleFrames.add(img);
                System.out.println("[ANIMATION] Loaded idle frame " + i + ": " + path);
            } catch (Exception e) {
                System.err.println("[ANIMATION] Failed to load idle frame " + i + ": " + e.getMessage());
            }
        }
        System.out.println("[ANIMATION] Loaded " + idleFrames.size() + " idle frames for player" + characterIndex);

        // 設定初始顯示為第 0 幀，並確保不透明且可見，且要確保是 idle 狀態
        currentState = "idle";
        currentFrame = 0;
        frameTimer = 0;
        if (imageView != null && !idleFrames.isEmpty()) {
            Image first = idleFrames.get(0);
            if (first != null && first.getWidth() > 0 && first.getHeight() > 0) {
                imageView.setImage(first);
                imageView.setOpacity(1.0);
                imageView.setVisible(true);
                imageView.setFitWidth(128);
                imageView.setFitHeight(128);
                imageView.setPreserveRatio(true);
                imageView.setTranslateX(-64);
                imageView.setTranslateY(-64);
                System.out.println("[ANIMATION] Initial state set to idle, frame 0 displayed");
            } else {
                System.err.println("[ANIMATION] Idle frame 0 is null or empty!");
            }
        }
    }

    private void loadWalkAnimation() {
        walkFrames.clear();
        // walk 幀命名為 player10~player19，無空白幀
        for (int i = 10; i <= 19; i++) {
            try {
                String path = "file:map picture/player" + characterIndex + "/walk/player" + i + ".png";
                Image img = new Image(path);
                walkFrames.add(img);
                System.out.println("[ANIMATION] Loaded walk frame " + i + ": " + path);
            } catch (Exception e) {
                System.err.println("[ANIMATION] Failed to load walk frame " + i + ": " + e.getMessage());
            }
        }
        System.out.println("[ANIMATION] Loaded " + walkFrames.size() + " walk frames for player" + characterIndex);
    }

    private void loadJumpAnimation() {
        jumpFrames.clear();
        // jump 幀：player20
        String path = "file:map picture/player" + characterIndex + "/jump/player20.png";
        System.out.println("[ANIMATION] Attempting to load jump frame from: " + path);
        try {
            Image img = new Image(path, false);  // 使用同步載入
            
            // 等待圖片載入完成
            if (img.isError()) {
                System.err.println("[ANIMATION] Jump image loading error: " + img.getException());
                img.getException().printStackTrace();
            } else {
                // 檢查尺寸
                double w = img.getWidth();
                double h = img.getHeight();
                System.out.println("[ANIMATION] Jump image dimensions: " + w + "x" + h);
                
                // 先加入，即使尺寸為 0（之後會在使用時處理）
                jumpFrames.add(img);
                if (w == 0 || h == 0) {
                    System.err.println("[ANIMATION] Warning: Jump image has zero dimensions but added to list!");
                } else {
                    System.out.println("[ANIMATION] Loaded jump frame successfully!");
                }
            }
        } catch (Exception e) {
            System.err.println("[ANIMATION] Exception loading jump frame: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[ANIMATION] Total jump frames loaded: " + jumpFrames.size());
    }

    private void loadFallAnimation() {
        fallFrames.clear();
        // fall 幀：player21
        String path = "file:map picture/player" + characterIndex + "/fall/player21.png";
        System.out.println("[ANIMATION] Attempting to load fall frame from: " + path);
        try {
            Image img = new Image(path, false);  // 使用同步載入
            
            // 等待圖片載入完成
            if (img.isError()) {
                System.err.println("[ANIMATION] Fall image loading error: " + img.getException());
                img.getException().printStackTrace();
            } else {
                // 檢查尺寸
                double w = img.getWidth();
                double h = img.getHeight();
                System.out.println("[ANIMATION] Fall image dimensions: " + w + "x" + h);
                
                // 先加入，即使尺寸為 0（之後會在使用時處理）
                fallFrames.add(img);
                if (w == 0 || h == 0) {
                    System.err.println("[ANIMATION] Warning: Fall image has zero dimensions but added to list!");
                } else {
                    System.out.println("[ANIMATION] Loaded fall frame successfully!");
                }
            }
        } catch (Exception e) {
            System.err.println("[ANIMATION] Exception loading fall frame: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[ANIMATION] Total fall frames loaded: " + fallFrames.size());
    }
    
    private void loadDeathAnimation() {
        deathFrames.clear();
        // death 幀：player22-26（5幀）
        for (int i = 22; i <= 26; i++) {
            String path = "file:map picture/player" + characterIndex + "/death/player" + i + ".png";
            System.out.println("[ANIMATION] Attempting to load death frame " + i + " from: " + path);
            try {
                Image img = new Image(path, false);  // 使用同步載入
                
                if (img.isError()) {
                    System.err.println("[ANIMATION] Death image " + i + " loading error: " + img.getException());
                    img.getException().printStackTrace();
                } else {
                    double w = img.getWidth();
                    double h = img.getHeight();
                    System.out.println("[ANIMATION] Death frame " + i + " dimensions: " + w + "x" + h);
                    
                    if (w == 0 || h == 0) {
                        System.err.println("[ANIMATION] Death frame " + i + " has zero dimensions!");
                    } else {
                        deathFrames.add(img);
                        System.out.println("[ANIMATION] Loaded death frame " + i + " successfully!");
                    }
                }
            } catch (Exception e) {
                System.err.println("[ANIMATION] Exception loading death frame " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("[ANIMATION] Total death frames loaded: " + deathFrames.size());
    }
    
    @Override
    public void onUpdate(double tpf) {
        if (imageView == null) {
            return;  // 沒有 ImageView，無法更新動畫
        }
        
        List<Image> frames = getFramesForState(currentState);
        if (frames.isEmpty()) {
            System.err.println("[ANIMATION] No frames for state: " + currentState);
            return;
        }

        // 防禦：若 currentFrame 超界，重置到迴圈起點
        if (currentFrame < 0 || currentFrame >= frames.size()) {
            currentFrame = getLoopStartIndex(currentState, frames);
        }

        frameTimer += tpf;
        double interval = getIntervalForState(currentState);
        if (frameTimer < interval) {
            return;
        }

        frameTimer -= interval;
        int loopStart = getLoopStartIndex(currentState, frames);
        
        // death 狀態不循環，播放完停在最後一幀
        if (currentState.equals("death")) {
            if (currentFrame < frames.size() - 1) {
                currentFrame++;
            }
            // 否則停在最後一幀，不再更新
        } else if (currentState.equals("jump") || currentState.equals("fall")) {
            // jump 和 fall 是單幀動畫，不需要循環更新
            // 保持在當前幀即可
        } else {
            // idle 和 walk 狀態正常循環
            currentFrame = (currentFrame + 1) % frames.size();
        }
        
        // 若當前幀無圖片或尺寸為 0，跳到下一個非空幀
        Image currentImage = frames.get(currentFrame);
        if (currentImage == null || currentImage.getWidth() == 0 || currentImage.getHeight() == 0) {
            System.err.println("[ANIMATION] Frame " + currentFrame + " for state " + currentState + " is empty or null");
            currentFrame = firstNonEmptyFrameIndex(frames);
            if (currentFrame >= frames.size()) {
                System.err.println("[ANIMATION] Could not find non-empty frame in state: " + currentState);
                return;
            }
            currentImage = frames.get(currentFrame);
        }

        imageView.setImage(currentImage);
        imageView.setOpacity(1.0);
        imageView.setVisible(true);
        imageView.setFitWidth(128);
        imageView.setFitHeight(128);
        imageView.setPreserveRatio(true);
        imageView.setTranslateX(-64);
        imageView.setTranslateY(-64);
        imageView.setScaleX(currentFlipX);  // 恢復翻轉狀態
    }
    
    public void setState(String newState) {
        if (!newState.equals(currentState)) {
            System.out.println("[ANIMATION] setState called: " + currentState + " -> " + newState);
            this.currentState = newState;
            List<Image> frames = getFramesForState(newState);
            System.out.println("[ANIMATION] Frames available for " + newState + ": " + frames.size());
            
            // 特別檢查 jump 和 fall
            if (newState.equals("jump") || newState.equals("fall")) {
                System.out.println("[ANIMATION] " + newState + " frames list: " + frames);
                if (!frames.isEmpty()) {
                    System.out.println("[ANIMATION] First frame in " + newState + ": " + frames.get(0) + 
                        ", width=" + (frames.get(0) != null ? frames.get(0).getWidth() : "null") +
                        ", height=" + (frames.get(0) != null ? frames.get(0).getHeight() : "null"));
                }
            }
            
            this.currentFrame = getLoopStartIndex(newState, frames);
            this.frameTimer = 0;
            if (!frames.isEmpty() && imageView != null) {
                Image img = frames.get(currentFrame);
                if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                    imageView.setImage(img);
                    imageView.setOpacity(1.0);
                    imageView.setVisible(true);
                    imageView.setFitWidth(128);
                    imageView.setFitHeight(128);
                    imageView.setPreserveRatio(true);
                    imageView.setTranslateX(-64);
                    imageView.setTranslateY(-64);
                    imageView.setScaleX(currentFlipX);  // 恢復翻轉狀態
                    System.out.println("[ANIMATION] Successfully changed to " + newState + " (frame " + currentFrame + "/" + frames.size() + ", size: " + img.getWidth() + "x" + img.getHeight() + ")");
                } else {
                    System.err.println("[ANIMATION] Frame " + currentFrame + " in state " + newState + " is null or empty (img=" + img + ")");
                }
            } else {
                System.err.println("[ANIMATION] Cannot set state " + newState + ": frames.size=" + frames.size() + ", imageView=" + (imageView != null ? "OK" : "NULL"));
            }
        }
    }
    
    public String getState() {
        return currentState;
    }
    
    // 設置 ImageView 的水平翻轉（接受 1 或 -1）
    public void setFlipX(double scaleX) {
        this.currentFlipX = scaleX;  // 記住翻轉狀態
        if (imageView != null) {
            imageView.setScaleX(scaleX);
            System.out.println("[ANIM COMPONENT] setFlipX called with scaleX=" + scaleX);
        }
    }
    
    public ImageView getImageView() {
        return imageView;
    }

    // 切換玩家角色索引，並重新載入對應動畫幀
    public void setCharacterIndex(int index) {
        if (this.characterIndex == index) return;
        this.characterIndex = index;
        // 重新載入所有動畫幀
        loadIdleAnimation();
        loadWalkAnimation();
        loadJumpAnimation();
        loadFallAnimation();
        loadDeathAnimation();
        // 立即更新為第一幀以避免閃爍
        if (imageView != null && !idleFrames.isEmpty()) {
            imageView.setImage(idleFrames.get(1));  // 從第 1 幀開始
            imageView.setFitWidth(128);
            imageView.setFitHeight(128);
            imageView.setPreserveRatio(true);
            imageView.setTranslateX(-64);
            imageView.setTranslateY(-64);
        }
        System.out.println("[ANIMATION] Character index changed to " + index + ", reloaded all animations including death");
    }

    // 當玩家視圖被替換（例如換角色）時，重新從 Entity 取出新的 ImageView
    public void refreshImageViewFromEntity() {
        if (entity == null) return;
        try {
            javafx.scene.Node viewNode = entity.getViewComponent().getChildren().get(0);
            ImageView found = null;
            if (viewNode instanceof javafx.scene.Group g) {
                for (javafx.scene.Node child : g.getChildren()) {
                    if (child instanceof ImageView iv) { found = iv; break; }
                }
            } else if (viewNode instanceof ImageView iv) {
                found = iv;
            }
            if (found != null) {
                imageView = found;
                imageView.setFitWidth(128);
                imageView.setFitHeight(128);
                imageView.setPreserveRatio(true);
                imageView.setTranslateX(-64);
                imageView.setTranslateY(-64);
                // 立即顯示第一幀
                if (!idleFrames.isEmpty()) {
                    imageView.setImage(idleFrames.get(1));  // 從第 1 幀開始
                }
                System.out.println("[ANIMATION] Rebound ImageView after view change");
            } else {
                System.err.println("[ANIMATION] Failed to rebind ImageView after view change");
            }
        } catch (Exception e) {
            System.err.println("[ANIMATION] Exception refreshing ImageView: " + e.getMessage());
        }
    }

    private List<Image> getFramesForState(String state) {
        return switch (state) {
            case "walk" -> walkFrames;  // walk 只用 walkFrames，不退回到 idle
            case "jump" -> jumpFrames;
            case "fall" -> fallFrames;
            case "death" -> deathFrames;
            case "idle" -> idleFrames;
            default -> idleFrames;  // 其他狀態暫時共用 idle
        };
    }

    private int getLoopStartIndex(String state, List<Image> frames) {
        int fallback = Math.min(firstNonEmptyFrameIndex(frames), Math.max(0, frames.size() - 1));
        return switch (state) {
            case "idle" -> 0;                  // idle 從 0 開始
            case "walk" -> 0;                  // walk 從 0 開始（player10 是首幀）
            case "jump" -> 0;                  // jump 從 0 開始（player20）
            case "fall" -> 0;                  // fall 從 0 開始（player21）
            case "death" -> 0;                 // death 從 0 開始（player22）
            default -> fallback;  // 其他狀態使用第一個非空幀
        };
    }

    private int firstNonEmptyFrameIndex(List<Image> frames) {
        for (int i = 0; i < frames.size(); i++) {
            Image img = frames.get(i);
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                return i;
            }
        }
        return 0;
    }

    private double getIntervalForState(String state) {
        return switch (state) {
            case "walk" -> walkFrameInterval;
            case "jump" -> jumpFrameInterval;
            case "death" -> deathFrameInterval;
            case "fall" -> fallFrameInterval;
            case "idle" -> frameInterval;
            default -> frameInterval;
        };
    }
}
