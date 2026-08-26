package core.schematic.export;

import core.interfaces.IBlockState;
import core.coordinates.Coordinate3D;

/**
 * Reads block state data for a single coordinate. Kept as a small interface (rather than a
 * concrete dependency on {@code WorldManager}) so {@link SpongeSchematicExporter} - and its NBT
 * encoding logic - can be unit tested with fake block data, without needing a live world.
 */
public interface BlockRegionReader {
    /**
     * @return the block at the given coordinate, or {@code null} if that part of the world was
     *         never loaded into memory (callers should treat this as air).
     */
    IBlockState blockAt(Coordinate3D coordinate);

    /**
     * @return the biome resource location (e.g. {@code minecraft:plains}) at the given
     *         coordinate, or {@code null} if biome data is unavailable for this coordinate
     *         (chunk not loaded, or pre-1.18 version without per-section biomes).
     */
    String biomeAt(Coordinate3D coordinate);
}
