package core.interfaces;

import core.coordinates.CoordinateDim2D;
import java.util.List;

/**
 * Per-version factory and delegate, implemented by each supported Minecraft version
 * (e.g. {@code version.v26_1.VersionModuleImpl}).
 *
 * <p>Core code never imports concrete per-version classes. Instead it goes through the
 * {@link core.sniffer.VersionRegistry} to obtain the {@link VersionModule} for the
 * client's protocol version, and uses this interface to create per-version objects or
 * run version-specific logic.
 *
 * <p>The methods here cover only the operations that core's {@link core.config.Config}
 * needs to delegate. Per-version code talks to its own concrete classes directly.
 */
public interface VersionModule {
    /** Lowest protocol version this module is willing to accept. */
    int minSupportedProtocolVersion();

    /** Default protocol version before the handshake reveals the real one. */
    int defaultProtocolVersion();

    /**
     * Called by {@link core.config.Config#setProtocolVersion(int)} after the protocol
     * version has been stored. Looks up the matching protocol entry, stores the data
     * version / game version on Config, and loads level data.
     */
    int onProtocolVersionSet(int protocolVersion);

    /**
     * Load the registries (blocks, entities, items, menus, villagers, block-entities)
     * for the current protocol version into the per-version singletons.
     */
    void loadRegistries();

    /**
     * Called by {@link core.config.Config#settingsComplete()} after the GUI settings have
     * been applied (centerX/centerZ rounded, etc.) but before the proxy is started.
     * Lets the per-version world manager pick up the new settings.
     */
    void onSettingsComplete(boolean markNewChunks, boolean writeChunks, boolean schematicMode, int extendedRenderDistance);

    /**
     * Start the proxy: create the per-version connection manager and begin forwarding.
     * Called once when settings are complete.
     */
    void startProxy();

    /**
     * The per-version {@code Protocol} object for the current protocol version, stored
     * opaquely as {@code Object} on {@link core.config.VersionReporter}.
     */
    Object getProtocol();

    /**
     * Set the world height bounds for the current dimension. Delegated to the
     * per-version chunk implementation which stores them as static state.
     */
    void setWorldHeight(int minY, int height);

    /**
     * The per-version dimension registry, exposed via the {@link IDimensionRegistry}
     * seam interface so that core code can look up dimensions without depending on
     * a concrete per-version class.
     */
    IDimensionRegistry getDimensionRegistry();

    /**
     * The per-version world manager, exposed via the {@link IWorldManager}
     * seam interface so that core GUI code can interact with the world
     * without depending on a concrete per-version class.
     */
    IWorldManager getWorldManager();

    /**
     * Create a per-version MCA file handle for the given region coordinates,
     * exposed via the {@link IMcaFile} seam interface.
     */
    IMcaFile createMcaFile(CoordinateDim2D regionCoords);

    /**
     * Print the event log for the chunk at the given coordinates (debug feature).
     */
    void printChunkEventLog(CoordinateDim2D pos);

    /**
     * The per-version NBT I/O bridge, exposed via the {@link INbtIO} seam interface
     * so that core schematic export code can write NBT without depending on a
     * per-version {@code NbtUtil} or {@code CompressionManager}.
     */
    INbtIO getNbtIO();

    /**
     * Create a per-version {@link core.schematic.export.BlockRegionReader} that reads
     * block/biome data from the live world. Used by the schematic export pipeline.
     */
    core.schematic.export.BlockRegionReader createBlockRegionReader();

    // --- Dimension factory methods ---

    /**
     * The Overworld dimension instance for this version.
     */
    IDimension overworld();

    /**
     * The Nether dimension instance for this version.
     */
    IDimension nether();

    /**
     * The End dimension instance for this version.
     */
    IDimension end();

    /**
     * The list of default (vanilla) dimensions: Overworld, Nether, End.
     */
    List<IDimension> defaultDimensions();

    /**
     * Find a dimension by its string identifier (e.g. "minecraft:overworld").
     * Custom dimensions are looked up in the dimension registry.
     */
    IDimension dimensionFromString(String name);

    /**
     * Find a standard (vanilla) dimension by its string identifier.
     * Non-vanilla identifiers default to Overworld.
     */
    IDimension standardDimensionFromString(String name);
}
