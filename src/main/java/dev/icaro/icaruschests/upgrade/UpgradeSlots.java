package dev.icaro.icaruschests.upgrade;

import org.bukkit.inventory.ItemStack;

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
}
