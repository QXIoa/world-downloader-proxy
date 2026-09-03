package core.interfaces;

/**
 * A single client->proxy->server connection session.
 *
 * <p>Each accepted client connection is handled by one {@link IConnectionSession}, which
 * bundles the per-connection {@link IConnectionManager} (encryption, compression, network
 * mode) together with the server-bound and client-bound {@link IDataReader}s. In multi-user
 * mode every connection gets its own session so the per-connection state is isolated, even
 * though the downloaded world itself is shared.
 */
public interface IConnectionSession {
    IConnectionManager getConnectionManager();

    IDataReader getServerBoundReader();

    IDataReader getClientBoundReader();
}