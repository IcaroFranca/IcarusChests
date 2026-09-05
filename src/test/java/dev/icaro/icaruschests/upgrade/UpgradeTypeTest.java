package dev.icaro.icaruschests.upgrade;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeTypeTest {

    @Test
    void filterIsNotAStackUpgradeAndHasNoPreviousTier() {
        assertFalse(UpgradeType.FILTER.isStackUpgrade());
        assertTrue(UpgradeType.FILTER.previousStackTier().isEmpty());
    }

    @Test
    void copperIsTheEntryStackTierWithNoPrevious() {
        assertTrue(UpgradeType.STACK_COPPER.isStackUpgrade());
        assertTrue(UpgradeType.STACK_COPPER.previousStackTier().isEmpty());
    }

    @Test
    void everyOtherStackTierPointsAtTheOneBelowIt() {
        assertEquals(Optional.of(UpgradeType.STACK_COPPER), UpgradeType.STACK_IRON.previousStackTier());
        assertEquals(Optional.of(UpgradeType.STACK_IRON), UpgradeType.STACK_GOLD.previousStackTier());
        assertEquals(Optional.of(UpgradeType.STACK_GOLD), UpgradeType.STACK_DIAMOND.previousStackTier());
        assertEquals(Optional.of(UpgradeType.STACK_DIAMOND), UpgradeType.STACK_NETHERITE.previousStackTier());
    }

    @Test
    void stackMultipliersStrictlyIncreaseUpTheChain() {
        UpgradeType current = UpgradeType.STACK_NETHERITE;
        double previousMultiplier = Double.MAX_VALUE;
        while (current != null) {
            assertTrue(current.stackMultiplier() < previousMultiplier,
                    current + "'s multiplier should be lower than the tier above it");
            previousMultiplier = current.stackMultiplier();
            current = current.previousStackTier().orElse(null);
        }
    }

    @Test
    void keyIsLowercaseAndUniquePerType() {
        long distinctKeys = Arrays.stream(UpgradeType.values())
                .map(UpgradeType::key)
                .distinct()
                .count();
        assertEquals(UpgradeType.values().length, distinctKeys, "every upgrade type must have a unique key");
        assertFalse(UpgradeType.STACK_COPPER.key().contains(" "), "recipe keys can't contain spaces");
    }
}
