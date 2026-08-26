package version.v26_1.packets.handler;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import version.v26_1.packets.DataTypeProvider;

import java.util.function.Function;

public interface PacketOperator extends Function<DataTypeProvider, Boolean> {
}
