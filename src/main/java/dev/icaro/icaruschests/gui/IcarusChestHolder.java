package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marks an {@link Inventory} as an IcarusChests page GUI, carrying enough
 * identity to resolve back to the {@code IcarusChest} it represents and
 * which page is currently displayed. Built and attached by {@link GuiFactory}
 * only — the inventory reference is assigned right after construction since
 * Bukkit requires the holder to exist before the inventory it owns can be
 * created.
 */
public final class IcarusChestHolder implements InventoryHolder {

    private final UUID chestId;
    private final ChestTier tier;
    private final int page;
    private Inventory inventory;

    IcarusChestHolder(UUID chestId, ChestTier tier, int page) {
        this.chestId = chestId;
        this.tier = tier;
        this.page = page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getChestId() {
        return chestId;
    }

    public ChestTier getTier() {
        return tier;
    }

    public int getPage() {
        return page;
    }
}
