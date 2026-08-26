package core.sniffer;

import core.config.Version;
import core.interfaces.VersionModule;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Maps Minecraft protocol version numbers to the appropriate {@link VersionModule}.
 *
 * <p>Modules are discovered via Java SPI ({@link ServiceLoader}). Each module declares
 * its exact protocol version via {@link VersionModule#defaultProtocolVersion()} and its
 * minimum acceptable version via {@link VersionModule#minSupportedProtocolVersion()}.
 * The registry routes each protocol number to the module that owns it.
 *
 * <p>For the current setup:
 * <ul>
 *   <li>protocol 775 (26.1) → {@code version.v26_1.module.VersionModuleImpl}</li>
 *   <li>protocol 776 (26.2) → {@code version.v26_2.module.VersionModuleImpl}</li>
 * </ul>
 * When a future module (e.g. v27_1) is added, it registers its own SPI provider and the
 * registry routes its protocol number accordingly.
 */
public final class VersionRegistry {
    private static VersionRegistry instance;

    private final Map<Integer, VersionModule> byProtocol = new HashMap<>();
    private final VersionModule defaultModule;

    private VersionRegistry() {
        ServiceLoader<VersionModule> loader = ServiceLoader.load(VersionModule.class);
        VersionModule highest = null;

        for (VersionModule module : loader) {
            // Register the module for its exact protocol version.
            byProtocol.put(module.defaultProtocolVersion(), module);

            // The default module (used before the handshake reveals the real protocol)
            // is the one with the highest default protocol version (i.e. the latest).
            if (highest == null || module.defaultProtocolVersion() > highest.defaultProtocolVersion()) {
                highest = module;
            }
        }
        this.defaultModule = highest;
    }

    /**
     * Get the module registered for the given protocol version, or the default module
     * if no exact match exists (e.g. protocol 0 before the handshake, or a future
     * protocol not yet explicitly registered).
     *
     * @param protocolVersion the protocol version from the client handshake
     * @return the matching module, or {@code null} if no module is available
     */
    public VersionModule getModule(int protocolVersion) {
        VersionModule m = byProtocol.get(protocolVersion);
        return m != null ? m : defaultModule;
    }

    /**
     * Whether any module accepts the given protocol version (i.e. it is at least the
     * minimum supported version of the module that owns it).
     */
    public boolean isSupported(int protocolVersion) {
        VersionModule m = byProtocol.get(protocolVersion);
        if (m != null) {
            return protocolVersion >= m.minSupportedProtocolVersion();
        }
        // No exact match — check if the default module would accept it
        return defaultModule != null && protocolVersion >= defaultModule.minSupportedProtocolVersion();
    }

    public static synchronized VersionRegistry getInstance() {
        if (instance == null) {
            instance = new VersionRegistry();
        }
        return instance;
    }

    /**
     * Set the singleton — intended for testing.
     */
    public static void setInstance(VersionRegistry registry) {
        instance = registry;
    }
}
