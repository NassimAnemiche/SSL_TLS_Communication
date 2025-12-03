import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SecureChatServer {

    private SSLServerSocket serverSocket;
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();

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
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    public void launch() {
        while (true) {
            try {
                SSLSocket client = (SSLSocket) serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            } catch (IOException e) {
                e.printStackTrace();
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
                int length;
                try {
                    length = in.readInt();
                } catch (EOFException eof) {
                    break;
                }
                if (length <= 0 || length > 10_000_000) {
                    sendError(out, "invalid-length");
                    break;
                }
                byte[] payload = new byte[length];
                in.readFully(payload);

                try {
                    handleProtocolMessage(s, payload, session);
                } catch (Exception ex) {
                    sendError(out, "malformed-message");
                }
            }

            if (session.username != null) {
                activeSessions.remove(session.username);
                for (ChatRoom r : chatRooms.values()) r.remove(session.username);
            }

        } catch (Exception e) {
            // keep simple: print and return
            e.printStackTrace();
        }
    }

    // Core protocol dispatcher
    public void handleProtocolMessage(SSLSocket socket, byte[] messageData, ClientSession session) {
        ProtocolParser parser = new ProtocolParser();
        Message m = parser.parse(messageData);
        if (m == null) {
            sendError(session.out, "unknown-or-malformed");
            return;
        }

        switch (m.type) {
            case LOGIN:
                processLogin((LoginMessage) m, session);
                break;
            case TEXT:
                TextMessage tm = (TextMessage) m;
                if (session.username == null) {
                    sendError(session.out, "not-authenticated");
                    return;
                }
                broadcastToRoom(new TextMessage(session.username, tm.room, tm.text));
                break;
            case PRIVATE:
                PrivateMessage pm = (PrivateMessage) m;
                if (session.username == null) {
                    sendError(session.out, "not-authenticated");
                    return;
                }
                sendPrivateMessage(new PrivateMessage(session.username, pm.targetUser, pm.text));
                break;
            default:
                sendError(session.out, "unknown-type");
        }
    }

    // Process a login: register username with its session output stream
    private void processLogin(LoginMessage message, ClientSession session) {
        String name = message.username.trim();
        if (name.isEmpty()) {
            sendError(session.out, "empty-username");
            return;
        }
        if (activeSessions.containsKey(name)) {
            sendError(session.out, "username-taken");
            return;
        }

        session.username = name;
        activeSessions.put(name, session);
        sendAck(session.out, "login-success");
    }

    // Broadcast to a room: create room if missing
    private void broadcastToRoom(TextMessage message) {
        ChatRoom room = chatRooms.computeIfAbsent(message.room, ChatRoom::new);
        // ensure sender is added into the room
        if (message.sender != null) room.add(message.sender);
        String outText = "[" + message.room + "] " + message.sender + ": " + message.text;
        for (String user : room.members()) {
            ClientSession cs = activeSessions.get(user);
            if (cs != null) sendMessage(cs.out, outText);
        }
    }

    // Send a private message to a single user
    private void sendPrivateMessage(PrivateMessage message) {
        ClientSession dest = activeSessions.get(message.targetUser);
        if (dest == null) {
            ClientSession from = activeSessions.get(message.sender);
            if (from != null) sendError(from.out, "user-offline");
            return;
        }
        String outText = "[private] " + message.sender + ": " + message.text;
        sendMessage(dest.out, outText);
    }

    /* ---- simple wire helpers ---- */
    private void sendError(DataOutputStream out, String code) {
        try {
            out.writeUTF("ERROR:" + code);
            out.flush();
        } catch (IOException ignored) {}
    }

    private void sendAck(DataOutputStream out, String msg) {
        try {
            out.writeUTF("OK:" + msg);
            out.flush();
        } catch (IOException ignored) {}
    }

    private void sendMessage(DataOutputStream out, String text) {
        try {
            out.writeUTF("MSG:" + text);
            out.flush();
        } catch (IOException ignored) {}
    }

    /* ---- inner classes for sessions, rooms and protocol ---- */
    private static class ClientSession {
        volatile String username; // set after login
        final DataOutputStream out;

        ClientSession(String username, DataOutputStream out) {
            this.username = username;
            this.out = out;
        }
    }

    private static class ChatRoom {
        private final String name;
        private final Set<String> members = Collections.newSetFromMap(new ConcurrentHashMap<>());

        ChatRoom(String name) { this.name = name; }

        void add(String user) { members.add(user); }
        void remove(String user) { members.remove(user); }
        Set<String> members() { return members; }
    }

    // Message abstractions
    private enum MessageType { LOGIN, TEXT, PRIVATE }

    private static abstract class Message {
        final MessageType type;
        final String sender; // may be null for login

        Message(MessageType type, String sender) { this.type = type; this.sender = sender; }
    }

    private static class LoginMessage extends Message {
        final String username;
        LoginMessage(String username) { super(MessageType.LOGIN, null); this.username = username; }
    }

    private static class TextMessage extends Message {
        final String room;
        final String text;
        TextMessage(String sender, String room, String text) { super(MessageType.TEXT, sender); this.room = room; this.text = text; }
    }

    private static class PrivateMessage extends Message {
        final String targetUser;
        final String text;
        PrivateMessage(String sender, String targetUser, String text) { super(MessageType.PRIVATE, sender); this.targetUser = targetUser; this.text = text; }
    }

    // Very small parser for the simple protocol described above
    private static class ProtocolParser {
        Message parse(byte[] payload) {
            if (payload.length < 1) return null;
            int t = payload[0] & 0xFF;
            byte[] body = Arrays.copyOfRange(payload, 1, payload.length);
            String s;
            try { s = new String(body, "UTF-8"); } catch (UnsupportedEncodingException e) { s = new String(body); }

            switch (t) {
                case 1: // login
                    return new LoginMessage(s.trim());
                case 2: // text : room\ntext
                    int nl = s.indexOf('\n');
                    if (nl == -1) return null;
                    String room = s.substring(0, nl).trim();
                    String text = s.substring(nl + 1);
                    // sender will be filled later by server using session
                    return new TextMessage(null, room, text);
                case 3: // private : target\ntext
                    int n2 = s.indexOf('\n');
                    if (n2 == -1) return null;
                    String target = s.substring(0, n2).trim();
                    String ptext = s.substring(n2 + 1);
                    return new PrivateMessage(null, target, ptext);
                default:
                    return null;
            }
        }
    }

    /* ---- small main to launch the server ---- */
    public static void main(String[] args) {
        int port = 8443;
        String ks = "server.jks";
        String pw = "password123";
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        if (args.length >= 2) ks = args[1];
        if (args.length >= 3) pw = args[2];

        try {
            SecureChatServer s = new SecureChatServer(port, ks, pw);
            s.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
