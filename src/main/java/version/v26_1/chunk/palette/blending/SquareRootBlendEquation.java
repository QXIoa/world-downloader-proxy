package version.v26_1.chunk.palette.blending;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

public class SquareRootBlendEquation implements IBlendEquation {
    double alpha;
    double beta;

    public SquareRootBlendEquation(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public double getRatio(int depth) {
        return alpha - beta / Math.sqrt(depth);
    }
}
