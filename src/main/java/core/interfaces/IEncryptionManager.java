package core.interfaces;

import java.io.OutputStream;

/**
 * Core seam interface for the per-version encryption manager.
 *
 * <p>Core proxy code ({@link core.proxy.ProxyServer}) uses this interface to set
 * stream targets on the encryption manager without depending on a concrete
 * per-version class.
 */
public interface IEncryptionManager {
    void setStreamToClient(OutputStream stream);
    void setStreamToServer(OutputStream stream);
    void setClientProfileKeyPair(String privateKey, String publicKey);
}
