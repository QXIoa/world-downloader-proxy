package version.v26_1.chunk.palette;

import se.llbit.nbt.SpecificTag;

/**
 * Interface for data type of palettes.
 */
public interface State {
    SpecificTag toNbt();
}
