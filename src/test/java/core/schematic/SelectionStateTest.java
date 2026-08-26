package core.schematic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import core.coordinates.Coordinate3D;
import core.interfaces.TestDimension;
import org.junit.jupiter.api.Test;

class SelectionStateTest {
    @Test
    void startsDisabledAndEmpty() {
        SelectionState state = new SelectionState();

        assertThat(state.isEnabled()).isFalse();
        assertThat(state.hasCompleteSelection()).isFalse();
    }

    @Test
    void enableReturnsWhetherStateActuallyChanged() {
        SelectionState state = new SelectionState();

        assertThat(state.enable()).isTrue();
        assertThat(state.enable()).isFalse();
        assertThat(state.disable()).isTrue();
        assertThat(state.disable()).isFalse();
    }

    @Test
    void toggleFlipsAndReturnsNewState() {
        SelectionState state = new SelectionState();

        assertThat(state.toggle()).isTrue();
        assertThat(state.isEnabled()).isTrue();
        assertThat(state.toggle()).isFalse();
        assertThat(state.isEnabled()).isFalse();
    }

    @Test
    void settingBothCornersCompletesSelection() {
        SelectionState state = new SelectionState();

        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());
        assertThat(state.hasCompleteSelection()).isFalse();

        state.setPos2(new Coordinate3D(5, 5, 5), TestDimension.overworld());
        assertThat(state.hasCompleteSelection()).isTrue();
        assertThat(state.toBoundingBox().volume()).isEqualTo(216);
    }

    @Test
    void togglingSelectionModeOffDoesNotClearCorners() {
        SelectionState state = new SelectionState();
        state.enable();
        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());
        state.setPos2(new Coordinate3D(1, 1, 1), TestDimension.overworld());

        state.disable();

        assertThat(state.hasCompleteSelection()).isTrue();
    }

    @Test
    void settingACornerInADifferentDimensionStartsOver() {
        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());
        state.setPos2(new Coordinate3D(1, 1, 1), TestDimension.overworld());

        state.setPos1(new Coordinate3D(10, 10, 10), TestDimension.nether());

        assertThat(state.hasCompleteSelection()).isFalse();
        assertThat(state.getDimension()).isEqualTo(TestDimension.nether());
        assertThat(state.getPos1()).isEqualTo(new Coordinate3D(10, 10, 10));
    }

    @Test
    void clearRemovesCornersAndDimensionButNotEnabledFlag() {
        SelectionState state = new SelectionState();
        state.enable();
        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());
        state.setPos2(new Coordinate3D(1, 1, 1), TestDimension.overworld());

        state.clear();

        assertThat(state.hasCompleteSelection()).isFalse();
        assertThat(state.getDimension()).isNull();
        assertThat(state.isEnabled()).isTrue();
    }

    @Test
    void toBoundingBoxThrowsWhenSelectionIsIncomplete() {
        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());

        assertThatThrownBy(state::toBoundingBox).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void newPos1ClearsOldPos2() {
        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());
        state.setPos2(new Coordinate3D(5, 5, 5), TestDimension.overworld());
        assertThat(state.hasCompleteSelection()).isTrue();

        // setting a new pos1 starts a fresh selection: pos2 must be discarded
        state.setPos1(new Coordinate3D(10, 10, 10), TestDimension.overworld());

        assertThat(state.hasCompleteSelection()).isFalse();
        assertThat(state.getPos1()).isEqualTo(new Coordinate3D(10, 10, 10));
        assertThat(state.getPos2()).isNull();
    }

    @Test
    void newPos2KeepsOldPos1() {
        SelectionState state = new SelectionState();
        state.setPos1(new Coordinate3D(0, 0, 0), TestDimension.overworld());
        state.setPos2(new Coordinate3D(5, 5, 5), TestDimension.overworld());

        // setting a new pos2 keeps pos1 and updates the selection
        state.setPos2(new Coordinate3D(10, 10, 10), TestDimension.overworld());

        assertThat(state.hasCompleteSelection()).isTrue();
        assertThat(state.getPos1()).isEqualTo(new Coordinate3D(0, 0, 0));
        assertThat(state.getPos2()).isEqualTo(new Coordinate3D(10, 10, 10));
    }
}
