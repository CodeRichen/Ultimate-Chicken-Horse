import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人平台遊戲伺服器（完整版）
 * 管理三個遊戲階段：選擇物件 -> 放置物件 -> 遊戲競賽
 * 支援預覽放置、完成計時、失敗處理、回合結束排行榜
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
    private static final Map<String, PlatformPlacement> playerPreviewPlacements = new ConcurrentHashMap<>();
    private static final Map<String, Integer> playerScores = new ConcurrentHashMap<>();
    private static final Map<String, Integer> roundScores = new ConcurrentHashMap<>();
    private static final List<FinishRecord> finishRecords = Collections.synchronizedList(new ArrayList<>());
    private static final Set<String> failedPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<String> completedPlayers = ConcurrentHashMap.newKeySet(); // 完成或失敗的玩家
    
    // 可選擇的物件
    private static List<GameObjectInfo> availableObjects = new ArrayList<>();
    
    // 計時器
    private static long gameStartTime = 0;
    private static final long GAME_DURATION = 120000; // 120秒
    private static final long LEADERBOARD_DISPLAY_TIME = 10000; // 10秒排行榜顯示時間

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  Multiplayer Platform Race Server");
        System.out.println("  Port: " + PORT);
        System.out.println("=================================");
        
        generateNewObjects();
        startGameLoop();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                
                String playerId = UUID.randomUUID().toString().substring(0, 8);
                String color = COLORS[colorIndex++ % COLORS.length];
                
                System.out.println("[CONNECT] Client: " + playerId + " | Color: " + color);

                ClientHandler handler = new ClientHandler(socket, playerId, color);
                clients.put(playerId, handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 生成新的可選物件（隨機大小和顏色）
     */
    private static void generateNewObjects() {
        availableObjects.clear();
        Random rand = new Random();
        
        for (int i = 0; i < 5; i++) {
            int width = 80 + rand.nextInt(150);  // 80-230
            int height = 15 + rand.nextInt(20);  // 15-35
            String color = String.format("#%06X", rand.nextInt(0xFFFFFF) | 0x800000);
            GameObjectInfo obj = new GameObjectInfo(i, width, height, color);
            availableObjects.add(obj);
        }
        System.out.println("[OBJECTS] Generated " + availableObjects.size() + " platforms");
    }
    
    /**
     * 遊戲循環監控
     */
    private static void startGameLoop() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                    
                    if (currentPhase == GamePhase.SELECTING) {
                        // 至少2個玩家才開始（可改成1個進行測試）
                        int minPlayers = 1;  // 改成1可以單人測試，改成2需要多人
                        if (clients.size() >= minPlayers && playerSelections.size() == clients.size()) {
                            switchToPlacingPhase();
                        }
                    }
                    else if (currentPhase == GamePhase.PLACING) {
                        // 所有玩家都放置了物件 -> 進入遊戲階段
                        if (!clients.isEmpty() && playerPlacements.size() == clients.size()) {
                            switchToPlayingPhase();
                        }
                    }
                    else if (currentPhase == GamePhase.PLAYING) {
                        long elapsed = System.currentTimeMillis() - gameStartTime;
                        
                        // 檢查是否所有玩家都完成或失敗
                        if (!clients.isEmpty() && completedPlayers.size() == clients.size()) {
                            System.out.println("[ROUND] All players completed!");
                            endRound();
                        }
                        // 時間到 -> 強制結束回合
                        else if (elapsed >= GAME_DURATION) {
                            System.out.println("[ROUND] Time's up!");
                            endRound();
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
        System.out.println("[PHASE] -> PLACING");
        broadcastPhaseChange(GamePhase.PLACING);
    }
    
    private static void switchToPlayingPhase() {
        currentPhase = GamePhase.PLAYING;
        gameStartTime = System.currentTimeMillis();
        finishRecords.clear();
        failedPlayers.clear();
        completedPlayers.clear();
        playerPreviewPlacements.clear();
        roundScores.clear();
        
        System.out.println("[PHASE] -> PLAYING (Duration: " + (GAME_DURATION/1000) + "s)");
        broadcastPhaseChange(GamePhase.PLAYING);
        broadcastAllPlacements();
    }
    
    /**
     * 結束回合，計算分數並顯示排行榜
     */
    private static void endRound() {
        // 防止重複觸發
        if (currentPhase != GamePhase.PLAYING) {
            return;
        }
        
        // 立即切換到等待狀態，防止重複觸發
        currentPhase = GamePhase.SELECTING;
        
        // 計算本回合分數
        calculateRoundScores();
        
        // 更新總分
        for (Map.Entry<String, Integer> entry : roundScores.entrySet()) {
            String playerId = entry.getKey();
            int score = entry.getValue();
            playerScores.put(playerId, playerScores.getOrDefault(playerId, 0) + score);
        }
        
        // 準備完成順序列表
        List<String> finishOrder = new ArrayList<>();
        for (FinishRecord record : finishRecords) {
            finishOrder.add(record.playerId);
        }
        
        // 添加失敗的玩家
        for (String playerId : failedPlayers) {
            if (!finishOrder.contains(playerId)) {
                finishOrder.add(playerId);
            }
        }
        
        // 發送回合結束訊息
        RoundEndMessage roundEndMsg = new RoundEndMessage(
            new HashMap<>(roundScores),
            new HashMap<>(playerScores),
            finishOrder
        );
        
        for (ClientHandler handler : clients.values()) {
            handler.sendObject(roundEndMsg);
        }
        
        System.out.println("[ROUND END] Displaying leaderboard...");
        printLeaderboard();
        
        // 等待一段時間後開始新回合
        new Thread(() -> {
            try {
                Thread.sleep(LEADERBOARD_DISPLAY_TIME);
                switchToSelectingPhase();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * 計算本回合分數
     * 第1名: 100分
     * 第2名: 70分
     * 第3名: 50分
     * 第4名及以後: 30分
     * 失敗（掉出地圖）: 0分
     */
    private static void calculateRoundScores() {
        roundScores.clear();
        
        // 按完成時間排序
        Collections.sort(finishRecords, Comparator.comparingLong(r -> r.finishTime));
        
        // 分配分數給完成的玩家
        for (int i = 0; i < finishRecords.size(); i++) {
            String playerId = finishRecords.get(i).playerId;
            int score;
            
            if (i == 0) score = 100;      // 第1名
            else if (i == 1) score = 70;  // 第2名
            else if (i == 2) score = 50;  // 第3名
            else score = 30;              // 第4名及以後
            
            roundScores.put(playerId, score);
        }
        
        // 失敗的玩家得0分
        for (String playerId : failedPlayers) {
            roundScores.put(playerId, 0);
        }
        
        // 未完成也未失敗的玩家（時間到了）得0分
        for (String playerId : clients.keySet()) {
            if (!roundScores.containsKey(playerId)) {
                roundScores.put(playerId, 0);
            }
        }
    }
    
    /**
     * 列印排行榜到控制台
     */
    private static void printLeaderboard() {
        System.out.println("\n========== LEADERBOARD ==========");
        
        // 按總分排序
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(playerScores.entrySet());
        sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int rank = 1;
        for (Map.Entry<String, Integer> entry : sortedScores) {
            String playerId = entry.getKey();
            int totalScore = entry.getValue();
            int roundScore = roundScores.getOrDefault(playerId, 0);
            
            System.out.printf("%d. %s | Round: +%d | Total: %d\n", 
                rank++, playerId, roundScore, totalScore);
        }
        System.out.println("=================================\n");
    }
    
    private static void switchToSelectingPhase() {
        currentPhase = GamePhase.SELECTING;
        playerSelections.clear();
        playerPlacements.clear();
        playerPreviewPlacements.clear();
        completedPlayers.clear();  // 清空完成狀態！
        finishRecords.clear();     // 清空完成記錄！
        failedPlayers.clear();     // 清空失敗記錄！
        generateNewObjects();
        
        System.out.println("[PHASE] -> SELECTING (Players: " + clients.size() + ")");
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
        for (ClientHandler handler : clients.values()) {
            handler.sendObject(msg);
        }
    }
    
    private static void broadcastAllPlacements() {
        for (Map.Entry<String, PlatformPlacement> entry : playerPlacements.entrySet()) {
            PlacementMessage msg = new PlacementMessage(entry.getKey(), entry.getValue(), true);
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
                sendObject(new InitMessage(playerId, color));
                sendObject(new PhaseChangeMessage(currentPhase));
                
                if (currentPhase == GamePhase.SELECTING && !availableObjects.isEmpty()) {
                    sendObject(new ObjectListMessage(availableObjects));
                }
                
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
                        else if (obj instanceof FailMessage failMsg) {
                            handleFail(failMsg);
                        }
                        
                    } catch (EOFException | SocketException e) {
                        System.out.println("[DISCONNECT] Client: " + playerId);
                        break;
                    } catch (StreamCorruptedException e) {
                        System.out.println("[ERROR] Stream corrupted for client: " + playerId);
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Client " + playerId + ": " + e.getMessage());
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
                System.out.println("[SELECT] " + playerId + " chose platform " + msg.objectId);
            }
        }
        
        /**
         * 處理玩家放置物件（預覽或確認）
         */
        private void handlePlacement(PlacementMessage msg) {
            if (currentPhase == GamePhase.PLACING) {
                msg.playerId = playerId;
                
                if (msg.confirmed) {
                    // 確認放置
                    playerPlacements.put(playerId, msg.placement);
                    playerPreviewPlacements.remove(playerId);
                    System.out.printf("[PLACE] %s confirmed at (%.0f, %.0f) rotation=%.0f°\n", 
                        playerId, msg.placement.x, msg.placement.y, msg.placement.rotation);
                    
                    // 廣播確認放置給所有玩家
                    for (ClientHandler handler : clients.values()) {
                        handler.sendObject(msg);
                    }
                } else {
                    // 只是預覽更新
                    playerPreviewPlacements.put(playerId, msg.placement);
                    
                    // 廣播預覽給其他玩家
                    for (ClientHandler handler : clients.values()) {
                        if (!handler.playerId.equals(playerId)) {
                            handler.sendObject(msg);
                        }
                    }
                }
            }
        }
        
        /**
         * 處理玩家完成關卡
         */
        private void handleFinish(FinishMessage msg) {
            if (currentPhase == GamePhase.PLAYING && !completedPlayers.contains(playerId)) {
                completedPlayers.add(playerId);
                finishRecords.add(new FinishRecord(playerId, msg.finishTime));
                
                int rank = finishRecords.size();
                System.out.printf("[FINISH] %s completed! Rank: %d | Time: %.2fs\n", 
                    playerId, rank, msg.finishTime / 1000.0);
            }
        }
        
        /**
         * 處理玩家失敗（掉出地圖）
         */
        private void handleFail(FailMessage msg) {
            if (currentPhase == GamePhase.PLAYING && !completedPlayers.contains(playerId)) {
                completedPlayers.add(playerId);
                failedPlayers.add(playerId);
                System.out.println("[FAIL] " + playerId + " fell off the map!");
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
            playerPreviewPlacements.remove(playerId);
            
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
    
    /**
     * 完成記錄（包含玩家ID和完成時間）
     */
    static class FinishRecord {
        String playerId;
        long finishTime;
        
        FinishRecord(String playerId, long finishTime) {
            this.playerId = playerId;
            this.finishTime = finishTime;
        }
    }
}

