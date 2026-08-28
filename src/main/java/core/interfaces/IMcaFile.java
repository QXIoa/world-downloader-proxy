package core.interfaces;

import core.coordinates.Coordinate2D;

/**
 * Core seam interface for the per-version MCA file (region file on disk).
 *
 * <p>Core GUI code uses this interface to read chunk binary data from region
 * files without depending on a concrete per-version class.
 */
public interface IMcaFile {
    /**
     * Read the chunk binary at the given chunk coordinates from this region file.
     */
    IChunkBinary getChunkBinary(Coordinate2D coord);

    /**
     * The region-level coordinates of this MCA file.
     */
    Coordinate2D getRegionLocation();
}
