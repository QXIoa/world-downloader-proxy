package core.interfaces;

import java.io.IOException;

/**
 * Core seam interface for the per-version data reader.
 *
 * <p>Core proxy code ({@link core.proxy.ProxyServer}) uses this interface to push
 * raw bytes into the packet parser without depending on a concrete per-version
 * class.
 */
public interface IDataReader {
    /**
     * Push raw byte data into the reader for parsing.
     * @param data   the byte array
     * @param length number of valid bytes
     */
    void pushData(byte[] data, int length) throws IOException;
}
