import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人平台遊戲伺服器
 * 管理三個遊戲階段：選擇物件 -> 放置物件 -> 遊戲進行
 */
public class GameServer {

    private static final int PORT = 5000;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final String[] COLORS = {
        "#FF0000", "#00FF00", "#0000FF", "#FFFF00", 
        "#FF00FF", "#00FFFF", "#FFA500", "#800080"
    };
    private static int colorIndex = 0;
    
    // 遊戲狀態
    private static volatile GamePhase currentPhase = GamePhase.SELECTING;
    private static final Map<String, Integer> playerSelections = new ConcurrentHashMap<>();
    private static final Map<String, PlatformPlacement> playerPlacements = new ConcurrentHashMap<>();
    private static final Map<String, Integer> playerScores = new ConcurrentHashMap<>();
    private static final Set<String> finishedPlayers = ConcurrentHashMap.newKeySet();
    
    // 可選擇的物件（每輪隨機大小）
    private static List<GameObjectInfo> availableObjects = new ArrayList<>();
    
    // 計時器
    private static long gameStartTime = 0;
    private static final long GAME_DURATION = 60000;

    public static void main(String[] args) {
        System.out.println("Game Server running on port " + PORT);
        
        // 生成初始物件
        generateNewObjects();
        
        // 啟動遊戲循環監控執行緒
        startGameLoop();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                
                String playerId = UUID.randomUUID().toString().substring(0, 8);
                String color = COLORS[colorIndex++ % COLORS.length];
                
                System.out.println("Client connected: " + playerId + " (Color: " + color + ")");

                ClientHandler handler = new ClientHandler(socket, playerId, color);
                clients.put(playerId, handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 生成新的可選物件（隨機大小）
     */
    private static void generateNewObjects() {
        availableObjects.clear();
        Random rand = new Random();
        
        // 生成5個不同大小的平台
        for (int i = 0; i < 5; i++) {
            int width = 80 + rand.nextInt(150);
            int height = 15 + rand.nextInt(20);
            String color = String.format("#%06X", rand.nextInt(0xFFFFFF) | 0x800000);
            GameObjectInfo obj = new GameObjectInfo(i, width, height, color);
            availableObjects.add(obj);
            System.out.println("Generated object " + i + ": " + width + "x" + height + " " + color);
        }
        System.out.println("Total objects generated: " + availableObjects.size());
    }
    
    /**
     * 遊戲循環監控
     */
    private static void startGameLoop() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                    
                    // 檢查是否所有玩家都選擇了物件
                    if (currentPhase == GamePhase.SELECTING) {
                        if (!clients.isEmpty() && playerSelections.size() == clients.size()) {
                            switchToPlacingPhase();
                        }
                    }
                    // 檢查是否所有玩家都放置了物件
                    else if (currentPhase == GamePhase.PLACING) {
                        if (!clients.isEmpty() && playerPlacements.size() == clients.size()) {
                            switchToPlayingPhase();
                        }
                    }
                    // 檢查遊戲是否結束
                    else if (currentPhase == GamePhase.PLAYING) {
                        long elapsed = System.currentTimeMillis() - gameStartTime;
                        if (elapsed >= GAME_DURATION) {
                            switchToSelectingPhase();
                        }
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    private static void switchToPlacingPhase() {
        currentPhase = GamePhase.PLACING;
        System.out.println("Phase changed to PLACING");
        broadcastPhaseChange(GamePhase.PLACING);
    }
    
    private static void switchToPlayingPhase() {
        currentPhase = GamePhase.PLAYING;
        gameStartTime = System.currentTimeMillis();
        finishedPlayers.clear();
        System.out.println("Phase changed to PLAYING");
        broadcastPhaseChange(GamePhase.PLAYING);
        broadcastAllPlacements();
    }
    
    private static void switchToSelectingPhase() {
        currentPhase = GamePhase.SELECTING;
        playerSelections.clear();
        playerPlacements.clear();
        generateNewObjects();
        System.out.println("Phase changed to SELECTING");
        broadcastPhaseChange(GamePhase.SELECTING);
        broadcastAvailableObjects();
        broadcastScores();
    }
    
    private static void broadcastPhaseChange(GamePhase phase) {
        PhaseChangeMessage msg = new PhaseChangeMessage(phase);
        for (ClientHandler handler : clients.values()) {
            handler.sendObject(msg);
        }
    }
    
    private static void broadcastAvailableObjects() {
        ObjectListMessage msg = new ObjectListMessage(availableObjects);
        System.out.println("Broadcasting " + availableObjects.size() + " objects to all clients");
        for (ClientHandler handler : clients.values()) {
            handler.sendObject(msg);
        }
    }
    
    private static void broadcastAllPlacements() {
        for (Map.Entry<String, PlatformPlacement> entry : playerPlacements.entrySet()) {
            PlacementMessage msg = new PlacementMessage(entry.getKey(), entry.getValue());
            for (ClientHandler handler : clients.values()) {
                handler.sendObject(msg);
            }
        }
    }
    
    private static void broadcastScores() {
        ScoreUpdateMessage msg = new ScoreUpdateMessage(new HashMap<>(playerScores));
        for (ClientHandler handler : clients.values()) {
            handler.sendObject(msg);
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private String playerId;
        private String color;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private volatile boolean running = true;

        public ClientHandler(Socket socket, String playerId, String color) {
            this.socket = socket;
            this.playerId = playerId;
            this.color = color;
            playerScores.putIfAbsent(playerId, 0);
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                // 發送初始化訊息
                InitMessage initMsg = new InitMessage(playerId, color);
                sendObject(initMsg);
                
                // 發送當前遊戲階段
                sendObject(new PhaseChangeMessage(currentPhase));
                
                System.out.println("Current phase: " + currentPhase + ", Available objects: " + availableObjects.size());
                
                // 發送可選物件列表（如果在選擇階段）
                if (currentPhase == GamePhase.SELECTING && !availableObjects.isEmpty()) {
                    System.out.println("Sending object list to new client");
                    sendObject(new ObjectListMessage(availableObjects));
                }
                
                // 發送分數
                sendObject(new ScoreUpdateMessage(new HashMap<>(playerScores)));

                // 接收客戶端訊息
                while (running) {
                    try {
                        Object obj = in.readObject();
                        
                        if (obj instanceof PlayerInfo info) {
                            info.playerId = playerId;
                            info.colorHex = color;
                            broadcast(info, playerId);
                        } 
                        else if (obj instanceof SelectionMessage selectionMsg) {
                            handleSelection(selectionMsg);
                        }
                        else if (obj instanceof PlacementMessage placementMsg) {
                            handlePlacement(placementMsg);
                        }
                        else if (obj instanceof FinishMessage finishMsg) {
                            handleFinish(finishMsg);
                        }
                        
                    } catch (EOFException | SocketException e) {
                        System.out.println("Client disconnected: " + playerId);
                        break;
                    } catch (StreamCorruptedException e) {
                        System.out.println("Stream corrupted for client " + playerId);
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error with client " + playerId + ": " + e.getMessage());
            } finally {
                cleanup();
            }
        }
        
        /**
         * 處理玩家選擇物件
         */
        private void handleSelection(SelectionMessage msg) {
            if (currentPhase == GamePhase.SELECTING) {
                playerSelections.put(playerId, msg.objectId);
                System.out.println("Player " + playerId + " selected object " + msg.objectId);
            }
        }
        
        /**
         * 處理玩家放置物件
         */
        private void handlePlacement(PlacementMessage msg) {
            if (currentPhase == GamePhase.PLACING) {
                msg.playerId = playerId;
                playerPlacements.put(playerId, msg.placement);
                System.out.println("Player " + playerId + " placed object at (" + 
                    msg.placement.x + ", " + msg.placement.y + ")");
            }
        }
        
        /**
         * 處理玩家完成關卡
         */
        private void handleFinish(FinishMessage msg) {
            if (currentPhase == GamePhase.PLAYING && !finishedPlayers.contains(playerId)) {
                finishedPlayers.add(playerId);
                int rank = finishedPlayers.size();
                
                // 計算分數：第一名100分，第二名70分，第三名50分，其他30分
                int score = 0;
                if (rank == 1) score = 100;
                else if (rank == 2) score = 70;
                else if (rank == 3) score = 50;
                else score = 30;
                
                playerScores.put(playerId, playerScores.get(playerId) + score);
                System.out.println("Player " + playerId + " finished! Rank: " + rank + ", Score: +" + score);
                
                broadcastScores();
            }
        }

        private void broadcast(PlayerInfo info, String excludeId) {
            for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                if (!entry.getKey().equals(excludeId)) {
                    entry.getValue().sendObject(info);
                }
            }
        }

        boolean sendObject(Object obj) {
            try {
                synchronized (out) {
                    out.writeObject(obj);
                    out.flush();
                    out.reset();
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private void cleanup() {
            running = false;
            clients.remove(playerId);
            playerSelections.remove(playerId);
            playerPlacements.remove(playerId);
            
            DisconnectMessage disconnectMsg = new DisconnectMessage(playerId);
            for (ClientHandler handler : clients.values()) {
                handler.sendObject(disconnectMsg);
            }
            
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                socket.close();
            } catch (IOException e) {
                // 忽略
            }
        }
    }
}

// 遊戲階段枚舉
enum GamePhase {
    SELECTING,  // 選擇物件
    PLACING,    // 放置物件
    PLAYING     // 遊戲進行
}