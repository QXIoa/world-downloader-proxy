package core.interfaces;

import core.coordinates.Coordinate2D;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDouble3D;
import java.util.function.BiConsumer;

/**
 * Core seam interface for the per-version world manager.
 *
 * <p>Core GUI code uses this interface to interact with the world manager without
 * depending on a concrete per-version class.
 */
public interface IWorldManager {
    void shutdown();
    void save();
    void deleteAllExisting();
    void drawExistingChunks(core.coordinates.Coordinate2D region);
    void drawExistingRegion(core.coordinates.Coordinate2D region);
    int countActiveRegions();
    int countActiveBinaryChunks();
    int countQueuedChunks();
    int countActiveChunks();
    int countExtendedChunks();
    IDimension getDimension();
    boolean isBelowGround();
    Coordinate3D getPlayerPosition();
    CoordinateDouble3D getPlayerPositionDouble();
    IEntityRegistry getEntityRegistry();
    void setPlayerPosListener(BiConsumer<CoordinateDouble3D, Double> playerPosListener);
    int countActiveEntities();
    int countActivePlayers();
    int countActiveMaps();

    /**
     * Unload all chunks that are farther than {@code radius} chunks from the given
     * center. Used by the schematic-mode radius guard to limit memory usage.
     *
     * @param center the player's current chunk coordinates
     * @param radius the maximum distance in chunks (Chebyshev distance)
     */
    void unloadChunksOutsideRadius(Coordinate2D center, int radius);

    /**
     * Check whether the chunk at the given coordinates is currently loaded in memory.
     * Used by the schematic export diagnostic to distinguish "chunk not loaded" from
     * "chunk loaded but block is air".
     *
     * @param chunkX chunk X coordinate (world X >> 4)
     * @param chunkZ chunk Z coordinate (world Z >> 4)
     * @return true if the chunk is in memory
     */
    boolean isChunkLoaded(int chunkX, int chunkZ);
}
