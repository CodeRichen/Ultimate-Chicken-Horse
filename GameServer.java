import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {

    private static final int PORT = 5000;
    private static final List<ObjectOutputStream> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        System.out.println("Server running on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getInetAddress());

                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                clients.add(out);

                // 每個 Client 用獨立 Thread 處理
                new Thread(() -> {
                    try {
                        while (true) {
                            Object obj = in.readObject();
                            if (obj instanceof PlayerInfo info) {
                                // 廣播給其他 Client
                                synchronized (clients) {
                                    for (ObjectOutputStream clientOut : clients) {
                                        try {
                                            clientOut.writeObject(info);
                                            clientOut.flush();
                                        } catch (IOException e) {
                                            // 忽略發送失敗
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Client disconnected.");
                        clients.remove(out);
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
