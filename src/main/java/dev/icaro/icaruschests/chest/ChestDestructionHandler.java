package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.upgrade.UpgradeKitRegistry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Shared "this chest is gone" logic used by both a normal break and an
 * explosion: drops the stored contents (vanilla never knew they existed,
 * since they live in our custom GUI, not the tile entity's own inventory)
 * plus any installed pluggable-upgrade items and a refund of the upgrade kit
 * that got it to its current tier — so breaking an upgraded chest never
 * leaves the player at a material loss — deletes the chest's row from
 * SQLite, and evicts it from {@link ChestManager}. The chest block itself
 * still drops normally via vanilla's own break/explosion handling; nothing
 * here needs to touch that.
 *
 * <p>A slot grown past its item's normal max stack by a Stack upgrade is
 * split back into normal-sized dropped stacks (see {@link
 * #dropNormalized}) — a dropped item is a real, world-saved entity, unlike
 * the chest's own custom GUI inventory, and Minecraft doesn't support one
 * above the vanilla max.
 */
public final class ChestDestructionHandler {

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final UpgradeKitRegistry upgradeKitRegistry;
    private final Plugin plugin;

    public ChestDestructionHandler(ChestManager chestManager, ChestRepository chestRepository,
                                    UpgradeKitRegistry upgradeKitRegistry, Plugin plugin) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.upgradeKitRegistry = upgradeKitRegistry;
        this.plugin = plugin;
    }

    /** @param dropAt where to scatter the contents — the block that was actually broken/exploded, not necessarily {@code chest.getLocation()}. */
    public void destroy(IcarusChest chest, Block dropAt) {
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                dropNormalized(dropAt, item);
            }
        }
        for (ItemStack upgrade : chest.getUpgrades()) {
            if (upgrade != null && upgrade.getType() != Material.AIR) {
                dropAt.getWorld().dropItemNaturally(dropAt.getLocation(), upgrade);
            }
        }
        chest.getTier().upgradeMaterial().ifPresent(ignored ->
                dropAt.getWorld().dropItemNaturally(dropAt.getLocation(), upgradeKitRegistry.createKit(chest.getTier())));

        chestRepository.delete(chest.getId()).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao remover bau " + chest.getId() + " do banco", ex);
            return null;
        });
        chestManager.unregister(chest.getLocation());
    }

    /**
     * A Stack upgrade lets a chest slot hold more than an item's normal max stack — but a dropped
     * item is a real, world-saved entity, and Minecraft's item format won't let a real stack claim
     * a size above 99 (the {@code minecraft:max_stack_size} data component, since the 1.20.5 item
     * rewrite; see also <a href="https://github.com/PaperMC/Paper/issues/11161">Paper#11161</a> on
     * the save corruption an out-of-range amount can cause). Splitting into normal-sized stacks
     * keeps every dropped entity within what the game actually supports.
     */
    private void dropNormalized(Block dropAt, ItemStack item) {
        int remaining = item.getAmount();
        int normalMax = item.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(remaining, normalMax);
            ItemStack piece = item.clone();
            piece.setAmount(chunk);
            dropAt.getWorld().dropItemNaturally(dropAt.getLocation(), piece);
            remaining -= chunk;
        }
    }
}
