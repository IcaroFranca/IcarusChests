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
 * <p>{@link #doubled} means this primary currently has a linked secondary;
 * while true, its effective capacity ({@link #effectiveTotalCapacity()}) is
 * exactly double {@link ChestTier#totalCapacity()}. {@link #contents} is
 * indexed globally, scrolled through a row at a time by the GUI — see
 * {@code GuiFactory} — with no slots sacrificed to navigation (the scroll
 * controls live in their own dedicated row, not carved out of capacity).
 */
public final class IcarusChest {

    private final UUID id;
    private final ChestLocation location;
    private ChestTier tier;
    private boolean doubled;
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

    public boolean isDoubled() {
        return doubled;
    }

    /** Only flips the flag — callers are responsible for resizing {@link #contents} and handling any overflow themselves. */
    public void setDoubled(boolean doubled) {
        this.doubled = doubled;
    }

    /** This tier's own capacity, doubled if {@link #isDoubled()}. Always a multiple of 9. */
    public int effectiveTotalCapacity() {
        return tier.totalCapacity() * (doubled ? 2 : 1);
    }

    /** Full, globally-indexed backing array (size {@link #effectiveTotalCapacity()}). Mutated in place by the GUI layer. */
    public ItemStack[] getContents() {
        return contents;
    }

    /** Replaces the entire backing array, e.g. after hydrating from SQLite or resizing on upgrade/link. Does not itself mark dirty. */
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
