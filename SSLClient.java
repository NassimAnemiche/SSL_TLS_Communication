
import javax.net.ssl.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SSLClient {

    private SSLSocket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private final String host;
    private final int port;
    private final boolean trustAllCerts;

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    public void connect() throws Exception {
        SSLContext context = trustAllCerts ? createTrustAllContext() : SSLContext.getDefault();
        SSLSocketFactory factory = context.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        socket.setSoTimeout(1000);
    }

    private SSLContext createTrustAllContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }

    public void sendFrame(byte type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + bodyBytes.length];
        payload[0] = type;
        System.arraycopy(bodyBytes, 0, payload, 1, bodyBytes.length);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    public void readReplies() throws IOException {
        long until = System.currentTimeMillis() + 800;
        while (System.currentTimeMillis() < until) {
            try {
                String r = in.readUTF();
                System.out.println("Server: " + r);
            } catch (SocketTimeoutException ste) {
                break;
            }
        }
    }

    public void disconnect() throws IOException {
        if (socket != null) socket.close();
    }

    public static void main(String[] args) {
        try {
<<<<<<< HEAD
            SSLClient client = new SSLClient("localhost", 8443, true);
=======
            SSLClient client = new SSLClient("localhost", 8443, false); // testing mode : trustAllCerts = true ||| production mode : false
>>>>>>> a8677123b2dd19b18ad62643bf5f4b345a4f5d47
            client.connect();

            client.sendFrame((byte)1, "alice"); // login
            client.readReplies();

            client.sendFrame((byte)2, "lobby\nHello from alice"); // room message
            client.readReplies();

            client.sendFrame((byte)3, "bob\nHey Bob, are you there?"); // private
            client.readReplies();

            client.sendFrame((byte)2, "badformat"); // malformed (no newline)
            client.readReplies();

            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
