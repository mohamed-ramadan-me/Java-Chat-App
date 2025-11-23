import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chat Server
 * Handles multiple client connections, broadcasting messages, and call
 * signaling.
 */
public class Server {
    private static final int PORT = 8889;

    // Thread-safe map to store connected clients (ID -> Handler)
    private static Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private static AtomicInteger idCounter = new AtomicInteger(1);

    public static void main(String[] args) {
        System.out.println("Chat Server starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                int clientId = idCounter.getAndIncrement();
                System.out.println("New client connected: User " + clientId + " (" + socket + ")");

                ClientHandler handler = new ClientHandler(socket, clientId);
                clients.put(clientId, handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Broadcasts a message to all clients except the sender.
     */
    static void broadcast(byte[] data, ClientHandler sender) {
        for (ClientHandler client : clients.values()) {
            if (client != sender) {
                client.sendMessage(data);
            }
        }
    }

    /**
     * Sends a message to a specific client by ID.
     */
    static void sendTo(int targetId, byte[] data) {
        ClientHandler client = clients.get(targetId);
        if (client != null) {
            client.sendMessage(data);
        }
    }

    /**
     * Broadcasts a system message (Sender ID 0) to all clients.
     */
    static void broadcastSystemMessage(String text) {
        try {
            byte[] body = text.getBytes("UTF-8");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream bufferOut = new DataOutputStream(buffer);
            bufferOut.writeByte(1); // TYPE_TEXT
            bufferOut.writeInt(0); // Sender ID 0 (System)
            bufferOut.writeInt(body.length);
            bufferOut.write(body);
            byte[] packet = buffer.toByteArray();

            for (ClientHandler client : clients.values()) {
                client.sendMessage(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void removeClient(ClientHandler client) {
        clients.remove(client.id);
        System.out.println("Client disconnected: User " + client.id);
    }

    /**
     * Handles communication with a single client.
     */
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private int id;
        private DataInputStream in;
        private DataOutputStream out;

        public ClientHandler(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                while (true) {
                    // --- Protocol Header ---
                    // [Type (1 byte)] [TargetID (4 bytes)] [Length (4 bytes)]
                    byte type = in.readByte();
                    int targetId = in.readInt(); // 0 = Broadcast, >0 = Direct Message
                    int length = in.readInt();

                    if (length < 0)
                        break; // Sanity check

                    byte[] body = new byte[length];
                    in.readFully(body);

                    // --- Construct Forward Packet ---
                    // [Type (1 byte)] [SenderID (4 bytes)] [Length (4 bytes)] [Body]
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    DataOutputStream bufferOut = new DataOutputStream(buffer);
                    bufferOut.writeByte(type);
                    bufferOut.writeInt(id); // Sender ID is the current client
                    bufferOut.writeInt(length);
                    bufferOut.write(body);

                    byte[] packet = buffer.toByteArray();

                    // --- Routing Logic ---
                    if (targetId == 0) {
                        Server.broadcast(packet, this);
                    } else {
                        Server.sendTo(targetId, packet);
                    }

                    // --- Call Status Monitoring ---
                    // Intercept call messages to broadcast status updates to everyone
                    String statusMsg = null;
                    if (type == 6) { // TYPE_CALL_REQUEST
                        statusMsg = "📞 User " + id + " is calling User " + targetId;
                    } else if (type == 7) { // TYPE_CALL_ACCEPT
                        statusMsg = "✓ User " + id + " accepted call from User " + targetId;
                    } else if (type == 8) { // TYPE_CALL_DECLINE
                        statusMsg = "✗ User " + id + " declined call from User " + targetId;
                    } else if (type == 9) { // TYPE_CALL_END
                        statusMsg = "Call ended between User " + id + " and User " + targetId;
                    } else if (type == 10) { // TYPE_GROUP_CALL_REQUEST
                        statusMsg = "📢 User " + id + " started a Group Call";
                    }

                    if (statusMsg != null) {
                        Server.broadcastSystemMessage(statusMsg);
                    }
                }
            } catch (EOFException e) {
                // Client disconnected normally
            } catch (IOException e) {
                // Connection error
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Server.removeClient(this);
            }
        }

        public void sendMessage(byte[] data) {
            try {
                synchronized (out) {
                    out.write(data);
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
