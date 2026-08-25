package config;

import game.protocol.Protocol;
import game.protocol.ProtocolVersionHandler;

public class VersionReporter {
    private final Protocol protocol;
    private final PacketFormat packetFormat;
    private final int protocolVersion;

    public VersionReporter(int protocolVersion) {
        this.protocolVersion = protocolVersion;
        this.protocol = ProtocolVersionHandler.getInstance().getProtocolByProtocolVersion(protocolVersion);
        this.packetFormat = new PacketFormat(this);
    }

    /**
     * @return the raw protocol version number from the client handshake (e.g. 498 for 1.14.4,
     *         769 for 1.21.4). This is the actual client version, which may differ from the
     *         matched JSON entry's protocol version when the exact version is not in
     *         {@code protocol-versions.json}.
     */
    public int getProtocolVersion() {
        return protocolVersion;
    }

    public int getDataVersion() {
        return protocol.getDataVersion();
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public PacketFormat packetFormat() {
        return packetFormat;
    }

    public static <T> T select(int dataVersion, Class<T> type, Option... opts) {
        for (Option opt : opts) {
            if (dataVersion >= opt.v.dataVersion) {
                return type.cast(opt.obj.get());
            }
        }
        return null;
    }

    public <T> T select(Class<T> type, Option... opts) {
        for (Option opt : opts) {
            if (isAtLeast(opt.v)) {
                return type.cast(opt.obj.get());
            }
        }
        return null;
    }

    public boolean isAtLeast(Version v) {
        return getDataVersion() >= v.dataVersion;
    }
}
