package version.v26_1.entity;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import version.v26_1.packets.DataTypeProvider;

public interface IMovableEntity {
    void incrementPosition(int dx, int dy, int dz);
    void readPosition(DataTypeProvider provider);
}
