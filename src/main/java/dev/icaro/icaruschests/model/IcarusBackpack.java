package dev.icaro.icaruschests.model;

import dev.icaro.icaruschests.tier.BackpackTier;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * In-memory representation of a single portable backpack. Unlike {@link IcarusChest} it has no
 * location at all — its identity lives entirely in the carried item's own PDC (see {@code
 * BackpackManager}/{@code NamespacedKeys#BACKPACK_ID}), since the item can move between a player's
 * inventory, another container, the ground, or between servers-worth of chunks without any of
 * that ever being this class's concern; persistence reuses the exact same {@code chest}/{@code
 * chest_inventory}/{@code chest_upgrade} tables a placed chest uses; nothing here needs its own.
 */
public final class IcarusBackpack implements StorageContainer {

    private final UUID id;
    private BackpackTier tier;
    private ItemStack[] contents;
    private ItemStack[] upgrades;
    private boolean dirty;
    private boolean contentsLoadFailed;

    public IcarusBackpack(UUID id, BackpackTier tier) {
        this.id = id;
        this.tier = tier;
        this.contents = new ItemStack[tier.totalCapacity()];
        this.upgrades = new ItemStack[tier.upgradeSlotCount()];
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public BackpackTier getTier() {
        return tier;
    }

    public void setTier(BackpackTier tier) {
        this.tier = tier;
    }

    @Override
    public int effectiveTotalCapacity() {
        return tier.totalCapacity();
    }

    @Override
    public ItemStack[] getContents() {
        return contents;
    }

    @Override
    public void setContents(ItemStack[] contents) {
        this.contents = contents;
    }

    @Override
    public ItemStack[] getUpgrades() {
        return upgrades;
    }

    @Override
    public void setUpgrades(ItemStack[] upgrades) {
        this.upgrades = upgrades;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isContentsLoadFailed() {
        return contentsLoadFailed;
    }

    @Override
    public void setContentsLoadFailed(boolean failed) {
        this.contentsLoadFailed = failed;
    }

    @Override
    public String noun() {
        return "Mochila";
    }
}
