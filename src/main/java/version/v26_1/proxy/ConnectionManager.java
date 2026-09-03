package version.v26_1.proxy;

import core.NetworkMode;
import core.config.Config;
import core.interfaces.IConnectionManager;
import core.interfaces.IConnectionSession;
import core.interfaces.IDataReader;
import core.proxy.ProxyServer;
import version.v26_1.module.VersionAccessors;
import version.v26_1.packets.DataReader;
import version.v26_1.packets.handler.*;
import version.v26_1.protocol.ConfigurationProtocol;
import version.v26_1.protocol.HandshakeProtocol;
import version.v26_1.protocol.LoginProtocol;
import version.v26_1.protocol.StatusProtocol;
import version.v26_1.world.WorldManager;

import java.util.function.Supplier;

/**
 * Class to manage the connection status.
 */
public class ConnectionManager implements IConnectionManager, IConnectionSession {
    private DataReader serverBoundDataReader;
    private DataReader clientBoundDataReader;
    private EncryptionManager encryptionManager;
    private CompressionManager compressionManager;

    public ConnectionManager() {
        this.compressionManager = new CompressionManager();
        this.encryptionManager = new EncryptionManager(compressionManager);
        this.serverBoundDataReader = DataReader.serverBound(encryptionManager);
        this.clientBoundDataReader = DataReader.clientBound(encryptionManager);
    }

    private NetworkMode mode = NetworkMode.STATUS;

    // Tracks the currently active game handler so its background resources (the selection
    // particle renderer's scheduler) can be shut down before it's discarded. Without this, an
    // abrupt disconnect (e.g. a client crash) never sends "ConfigurationAcknowledged", so the
    // old handler's scheduler thread keeps running and injects stale LevelParticles packets into
    // whatever connection is active next — including before that connection reaches the Play
    // state, where the client rejects them as an unknown packet.
    private ServerBoundGamePacketHandler serverBoundGamePacketHandler;

    public NetworkMode getMode() {
        return mode;
    }

    @Override
    public IConnectionManager getConnectionManager() {
        return this;
    }

    public void setMode(NetworkMode mode) {
        this.mode = mode;

        if (serverBoundGamePacketHandler != null) {
            serverBoundGamePacketHandler.shutdown();
            serverBoundGamePacketHandler = null;
        }

        switch (mode) {
            case STATUS:
                PacketHandler.setProtocol(new StatusProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundStatusPacketHandler(this));
                clientBoundDataReader.setPacketHandler(new ClientBoundStatusPacketHandler(this));
                break;
            case LOGIN:
                PacketHandler.setProtocol(new LoginProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundLoginPacketHandler(this));
                clientBoundDataReader.setPacketHandler(new ClientBoundLoginPacketHandler(this));
                break;
            case GAME:
                PacketHandler.setProtocol(VersionAccessors.protocol());
                serverBoundGamePacketHandler = new ServerBoundGamePacketHandler(this);
                serverBoundDataReader.setPacketHandler(serverBoundGamePacketHandler);
                clientBoundDataReader.setPacketHandler(ClientBoundGamePacketHandler.of(this));
                break;
            case HANDSHAKE:
                PacketHandler.setProtocol(new HandshakeProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundHandshakePacketHandler(this));
                clientBoundDataReader.setPacketHandler(new ClientBoundHandshakePacketHandler(this));
                break;
            case CONFIGURATION:
                PacketHandler.setProtocol(new ConfigurationProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundConfigurationPacketHandler(this));
                clientBoundDataReader.setPacketHandler(ClientBoundConfigurationPacketHandler.of(this));
                break;
        }
    }

    /**
     * Starts the proxy.
     */
    public void startProxy() {
        boolean multi = Config.isMultiUser();

        // In multi-user mode a fresh ConnectionManager is created for every accepted client so the
        // per-connection state (encryption, compression, packet readers) stays isolated between
        // concurrent connections. In single-user mode the (single) outer instance is reused.
        Supplier<IConnectionSession> sessionFactory = multi ? this::newConnectionSession : () -> registerSession(this);

        ProxyServer proxy = new ProxyServer(sessionFactory, Config.getConnectionDetails(), multi);
        proxy.runServer();
    }

    /**
     * Create and register a fresh connection session (used in multi-user mode).
     */
    private IConnectionSession newConnectionSession() {
        return registerSession(new ConnectionManager());
    }

    /**
     * Register the session's packet injector and encryption manager as the global ones used to push
     * extended-render-distance chunks back to the client, then return it for proxying.
     */
    private IConnectionSession registerSession(ConnectionManager cm) {
        Config.registerPacketInjector(cm.getEncryptionManager().getPacketInjector());
        Config.registerEncryptionManager(cm.getEncryptionManager());
        return cm;
    }

    /**
     * Reset the connection when its lost.
     */
    public void reset() {
        encryptionManager.reset();
        compressionManager.reset();
        serverBoundDataReader.reset();
        clientBoundDataReader.reset();
        setMode(NetworkMode.HANDSHAKE);
        WorldManager.getInstance().resetConnection();
    }

    public EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public CompressionManager getCompressionManager() {
        return compressionManager;
    }

    @Override
    public IDataReader getServerBoundReader() {
        return serverBoundDataReader;
    }

    @Override
    public IDataReader getClientBoundReader() {
        return clientBoundDataReader;
    }
}
