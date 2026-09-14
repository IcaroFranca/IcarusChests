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

    /**
     * Inserts as much of {@code incoming} as fits into {@code contents}, respecting {@code
     * stackMultiplier} (see {@link #bestStackMultiplier}) — first topping off any existing
     * matching stack up to its capped size, then filling empty slots at that same cap. Shared by
     * {@code ChestGuiListener#handleShiftDeposit} (a shift-click deposit) and {@code
     * ChestHopperListener} (a hopper feeding the chest from outside any GUI) so the one rule for
     * "how much of this item can a Stack-upgraded container take right now" never drifts between
     * the two.
     *
     * <p>Deliberately does not check a Filter upgrade at all — callers that care check it
     * themselves before calling this, since what "rejected" should do differs by caller (e.g.
     * cancelling a click outright vs. simply leaving a hopper's item where it is for a later
     * retry). Mutates {@code contents} in place; returns how much of {@code incoming} was actually
     * inserted — 0 if every matching slot and every empty slot is already at cap.
     */
    public static int insertRespectingStackCap(ItemStack[] contents, double stackMultiplier, ItemStack incoming) {
        int remaining = incoming.getAmount();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack existing = contents[i];
            if (existing != null && existing.isSimilar(incoming)) {
                int cap = (int) Math.floor(existing.getMaxStackSize() * stackMultiplier);
                int space = cap - existing.getAmount();
                if (space > 0) {
                    int toMove = Math.min(space, remaining);
                    existing.setAmount(existing.getAmount() + toMove);
                    remaining -= toMove;
                }
            }
        }
        int emptySlotCap = (int) Math.floor(incoming.getMaxStackSize() * stackMultiplier);
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null) {
                int toMove = Math.min(emptySlotCap, remaining);
                contents[i] = withAmount(incoming, toMove);
                remaining -= toMove;
            }
        }
        return incoming.getAmount() - remaining;
    }

    private static ItemStack withAmount(ItemStack base, int amount) {
        ItemStack copy = base.clone();
        copy.setAmount(amount);
        return copy;
    }
}
