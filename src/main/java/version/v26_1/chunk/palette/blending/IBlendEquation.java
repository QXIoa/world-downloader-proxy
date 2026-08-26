package version.v26_1.chunk.palette.blending;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

public interface IBlendEquation {
    double getRatio(int depth);
}
