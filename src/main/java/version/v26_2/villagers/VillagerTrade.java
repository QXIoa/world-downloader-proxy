package version.v26_2.villagers;

import version.v26_2.container.Slot;

public record VillagerTrade(Slot firstItem, Slot secondItem, Slot sellingItem, int demand, int maxUses,
        float priceMultiplier, int specialPrice, int uses, int xp) {
}