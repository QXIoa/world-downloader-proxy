package core.interfaces;

/**
 * Core seam interface for a block state, used by the schematic export pipeline.
 *
 * <p>The Sponge Schematic V3 format only needs the block's resource-location name
 * (e.g. {@code minecraft:stone}) and its properties map (as NBT). Both are
 * version-independent: the format is an external standard, not a Minecraft
 * protocol detail. This interface lets {@code core.schematic.export} encode
 * schematics without depending on a per-version {@code BlockState} class.
 */
public interface IBlockState {
    /**
     * The block's resource location, e.g. {@code minecraft:stone}.
     */
    String getName();

    /**
     * The block's properties as a CompoundTag (NBT). May be empty for blocks
     * with no properties.
     */
    Object getProperties();
}
