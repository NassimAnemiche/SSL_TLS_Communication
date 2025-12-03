import javax.net.ssl.*;
import java.io.*;
import java.security.SecureRandom;
import java.util.Scanner;

public class SecureChatClient {

    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public SecureChatClient(String host, int port) throws Exception {
        SSLContext ctx = createTrustAllContext();
        SSLSocketFactory factory = ctx.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();

        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    private SSLContext createTrustAllContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }

    private void sendMessage(ChatMessage msg) {
        try {
            byte[] full = msg.toBytes();
            int bodyLength = full.length - 8;
            out.writeInt(bodyLength);
            out.write(full, 4, 4 + bodyLength);
            out.flush();
        } catch (IOException ignored) {}
    }

    private ChatMessage readMessage() {
        try {
            int bodyLength = in.readInt();
            byte[] full = new byte[8 + bodyLength];
            full[0] = (byte) ((bodyLength >> 24) & 0xFF);
            full[1] = (byte) ((bodyLength >> 16) & 0xFF);
            full[2] = (byte) ((bodyLength >> 8) & 0xFF);
            full[3] = (byte) (bodyLength & 0xFF);
            in.readFully(full, 4, 4 + bodyLength);
            return ChatMessage.fromBytes(full);
        } catch (IOException e) {
            return null;
        }
    }

    public void runCLI() {
        Scanner sc = new Scanner(System.in);

        new Thread(() -> {
            while (true) {
                ChatMessage msg = readMessage();
                if (msg == null) continue;
                System.out.println("[SERVER] " + msg.getType() + " | " +
                        "from=" + msg.getSender() +
                        " | room=" + msg.getRoom() +
                        " | content=" + msg.getContent());
            }
        }).start();

        while (true) {
            String line = sc.nextLine().trim();

            if (line.startsWith("/login ")) {
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 2) {
                    System.out.println("Usage: /login <username> <password>");
                    continue;
                }
                String username = parts[1];
                String password = parts.length >= 3 ? parts[2] : "";
                sendMessage(new ChatMessage(
                        MessageType.LOGIN_REQUEST,
                        username,
                        null,
                        null,
                        password
                ));
            }

            else if (line.startsWith("/join ")) {
                String[] parts = line.split("\\s+", 2);
                if (parts.length < 2) {
                    System.out.println("Usage: /join <roomname>");
                    continue;
                }
                String room = parts[1];
                sendMessage(new ChatMessage(
                        MessageType.TEXT_MESSAGE,
                        null,
                        null,
                        room,
                        "joined room"
                ));
            }

            else if (line.startsWith("/msg ")) {
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) {
                    System.out.println("Usage: /msg <username> <message>");
                    continue;
                }
                String target = parts[1];
                String text = parts[2];
                sendMessage(new ChatMessage(
                        MessageType.PRIVATE_MESSAGE,
                        null,
                        target,
                        null,
                        text
                ));
            }

            else if (line.equals("/rooms")) {
                System.out.println("Rooms listing is not fully implemented on the server.");
            }

            else if (line.equals("/users")) {
                System.out.println("User listing is not fully implemented on the server.");
            }

            else if (line.equals("/quit")) {
                System.out.println("Closing client.");
                try {
                    socket.close();
                } catch (IOException ignored) {}
                System.exit(0);
            }

            else {
                System.out.println("Unknown command. Use: /login /join /msg /rooms /users /quit");
            }
        }
    }

    public static void main(String[] args) {
        try {
            SecureChatClient client = new SecureChatClient("localhost", 9000);
            client.runCLI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
