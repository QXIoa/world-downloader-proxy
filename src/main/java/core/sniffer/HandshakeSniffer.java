package core.sniffer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parses a Minecraft client-to-server handshake packet from raw bytes without
 * depending on any per-version {@code DataTypeProvider}.
 *
 * <p>The handshake packet layout has been stable since Minecraft 1.7 and is identical
 * for all supported versions (26.x):
 * <ol>
 *   <li>protocol version (VarInt)</li>
 *   <li>host name (String: VarInt length + UTF-8 bytes)</li>
 *   <li>port (Unsigned Short)</li>
 *   <li>next state (VarInt: 1 = status, 2 = login)</li>
 * </ol>
 *
 * <p>This class lives in core so that the proxy can sniff the protocol version from
 * the very first packet and select the appropriate per-version module via
 * {@link VersionRegistry} before any per-version packet handler is instantiated.
 */
public final class HandshakeSniffer {
    private HandshakeSniffer() { }

    /**
     * Result of sniffing a handshake packet.
     */
    public static final class Handshake {
        private final int protocolVersion;
        private final String host;
        private final int port;
        private final int nextState;

        public Handshake(int protocolVersion, String host, int port, int nextState) {
            this.protocolVersion = protocolVersion;
            this.host = host;
            this.port = port;
            this.nextState = nextState;
        }

        public int getProtocolVersion() { return protocolVersion; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getNextState() { return nextState; }

        public boolean isLogin() { return nextState == 2; }
        public boolean isStatus() { return nextState == 1; }
    }

    /**
     * Parse a handshake packet from the given byte array, skipping the leading
     * packet-length VarInt and packet-id VarInt.
     *
     * @param data   the raw packet data (length prefix + packet id + payload)
     * @param offset offset into {@code data} where the packet starts
     * @param length number of valid bytes starting at {@code offset}
     * @return the parsed {@link Handshake}, or {@code null} if the data is incomplete
     */
    public static Handshake sniff(byte[] data, int offset, int length) {
        int pos = offset;
        int end = offset + length;

        // skip packet length VarInt
        int packetLen = readVarInt(data, pos, end);
        if (packetLen < 0) return null;
        pos += varIntSize(packetLen);

        // skip packet id VarInt
        int packetId = readVarInt(data, pos, end);
        if (packetId < 0) return null;
        pos += varIntSize(packetId);

        // protocol version
        int protocolVersion = readVarInt(data, pos, end);
        if (protocolVersion < 0) return null;
        pos += varIntSize(protocolVersion);

        // host string (VarInt length + UTF-8 bytes)
        int strLen = readVarInt(data, pos, end);
        if (strLen < 0) return null;
        pos += varIntSize(strLen);
        if (pos + strLen > end) return null;
        String host = new String(data, pos, strLen, StandardCharsets.UTF_8);
        pos += strLen;

        // port (unsigned short)
        if (pos + 2 > end) return null;
        int port = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;

        // next state
        int nextState = readVarInt(data, pos, end);
        if (nextState < 0) return null;

        return new Handshake(protocolVersion, host, port, nextState);
    }

    /**
     * Convenience: sniff from a byte array starting at offset 0.
     */
    public static Handshake sniff(byte[] data) {
        return sniff(data, 0, data.length);
    }

    /**
     * Read a VarInt from the buffer. Returns -1 if the data is incomplete.
     */
    private static int readVarInt(byte[] data, int offset, int end) {
        int result = 0;
        int shift = 0;
        int pos = offset;
        while (pos < end) {
            byte b = data[pos++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift >= 35) {
                return -1; // VarInt too large
            }
        }
        return -1; // incomplete
    }

    /**
     * Number of bytes the given VarInt value occupies.
     */
    private static int varIntSize(int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) return 1;
        if ((value & (0xFFFFFFFF << 14)) == 0) return 2;
        if ((value & (0xFFFFFFFF << 21)) == 0) return 3;
        if ((value & (0xFFFFFFFF << 28)) == 0) return 4;
        return 5;
    }
}
