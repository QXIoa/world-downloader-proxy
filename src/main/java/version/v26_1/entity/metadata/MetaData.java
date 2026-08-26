package version.v26_1.entity.metadata;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import version.v26_1.packets.DataTypeProvider;
import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntTag;
import se.llbit.nbt.StringTag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses entity metadata ("Set Entity Data" packet). If a future Minecraft version changes the metadata
 * type IDs or the tracked fields again, add a new subclass overriding {@link #getTypeHandler},
 * {@link #getIndexHandler} and/or {@link #addNbtTags}, and pick it in {@link #getVersionedMetaData()} (see
 * the pre-26.x history of this class in git for an example of that pattern).
 */
public class MetaData {
    private final static int TERMINATOR = 0xFF;

    // network.syncher.EntityDataSerializers
    private static final Map<Integer, Consumer<DataTypeProvider>> typeHandlers = new HashMap<>();
    static {
        typeHandlers.put(0, DataTypeProvider::readNext);
        typeHandlers.put(1, DataTypeProvider::readVarInt);
        typeHandlers.put(2, DataTypeProvider::readVarLong);
        typeHandlers.put(3, DataTypeProvider::readFloat);
        typeHandlers.put(4, DataTypeProvider::readString);
        typeHandlers.put(5, DataTypeProvider::readChat);
        typeHandlers.put(6, DataTypeProvider::readOptChat);
        typeHandlers.put(7, DataTypeProvider::readSlot);
        typeHandlers.put(8, DataTypeProvider::readBoolean);
        typeHandlers.put(9, DataTypeProvider::readBoolean);
        typeHandlers.put(10, DataTypeProvider::readLong);
        typeHandlers.put(11, provider -> {
            if (provider.readBoolean()) {
                provider.readLong();
            }
        });
        typeHandlers.put(12, DataTypeProvider::readVarInt);
        typeHandlers.put(14, DataTypeProvider::readVarInt);
        typeHandlers.put(15, DataTypeProvider::readNbtTag);
        typeHandlers.put(17, (provider -> {
            provider.readVarInt();
            provider.readVarInt();
            provider.readVarInt();
        }));
        typeHandlers.put(18, DataTypeProvider::readOptVarInt);
        typeHandlers.put(19, DataTypeProvider::readVarInt);
    }

    private int air;
    private String customName;
    private boolean customNameVisible;
    private boolean isInvisible;
    private boolean isSilent;
    private boolean hasNoGravity;

    protected MetaData() { }

    public void parse(DataTypeProvider provider) {
        while (true) {
            int index = provider.readNext() & 0xFF;
            if (index == TERMINATOR) { break; }

            int type = provider.readVarInt();

            Consumer<DataTypeProvider> indexHandler = getIndexHandler(index);
            Consumer<DataTypeProvider> typeHandler = getTypeHandler(type);

            if (indexHandler == null && typeHandler == null) { break; }

            if (indexHandler != null) {
                indexHandler.accept(provider);
            } else {
                typeHandler.accept(provider);
            }
        }
    }

    /**
     * Returns a MetaData object of the correct version.
     * @return the metadata matching the given version
     */
    public static MetaData getVersionedMetaData() {
        return new MetaData();
    }

    public Consumer<DataTypeProvider> getTypeHandler(int i) {
        return typeHandlers.getOrDefault(i, null);
    }

    public Consumer<DataTypeProvider> getIndexHandler(int i) {
        switch (i) {
            case 0: return provider -> isInvisible = (provider.readNext() & 0x20) > 0;
            case 1: return provider -> this.air = provider.readVarInt();
            case 2: return provider -> this.customName = provider.readOptChat();
            case 3: return provider -> this.customNameVisible = provider.readBoolean();
            case 4: return provider -> this.isSilent = provider.readBoolean();
            case 5: return provider -> this.hasNoGravity = provider.readBoolean();
        }
        return null;
    }

    public void addNbtTags(CompoundTag nbt) {
        nbt.add("Silent", new ByteTag(isSilent ? 1 : 0));
        nbt.add("NoGravity", new ByteTag(hasNoGravity ? 1 : 0));
        nbt.add("Invisible", new ByteTag(isInvisible ? 1 : 0));
        nbt.add("CustomNameVisible", new ByteTag(customNameVisible ? 1 : 0));
        nbt.add("Air", new IntTag(air));
        if (customName != null && customName.length() > 0) {
            nbt.add("CustomName", new StringTag(customName));
        }
    }
}
