package dev.icaro.icaruschests.model;

import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * In-memory representation of a single tiered chest ("primary"). A double
 * chest's secondary half has no {@code IcarusChest} of its own — its block is
 * tagged with a PDC pointer to the primary's location instead, and always
 * resolves to this same instance (see {@code ChestManager}).
 *
 * <p>{@link #contents} is indexed globally across all pages
 * ({@code page * tier.slotsPerPage() + localSlot}); slots that a page's GUI
 * reserves for navigation buttons are simply never read from or written to,
 * so they permanently reduce usable capacity by one each (see
 * {@code GuiFactory}).
 */
public final class IcarusChest {

    private final UUID id;
    private final ChestLocation location;
    private ChestTier tier;
    private ItemStack[] contents;
    private boolean dirty;

    public IcarusChest(UUID id, ChestLocation location, ChestTier tier) {
        this.id = id;
        this.location = location;
        this.tier = tier;
        this.contents = new ItemStack[tier.totalCapacity()];
    }

    public UUID getId() {
        return id;
    }

    public ChestLocation getLocation() {
        return location;
    }

    public ChestTier getTier() {
        return tier;
    }

    public void setTier(ChestTier tier) {
        this.tier = tier;
    }

    /** Full, globally-indexed backing array (size {@code tier.totalCapacity()}). Mutated in place by the GUI layer. */
    public ItemStack[] getContents() {
        return contents;
    }

    /** Replaces the entire backing array, e.g. after hydrating from SQLite or resizing on upgrade. Does not itself mark dirty. */
    public void setContents(ItemStack[] contents) {
        this.contents = contents;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
