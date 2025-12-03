
import javax.net.ssl.*;
import java.io.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SSLClient {

    private SSLSocket socket;
    private String host;
    private int port;
    private boolean trustAllCerts;

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    public void connect() throws Exception {
        SSLContext context = trustAllCerts ? createTrustAllContext() : SSLContext.getDefault();
        SSLSocketFactory factory = context.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();  // handshake explicit
    }

    private SSLContext createTrustAllContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }

    public void sendMessage(String message) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(message);
    }

    public String receiveResponse() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        return in.readLine();
    }

    public void disconnect() throws IOException {
        socket.close();
    }

    public static void main(String[] args) {
        try {
            SSLClient client = new SSLClient("localhost", 8443, true); // testing mode
            client.connect();
            System.out.println("Sent: hello from client");
            client.sendMessage("hello from client");
            System.out.println(client.receiveResponse());
            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
