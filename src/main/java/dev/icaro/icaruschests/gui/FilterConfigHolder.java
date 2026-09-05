package dev.icaro.icaruschests.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Holder for the 9-slot "which items does this Filter accept" picker GUI
 * (see {@code FilterConfigListener}). Remembers which hotbar slot the Filter
 * item being configured came from, so the result is written back to the
 * right item even if the player scrolled their hotbar while the GUI was
 * open.
 */
public final class FilterConfigHolder implements InventoryHolder {

    private final int sourceHotbarSlot;
    private Inventory inventory;

    public FilterConfigHolder(int sourceHotbarSlot) {
        this.sourceHotbarSlot = sourceHotbarSlot;
    }

    public int getSourceHotbarSlot() {
        return sourceHotbarSlot;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
