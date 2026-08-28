package version.v26_2.packets.handler;

import version.v26_2.packets.DataTypeProvider;

import java.util.function.Function;

public interface PacketOperator extends Function<DataTypeProvider, Boolean> {
}
