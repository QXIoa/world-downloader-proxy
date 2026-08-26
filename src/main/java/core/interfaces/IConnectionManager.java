package core.interfaces;

import core.NetworkMode;

/**
 * Core seam interface for the per-version connection manager.
 *
 * <p>Core proxy code ({@link core.proxy.ProxyServer}) uses this interface to
 * interact with the connection manager without depending on a concrete
 * per-version class.
 */
public interface IConnectionManager {
    /**
     * Get the encryption manager, exposed via the {@link IEncryptionManager} seam.
     */
    IEncryptionManager getEncryptionManager();

    /**
     * Set the current network mode (handshake, status, login, configuration, game).
     */
    void setMode(NetworkMode mode);

    /**
     * Reset the connection state.
     */
    void reset();
}
