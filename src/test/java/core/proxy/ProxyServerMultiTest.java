package core.proxy;

import core.NetworkMode;
import core.interfaces.IConnectionManager;
import core.interfaces.IConnectionSession;
import core.interfaces.IDataReader;
import core.interfaces.IEncryptionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end relay test for the proxy's per-connection handling.
 *
 * <p>A fake "remote server" echoes whatever it reads back to the sender. In multi-user mode the
 * proxy must relay several simultaneous client connections independently, so each client's bytes
 * reach its own outbound connection and its echo comes back through the same tunnel.
 */
class ProxyServerMultiTest {

    private ServerSocket remoteServer;
    private final List<Socket> remoteConnections = new CopyOnWriteArrayList<>();
    private final AtomicInteger outboundConnectionCount = new AtomicInteger();
    private final CountDownLatch remoteAccepted = new CountDownLatch(1);

    private ProxyServer proxy;
    private int remotePort;
    private int localPort;

    @BeforeEach
    void setUp() throws IOException {
        remoteServer = new ServerSocket(0);
        remotePort = remoteServer.getLocalPort();

        Thread acceptor = new Thread(() -> {
            while (!remoteServer.isClosed()) {
                try {
                    Socket s = remoteServer.accept();
                    remoteConnections.add(s);
                    outboundConnectionCount.incrementAndGet();
                    remoteAccepted.countDown();
                    new Thread(() -> echo(s)).start();
                } catch (IOException e) {
                    break;
                }
            }
        }, "Fake Remote Acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        ServerSocket localSocket = new ServerSocket(0);
        localPort = localSocket.getLocalPort();
        localSocket.close();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (proxy != null) {
            proxy.interrupt();
        }
        for (Socket s : remoteConnections) {
            try { s.close(); } catch (IOException ignored) { }
        }
        remoteServer.close();
    }

    /**
     * A single client that connects to the proxy local port, sends a marker, and expects it echoed
     * back through the proxy+remote tunnel.
     */
    private String roundTrip(String marker) throws Exception {
        try (Socket client = new Socket("127.0.0.1", localPort)) {
            client.setSoTimeout(5000);
            client.getOutputStream().write(marker.getBytes());
            client.getOutputStream().flush();
            byte[] buf = new byte[marker.length()];
            int read = 0;
            while (read < marker.length()) {
                int n = client.getInputStream().read(buf, read, marker.length() - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, 0, read);
        }
    }

    private void startProxy(boolean multi) {
        java.util.function.Supplier<IConnectionSession> factory = this::newSession;
        ConnectionDetails details = new ConnectionDetails("127.0.0.1:" + remotePort, localPort, false);
        proxy = new ProxyServer(factory, details, multi);
        // runServer() binds the local ServerSocket synchronously, so the port is already listening
        // (with an OS backlog) well before any client connects.
        proxy.runServer();
    }

    private IConnectionSession newSession() {
        return new TestSession();
    }

    @Test
    void multiUserModeRelaysMultipleSimultaneousClients() throws Exception {
        startProxy(true);

        // Three clients connect at the same time and each send a distinct marker.
        CountDownLatch done = new CountDownLatch(3);
        List<String> results = new CopyOnWriteArrayList<>();
        for (String marker : List.of("AAA", "BBB", "CCC")) {
            new Thread(() -> {
                try {
                    results.add(roundTrip(marker));
                } catch (Exception e) {
                    results.add("ERROR:" + e);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertThat(done.await(10, TimeUnit.SECONDS)).as("all three relays completed").isTrue();
        assertThat(results).containsExactlyInAnyOrder("AAA", "BBB", "CCC");
        // Exactly three independent outbound connections were made to the remote server.
        assertThat(outboundConnectionCount.get()).isEqualTo(3);
    }

    @Test
    void singleUserModeRelaysOneConnection() throws Exception {
        startProxy(false);

        assertThat(roundTrip("SINGLE")).isEqualTo("SINGLE");
        assertThat(outboundConnectionCount.get()).isEqualTo(1);
    }

    private void echo(Socket s) {
        try {
            s.setSoTimeout(2000);
            byte[] buf = new byte[256];
            int n;
            while ((n = s.getInputStream().read(buf)) != -1) {
                s.getOutputStream().write(buf, 0, n);
                s.getOutputStream().flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /** Minimal in-test implementation of the core session interfaces. */
    private static final class TestSession implements IConnectionSession {
        final TestEncryptionManager em = new TestEncryptionManager();

        final IDataReader serverBound = (data, length) -> em.toServer.write(data, 0, length);
        final IDataReader clientBound = (data, length) -> em.toClient.write(data, 0, length);

        final IConnectionManager cm = new IConnectionManager() {
            @Override
            public IEncryptionManager getEncryptionManager() {
                return em;
            }

            @Override
            public void setMode(NetworkMode mode) {
            }

            @Override
            public void reset() {
            }
        };

        @Override
        public IConnectionManager getConnectionManager() {
            return cm;
        }

        @Override
        public IDataReader getServerBoundReader() {
            return serverBound;
        }

        @Override
        public IDataReader getClientBoundReader() {
            return clientBound;
        }
    }

    private static final class TestEncryptionManager implements IEncryptionManager {
        OutputStream toClient;
        OutputStream toServer;

        @Override
        public void setStreamToClient(OutputStream stream) {
            this.toClient = stream;
        }

        @Override
        public void setStreamToServer(OutputStream stream) {
            this.toServer = stream;
        }

        @Override
        public void setClientProfileKeyPair(String privateKey, String publicKey) {
        }
    }
}
