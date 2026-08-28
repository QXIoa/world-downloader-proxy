package core.schematic;

import core.coordinates.Coordinate3D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundingBoxTest {
    @Test
    void normalizesCornersRegardlessOfOrder() {
        BoundingBox box = new BoundingBox(new Coordinate3D(10, 70, -5), new Coordinate3D(2, 64, 3));

        assertThat(box.getMin()).isEqualTo(new Coordinate3D(2, 64, -5));
        assertThat(box.getMax()).isEqualTo(new Coordinate3D(10, 70, 3));
    }

    @Test
    void computesSizeInclusiveOfBothCorners() {
        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(1, 1, 1));

        assertThat(box.sizeX()).isEqualTo(2);
        assertThat(box.sizeY()).isEqualTo(2);
        assertThat(box.sizeZ()).isEqualTo(2);
        assertThat(box.volume()).isEqualTo(8);
    }

    @Test
    void singleBlockSelectionHasVolumeOne() {
        BoundingBox box = new BoundingBox(new Coordinate3D(5, 5, 5), new Coordinate3D(5, 5, 5));

        assertThat(box.volume()).isEqualTo(1);
    }

    @Test
    void forEachBlockVisitsEveryCoordinateInXThenZThenYOrder() {
        BoundingBox box = new BoundingBox(new Coordinate3D(0, 0, 0), new Coordinate3D(1, 1, 1));

        List<Coordinate3D> visited = new ArrayList<>();
        box.forEachBlock(visited::add);

        assertThat(visited).containsExactly(
            new Coordinate3D(0, 0, 0),
            new Coordinate3D(1, 0, 0),
            new Coordinate3D(0, 0, 1),
            new Coordinate3D(1, 0, 1),
            new Coordinate3D(0, 1, 0),
            new Coordinate3D(1, 1, 0),
            new Coordinate3D(0, 1, 1),
            new Coordinate3D(1, 1, 1)
        );
    }
}
