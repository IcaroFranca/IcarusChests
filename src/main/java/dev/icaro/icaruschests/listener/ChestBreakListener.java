package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Drops a broken chest's stored contents (vanilla has no idea they exist,
 * since they live in our custom GUI, not the tile entity's own inventory),
 * deletes its row from SQLite, and removes it from the {@link ChestManager}
 * cache.
 *
 * <p>M4 scope only: a double-chest half breaking still only takes down
 * itself, not its linked partner as one unit — that lands in M6 alongside
 * the rest of double-chest linking.
 */
public final class ChestBreakListener implements Listener {

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestBreakListener(ChestManager chestManager, ChestRepository chestRepository, Plugin plugin) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    // HIGH rather than MONITOR: this handler drops items into the world, so
    // it must not run after the event may already have been cancelled by a
    // lower-priority listener (protection plugins, etc).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ChestLocation location = ChestLocation.of(block);

        chestManager.get(location).ifPresent(chest -> {
            dropContents(block, chest);
            chestRepository.delete(chest.getId()).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Falha ao remover bau " + chest.getId() + " do banco", ex);
                return null;
            });
            chestManager.unregister(location);
        });
    }

    private void dropContents(Block block, IcarusChest chest) {
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }
        }
    }
}
