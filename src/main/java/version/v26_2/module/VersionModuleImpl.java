package version.v26_2.module;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import core.interfaces.INbtIO;
import core.interfaces.IWorldManager;
import core.interfaces.IMcaFile;
import core.coordinates.CoordinateDim2D;
import core.interfaces.IDimensionRegistry;
import core.interfaces.IDimension;
import version.v26_2.dimension.Dimension;
import core.config.Version;
import core.interfaces.VersionModule;
import version.v26_2.protocol.Protocol;
import version.v26_2.protocol.ProtocolVersionHandler;
import version.v26_2.proxy.ConnectionManager;
import version.v26_2.registries.RegistryLoader;
import version.v26_2.registries.RegistryManager;
import version.v26_2.world.WorldManager;

/**
 * v26.2 implementation of {@link VersionModule}.
 *
 * <p>This module handles Minecraft 26.2 (protocol 776, data version 4903) exclusively.
 * It is a fully independent copy of the v26.1 module — changes to one version's code
 * do not affect the other (see docs/WET_VERSION_ARCHITECTURE.md, phase 2f).
 */
public class VersionModuleImpl implements VersionModule {
    private Protocol currentProtocol;

    @Override
    public int minSupportedProtocolVersion() {
        return Version.V26_2.protocolVersion;
    }

    @Override
    public int defaultProtocolVersion() {
        return Version.V26_2.protocolVersion;
    }

    @Override
    public int onProtocolVersionSet(int protocolVersion) {
        try {
            Protocol p = ProtocolVersionHandler.getInstance().getProtocolByProtocolVersion(protocolVersion);
            this.currentProtocol = p;
            core.config.Config.getInstance().setDataVersion(p.getDataVersion());
            core.config.Config.getInstance().setGameVersion(p.getVersion());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            WorldManager.getInstance().loadLevelData();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return currentProtocol == null ? 0 : currentProtocol.getDataVersion();
    }

    @Override
    public void onSettingsComplete(boolean markNewChunks, boolean writeChunks, boolean schematicMode, int extendedRenderDistance) {
        WorldManager.getInstance().setWorldManagerVariables(markNewChunks, writeChunks);
        WorldManager.getInstance().setSchematicMode(schematicMode);
        WorldManager.getInstance().updateExtendedRenderDistance(extendedRenderDistance);
    }

    @Override
    public void loadRegistries() {
        try {
            if (currentProtocol == null) { return; }
            RegistryLoader loader = RegistryLoader.forVersion(currentProtocol.getVersion());
            if (loader == null) { return; }

            WorldManager.getInstance().setEntityMap(loader.generateEntityNames());

            RegistryManager.getInstance().setRegistries(loader);

            WorldManager.getInstance().startSaveService();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startProxy() {
        new ConnectionManager().startProxy();
    }

    @Override
    public void setWorldHeight(int minY, int height) {
        version.v26_2.chunk.Chunk.setWorldHeight(minY, height);
    }

    @Override
    public IWorldManager getWorldManager() {
        return WorldManager.getInstance();
    }

    @Override
    public IMcaFile createMcaFile(CoordinateDim2D regionCoords) {
        return new version.v26_2.region.McaFile(regionCoords);
    }

    @Override
    public void printChunkEventLog(CoordinateDim2D pos) {
        version.v26_2.chunk.Chunk.printEventLog(pos);
    }

    @Override
    public INbtIO getNbtIO() {
        return new NbtIOImpl();
    }

    @Override
    public core.schematic.export.BlockRegionReader createBlockRegionReader() {
        return new version.v26_2.schematic.export.WorldManagerBlockRegionReader(WorldManager.getInstance());
    }

    public IDimensionRegistry getDimensionRegistry() {
        return WorldManager.getInstance().getDimensionRegistry();
    }

    @Override
    public Object getProtocol() {
        if (currentProtocol == null && core.config.Config.getInstance() != null) {
            int pv = core.config.Config.getProtocolVersion();
            currentProtocol = ProtocolVersionHandler.getInstance().getProtocolByProtocolVersion(pv);
        }
        return currentProtocol;
    }

    // --- Dimension factory methods ---

    @Override
    public IDimension overworld() {
        return Dimension.OVERWORLD;
    }

    @Override
    public IDimension nether() {
        return Dimension.NETHER;
    }

    @Override
    public IDimension end() {
        return Dimension.END;
    }

    @Override
    public java.util.List<IDimension> defaultDimensions() {
        return new java.util.ArrayList<>(Dimension.DEFAULTS);
    }

    @Override
    public IDimension dimensionFromString(String name) {
        return Dimension.fromString(name);
    }

    @Override
    public IDimension standardDimensionFromString(String name) {
        return Dimension.standardDimensionFromString(name);
    }
}
