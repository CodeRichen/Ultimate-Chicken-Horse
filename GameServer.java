import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人平台遊戲伺服器（完整版 - 含房間系統）
 * 支援創建/加入房間、5輪遊戲制度
 */
public class GameServer {

    private static final int PORT = 5000;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final String[] COLORS = {
        "#FF0000", "#00FF00", "#0000FF", "#FFFF00", 
        "#FF00FF", "#00FFFF", "#FFA500"
    };
    private static int colorIndex = 0;

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  Multiplayer Platform Race Server");
        System.out.println("  Port: " + PORT);
        System.out.println("  Room System Enabled");
        System.out.println("=================================");

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

    static class ClientHandler implements Runnable {
        private Socket socket;
        private String playerId;
        private String color;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private volatile boolean running = true;
        private Room currentRoom = null;
        private volatile boolean roundMonitorRunning = false;

        public ClientHandler(Socket socket, String playerId, String color) {
            this.socket = socket;
            this.playerId = playerId;
            this.color = color;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                // 發送初始化訊息
                sendObject(new InitMessage(playerId, color));

                // 接收客戶端訊息
                while (running) {
                    try {
                        Object obj = in.readObject();
                        
                        // 房間相關訊息
                        if (obj instanceof CreateRoomRequest createReq) {
                            handleCreateRoom(createReq);
                        }
                        else if (obj instanceof JoinRoomRequest joinReq) {
                            handleJoinRoom(joinReq);
                        }
                        else if (obj instanceof PlayerReadyMessage readyMsg) {
                            handlePlayerReady(readyMsg);
                        }
                        else if (obj instanceof StartGameRequest) {
                            handleStartGame();
                        }
                        else if (obj instanceof LeaveRoomRequest) {
                            handleLeaveRoom();
                        }
                        // 遊戲相關訊息
                        else if (obj instanceof PlayerInfo info) {
                            if (currentRoom != null && currentRoom.getState() == RoomState.PLAYING) {
                                info.playerId = playerId;
                                info.colorHex = color;
                                broadcastToRoom(info, playerId);
                            }
                        } 
                        else if (obj instanceof SelectionMessage selectionMsg) {
                            if (currentRoom != null) {
                                handleSelection(selectionMsg);
                            }
                        }
                        else if (obj instanceof PlacementMessage placementMsg) {
                            if (currentRoom != null) {
                                handlePlacement(placementMsg);
                            }
                        }
                        else if (obj instanceof FinishMessage finishMsg) {
                            if (currentRoom != null) {
                                handleFinish(finishMsg);
                            }
                        }
                        else if (obj instanceof FailMessage failMsg) {
                            if (currentRoom != null) {
                                handleFail(failMsg);
                            }
                        }
                       
else if (obj instanceof JoinRandomRoomRequest) {
    handleJoinRandomRoom();
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
         * 創建房間
         */
       private void handleCreateRoom(CreateRoomRequest req) {
            if (currentRoom != null) {
                sendObject(new CreateRoomResponse(false, null, "Already in a room"));
                return;
            }
            
            currentRoom = RoomManager.createRoom(playerId, req.maxPlayers, req.roomType);  // 加入 roomType
            sendObject(new CreateRoomResponse(true, currentRoom.getInfo().roomCode, "Room created successfully"));
            sendObject(new RoomUpdateMessage(currentRoom.getInfo()));
            
            System.out.println("[ROOM] " + playerId + " created " + req.roomType + " room " + 
                            currentRoom.getInfo().roomCode);
        }
        
        /**
         * 加入房間
         */
        private void handleJoinRoom(JoinRoomRequest req) {
            if (currentRoom != null) {
                sendObject(new JoinRoomResponse(false, "Already in a room", null));
                return;
            }
            
            currentRoom = RoomManager.joinRoom(playerId, req.roomCode);
            
            if (currentRoom == null) {
                sendObject(new JoinRoomResponse(false, "Room not found or full", null));
                return;
            }
            
            sendObject(new JoinRoomResponse(true, "Joined successfully", currentRoom.getInfo()));
            
            // 通知房間所有玩家
            broadcastRoomUpdate();
            
            System.out.println("[ROOM] " + playerId + " joined room " + req.roomCode);
        }
        
        /**
         * 玩家準備/取消準備
         */
        private void handlePlayerReady(PlayerReadyMessage msg) {
            if (currentRoom == null) return;
            
            currentRoom.setPlayerReady(playerId, msg.ready);
            broadcastRoomUpdate();
            
            System.out.println("[ROOM] " + playerId + " ready status: " + msg.ready);
        }
        
        /**
         * 開始遊戲（僅房主）
         */
        private void handleStartGame() {
            if (currentRoom == null) return;
            if (!currentRoom.getHostId().equals(playerId)) {
                System.out.println("[ROOM] Non-host " + playerId + " tried to start game");
                return;
            }
            
            if (!currentRoom.allPlayersReady()) {
                System.out.println("[ROOM] Not all players ready");
                return;
            }
            
            currentRoom.startGame();
            System.out.println("[ROOM] Game started in room " + currentRoom.getInfo().roomCode + 
                             " (Round 1/5)");
            
            // 開始第一輪
            startNewRound();
        }
        
        /**
         * 開始新一輪遊戲
         */
        private void startNewRound() {
            if (currentRoom == null) return;
            
            // 生成新物件
            List<GameObjectInfo> availableObjects = generateNewObjects();
            
            // 初始化回合狀態
            currentRoom.playerSelections.clear();
            currentRoom.playerPlacements.clear();
            currentRoom.playerPreviewPlacements.clear();
            currentRoom.finishRecords.clear();
            currentRoom.failedPlayers.clear();
            currentRoom.completedPlayers.clear();
            
            // 發送選擇階段訊息
            PhaseChangeMessage phaseMsg = new PhaseChangeMessage(GamePhase.SELECTING);
            ObjectListMessage objMsg = new ObjectListMessage(availableObjects);
            
            for (String pid : currentRoom.getPlayerIds()) {
                ClientHandler handler = clients.get(pid);
                if (handler != null) {
                    handler.sendObject(phaseMsg);
                    handler.sendObject(objMsg);
                }
            }
            
            // 啟動回合監控
            startRoundMonitor();
        }
        

            private void handleJoinRandomRoom() {
                if (currentRoom != null) {
                    sendObject(new JoinRoomResponse(false, "Already in a room", null));
                    return;
                }
                
                currentRoom = RoomManager.joinRandomPublicRoom(playerId);
                
                if (currentRoom == null) {
                    sendObject(new JoinRoomResponse(false, "No public rooms available", null));
                    return;
                }
                
                sendObject(new JoinRoomResponse(true, "Joined public room", currentRoom.getInfo()));
                
                // 通知房間所有玩家
                broadcastRoomUpdate();
                
                System.out.println("[ROOM] " + playerId + " randomly joined public room " + 
                                currentRoom.getInfo().roomCode);
            }
        /**
         * 回合監控線程
         */
        private void startRoundMonitor() {
            if (roundMonitorRunning) return; // 防止重複啟動
            roundMonitorRunning = true;

            new Thread(() -> {
                try {
                    GamePhase currentPhase = GamePhase.SELECTING;
                    long gameStartTime = 0;
                    final long GAME_DURATION = 120000; // 120秒
                    System.out.println("[DEBUG] RoundMonitor started. RoomState=" + currentRoom.getState());
                    while (currentRoom != null && currentRoom.getState() == RoomState.PLAYING) {
                        Thread.sleep(100);
                        
                        if (currentPhase == GamePhase.SELECTING) {
                            // 所有玩家都選擇了
                            if (currentRoom.playerSelections.size() == currentRoom.getPlayerIds().size()) {
                                currentPhase = GamePhase.PLACING;
                                PhaseChangeMessage msg = new PhaseChangeMessage(GamePhase.PLACING);
                                for (String pid : currentRoom.getPlayerIds()) {
                                    ClientHandler handler = clients.get(pid);
                                    if (handler != null) handler.sendObject(msg);
                                }
                                System.out.println("[ROUND] Phase -> PLACING");
                            }
                        }
                        else if (currentPhase == GamePhase.PLACING) {
                            // 所有玩家都放置了
                            if (currentRoom.playerPlacements.size() == currentRoom.getPlayerIds().size()) {
                                currentPhase = GamePhase.PLAYING;
                                gameStartTime = System.currentTimeMillis();
                                
                                PhaseChangeMessage msg = new PhaseChangeMessage(GamePhase.PLAYING);
                                for (String pid : currentRoom.getPlayerIds()) {
                                    ClientHandler handler = clients.get(pid);
                                    if (handler != null) {
                                        handler.sendObject(msg);
                                        // 發送所有平台放置
                                        for (Map.Entry<String, PlatformPlacement> entry : currentRoom.playerPlacements.entrySet()) {
                                            handler.sendObject(new PlacementMessage(entry.getKey(), entry.getValue(), true));
                                        }
                                    }
                                }
                                System.out.println("[ROUND] Phase -> PLAYING");
                            }
                        }
                    else if (currentPhase == GamePhase.PLAYING) {
                        long elapsed = System.currentTimeMillis() - gameStartTime;
// System.out.println("[DEBUG] completedPlayers=" + currentRoom.completedPlayers);
                    // System.out.println("[DEBUG] failedPlayers=" + currentRoom.failedPlayers);
                        boolean allFinished = currentRoom.completedPlayers.size() == currentRoom.getPlayerIds().size();
                        boolean allFailed = currentRoom.failedPlayers.size() == currentRoom.getPlayerIds().size();
                        boolean timeUp = elapsed >= GAME_DURATION;
                    
                    //  System.out.println("[DEBUG] allFinished=" + allFinished +
                    //                    ", allFailed=" + allFailed +
                    //                    ", timeUp=" + timeUp);
                        if (allFinished || allFailed || timeUp) {
                            System.out.println("[ROUND] Ending round - " +
                                (allFinished ? "all finished" : allFailed ? "all failed" : "time up"));

                            endCurrentRound();
                            return;
                        }
                    }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
            roundMonitorRunning = false; // 執行完畢後釋放
        }
            }).start();
        }
        
        /**
         * 結束當前回合
         */
        private void endCurrentRound() {
            if (currentRoom == null) return;
            
            // 計算分數
            Map<String, Integer> roundScores = calculateRoundScores();
            
            // 更新總分
            for (Map.Entry<String, Integer> entry : roundScores.entrySet()) {
                String pid = entry.getKey();
                int score = entry.getValue();
                currentRoom.playerTotalScores.put(pid, 
                    currentRoom.playerTotalScores.getOrDefault(pid, 0) + score);
            }
            
            // 準備完成順序
            List<String> finishOrder = new ArrayList<>();
            for (FinishRecord record : currentRoom.finishRecords) {
                finishOrder.add(record.playerId);
            }
            for (String pid : currentRoom.failedPlayers) {
                if (!finishOrder.contains(pid)) finishOrder.add(pid);
            }
            for (String pid : currentRoom.getPlayerIds()) {
                if (!finishOrder.contains(pid)) finishOrder.add(pid);
            }
            
            // 發送回合結束訊息
            RoundEndMessage roundEndMsg = new RoundEndMessage(
                roundScores,
                new HashMap<>(currentRoom.playerTotalScores),
                 finishOrder,
              currentRoom.getInfo().currentRound,
currentRoom.getInfo().totalRounds

            );
            
            for (String pid : currentRoom.getPlayerIds()) {
                ClientHandler handler = clients.get(pid);
                if (handler != null) {
                    handler.sendObject(roundEndMsg);
                }
            }
            
            System.out.println("[ROUND] Round " + currentRoom.getCurrentRound() + " ended");
            printLeaderboard(roundScores);
            
            // 等待後決定下一步
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 3秒顯示排行榜
                    
                    if (currentRoom == null) return;
                    
                    // 檢查是否完成5輪
                    if (currentRoom.isGameComplete()) {
                        // 遊戲結束，返回房間
                        currentRoom.returnToWaiting();
                        
                        ReturnToRoomMessage returnMsg = new ReturnToRoomMessage(
                            "Game Complete! 5 rounds finished. Ready up for another game!");
                        
                        for (String pid : currentRoom.getPlayerIds()) {
                            ClientHandler handler = clients.get(pid);
                            if (handler != null) {
                                handler.sendObject(returnMsg);
                                handler.sendObject(new RoomUpdateMessage(currentRoom.getInfo()));
                            }
                        }
                        
                        System.out.println("[GAME] Complete! Room " + currentRoom.getInfo().roomCode + 
                                         " returned to waiting");
                    } else {
                        // 繼續下一輪
                        currentRoom.nextRound();
                        System.out.println("[GAME] Starting round " + currentRoom.getCurrentRound() + "/5");
                        startNewRound();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        
        /**
         * 計算回合分數
         */
        private Map<String, Integer> calculateRoundScores() {
            Map<String, Integer> scores = new HashMap<>();
            
            // 排序完成記錄
            Collections.sort(currentRoom.finishRecords, Comparator.comparingLong(r -> r.finishTime));
            
            // 分配分數
            for (int i = 0; i < currentRoom.finishRecords.size(); i++) {
                String pid = currentRoom.finishRecords.get(i).playerId;
                int score = (i == 0) ? 100 : (i == 1) ? 70 : (i == 2) ? 50 : 30;
                scores.put(pid, score);
            }
            
            // 失敗和未完成的玩家得0分
            for (String pid : currentRoom.getPlayerIds()) {
                scores.putIfAbsent(pid, 0);
            }
            
            return scores;
        }
        
        /**
         * 列印排行榜
         */
        private void printLeaderboard(Map<String, Integer> roundScores) {
            System.out.println("\n========== LEADERBOARD ==========");
            
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(currentRoom.playerTotalScores.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            int rank = 1;
            for (Map.Entry<String, Integer> entry : sorted) {
                String pid = entry.getKey();
                int total = entry.getValue();
                int round = roundScores.getOrDefault(pid, 0);
                
                System.out.printf("%d. %s | Round: +%d | Total: %d\n", rank++, pid, round, total);
            }
            System.out.println("=================================\n");
        }
        
        /**
         * 處理選擇
         */
        private void handleSelection(SelectionMessage msg) {
            if (currentRoom == null) return;
            currentRoom.playerSelections.put(playerId, msg.objectId);
            System.out.println("[SELECT] " + playerId + " chose platform " + msg.objectId);
        }
        
        /**
         * 處理放置
         */
        private void handlePlacement(PlacementMessage msg) {
            if (currentRoom == null) return;
            
            msg.playerId = playerId;
            
            if (msg.confirmed) {
                currentRoom.playerPlacements.put(playerId, msg.placement);
                currentRoom.playerPreviewPlacements.remove(playerId);
                System.out.printf("[PLACE] %s confirmed at (%.0f, %.0f) rotation=%.0f°\n", 
                    playerId, msg.placement.x, msg.placement.y, msg.placement.rotation);
                
                // 廣播確認放置
                broadcastToRoom(msg, null);
            } else {
                currentRoom.playerPreviewPlacements.put(playerId, msg.placement);
                // 廣播預覽
                broadcastToRoom(msg, playerId);
            }
        }
        
        /**
         * 處理完成
         */
        private void handleFinish(FinishMessage msg) {
            if (currentRoom == null) return;
            if (currentRoom.completedPlayers.contains(playerId)) return;
            
            currentRoom.completedPlayers.add(playerId);
            currentRoom.finishRecords.add(new FinishRecord(playerId, msg.finishTime));
            
            System.out.printf("[FINISH] %s completed! Rank: %d | Time: %.2fs\n", 
                playerId, currentRoom.finishRecords.size(), msg.finishTime / 1000.0);
        }
        
        /**
         * 處理失敗
         */
        private void handleFail(FailMessage msg) {
            if (currentRoom == null) return;
            if (currentRoom.completedPlayers.contains(playerId)) return;
            
            currentRoom.completedPlayers.add(playerId);
            currentRoom.failedPlayers.add(playerId);
            System.out.println("[FAIL] " + playerId + " fell off the map!");
        }
        
        /**
         * 離開房間
         */
        private void handleLeaveRoom() {
            if (currentRoom == null) return;
            
            RoomManager.leaveRoom(playerId);
            currentRoom = null;
            
            System.out.println("[ROOM] " + playerId + " left the room");
        }
        
        /**
         * 廣播房間更新
         */
        private void broadcastRoomUpdate() {
            if (currentRoom == null) return;
            
            RoomUpdateMessage msg = new RoomUpdateMessage(currentRoom.getInfo());
            for (String pid : currentRoom.getPlayerIds()) {
                ClientHandler handler = clients.get(pid);
                if (handler != null) {
                    handler.sendObject(msg);
                }
            }
        }
        
        /**
         * 廣播訊息給房間內所有玩家（可排除某玩家）
         */
        private void broadcastToRoom(Object obj, String excludeId) {
            if (currentRoom == null) return;
            
            for (String pid : currentRoom.getPlayerIds()) {
                if (excludeId != null && pid.equals(excludeId)) continue;
                
                ClientHandler handler = clients.get(pid);
                if (handler != null) {
                    handler.sendObject(obj);
                }
            }
        }
        
        /**
         * 生成新物件
         */
        private List<GameObjectInfo> generateNewObjects() {
            List<GameObjectInfo> objects = new ArrayList<>();
            Random rand = new Random();
            
            for (int i = 0; i < 5; i++) {
                int width = 80 + rand.nextInt(150);
                int height = 15 + rand.nextInt(20);
                String color = String.format("#%06X", rand.nextInt(0xFFFFFF) | 0x800000);
                objects.add(new GameObjectInfo(i, width, height, color));
            }
            
            return objects;
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
            
            if (currentRoom != null) {
                RoomManager.leaveRoom(playerId);
                broadcastRoomUpdate();
                
                DisconnectMessage disconnectMsg = new DisconnectMessage(playerId);
                broadcastToRoom(disconnectMsg, null);
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
    
    static class FinishRecord {
        String playerId;
        long finishTime;
        
        FinishRecord(String playerId, long finishTime) {
            this.playerId = playerId;
            this.finishTime = finishTime;
        }
    }
}

/**
 * Room 類別擴展（添加遊戲狀態）
 */
class Room {
    private final RoomInfo info;
    private final Object lock = new Object();
    
    // 遊戲狀態
    final Map<String, Integer> playerSelections = new ConcurrentHashMap<>();
    final Map<String, PlatformPlacement> playerPlacements = new ConcurrentHashMap<>();
    final Map<String, PlatformPlacement> playerPreviewPlacements = new ConcurrentHashMap<>();
    final Map<String, Integer> playerTotalScores = new ConcurrentHashMap<>();
    final List<GameServer.FinishRecord> finishRecords = Collections.synchronizedList(new ArrayList<>());
    final Set<String> failedPlayers = ConcurrentHashMap.newKeySet();
    final Set<String> completedPlayers = ConcurrentHashMap.newKeySet();
    
    public Room(String roomCode, String hostId, int maxPlayers, RoomType roomType) {
    this.info = new RoomInfo(roomCode, hostId, maxPlayers, roomType);
    // 初始化分數
    playerTotalScores.put(hostId, 0);
}
    
    public boolean addPlayer(String playerId) {
        synchronized (lock) {
            if (info.state != RoomState.WAITING) return false;
            if (info.playerIds.size() >= info.maxPlayers) return false;
            if (!info.playerIds.contains(playerId)) {
                info.playerIds.add(playerId);
                info.readyStatus.put(playerId, false);
                playerTotalScores.put(playerId, 0);
                return true;
            }
            return false;
        }
    }
    
    public void removePlayer(String playerId) {
        synchronized (lock) {
            info.playerIds.remove(playerId);
            info.readyStatus.remove(playerId);
            playerTotalScores.remove(playerId);
            
            if (playerId.equals(info.hostId) && !info.playerIds.isEmpty()) {
                info.hostId = info.playerIds.get(0);
                System.out.println("[ROOM] Host transferred to " + info.hostId);
            }
        }
    }
    
    public void setPlayerReady(String playerId, boolean ready) {
        synchronized (lock) {
            info.readyStatus.put(playerId, ready);
        }
    }
    
 public boolean allPlayersReady() {
    synchronized (lock) {
        if (info.playerIds.isEmpty()) return false;
        for (String pid : info.playerIds) {
            if (pid.equals(info.hostId)) continue; // 房主略過
            if (!info.readyStatus.getOrDefault(pid, false)) return false;
        }
        return true;
    }
}

    
    public void startGame() {
        synchronized (lock) {
            info.state = RoomState.PLAYING;
            info.currentRound = 1;
            for (String pid : info.playerIds) {
                info.readyStatus.put(pid, false);
            }
        }
    }
    
    public void nextRound() {
        synchronized (lock) {
            info.currentRound++;
        }
    }
    
    public boolean isGameComplete() {
        synchronized (lock) {
            return info.currentRound >= info.totalRounds;
        }
    }
    
    public void returnToWaiting() {
        synchronized (lock) {
            info.state = RoomState.WAITING;
            info.currentRound = 0;
            for (String pid : info.playerIds) {
                info.readyStatus.put(pid, false);
            }
        }
    }
    
    public RoomInfo getInfo() {
    synchronized (lock) {
        RoomInfo copy = new RoomInfo(info.roomCode, info.hostId, info.maxPlayers, info.roomType);
        copy.playerIds = new ArrayList<>(info.playerIds);
        copy.state = info.state;
        copy.currentRound = info.currentRound;
        copy.totalRounds = info.totalRounds;
        copy.readyStatus = new HashMap<>(info.readyStatus);
        return copy;
    }
}
    
    public boolean isEmpty() {
        synchronized (lock) {
            return info.playerIds.isEmpty();
        }
    }
    
    public String getHostId() {
        synchronized (lock) {
            return info.hostId;
        }
    }
    
    public List<String> getPlayerIds() {
        synchronized (lock) {
            return new ArrayList<>(info.playerIds);
        }
    }
    
    public RoomState getState() {
        synchronized (lock) {
            return info.state;
        }
    }
    
    public int getCurrentRound() {
        synchronized (lock) {
            return info.currentRound;
        }
    }
}