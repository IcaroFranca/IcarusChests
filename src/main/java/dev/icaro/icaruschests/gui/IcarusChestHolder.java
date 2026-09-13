package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.tier.StorageTier;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marks an {@link Inventory} as an IcarusChests GUI, carrying enough identity
 * to resolve back to the {@code StorageContainer} (a chest or a backpack) it
 * represents. Built and attached by {@link GuiFactory} only — the inventory
 * reference is assigned right after construction since Bukkit requires the
 * holder to exist before the inventory it owns can be created.
 *
 * <p>Unlike the container it points to, a holder is scoped to one *shared*
 * viewing session, not one player — every simultaneous viewer of the same
 * chest/backpack is handed the exact same {@link Inventory}/holder pair (see
 * {@link GuiFactory#open}), so {@link #scrollOffset} and {@link
 * #nextSortType} are shared state too: one viewer scrolling or clicking
 * Organize moves everyone's view together, the same way vanilla's own double
 * chest looks identical to every player looking at it. Both reset only once
 * the session actually ends (every viewer gone, see {@link
 * GuiFactory#forgetIfEmpty}) and a later open builds a fresh pair.
 */
public final class IcarusChestHolder implements InventoryHolder {

    private final UUID chestId;
    private final StorageTier tier;
    private Inventory inventory;
    private int scrollOffset;
    private SortType nextSortType = SortType.values()[0];

    IcarusChestHolder(UUID chestId, StorageTier tier) {
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

    public StorageTier getTier() {
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
