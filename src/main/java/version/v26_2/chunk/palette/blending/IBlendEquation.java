package version.v26_2.chunk.palette.blending;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

public interface IBlendEquation {
    double getRatio(int depth);
}
