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
                dropAt.getWorld().dropItemNaturally(dropAt.getLocation(), item);
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
}
