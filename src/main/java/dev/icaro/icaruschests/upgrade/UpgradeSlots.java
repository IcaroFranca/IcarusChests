package dev.icaro.icaruschests.upgrade;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Small helpers over a chest's upgrade slot array, kept out of {@code IcarusChest} itself to avoid a model→upgrade dependency. */
public final class UpgradeSlots {

    private UpgradeSlots() {
    }

    public static boolean has(ItemStack[] upgrades, UpgradeType type) {
        for (ItemStack item : upgrades) {
            if (UpgradeRegistry.typeOf(item).filter(installed -> installed == type).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Highest stack-multiplier among installed {@code STACK_*} upgrades, or
     * {@code 1.0} (no bonus, normal vanilla stacking) if none are installed.
     * Several tiers installed at once don't compound — only the best applies.
     */
    public static double bestStackMultiplier(ItemStack[] upgrades) {
        double best = 1.0;
        for (ItemStack item : upgrades) {
            Optional<UpgradeType> type = UpgradeRegistry.typeOf(item);
            if (type.isPresent() && type.get().isStackUpgrade()) {
                best = Math.max(best, type.get().stackMultiplier());
            }
        }
        return best;
    }

    /**
     * Same as {@link #bestStackMultiplier(ItemStack[])} but pretending {@code excludedIndex}
     * were empty — used to check what a chest's cap would drop to if a given Stack upgrade were
     * removed, before actually removing it (see {@code ChestGuiListener}).
     */
    public static double bestStackMultiplierExcluding(ItemStack[] upgrades, int excludedIndex) {
        double best = 1.0;
        for (int i = 0; i < upgrades.length; i++) {
            if (i == excludedIndex) {
                continue;
            }
            Optional<UpgradeType> type = UpgradeRegistry.typeOf(upgrades[i]);
            if (type.isPresent() && type.get().isStackUpgrade()) {
                best = Math.max(best, type.get().stackMultiplier());
            }
        }
        return best;
    }

    /** The installed Filter upgrade item, if any — carries its own accepted-materials list in its PDC (see {@link UpgradeRegistry}). */
    public static Optional<ItemStack> filterItem(ItemStack[] upgrades) {
        for (ItemStack item : upgrades) {
            if (UpgradeRegistry.typeOf(item).filter(type -> type == UpgradeType.FILTER).isPresent()) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
