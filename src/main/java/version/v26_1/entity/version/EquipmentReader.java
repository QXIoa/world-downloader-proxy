package version.v26_1.entity.version;

import version.v26_1.container.Slot;
import version.v26_1.packets.DataTypeProvider;

/**
 * Reads the equipment slots sent in the "Set Equipment" packet. If a future Minecraft version changes this
 * format again, add a new subclass overriding {@link #readSlots} and pick it in {@link #getVersioned()}
 * (see the pre-26.x history of this class in git for an example of that pattern).
 */
public class EquipmentReader {
    public static EquipmentReader getVersioned() {
        return new EquipmentReader();
    }

    public Slot[] readSlots(Slot[] equipment, DataTypeProvider provider) {
        if (equipment == null) {
            equipment = new Slot[8];
        }

        boolean hasNext;
        do {
            byte slotData = provider.readNext();

            hasNext = (slotData & 0x80) > 0;
            int slotId = (slotData & 0x7f);
            equipment[slotId] = provider.readSlot();
        } while (hasNext);

        return equipment;
    }
}
