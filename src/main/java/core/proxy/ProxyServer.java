package core.proxy;

import core.NetworkMode;
import core.interfaces.IConnectionManager;
import core.interfaces.IConnectionSession;
import core.interfaces.IDataReader;
import core.messages.Messages;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static core.util.ExceptionHandling.attempt;

/**
 * Proxy server class, handles receiving of data and forwarding it to the right places.
 *
 * <p>Each accepted Minecraft client connection is serviced through a {@link IConnectionSession}
 * created by the supplied {@code sessionFactory}. In single-user mode (the default) connections
 * are handled serially, one at a time. When {@code multi} is {@code true}, an acceptor thread
 * spawns a dedicated handler thread per connection, each with its own session so encryption,
 * compression and packet readers stay isolated between concurrent clients.
 */
public class ProxyServer extends Thread {
    private final ConnectionDetails connectionDetails;
    private final Supplier<IConnectionSession> sessionFactory;
    private final boolean multi;
    private ServerSocket serverSocket;

    public ProxyServer(Supplier<IConnectionSession> sessionFactory, ConnectionDetails connectionDetails, boolean multi) {
        this.connectionDetails = connectionDetails;
        this.sessionFactory = sessionFactory;
        this.multi = multi;
    }

    /**
     * Bind the local {@link ServerSocket} synchronously, then start the accept loop in the
     * background. Binding synchronously means the local port is already listening by the time this
     * method returns, so connecting clients queue in the OS backlog instead of getting refused.
     */
    public void runServer() {
        setName("Proxy");

        String friendlyHost = connectionDetails.getFriendlyHost();
        System.out.println(Messages.console("console.proxy.starting", friendlyHost, connectionDetails.getPortLocal()));

        attempt(() -> serverSocket = connectionDetails.getServerSocket(), (ex) -> {
            ex.printStackTrace();
            System.exit(1);
        });

        this.start();
        this.setPriority(10);
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        // Server accept loop — runs until the process exits or an unrecoverable error occurs.
        while (true) {
            AtomicReference<Socket> client = new AtomicReference<>();

            attempt(() -> client.set(serverSocket.accept()), (ex) -> {
                ex.printStackTrace();
            });

            if (client.get() == null) {
                continue;
            }

            if (multi) {
                // spawn a dedicated thread per connection so multiple clients can be proxied at once
                Thread handler = new Thread(() -> handleConnection(client.get()), "Proxy Connection");
                handler.start();
            } else {
                // single-user mode: handle one connection fully before accepting the next
                handleConnection(client.get());
            }
        }
    }

    /**
     * Proxy a single accepted client connection to the remote server and back using its own session.
     *
     * @param client the accepted connection from the Minecraft client
     */
    private void handleConnection(Socket client) {
        final byte[] request = new byte[4096];
        final byte[] reply = new byte[4096];

        IConnectionSession session = sessionFactory.get();
        IConnectionManager connectionManager = session.getConnectionManager();

        AtomicReference<Socket> server = new AtomicReference<>();

        attempt(() -> {
            final InputStream streamFromClient = client.getInputStream();
            final OutputStream streamToClient = client.getOutputStream();
            connectionManager.getEncryptionManager().setStreamToClient(streamToClient);

            // If the server cannot connect, close client connection
            attempt(() -> server.set(connectionDetails.getClientSocket()), (ex) -> {
                System.err.println(Messages.console("console.proxy.cannot_connect",
                        connectionDetails.getFriendlyHost(), ex.getClass().getCanonicalName()));
                attempt(client::close);
            });
            if (server.get() == null) {
                return;
            }

            final InputStream streamFromServer = server.get().getInputStream();
            final OutputStream streamToServer = server.get().getOutputStream();
            connectionManager.getEncryptionManager().setStreamToServer(streamToServer);

            // start client listener thread
            Thread clientListener = new Thread(() -> {
                IDataReader serverBound = session.getServerBoundReader();
                connectionManager.setMode(NetworkMode.HANDSHAKE);
                attempt(() -> {
                    int bytesRead;
                    while ((bytesRead = streamFromClient.read(request)) != -1) {
                        serverBound.pushData(request, bytesRead);
                    }
                }, (ex) -> {
                    Throwable cause = ex.getCause();
                    if (cause != null) {
                        cause.printStackTrace();
                    }
                    System.out.println(Messages.console("console.proxy.server_disconnected"));
                    connectionManager.reset();
                });
                // the client closed the connection to us, so close our connection to the server.
                attempt(streamToServer::close);
            }, "Proxy Client Listener");
            clientListener.start();
            clientListener.setPriority(10);

            // listen to messages from server
            IDataReader clientBound = session.getClientBoundReader();
            attempt(() -> {
                int bytesRead;
                while ((bytesRead = streamFromServer.read(reply)) != -1) {
                    clientBound.pushData(reply, bytesRead);
                }
            }, (ex) -> {
                Throwable cause = ex.getCause();
                if (cause != null) {
                    cause.printStackTrace();
                }
                System.out.println(Messages.console("console.proxy.client_disconnected"));
                connectionManager.reset();
            });

            // The server closed its connection to us, so we close our connection to our client.
            streamToClient.close();
        }, (ex) -> {
            if (server.get() != null) { attempt(server.get()::close); }
            attempt(client::close);
        });
    }
}
