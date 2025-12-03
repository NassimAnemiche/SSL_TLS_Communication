import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import java.security.SecureRandom;
import java.util.*;

public class SecureChatServer {

    private SSLServerSocket serverSocket;
    private final Map<String, ClientSession> activeSessions = new HashMap<>();
    private final Map<String, ChatRoom> chatRooms = new HashMap<>();
    private final ProtocolParser messageParser = new ProtocolParser();

    public SecureChatServer(int port, String keystorePath, String password) throws Exception {
        SSLContext context = createSSLContext(keystorePath, password);
        SSLServerSocketFactory factory = context.getServerSocketFactory();
        serverSocket = (SSLServerSocket) factory.createServerSocket();
        serverSocket.bind(new InetSocketAddress(port));
        System.out.println("SecureChatServer listening on " + port);
    }

    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    public void launch() {
        while (true) {
            try {
                SSLSocket client = (SSLSocket) serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handleClient(SSLSocket socket) {
        try (SSLSocket s = socket) {
            s.startHandshake();

            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            ClientSession session = new ClientSession(null, out);

            while (true) {
                int bodyLength;
                try {
                    bodyLength = in.readInt();
                } catch (EOFException eof) {
                    break;
                }

                if (bodyLength <= 0 || bodyLength > 10_000_000) {
                    sendError(session, "invalid-length");
                    break;
                }

                byte[] full = new byte[8 + bodyLength];
                full[0] = (byte) ((bodyLength >> 24) & 0xFF);
                full[1] = (byte) ((bodyLength >> 16) & 0xFF);
                full[2] = (byte) ((bodyLength >> 8) & 0xFF);
                full[3] = (byte) (bodyLength & 0xFF);

                in.readFully(full, 4, 4 + bodyLength);
                handleProtocolMessage(full, session);
            }

            if (session.username != null) {
                activeSessions.remove(session.username);
                for (ChatRoom room : chatRooms.values()) {
                    room.removeMember(session);
                }
            }

        } catch (Exception ignored) {}
    }

    public void handleProtocolMessage(byte[] messageData, ClientSession session) {
        ChatMessage msg = messageParser.parse(messageData);
        if (msg == null) {
            sendError(session, "malformed-message");
            return;
        }

        MessageType type = msg.getType();

        switch (type) {
            case LOGIN_REQUEST:
                processLogin(msg, session);
                break;

            case TEXT_MESSAGE:
                if (session.username == null) {
                    sendError(session, "not-authenticated");
                    return;
                }
                ChatMessage textMsg = new ChatMessage(
                        MessageType.TEXT_MESSAGE,
                        session.username,
                        null,
                        msg.getRoom(),
                        msg.getContent()
                );
                broadcastToRoom(textMsg);
                break;

            case PRIVATE_MESSAGE:
                if (session.username == null) {
                    sendError(session, "not-authenticated");
                    return;
                }
                ChatMessage privMsg = new ChatMessage(
                        MessageType.PRIVATE_MESSAGE,
                        session.username,
                        msg.getRecipient(),
                        null,
                        msg.getContent()
                );
                sendPrivateMessage(privMsg);
                break;

            default:
                sendError(session, "unknown-type");
        }
    }

    private void processLogin(ChatMessage message, ClientSession session) {
        String username = message.getSender();
        if (username == null || username.trim().isEmpty()) {
            sendError(session, "empty-username");
            return;
        }
        if (activeSessions.containsKey(username)) {
            sendError(session, "username-taken");
            return;
        }

        session.username = username;
        activeSessions.put(username, session);

        ChatMessage response = new ChatMessage(
                MessageType.LOGIN_RESPONSE,
                "server",
                username,
                null,
                "login-success"
        );
        sendToSession(session, response);

        ChatRoom room = chatRooms.computeIfAbsent("General", ChatRoom::new);
        room.addMember(session);
    }

    private void broadcastToRoom(ChatMessage message) {
        String roomName = message.getRoom();
        if (roomName == null) return;

        ChatRoom room = chatRooms.computeIfAbsent(roomName, ChatRoom::new);
        room.addMember(activeSessions.get(message.getSender()));

        for (ClientSession member : room.getMembers()) {
            sendToSession(member, message);
        }
    }

    private void sendPrivateMessage(ChatMessage message) {
        ClientSession dest = activeSessions.get(message.getRecipient());
        if (dest == null) {
            sendError(activeSessions.get(message.getSender()), "user-offline");
            return;
        }
        sendToSession(dest, message);
    }

    private void sendError(ClientSession session, String code) {
        if (session == null) return;

        ChatMessage err = new ChatMessage(
                MessageType.ERROR_RESPONSE,
                "server",
                session.username,
                null,
                code
        );
        sendToSession(session, err);
    }

    private void sendToSession(ClientSession session, ChatMessage msg) {
        if (session == null || session.out == null) return;

        try {
            byte[] full = msg.toBytes();
            int bodyLength = full.length - 8;
            session.out.writeInt(bodyLength);
            session.out.write(full, 4, 4 + bodyLength);
            session.out.flush();
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        int port = 9000;
        String ks = "server.jks";
        String pw = "password123";

        try {
            SecureChatServer server = new SecureChatServer(port, ks, pw);
            server.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ClientSession {
    String username;
    final DataOutputStream out;

    ClientSession(String username, DataOutputStream out) {
        this.username = username;
        this.out = out;
    }
}

class ChatRoom {
    private final String name;
    private final Set<ClientSession> members = new HashSet<>();

    ChatRoom(String name) { this.name = name; }

    void addMember(ClientSession session) { members.add(session); }
    void removeMember(ClientSession session) { members.remove(session); }
    Set<ClientSession> getMembers() { return members; }
}

class ProtocolParser {
    ChatMessage parse(byte[] data) {
        return ChatMessage.fromBytes(data);
    }
}
