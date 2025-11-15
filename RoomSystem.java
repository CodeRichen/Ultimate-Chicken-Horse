import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房間系統相關類別
 */

// 房間類型
enum RoomType {
    PRIVATE,  // 私人房間（需要房間碼）
    PUBLIC    // 公共房間（可隨機加入）
}

// 房間狀態
enum RoomState {
    WAITING,    // 等待玩家
    READY,      // 準備開始
    PLAYING     // 遊戲中
}

// 房間資訊
class RoomInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    String roomCode;
    String hostId;
    List<String> playerIds;
    int maxPlayers;
    RoomState state;
    int currentRound;
    int totalRounds;
    Map<String, Boolean> readyStatus;
    RoomType roomType;
    
    public RoomInfo(String roomCode, String hostId, int maxPlayers, RoomType roomType) {
        this.roomCode = roomCode;
        this.hostId = hostId;
        this.playerIds = new ArrayList<>();
        this.playerIds.add(hostId);
        this.maxPlayers = maxPlayers;
        this.state = RoomState.WAITING;
        this.currentRound = 0;
        this.totalRounds = 5;
        this.readyStatus = new HashMap<>();
        this.readyStatus.put(hostId, false);
        this.roomType = roomType;
    }
}

// 創建房間請求
class CreateRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    int maxPlayers;
    RoomType roomType;
    
    public CreateRoomRequest(int maxPlayers, RoomType roomType) {
        this.maxPlayers = maxPlayers;
        this.roomType = roomType;
    }
}

// 創建房間回應
class CreateRoomResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    boolean success;
    String roomCode;
    String message;
    
    public CreateRoomResponse(boolean success, String roomCode, String message) {
        this.success = success;
        this.roomCode = roomCode;
        this.message = message;
    }
}

// 加入房間請求
class JoinRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    String roomCode;
    
    public JoinRoomRequest(String roomCode) {
        this.roomCode = roomCode;
    }
}

// 隨機加入公共房間請求
class JoinRandomRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;
}

// 加入房間回應
class JoinRoomResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    boolean success;
    String message;
    RoomInfo roomInfo;
    
    public JoinRoomResponse(boolean success, String message, RoomInfo roomInfo) {
        this.success = success;
        this.message = message;
        this.roomInfo = roomInfo;
    }
}

// 房間更新訊息
class RoomUpdateMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    RoomInfo roomInfo;
    
    public RoomUpdateMessage(RoomInfo roomInfo) {
        this.roomInfo = roomInfo;
    }
}

// 玩家準備狀態訊息
class PlayerReadyMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String playerId;
    boolean ready;
    
    public PlayerReadyMessage(String playerId, boolean ready) {
        this.playerId = playerId;
        this.ready = ready;
    }
}

// 開始遊戲請求（僅房主）
class StartGameRequest implements Serializable {
    private static final long serialVersionUID = 1L;
}

// 遊戲結束後返回房間訊息
class ReturnToRoomMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    String message;
    
    public ReturnToRoomMessage(String message) {
        this.message = message;
    }
}

// 離開房間請求
class LeaveRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;
}

/**
 * 房間管理器
 */
class RoomManager {
    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final Map<String, String> playerToRoom = new ConcurrentHashMap<>();
    private static final Random random = new Random();
    
    /**
     * 生成4位數房間代碼
     */
    public static String generateRoomCode() {
        String code;
        do {
            code = String.format("%04d", random.nextInt(10000));
        } while (rooms.containsKey(code));
        return code;
    }
    
    /**
     * 創建房間
     */
    public static Room createRoom(String hostId, int maxPlayers, RoomType roomType) {
        String roomCode = generateRoomCode();
        Room room = new Room(roomCode, hostId, maxPlayers, roomType);
        rooms.put(roomCode, room);
        playerToRoom.put(hostId, roomCode);
        System.out.println("[ROOM] Created " + roomType + " room " + roomCode + 
                          " by " + hostId + " (max: " + maxPlayers + ")");
        return room;
    }
    
    /**
     * 加入房間
     */
    public static Room joinRoom(String playerId, String roomCode) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            return null;
        }
        
        if (room.addPlayer(playerId)) {
            playerToRoom.put(playerId, roomCode);
            System.out.println("[ROOM] " + playerId + " joined room " + roomCode);
            return room;
        }
        return null;
    }
    
    /**
     * 隨機加入公共房間
     */
    public static Room joinRandomPublicRoom(String playerId) {
        // 找到所有等待中的公共房間
        List<Room> availableRooms = new ArrayList<>();
        
        for (Room room : rooms.values()) {
            RoomInfo info = room.getInfo();
            if (info.roomType == RoomType.PUBLIC && 
                info.state == RoomState.WAITING &&
                info.playerIds.size() < info.maxPlayers) {
                availableRooms.add(room);
            }
        }
        
        if (availableRooms.isEmpty()) {
            System.out.println("[ROOM] No public rooms available for " + playerId);
            return null;
        }
        
        // 隨機選擇一個房間
        Room room = availableRooms.get(random.nextInt(availableRooms.size()));
        
        if (room.addPlayer(playerId)) {
            playerToRoom.put(playerId, room.getInfo().roomCode);
            System.out.println("[ROOM] " + playerId + " randomly joined public room " + 
                             room.getInfo().roomCode);
            return room;
        }
        
        return null;
    }
    
    /**
     * 獲取玩家所在房間
     */
    public static Room getPlayerRoom(String playerId) {
        String roomCode = playerToRoom.get(playerId);
        return roomCode != null ? rooms.get(roomCode) : null;
    }
    
    /**
     * 玩家離開房間
     */
    public static void leaveRoom(String playerId) {
        String roomCode = playerToRoom.remove(playerId);
        if (roomCode != null) {
            Room room = rooms.get(roomCode);
            if (room != null) {
                room.removePlayer(playerId);
                if (room.isEmpty()) {
                    rooms.remove(roomCode);
                    System.out.println("[ROOM] Room " + roomCode + " deleted (empty)");
                }
            }
        }
    }
    
    /**
     * 獲取房間
     */
    public static Room getRoom(String roomCode) {
        return rooms.get(roomCode);
    }
    
    /**
     * 獲取所有公共房間數量（用於調試）
     */
    public static int getPublicRoomCount() {
        int count = 0;
        for (Room room : rooms.values()) {
            if (room.getInfo().roomType == RoomType.PUBLIC && 
                room.getInfo().state == RoomState.WAITING) {
                count++;
            }
        }
        return count;
    }
}