package dev.icaro.icaruschests.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Holder for the read-only, paginated recipe book GUI (see {@code RecipeBookGui}/{@code RecipeBookListener}). */
public final class RecipeBookHolder implements InventoryHolder {

    private int page;
    private Inventory inventory;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
