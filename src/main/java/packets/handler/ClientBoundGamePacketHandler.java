package packets.handler;

import java.util.HashMap;
import java.util.Map;

import config.Config;
import config.Option;
import config.Version;
import game.data.WorldManager;
import game.data.coordinates.Coordinate3D;
import game.data.coordinates.CoordinateDim2D;
import game.data.dimension.Dimension;
import game.data.entity.EntityRegistry;
import game.data.entity.MobEntity;
import game.data.entity.ObjectEntity;
import packets.handler.version.*;
import packets.handler.plugins.PluginChannelHandler;
import proxy.ConnectionManager;
import schematic.CommandTreeInjector;
import schematic.CreativeMode;
import schematic.CreativeModeRegistry;
import se.llbit.nbt.SpecificTag;

public class ClientBoundGamePacketHandler extends PacketHandler {
    private final HashMap<String, PacketOperator> operations = new HashMap<>();
    public ClientBoundGamePacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);

        WorldManager worldManager = WorldManager.getInstance();
        EntityRegistry entityRegistry = WorldManager.getInstance().getEntityRegistry();

        operations.put("SetEntityData", provider -> {
            entityRegistry.addMetadata(provider);
            return true;
        });

        operations.put("SetEquipment", provider -> {
            entityRegistry.addEquipment(provider);
            return true;
        });

        operations.put("AddMob", provider -> {
            entityRegistry.addEntity(provider, MobEntity::parse);
            return true;
        });

        operations.put("AddEntity", provider -> {
            entityRegistry.addEntity(provider, ObjectEntity::parse);
            return true;
        });

        operations.put("AddPlayer", provider -> {
            entityRegistry.addPlayer(provider);
            return true;
        });

        operations.put("RemoveEntities", provider -> {
            entityRegistry.destroyEntities(provider);
            return true;
        });

        operations.put("MoveEntityPos", provider -> {
            entityRegistry.updatePositionRelative(provider);
            return true;
        });
        operations.put("MoveEntityPosRot", provider -> {
            entityRegistry.updatePositionRelative(provider);
            return true;
        });
        operations.put("TeleportEntity", provider -> {
            entityRegistry.updatePositionAbsolute(provider);
            return true;
        });

        operations.put("MapItemData", provider -> {
            worldManager.getMapRegistry().readMap(provider);
            return true;
        });

        operations.put("Login", provider -> {
            provider.readInt();
            provider.readNext();
            int dimensionEnum = provider.readInt();

            worldManager.setDimension(Dimension.fromId(dimensionEnum));

            return true;
        });

        operations.put("Respawn", provider -> {
            int dimensionEnum = provider.readInt();
            worldManager.setDimension(Dimension.fromId(dimensionEnum));
            worldManager.getEntityRegistry().reset();
            return true;
        });

        operations.put("LevelChunk", provider -> {
            try {
                worldManager.getChunkFactory().addChunk(provider);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return true;
        });

        operations.put("LightUpdate", provider -> {
            worldManager.updateLight(provider);

            return true;
        });

        operations.put("BlockUpdate", provider -> {
            WorldManager.getInstance().blockChange(provider);
            return true;
        });
        operations.put("SectionBlocksUpdate", provider -> {
            int x = provider.readInt();
            int z = provider.readInt();
            worldManager.multiBlockChange(new Coordinate3D(x, 0, z), provider);

            return true;
        });

        operations.put("ForgetLevelChunk", provider -> {
            CoordinateDim2D co = new CoordinateDim2D(provider.readInt(), provider.readInt(), WorldManager.getInstance().getDimension());
            worldManager.unloadChunk(co);
            return worldManager.canForget(co);
        });

        operations.put("BlockEntityData", provider -> {
            Coordinate3D position = provider.readCoordinates();
            byte action = provider.readNext();
            SpecificTag entityData = provider.readNbtTag();

            worldManager.getChunkFactory().updateTileEntity(position, entityData);
            return true;
        });

        operations.put("OpenScreen", provider -> {
            int windowId = provider.readNext();
            String windowType = provider.readString();
            String windowTitle = provider.readChat();

            int numSlots = provider.readNext() & 0xFF;

            worldManager.getContainerManager().openWindow_1_12(windowId, numSlots, windowTitle);

            return true;
        });
        operations.put("ContainerClose", provider -> {
            final byte windowId = provider.readNext();
            worldManager.getContainerManager().closeWindow(windowId);
            worldManager.getVillagerManager().closeWindow(windowId);
            return true;
        });

        operations.put("ContainerSetContent", provider -> {
            int windowId = provider.readNext();

            int count = provider.readShort();
            worldManager.getContainerManager().items(windowId, count, provider);

            return true;
        });

        operations.put("MerchantOffers", provider -> {
            worldManager.getEntityRegistry().addVillagerTrades(provider);
            return true;
        });

        operations.put("CustomPayload", provider -> {
            PluginChannelHandler.getInstance().handleCustomPayload(provider);
            return true;
        });

        operations.put("SetChunkCacheRadius", provider -> {
            int dist = provider.readVarInt();

            return dist > Config.getExtendedRenderDistance();
        });

        operations.put("DeclareCommands", provider -> {
            return new CommandTreeInjector().process(provider);
        });

        // Track the player's game mode from the server's GameEvent packets so CreativeMode
        // can restore it when fly mode is disabled. While fly mode is active, block the
        // server's gamemode-change events so the client stays in spectator and can noclip.
        operations.put("GameEvent", provider -> {
            int event = provider.readNext();
            float value = provider.readFloat();
            CreativeMode creativeMode = CreativeModeRegistry.get();
            if (creativeMode != null) {
                creativeMode.onServerGameEvent(event, value);
                // Block server from changing our gamemode while we're in fly mode
                if (event == 3 && creativeMode.shouldInterceptMovement()) {
                    return false; // don't forward to client
                }
            }
            return true;
        });

        // While in fly mode, swallow the server's position synchronization packets
        // so the client doesn't get teleported back. Send AcceptTeleportation back so the
        // server doesn't kick the player for not acknowledging the teleport.
        operations.put("PlayerPosition", provider -> {
            CreativeMode creativeMode = CreativeModeRegistry.get();
            if (creativeMode == null || !creativeMode.shouldInterceptMovement()) {
                return true; // forward to client normally
            }
            // Read the teleport ID so we can acknowledge it, then drop the packet.
            // Format (1.21.6+): teleportId (VarInt), x/y/z (Double), velX/velY/velZ (Double),
            //                   yaw (Float), pitch (Float), flags (Int)
            int teleportId = provider.readVarInt();
            // send AcceptTeleportation back to the server
            creativeMode.sendAcceptTeleportation(teleportId);
            return false; // don't forward to client
        });
    }

    public static PacketHandler of(ConnectionManager connectionManager) {
        return Config.versionReporter().select(PacketHandler.class,
                Option.of(Version.V1_20_6, () -> new ClientBoundGamePacketHandler_1_20_6(connectionManager)),
                Option.of(Version.V1_20_2, () -> new ClientBoundGamePacketHandler_1_20_2(connectionManager)),
                Option.of(Version.V1_19, () -> new ClientBoundGamePacketHandler_1_19(connectionManager)),
                Option.of(Version.V1_18, () -> new ClientBoundGamePacketHandler_1_18(connectionManager)),
                Option.of(Version.V1_17, () -> new ClientBoundGamePacketHandler_1_17(connectionManager)),
                Option.of(Version.V1_16, () -> new ClientBoundGamePacketHandler_1_16(connectionManager)),
                Option.of(Version.V1_15, () -> new ClientBoundGamePacketHandler_1_15(connectionManager)),
                Option.of(Version.V1_14, () -> new ClientBoundGamePacketHandler_1_14(connectionManager)),
                Option.of(Version.ANY, () -> new ClientBoundGamePacketHandler(connectionManager))
        );
    }

    @Override
    public Map<String, PacketOperator> getOperators() {
        return operations;
    }

    @Override
    public boolean isClientBound() {
        return true;
    }
}
