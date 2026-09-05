package dev.icaro.icaruschests.tier;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
        assertEquals(45, ChestTier.COPPER.totalCapacity());
        assertEquals(54, ChestTier.IRON.totalCapacity());
        assertEquals(81, ChestTier.GOLD.totalCapacity());
        assertEquals(108, ChestTier.DIAMOND.totalCapacity());
        assertEquals(135, ChestTier.NETHERITE.totalCapacity());
    }

    @Test
    void totalCapacityIsAlwaysAMultipleOfARow() {
        // The GUI scrolls a row (9 slots) at a time, so a capacity that isn't
        // a clean multiple of 9 could never be fully reachable by scrolling.
        for (ChestTier tier : ChestTier.values()) {
            assertEquals(0, tier.totalCapacity() % 9, tier + "'s capacity must be a multiple of 9");
        }
    }

    @Test
    void everyTierHasADistinctTitleColor() {
        long distinctColors = Arrays.stream(ChestTier.values())
                .map(ChestTier::titleColor)
                .distinct()
                .count();
        assertEquals(ChestTier.values().length, distinctColors, "every tier should have its own title color");
    }

    @Test
    void upgradeSlotCountNeverDecreasesBetweenTiers() {
        // TierUpgradeService resizes the upgrades array with a plain Arrays.copyOf
        // on tier-up, which only works safely if this never shrinks.
        ChestTier[] values = ChestTier.values();
        for (int i = 1; i < values.length; i++) {
            assertTrue(values[i].upgradeSlotCount() >= values[i - 1].upgradeSlotCount(),
                    values[i] + " must have at least as many upgrade slots as " + values[i - 1]);
        }
        assertTrue(ChestTier.NORMAL.upgradeSlotCount() >= 1, "even the base tier should have at least one upgrade slot");
        assertTrue(ChestTier.NETHERITE.upgradeSlotCount() <= ChestTier.MAX_UPGRADE_SLOTS);
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
        long distinctKeys = Arrays.stream(ChestTier.values())
                .map(ChestTier::upgradeKitKey)
                .distinct()
                .count();
        assertEquals(ChestTier.values().length, distinctKeys, "every tier must have a unique upgrade kit key");
        assertFalse(ChestTier.COPPER.upgradeKitKey().contains(" "), "recipe keys can't contain spaces");
    }

    @Test
    void recipeShapeHasAChestInTheCenterAndTheRightAmountOfMaterial() {
        for (ChestTier tier : ChestTier.values()) {
            if (tier.upgradeMaterial().isEmpty()) {
                continue;
            }
            String[] shape = tier.recipeShape();
            assertEquals(3, shape.length, tier + " recipe shape must be a 3-row grid");
            for (String row : shape) {
                assertEquals(3, row.length(), tier + " recipe row must be 3 characters wide");
            }
            assertEquals('C', shape[1].charAt(1), tier + "'s recipe must have a chest in the center slot");

            long materialCount = shape[0].chars().filter(c -> c == 'M').count()
                    + shape[1].chars().filter(c -> c == 'M').count()
                    + shape[2].chars().filter(c -> c == 'M').count();
            assertEquals(tier.upgradeAmount(), materialCount,
                    tier + "'s recipe shape must use exactly upgradeAmount() material slots");
        }
    }
}
