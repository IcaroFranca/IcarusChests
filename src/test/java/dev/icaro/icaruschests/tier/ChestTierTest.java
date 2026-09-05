package dev.icaro.icaruschests.tier;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestTierTest {

    @Test
    void normalTierHasNoUpgradeMaterial() {
        assertTrue(ChestTier.NORMAL.upgradeMaterial().isEmpty());
    }

    @Test
    void everyOtherTierHasAnUpgradeMaterial() {
        for (ChestTier tier : ChestTier.values()) {
            if (tier == ChestTier.NORMAL) {
                continue;
            }
            assertTrue(tier.upgradeMaterial().isPresent(), tier + " should define an upgrade material");
            assertTrue(tier.upgradeAmount() > 0, tier + " should require a positive upgrade amount");
        }
    }

    @Test
    void capacitiesMatchTheDesignedProgression() {
        assertEquals(27, ChestTier.NORMAL.totalCapacity());
        assertEquals(36, ChestTier.COPPER.totalCapacity());
        assertEquals(45, ChestTier.IRON.totalCapacity());
        assertEquals(54, ChestTier.GOLD.totalCapacity());
        assertEquals(108, ChestTier.DIAMOND.totalCapacity());
        assertEquals(162, ChestTier.NETHERITE.totalCapacity());
    }

    @Test
    void slotsPerPageNeverExceedsTheVanillaChestLimit() {
        for (ChestTier tier : ChestTier.values()) {
            assertTrue(tier.slotsPerPage() <= ChestTier.MAX_SLOTS_PER_PAGE,
                    tier + " has more slots per page than a vanilla chest inventory allows");
            assertEquals(0, tier.slotsPerPage() % 9, tier + "'s slotsPerPage must be a multiple of 9");
        }
    }

    @Test
    void tiersUpgradeInStrictSequentialOrder() {
        assertEquals(Optional.of(ChestTier.COPPER), ChestTier.NORMAL.next());
        assertEquals(Optional.of(ChestTier.IRON), ChestTier.COPPER.next());
        assertEquals(Optional.of(ChestTier.GOLD), ChestTier.IRON.next());
        assertEquals(Optional.of(ChestTier.DIAMOND), ChestTier.GOLD.next());
        assertEquals(Optional.of(ChestTier.NETHERITE), ChestTier.DIAMOND.next());
        assertTrue(ChestTier.NETHERITE.next().isEmpty(), "Netherite is the max tier, it should have no next()");
    }

    @Test
    void byOrdinalRoundTripsForEveryTier() {
        for (ChestTier tier : ChestTier.values()) {
            assertEquals(Optional.of(tier), ChestTier.byOrdinal(tier.ordinal()));
        }
        assertTrue(ChestTier.byOrdinal(-1).isEmpty());
        assertTrue(ChestTier.byOrdinal(ChestTier.values().length).isEmpty());
    }

    @Test
    void upgradeMaterialsMatchTheDesignedProgression() {
        assertEquals(Optional.of(Material.COPPER_INGOT), ChestTier.COPPER.upgradeMaterial());
        assertEquals(Optional.of(Material.IRON_INGOT), ChestTier.IRON.upgradeMaterial());
        assertEquals(Optional.of(Material.GOLD_INGOT), ChestTier.GOLD.upgradeMaterial());
        assertEquals(Optional.of(Material.DIAMOND), ChestTier.DIAMOND.upgradeMaterial());
        assertEquals(Optional.of(Material.NETHERITE_INGOT), ChestTier.NETHERITE.upgradeMaterial());
    }

    @Test
    void upgradeKitKeyIsLowercaseAndUnique() {
        long distinctKeys = java.util.Arrays.stream(ChestTier.values())
                .map(ChestTier::upgradeKitKey)
                .distinct()
                .count();
        assertEquals(ChestTier.values().length, distinctKeys, "every tier must have a unique upgrade kit key");
        assertFalse(ChestTier.COPPER.upgradeKitKey().contains(" "), "recipe keys can't contain spaces");
    }
}
