package dev.icaro.icaruschests.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marks the small menu opened by the Organize control button as belonging to
 * IcarusChests, and remembers which chest it should sort when a sort-type
 * item is clicked — see {@code ChestOrganizeListener}. Built and attached by
 * {@link OrganizeMenuGui} only, same pattern as {@link IcarusChestHolder}.
 */
public final class OrganizeMenuHolder implements InventoryHolder {

    private final UUID chestId;
    private Inventory inventory;

    OrganizeMenuHolder(UUID chestId) {
        this.chestId = chestId;
    }

    public UUID getChestId() {
        return chestId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
