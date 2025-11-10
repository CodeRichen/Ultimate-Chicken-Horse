import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {

    private static final int PORT = 5000;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final String[] COLORS = {
        "#FF0000", "#00FF00", "#0000FF", "#FFFF00", 
        "#FF00FF", "#00FFFF", "#FFA500", "#800080"
    };
    private static int colorIndex = 0;

    public static void main(String[] args) {
        System.out.println("🎮 Game Server running on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true); // 禁用 Nagle 算法
                
                String playerId = UUID.randomUUID().toString().substring(0, 8);
                String color = COLORS[colorIndex++ % COLORS.length];
                
                System.out.println("✅ Client connected: " + playerId + " (Color: " + color + ")");

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

                // 發送自己的玩家ID和顏色
                InitMessage initMsg = new InitMessage(playerId, color);
                synchronized (out) {
                    out.writeObject(initMsg);
                    out.flush();
                }

                // 發送現有玩家列表
                synchronized (clients) {
                    for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                        if (!entry.getKey().equals(playerId)) {
                            // 通知新玩家其他玩家的存在
                            PlayerInfo existingPlayer = new PlayerInfo(
                                entry.getKey(), 
                                entry.getValue().color, 
                                100, 500, false, 1.0
                            );
                            synchronized (out) {
                                out.writeObject(existingPlayer);
                                out.flush();
                            }
                        }
                    }
                }

                // 通知其他玩家新玩家加入
                PlayerInfo newPlayerInfo = new PlayerInfo(playerId, color, 100, 500, false, 1.0);
                broadcast(newPlayerInfo, playerId);

                // 接收並廣播玩家位置更新
                while (running) {
                    try {
                        Object obj = in.readObject();
                        
                        if (obj instanceof PlayerInfo info) {
                            // 確保玩家ID和顏色正確
                            info.playerId = playerId;
                            info.colorHex = color;
                            
                            // 廣播給所有其他客戶端
                            broadcast(info, playerId);
                        } else if (obj instanceof PlatformInfo platformInfo) {
                            // 廣播平台移動給所有其他客戶端
                            broadcastPlatform(platformInfo, playerId);
                        }
                    } catch (EOFException | SocketException e) {
                        System.out.println("❌ Client disconnected: " + playerId);
                        break;
                    } catch (StreamCorruptedException e) {
                        System.out.println("⚠️ Stream corrupted for client " + playerId);
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Error with client " + playerId + ": " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void broadcast(PlayerInfo info, String excludeId) {
            List<String> deadClients = new ArrayList<>();
            
            synchronized (clients) {
                for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                    if (!entry.getKey().equals(excludeId)) {
                        ClientHandler handler = entry.getValue();
                        if (!handler.sendObject(info)) {
                            deadClients.add(entry.getKey());
                        }
                    }
                }
            }
            
            // 移除死亡連線
            for (String deadId : deadClients) {
                clients.remove(deadId);
            }
        }

        private void broadcastPlatform(PlatformInfo platformInfo, String excludeId) {
            List<String> deadClients = new ArrayList<>();
            
            synchronized (clients) {
                for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                    if (!entry.getKey().equals(excludeId)) {
                        ClientHandler handler = entry.getValue();
                        if (!handler.sendObject(platformInfo)) {
                            deadClients.add(entry.getKey());
                        }
                    }
                }
            }
            
            // 移除死亡連線
            for (String deadId : deadClients) {
                clients.remove(deadId);
            }
        }

        private boolean sendObject(Object obj) {
            try {
                synchronized (out) {
                    out.writeObject(obj);
                    out.flush();
                    out.reset(); // 清除快取，避免物件重用問題
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private void cleanup() {
            running = false;
            clients.remove(playerId);
            
            // 通知其他玩家該玩家離開
            DisconnectMessage disconnectMsg = new DisconnectMessage(playerId);
            synchronized (clients) {
                for (ClientHandler handler : clients.values()) {
                    handler.sendObject(disconnectMsg);
                }
            }
            
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                socket.close();
            } catch (IOException e) {
                // 忽略
            }
            
            System.out.println("🔌 Client " + playerId + " fully cleaned up");
        }
    }
}