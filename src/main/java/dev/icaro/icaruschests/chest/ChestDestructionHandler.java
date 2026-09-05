package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Shared "this chest is gone" logic used by both a normal break and an
 * explosion: drops the stored contents (vanilla never knew they existed,
 * since they live in our custom GUI, not the tile entity's own inventory),
 * deletes the chest's row from SQLite, and evicts it from {@link ChestManager}.
 */
public final class ChestDestructionHandler {

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestDestructionHandler(ChestManager chestManager, ChestRepository chestRepository, Plugin plugin) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    /** @param dropAt where to scatter the contents — the block that was actually broken/exploded, not necessarily {@code chest.getLocation()}. */
    public void destroy(IcarusChest chest, Block dropAt) {
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                dropAt.getWorld().dropItemNaturally(dropAt.getLocation(), item);
            }
        }
        chestRepository.delete(chest.getId()).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao remover bau " + chest.getId() + " do banco", ex);
            return null;
        });
        chestManager.unregister(chest.getLocation());
    }
}
