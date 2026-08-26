package version.v26_2.chunk.palette;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import se.llbit.nbt.SpecificTag;

/**
 * Interface for data type of palettes.
 */
public interface State {
    SpecificTag toNbt();
}
