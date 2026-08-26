package core.coordinates;

import core.interfaces.IDimension;

public class CoordinateDim3D extends Coordinate3D {
    private final IDimension dimension;

    public CoordinateDim3D(Coordinate3D pos, IDimension dimension) {
        super(pos.getX(), pos.getY(), pos.getZ());
        this.dimension = dimension;
    }

    public CoordinateDim2D globalToDimChunk() {
        return new CoordinateDim2D(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT, this.dimension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        CoordinateDim3D that = (CoordinateDim3D) o;

        return dimension == that.dimension;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (dimension != null ? dimension.hashCode() : 0);
        return result;
    }
}
