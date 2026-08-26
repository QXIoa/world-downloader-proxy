package core.dimension;

/**
 * Shared constants for chunk/region geometry.
 *
 * <p>These values are identical across all supported Minecraft versions and are
 * used by core GUI code (e.g. {@link core.gui.images.RegionImage}) without
 * depending on per-version {@code Chunk} or {@code Region} classes.
 */
public final class WorldGeometry {
    private WorldGeometry() { }

    /** Width/height of a chunk section in blocks. */
    public static final int SECTION_WIDTH = 16;

    /** Number of chunks per side of a region file. */
    public static final int REGION_SIZE = 32;

    /** Total blocks per side of a region (SECTION_WIDTH * REGION_SIZE). */
    public static final int REGION_TOTAL_SIZE = SECTION_WIDTH * REGION_SIZE;
}
