package version.v26_1.villagers;

import version.v26_1.container.Slot;

public record VillagerTrade(Slot firstItem, Slot secondItem, Slot sellingItem, int demand, int maxUses,
        float priceMultiplier, int specialPrice, int uses, int xp) {
}