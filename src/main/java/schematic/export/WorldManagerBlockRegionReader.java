package schematic.export;

import game.data.WorldManager;
import game.data.chunk.palette.BlockState;
import game.data.coordinates.Coordinate3D;

/**
 * The production {@link BlockRegionReader}: reads block data out of the in-memory world held by
 * {@link WorldManager}. This is the only class in the export pipeline that talks to
 * {@code WorldManager}.
 *
 * {@code WorldManager.blockStateAt} always reads from the manager's currently active dimension;
 * callers are responsible for verifying that dimension still matches the one the selection was
 * made in before reading (see {@code SchematicExportService}).
 */
public class WorldManagerBlockRegionReader implements BlockRegionReader {
    private final WorldManager worldManager;

    public WorldManagerBlockRegionReader(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    @Override
    public BlockState blockAt(Coordinate3D coordinate) {
        return worldManager.blockStateAt(coordinate);
    }

    @Override
    public String biomeAt(Coordinate3D coordinate) {
        return worldManager.biomeAt(coordinate);
    }
}
