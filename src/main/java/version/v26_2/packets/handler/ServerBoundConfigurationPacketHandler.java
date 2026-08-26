package version.v26_2.packets.handler;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import core.NetworkMode;
import java.util.HashMap;
import java.util.Map;
import version.v26_2.proxy.ConnectionManager;

public class ServerBoundConfigurationPacketHandler extends PacketHandler {
    private HashMap<String, PacketOperator> operations = new HashMap<>();

    public ServerBoundConfigurationPacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);

        operations.put("FinishConfiguration", provider -> {
            getConnectionManager().setMode(NetworkMode.GAME);
            return true;
        });
    }

    @Override
    public Map<String, PacketOperator> getOperators() {
        return operations;
    }

    @Override
    public boolean isClientBound() {
        return false;
    }
}
