
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;

public class SSLTCPServer {

    private int port;
    private SSLServerSocket serverSocket;
    private boolean isRunning;

    public SSLTCPServer(int port, String keystorePath, String password) throws Exception {
        this.port = port;

        SSLContext context = createSSLContext(keystorePath, password);
        SSLServerSocketFactory factory = context.getServerSocketFactory();

        serverSocket = (SSLServerSocket) factory.createServerSocket();
        serverSocket.bind(new InetSocketAddress(port));

        System.out.println("Server running on port " + port); // seule ligne d’output
    }

    public void launch() {
        isRunning = true;
        while (isRunning) {
            try {
                SSLSocket client = (SSLSocket) serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            } catch (IOException ignored) {}
        }
    }

    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new FileInputStream(keystorePath), password.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    private void handleClient(SSLSocket client) {
        try {
            client.startHandshake();

            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                out.println(line); // echo minimal
            }

            client.close();
        } catch (Exception ignored) {}
    }

    public void shutdown() {
        isRunning = false;
        try { serverSocket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        try {
            SSLTCPServer server = new SSLTCPServer(8443, "server.jks", "password123");
            server.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}