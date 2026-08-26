package core.interfaces;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Core seam interface for a Minecraft dimension.
 *
 * <p>Core code (coordinates, schematic, GUI) uses this interface to refer to
 * dimensions without depending on a concrete per-version {@code Dimension} class.
 * Each version module provides its own {@code Dimension} implementation and
 * exposes instances through {@link VersionModule}.
 */
public interface IDimension {
    /**
     * The dimension type identifier, e.g. {@code minecraft:overworld}.
     * Defaults to {@code minecraft:overworld} if unset.
     */
    String getType();

    /**
     * Path where the dimension's world data should be saved, relative to the
     * world output directory (e.g. {@code dimensions/minecraft/overworld}).
     */
    String getPath();

    /**
     * Write the dimension definition file into the given prefix directory.
     */
    void write(Path prefix) throws IOException;

    /**
     * The full identifier, e.g. {@code minecraft:overworld}.
     */
    @Override
    String toString();

    /**
     * The full identifier (same as {@link #toString()}).
     */
    String getName();

    /**
     * Set the dimension type from a registry type identifier.
     */
    void setType(String dimensionType);

    /**
     * Whether this dimension is the Nether.
     */
    boolean isNether();
}
