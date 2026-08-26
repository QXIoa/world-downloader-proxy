package version.v26_2.villagers;
import core.schematic.SelectionState;
import core.schematic.SelectionCommand;

import version.v26_2.container.Slot;

public record VillagerTrade(Slot firstItem, Slot secondItem, Slot sellingItem, int demand, int maxUses,
        float priceMultiplier, int specialPrice, int uses, int xp) {
}