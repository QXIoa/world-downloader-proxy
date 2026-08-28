package version.v26_1.packets.handler;

import core.NetworkMode;
import version.v26_1.proxy.ConnectionManager;

import java.util.HashMap;
import java.util.Map;

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
