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
 * 多人平台遊戲客戶端（修正版）
 */
public class GameClient extends GameApplication {

    private Entity player;
    private List<Entity> platformEntities = new ArrayList<>();
    private Map<String, Entity> otherPlayers = new HashMap<>();
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myPlayerId;
    private Color myColor = Color.RED;
    private volatile boolean connected = false;
    private volatile boolean running = true;
    
    private GamePhase currentPhase = GamePhase.SELECTING;
    private List<GameObjectInfo> availableObjects = new ArrayList<>();
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
    
    private long gameStartTime = 0;
    private static final long GAME_DURATION = 120000;
    
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    
    private boolean hasFinished = false;
    private boolean hasFailed = false;

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
        
        player = FXGL.entityBuilder()
                .at(100, 900)
                .view(new Circle(25, myColor))
                .with(new PlayerControl(platformEntities))
                .buildAndAttach();
        player.setVisible(false);
        
        createUI();
        connectToServer();
        startNetworkThread();
        startPositionSender();
        startPlacementPreviewSender();
    }
    
    private void createFixedPlatforms() {
        startPlatform = createPlatform(50, SCREEN_HEIGHT - 150, 200, 30, Color.GREEN);
        endPlatform = createPlatform(SCREEN_WIDTH - 250, SCREEN_HEIGHT - 150, 200, 30, Color.GOLD);
        
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
                .at(SCREEN_WIDTH - 200, SCREEN_HEIGHT - 130)
                .view(endLabel)
                .buildAndAttach();
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
            
            Rectangle objRect = new Rectangle(obj.width, obj.height, Color.web(obj.color));
            objRect.setStroke(Color.YELLOW);
            objRect.setStrokeWidth(1);
            Entity objPreview = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - obj.width / 2, yPos + 50 - obj.height / 2)
                    .view(objRect)
                    .buildAndAttach();
            objectButtons.add(objPreview);
            
            Text numLabel = new Text("Platform " + (i + 1));
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
        }
    }
    
    private void clearObjectSelection() {
        System.out.println("[CLIENT] Clearing object selection UI");
        for (Entity btn : objectButtons) {
            btn.removeFromWorld();
        }
        objectButtons.clear();
    }
    
    private void showFinishButton() {
        System.out.println("[CLIENT] Showing finish button");
        if (finishButton != null) {
            finishButton.removeFromWorld();
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
        
        FXGL.entityBuilder()
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
    }
    
    private void showLeaderboard(Map<String, Integer> roundScores, Map<String, Integer> totalScores, List<String> finishOrder) {
        System.out.println("[CLIENT] Showing leaderboard with " + finishOrder.size() + " players");
        hideLeaderboard();
        
        Rectangle bg = new Rectangle(600, 500, Color.rgb(40, 40, 50, 0.95));
        bg.setStroke(Color.GOLD);
        bg.setStrokeWidth(4);
        
        Entity bgEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 300, SCREEN_HEIGHT / 2 - 250)
                .view(bg)
                .buildAndAttach();
        leaderboardEntities.add(bgEntity);
        
        Text title = new Text("ROUND COMPLETE!");
        title.setFont(Font.font(36));
        title.setFill(Color.GOLD);
        Entity titleEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 150, SCREEN_HEIGHT / 2 - 200)
                .view(title)
                .buildAndAttach();
        leaderboardEntities.add(titleEntity);
        
        int yOffset = 0;
        for (int i = 0; i < finishOrder.size(); i++) {
            String playerId = finishOrder.get(i);
            int roundScore = roundScores.getOrDefault(playerId, 0);
            int totalScore = totalScores.getOrDefault(playerId, 0);
            
            String rank = (i + 1) + ". ";
            String scoreInfo = playerId + " - Round: +" + roundScore + " | Total: " + totalScore;
            
            Text scoreText = new Text(rank + scoreInfo);
            scoreText.setFont(Font.font(22));
            scoreText.setFill(i == 0 ? Color.GOLD : Color.WHITE);
            
            Entity scoreEntity = FXGL.entityBuilder()
                    .at(SCREEN_WIDTH / 2 - 250, SCREEN_HEIGHT / 2 - 100 + yOffset)
                    .view(scoreText)
                    .buildAndAttach();
            leaderboardEntities.add(scoreEntity);
            
            yOffset += 40;
        }
        
        Text hint = new Text("Next round starting soon...");
        hint.setFont(Font.font(20));
        hint.setFill(Color.LIGHTGRAY);
        Entity hintEntity = FXGL.entityBuilder()
                .at(SCREEN_WIDTH / 2 - 150, SCREEN_HEIGHT / 2 + 180)
                .view(hint)
                .buildAndAttach();
        leaderboardEntities.add(hintEntity);
    }
    
    private void hideLeaderboard() {
        System.out.println("[CLIENT] Hiding leaderboard, removing " + leaderboardEntities.size() + " entities");
        for (Entity entity : leaderboardEntities) {
            entity.removeFromWorld();
        }
        leaderboardEntities.clear();
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
                    else if (obj instanceof ObjectListMessage objListMsg) {
                        System.out.println("[CLIENT] Received object list: " + objListMsg.objects.size() + " objects");
                        javafx.application.Platform.runLater(() -> {
                            availableObjects = objListMsg.objects;
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
                            playerScores = scoreMsg.scores;
                            updateScoreDisplay();
                        });
                    }
                    else if (obj instanceof RoundEndMessage roundEndMsg) {
                        System.out.println("[CLIENT] Round ended!");
                        javafx.application.Platform.runLater(() -> {
                            showLeaderboard(roundEndMsg.roundScores, roundEndMsg.totalScores, roundEndMsg.finishOrder);
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
                    if (connected && currentPhase == GamePhase.PLAYING && 
                        player != null && player.isVisible() && !hasFinished && !hasFailed) {
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
                    if (connected && currentPhase == GamePhase.PLACING && 
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
        
        switch (newPhase) {
            case SELECTING:
                phaseText.setText("Phase: SELECT YOUR PLATFORM");
                player.setVisible(false);
                selectedObjectId = null;
                selectedObj = null;
                myPlacement = null;
                currentRotation = 0;
                isDragging = false;
                hasFinished = false;
                hasFailed = false;
                hideFinishButton();
                hideLeaderboard();
                
                if (previewPlatform != null) {
                    previewPlatform.removeFromWorld();
                    previewPlatform = null;
                }
                
                for (Entity preview : otherPreviewEntities.values()) {
                    preview.removeFromWorld();
                }
                otherPreviewEntities.clear();
                otherPreviewPlacements.clear();
                
                // 清除其他玩家實體（防止重疊）
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
                clearObjectSelection();
                    if (previewPlatform != null && myPlacement == null) {
                        showFinishButton();
                        }
                break;
                
            case PLAYING:
                phaseText.setText("Phase: RACE TO FINISH!");
                hideFinishButton();
                isDragging = false;
                
                if (previewPlatform != null) {
                    previewPlatform.removeFromWorld();
                    previewPlatform = null;
                }
                
                for (Entity preview : otherPreviewEntities.values()) {
                    preview.removeFromWorld();
                }
                otherPreviewEntities.clear();
                
                // 創建所有玩家本回合放置的平台
                System.out.println("[CLIENT] Creating platforms: my=" + (myPlacement != null) + 
                                 " others=" + otherPlacements.size());
                
                for (Map.Entry<String, PlatformPlacement> entry : otherPlacements.entrySet()) {
                    PlatformPlacement p = entry.getValue();
                    System.out.println("[CLIENT] Creating platform for " + entry.getKey() + 
                                     " at (" + p.x + "," + p.y + ") rotation=" + p.rotation);
                    createPlatformWithRotation(p.x, p.y, p.width, p.height, Color.web(p.color), p.rotation);
                }
                
                if (myPlacement != null) {
                    System.out.println("[CLIENT] Creating my platform at (" + myPlacement.x + "," + 
                                     myPlacement.y + ") rotation=" + myPlacement.rotation);
                    createPlatformWithRotation(myPlacement.x, myPlacement.y, myPlacement.width, 
                                 myPlacement.height, Color.web(myPlacement.color), myPlacement.rotation);
                }
                
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
                preview.removeFromWorld();
            }
            otherPreviewPlacements.remove(msg.playerId);
        } else {
            otherPreviewPlacements.put(msg.playerId, msg.placement);
            updateOtherPreview(msg.playerId, msg.placement);
        }
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
                }
            }
        }, KeyCode.A);
        
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
                if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
                    player.getComponent(PlayerControl.class).jump();
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

        // 旋轉改為每次90度
        FXGL.getInput().addAction(new UserAction("Rotate Left") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLACING && previewPlatform != null && myPlacement == null) {
                    currentRotation -= 90;
                    previewPlatform.setRotation(currentRotation);
                    System.out.println("[CLIENT] Rotated left to " + currentRotation + "°");
                }
            }
        }, KeyCode.Q);
        
        FXGL.getInput().addAction(new UserAction("Rotate Right") {
            @Override
            protected void onActionBegin() {
                if (currentPhase == GamePhase.PLACING && previewPlatform != null && myPlacement == null) {
                    currentRotation += 90;
                    previewPlatform.setRotation(currentRotation);
                    System.out.println("[CLIENT] Rotated right to " + currentRotation + "°");
                }
            }
        }, KeyCode.E);

        FXGL.getInput().addAction(new UserAction("Click") {
            @Override
            protected void onActionBegin() {
                Point2D mousePos = FXGL.getInput().getMousePositionWorld();
                
                if (currentPhase == GamePhase.SELECTING && selectedObjectId == null) {
                    handleObjectSelection(mousePos);
                }
                else if (currentPhase == GamePhase.PLACING && myPlacement == null) {
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
                
                selectedObjectId = obj.id;
                selectedObj = obj;
                System.out.println("[CLIENT] Selected object " + obj.id);
                
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
                
                // currentPhase = GamePhase.PLACING;
                // phaseText.setText("Phase: DRAG & ROTATE (Q/E 90°), THEN FINISH");
                // showFinishButton();
                phaseText.setText("Waiting for other players to select...");
                break;
            }
        }
    }
    
    private void createPreviewPlatform() {
        if (selectedObj != null && previewPlatform == null) {
            double x = SCREEN_WIDTH / 2 - selectedObj.width / 2.0;
            double y = SCREEN_HEIGHT / 2 - selectedObj.height / 2.0;
            
            Rectangle rect = new Rectangle(selectedObj.width, selectedObj.height, Color.web(selectedObj.color));
            rect.setStroke(Color.YELLOW);
            rect.setStrokeWidth(3);
            
            previewPlatform = FXGL.entityBuilder()
                    .at(x, y)
                    .view(rect)
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
        } else {
            System.err.println("[CLIENT ERROR] Cannot confirm: selectedObj=" + selectedObj + 
                             " previewPlatform=" + previewPlatform + " myPlacement=" + myPlacement);
        }
    }
    
    @Override
    protected void onUpdate(double tpf) {
        // 處理拖曳
        if (currentPhase == GamePhase.PLACING && isDragging && previewPlatform != null && myPlacement == null) {
            Point2D mousePos = FXGL.getInput().getMousePositionWorld();
            previewPlatform.setPosition(
                mousePos.getX() - dragOffset.getX(),
                mousePos.getY() - dragOffset.getY()
            );
        }
        
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
        
        // 檢查是否到達終點
        if (currentPhase == GamePhase.PLAYING && player.isVisible() && !hasFinished && !hasFailed) {
            double playerX = player.getX();
            double playerY = player.getY();
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
                    System.out.println("[CLIENT] Reached finish! Time: " + finishTime + "ms");
                    // 不隱藏玩家，讓玩家停留在終點
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to send finish message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // 檢查是否掉出地圖
            if (playerY > SCREEN_HEIGHT + 100) {
                try {
                    synchronized (out) {
                        out.writeObject(new FailMessage(myPlayerId));
                        out.flush();
                        out.reset();
                    }
                    hasFailed = true;
                    player.setVisible(false);
                    System.out.println("[CLIENT] Failed - fell off map");
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Failed to send fail message: " + e.getMessage());
                    e.printStackTrace();
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

    public CollisionInfo checkCollision(double playerX, double playerY, double radius, double velocityY) {
        if (entity.getRotation() != 0) {
            return checkRotatedCollision(playerX, playerY, radius, velocityY);
        }
        
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
    
    private CollisionInfo checkRotatedCollision(double playerX, double playerY, double radius, double velocityY) {
        double centerX = entity.getX() + width / 2;
        double centerY = entity.getY() + height / 2;
        
        double angle = Math.toRadians(-entity.getRotation());
        
        double dx = playerX - centerX;
        double dy = playerY - centerY;
        double localX = dx * Math.cos(angle) - dy * Math.sin(angle);
        double localY = dx * Math.sin(angle) + dy * Math.cos(angle);
        
        double halfWidth = width / 2;
        double halfHeight = height / 2;
        
        double closestX = Math.max(-halfWidth, Math.min(halfWidth, localX));
        double closestY = Math.max(-halfHeight, Math.min(halfHeight, localY));
        
        double distX = localX - closestX;
        double distY = localY - closestY;
        double distSquared = distX * distX + distY * distY;
        
        if (distSquared > radius * radius) {
            return new CollisionInfo(false, CollisionSide.NONE);
        }
        
        CollisionSide side = CollisionSide.NONE;
        if (Math.abs(closestY + halfHeight) < 5 && velocityY >= 0) {
            side = CollisionSide.TOP;
        } else if (Math.abs(closestY - halfHeight) < 5 && velocityY <= 0) {
            side = CollisionSide.BOTTOM;
        } else if (Math.abs(closestX + halfWidth) < 5) {
            side = CollisionSide.LEFT;
        } else if (Math.abs(closestX - halfWidth) < 5) {
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
    
    public void reset() {
        velocityX = 0;
        velocityY = 0;
        onGround = false;
        crouching = false;
    }

    @Override
    public void onUpdate(double tpf) {
        velocityY += gravity;
        
        if (velocityY > MAX_VELOCITY_Y) velocityY = MAX_VELOCITY_Y;
        if (velocityY < -MAX_VELOCITY_Y) velocityY = -MAX_VELOCITY_Y;
        
        entity.setX(entity.getX() + velocityX);
        entity.setY(entity.getY() + velocityY);
        
        onGround = false;
        
        for (Entity platform : platforms) {
            if (!platform.hasComponent(PlatformComponent.class)) {
                continue;
            }
            
            PlatformComponent pc = platform.getComponent(PlatformComponent.class);
            CollisionInfo collision = pc.checkCollision(entity.getX(), entity.getY(), playerRadius, velocityY);
            
            if (collision.collided) {
                switch (collision.side) {
                    case TOP:
                        if (platform.getRotation() != 0) {
                            double angle = Math.toRadians(-platform.getRotation());
                            double centerX = platform.getX() + pc.getWidth() / 2;
                            double centerY = platform.getY() + pc.getHeight() / 2;
                            
                            double localY = -pc.getHeight() / 2;
                            double dx = entity.getX() - centerX;
                            double localX = dx * Math.cos(angle);
                            
                            double worldY = centerY + (localX * Math.sin(angle) + localY * Math.cos(angle));
                            entity.setY(worldY - playerRadius);
                        } else {
                            entity.setY(platform.getY() - playerRadius);
                        }
                        
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