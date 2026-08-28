package version.v26_2.entity;

import version.v26_2.packets.DataTypeProvider;

public interface IMovableEntity {
    void incrementPosition(int dx, int dy, int dz);
    void readPosition(DataTypeProvider provider);
}
