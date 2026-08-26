package core.schematic;

import core.coordinates.Coordinate3D;
import java.util.function.Consumer;

/**
 * An axis-aligned block region defined by two corner coordinates, in no particular order. Pure
 * value object: no knowledge of packets, the world, dimensions or NBT.
 */
public final class BoundingBox {
    private final Coordinate3D min;
    private final Coordinate3D max;

    public BoundingBox(Coordinate3D corner1, Coordinate3D corner2) {
        this.min = new Coordinate3D(
            Math.min(corner1.getX(), corner2.getX()),
            Math.min(corner1.getY(), corner2.getY()),
            Math.min(corner1.getZ(), corner2.getZ())
        );
        this.max = new Coordinate3D(
            Math.max(corner1.getX(), corner2.getX()),
            Math.max(corner1.getY(), corner2.getY()),
            Math.max(corner1.getZ(), corner2.getZ())
        );
    }

    public Coordinate3D getMin() {
        return min;
    }

    public Coordinate3D getMax() {
        return max;
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /**
     * Visit every block coordinate in the box. Iterates y slowest and x fastest, matching the
     * block data ordering expected by the Sponge Schematic format.
     */
    public void forEachBlock(Consumer<Coordinate3D> visitor) {
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    visitor.accept(new Coordinate3D(x, y, z));
                }
            }
        }
    }

    @Override
    public String toString() {
        return sizeX() + "x" + sizeY() + "x" + sizeZ() + " (" + volume() + " blocks)";
    }
}
