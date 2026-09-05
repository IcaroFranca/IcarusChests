package dev.icaro.icaruschests.model;

import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * In-memory representation of a single tiered chest block. One instance
 * exists per physical chest block; a double chest is two linked instances
 * (see {@link #linkedChestId}), not one instance spanning both halves.
 *
 * <p>{@link #contents} is indexed globally across all pages
 * ({@code page * tier.slotsPerPage() + localSlot}); slots that a page's GUI
 * reserves for navigation buttons are simply never read from or written to,
 * so they permanently reduce usable capacity by one each (see
 * {@code GuiFactory}). Nothing here is persisted yet — SQLite-backed
 * load/save and {@code dirty}-triggered saving arrive in M4, so contents are
 * currently lost on server restart.
 */
public final class IcarusChest {

    private final UUID id;
    private final ChestLocation location;
    private ChestTier tier;
    private UUID linkedChestId;
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

    /** Id of the other half of this double chest, or {@code null} if this is a single chest. */
    public UUID getLinkedChestId() {
        return linkedChestId;
    }

    public void setLinkedChestId(UUID linkedChestId) {
        this.linkedChestId = linkedChestId;
    }

    public boolean isLinked() {
        return linkedChestId != null;
    }

    /** Full, globally-indexed backing array (size {@code tier.totalCapacity()}). Mutated in place by the GUI layer. */
    public ItemStack[] getContents() {
        return contents;
    }

    /** Replaces the entire backing array, e.g. after hydrating from SQLite. Does not itself mark dirty. */
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
