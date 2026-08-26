package version.v26_1.entity.specific;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import version.v26_1.container.Slot;
import version.v26_1.entity.ObjectEntity;
import version.v26_1.entity.metadata.MetaData;
import version.v26_1.packets.DataTypeProvider;
import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntTag;

import java.util.function.Consumer;

/**
 * Handle item frames as they can be used as decorations
 */
public class ItemFrame extends ObjectEntity {
    int facing;
    private ItemFrameMetaData metaData;

    public ItemFrame() {
        super();
    }

    /**
     * Add additional fields needed to render item frames.
     */
    @Override
    protected void addNbtData(CompoundTag root) {
        super.addNbtData(root);

        root.add("Facing", new IntTag(facing));

        // use math.floor instead of just cast so that negative numbers are handled correctly
        root.add("TileX", new IntTag((int) Math.floor(x)));
        root.add("TileY", new IntTag((int) Math.floor(y)));
        root.add("TileZ", new IntTag((int) Math.floor(z)));

        // prevent floating item frames from popping off
        root.add("Fixed", new ByteTag(1));

        if (metaData != null) {
            metaData.addNbtTags(root);
        }
    }

    @Override
    protected void setData(int data) {
        this.facing = data;
    }

    @Override
    public void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            // 1.17+ reordered the item-frame metadata fields; that order is the only one used by
            // the supported versions (26.x). The base ItemFrameMetaData keeps the pre-1.17 order
            // as an extension point should a future version revert it.
            metaData = new ItemFrameMetaData_1_17();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ex) {
            // couldn't parse metadata, whatever
        }
    }

    private static class ItemFrameMetaData extends MetaData {
        Slot item;
        int rotation;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            if (item != null) {
                nbt.add("Item", item.toNbt());
            }
            nbt.add("ItemRotation", new IntTag(rotation));
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 7 -> provider -> item = provider.readSlot();
                case 8 -> provider -> rotation = provider.readVarInt();
                default -> super.getIndexHandler(i);
            };
        }
    }

    private class ItemFrameMetaData_1_17 extends ItemFrameMetaData {
        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            // order of metadata fields for item frames changed a little bit in 1.17
            return switch (i) {
                case 7 -> provider -> rotation = provider.readVarInt();
                case 8 -> provider -> item = provider.readSlot();
                default -> super.getIndexHandler(i);
            };
        }
    }
}