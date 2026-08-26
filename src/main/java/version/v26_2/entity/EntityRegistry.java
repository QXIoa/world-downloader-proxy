package version.v26_2.entity;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import core.interfaces.IEntityRegistry;
import core.interfaces.IPlayerEntity;
import static core.util.ExceptionHandling.attempt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import version.v26_2.world.WorldManager;
import version.v26_2.chunk.Chunk;
import core.coordinates.CoordinateDim2D;
import version.v26_2.entity.specific.Villager;
import version.v26_2.packets.DataTypeProvider;
import version.v26_2.packets.UUID;
import se.llbit.nbt.SpecificTag;

public class EntityRegistry implements IEntityRegistry {

    private final Map<UUID, PlayerEntity> players;
    private final Map<CoordinateDim2D, Set<Entity>> perChunk;
    private final Map<Integer, Entity> entities;
    private final WorldManager worldManager;

    private final ExecutorService executor;

    public EntityRegistry(WorldManager manager) {
        this.worldManager = manager;
        this.perChunk = new ConcurrentHashMap<>();
        this.entities = new ConcurrentHashMap<>();
        this.players = new ConcurrentHashMap<>();

        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Entity Parser Service"));;
    }
    /**
     * Add a new entity.
     */
    public void addEntity(DataTypeProvider provider, Function<DataTypeProvider, Entity> parser) {
        this.executor.execute(() -> attempt(() -> {
            Entity ent = parser.apply(provider);
            if (ent == null) { return; }
            entities.put(ent.getId(), ent);

            ent.registerOnLocationChange((oldPos, newPos) -> {
                CoordinateDim2D oldChunk = oldPos == null ? null : oldPos.globalToDimChunk();
                CoordinateDim2D newChunk = newPos.globalToDimChunk();

                // if they're the same, just mark the chunk as unsaved
                if (oldPos == newPos) {
                    markUnsaved(newChunk);
                    return;
                }

                Set<Entity> entities = oldChunk == null ? null : perChunk.get(oldChunk);
                if (entities != null) {
                    entities.remove(ent);

                    if (entities.isEmpty()) {
                        perChunk.remove(oldChunk);
                    }
                }

                Set<Entity> set = perChunk.computeIfAbsent(newChunk, (k) -> ConcurrentHashMap.newKeySet());
                set.add(ent);

                markUnsaved(newChunk);

            });
            
            if (ent instanceof Villager villager) {
                villager.registerOnTradeUpdate((pos) -> markUnsaved(pos.globalToDimChunk()));
            }
        }));
    }

    public void addPlayer(DataTypeProvider provider) {
        executor.execute(() -> attempt(() -> {
            PlayerEntity player = PlayerEntity.parse(provider);
            players.put(player.getUUID(), player);
        }));
    }

    public void updatePlayerAction(DataTypeProvider provider) {
        executor.execute(() -> attempt(() -> {
            byte actions = provider.readNext();
            int playerCnt = provider.readVarInt();


            for (int i = 0; i < playerCnt; i++) {
                UUID uuid = provider.readUUID();

                if ((actions & 0x01) > 0) {
                    // Only create a new PlayerEntity if we don't already have one - SpawnPlayer
                    // (AddPlayer packet) sets the initial position; PlayerInfoUpdate just registers
                    // the UUID/name and has no position, so overwriting an existing player here
                    // would discard their position and cause NPEs on later MoveEntityPos packets.
                    players.computeIfAbsent(uuid, PlayerEntity::new);

                    String name = provider.readString();
                    int properties = provider.readVarInt();
                    for (int j = 0; j < properties; j++) {
                        provider.readString();
                        provider.readString();
                        boolean signed = provider.readBoolean();
                        if (signed) provider.readString();
                    }
                }

                if ((actions & 0x02) > 0) {
                    boolean signature = provider.readBoolean();
                    if (signature) {
                        provider.readUUID();
                        provider.readLong();
                        int encKeySz = provider.readVarInt();
                        provider.readByteArray(encKeySz);
                        int pubKeySz = provider.readVarInt();
                        provider.readByteArray(pubKeySz);
                    }
                }

                if ((actions & 0x04) > 0) {
                    provider.readVarInt();
                }

                if ((actions & 0x08) > 0) {
                    provider.readBoolean();
                }

                if ((actions & 0x10) > 0) {
                    provider.readVarInt();
                }

                if ((actions & 0x20) > 0) {
                    boolean displayName = provider.readBoolean();
                    if (displayName) {
                        provider.readChat();
                    }
                }

                // 1.21.2+ adds list order/priority (VarInt)
                if ((actions & 0x40) > 0) {
                    provider.readVarInt();
                }

                // 1.21.4+ adds hat visibility (Boolean)
                if ((actions & 0x80) > 0) {
                    provider.readBoolean();
                }
            }
        }));
    }

