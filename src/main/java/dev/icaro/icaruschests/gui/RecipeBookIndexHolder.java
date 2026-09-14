package dev.icaro.icaruschests.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Holder for the read-only recipe index GUI (see {@code RecipeBookIndexGui}/{@code RecipeBookListener}). */
public final class RecipeBookIndexHolder implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
