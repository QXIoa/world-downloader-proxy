package core.interfaces;

/**
 * Core seam interface for the per-version dimension registry.
 *
 * <p>Core code uses this interface to look up dimensions and dimension types by name
 * without depending on a concrete per-version {@code DimensionRegistry} class.
 */
public interface IDimensionRegistry {
    /**
     * Look up a dimension by its string identifier (e.g. "minecraft:overworld").
     * @return the dimension, or {@code null} if not found
     */
    IDimension getDimension(String name);

    /**
     * Look up a dimension type by its string identifier.
     * @return the dimension type name, or {@code null} if not found
     */
    String getDimensionTypeName(String dimensionType);

    /**
     * Get the world height bounds for a dimension type.
     * @return an int array of {minY, height}, or {@code null} if not found
     */
    int[] getDimensionTypeBounds(String dimensionType);
}
