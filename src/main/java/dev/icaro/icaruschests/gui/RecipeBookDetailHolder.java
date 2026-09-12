package dev.icaro.icaruschests.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Holder for the read-only single-recipe detail GUI (see {@code RecipeBookDetailGui}/{@code RecipeBookListener}). */
public final class RecipeBookDetailHolder implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
