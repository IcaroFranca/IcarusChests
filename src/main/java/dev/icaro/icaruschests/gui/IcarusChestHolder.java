package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marks an {@link Inventory} as an IcarusChests GUI, carrying enough identity
 * to resolve back to the {@code IcarusChest} it represents. Built and
 * attached by {@link GuiFactory} only — the inventory reference is assigned
 * right after construction since Bukkit requires the holder to exist before
 * the inventory it owns can be created.
 *
 * <p>Unlike the {@code IcarusChest} it points to, a holder is scoped to one
 * viewing session: {@link #scrollOffset} is mutated in place as the viewer
 * scrolls (see {@code ChestGuiListener}), since scrolling reuses the same
 * {@link Inventory} rather than reopening a new one. {@link #nextSortType}
 * works the same way for the Organize button: each click applies it and
 * advances to the next one, so a fresh session always starts back at the
 * first {@link SortType}.
 */
public final class IcarusChestHolder implements InventoryHolder {

    private final UUID chestId;
    private final ChestTier tier;
    private Inventory inventory;
    private int scrollOffset;
    private SortType nextSortType = SortType.values()[0];

    IcarusChestHolder(UUID chestId, ChestTier tier) {
        this.chestId = chestId;
        this.tier = tier;
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

    public int getScrollOffset() {
        return scrollOffset;
    }

    void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
    }

    /** The {@link SortType} the Organize button applies on its next click. */
    public SortType getNextSortType() {
        return nextSortType;
    }

    void setNextSortType(SortType nextSortType) {
        this.nextSortType = nextSortType;
    }
}
