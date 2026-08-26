package core.config;

/**
 * Reports the active Minecraft version and answers version-comparison questions
 * ({@link #isAtLeast(Version)}, {@link #select(Class, Option...)}).
 *
 * <p>Lives in core so that GUI/schematic code can depend on it without pulling in
 * per-version protocol classes. The concrete {@code Protocol} object (which is
 * per-version) is stored opaquely as {@code Object} and exposed via
 * {@link #getProtocol()}; per-version code casts it back to its concrete type.
 */
public class VersionReporter {
    private final int protocolVersion;
    private final int dataVersion;
    private Object protocol;

    public VersionReporter(int protocolVersion, int dataVersion) {
        this.protocolVersion = protocolVersion;
        this.dataVersion = dataVersion;
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
        return dataVersion;
    }

    /**
     * The per-version {@code Protocol} object, stored opaquely so that core does not depend
     * on a concrete protocol class. Per-version callers cast this to their concrete
     * {@code Protocol} type.
     */
    public Object getProtocol() {
        return protocol;
    }

    public void setProtocol(Object protocol) {
        this.protocol = protocol;
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