    private void markUnsaved(CoordinateDim2D coord) {
        Chunk chunk = worldManager.getChunk(coord);
        if (chunk != null) {
            worldManager.touchChunk(chunk);
        }
    }

    /**
     * Delete all tile entities for a chunk, only done when the chunk is also unloaded. Note that this only related to
     * tile entities sent in the update-tile-entity packets, ones sent with the chunk will only be stored in the chunk.
     * @param location the position of the chunk for which we can delete tile entities.
     */
    public void unloadChunk(CoordinateDim2D location) {
        Set<Entity> entities = perChunk.remove(location);
        if (entities == null) { return; }

        for (Entity e : entities) {
            this.entities.remove(e.getId());
        }
    }


    public void addMetadata(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            Entity ent = entities.get(provider.readVarInt());

            if (ent != null) {
                ent.parseMetadata(provider);
            }
        }));
    }

    public void updatePositionRelative(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            IMovableEntity ent = getMovableEntity(provider.readVarInt());

            if (ent != null) {
                ent.incrementPosition(provider.readShort(), provider.readShort(), provider.readShort());
            }
        }));
    }

    public void updatePositionAbsolute(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            IMovableEntity ent = getMovableEntity(provider.readVarInt());

            if (ent != null) {
                ent.readPosition(provider);
            }
        }));
    }

    public IMovableEntity getMovableEntity(int entId) {
        Entity tmpEnt = entities.get(entId);
        if (tmpEnt == null) return null;
        IMovableEntity ent = players.get(tmpEnt.uuid);
        if (ent != null) {
            return ent;
        }

        return tmpEnt;
    }

    public List<SpecificTag> getEntitiesNbt(CoordinateDim2D location) {
        Set<Entity> entities = perChunk.get(location);

        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream().map(Entity::toNbt).collect(Collectors.toList());
    }

    public void reset() {
        this.entities.clear();
        this.perChunk.clear();
        this.players.clear();
    }

    public void addEquipment(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            int id = provider.readVarInt();
            Entity ent = entities.get(id);

            if (ent != null) {
                ent.addEquipment(provider);
            }
        }));
    }

    /**
     * When destroyEntities is called, we don't remove the entities from the perChunk map. These will only be removed
     * when the chunk is unloaded. This way we won't accidentally delete entities that belong to an unsaved chunk.
     */
    public void destroyEntities(DataTypeProvider provider) {
        int count = provider.readVarInt();
        while (count-- > 0) {
            int id = provider.readVarInt();
            if (entities.containsKey(id)) {
                players.remove(entities.get(id).uuid);
                entities.remove(id);
            }
        }
    }

    public int countActiveEntities() {
        return this.entities.size();
    }
    public int countActivePlayers() {
        return this.players.size();
    }

    public Collection<core.interfaces.IPlayerEntity> getPlayerSet() {
        return (Collection<core.interfaces.IPlayerEntity>) (Collection) players.values();
    }

    public void addVillagerTrades(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            worldManager.getVillagerManager().parseAndStoreVillagerTrade(provider);
        }));
    }
}
