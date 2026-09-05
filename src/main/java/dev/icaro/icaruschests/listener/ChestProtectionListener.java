package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestDestructionHandler;
import dev.icaro.icaruschests.chest.ChestManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Keeps IcarusChests consistent against world events that would otherwise
 * silently desync a block's position/existence from its PDC tags and
 * database row: pistons pushing/pulling a tagged block, and explosions
 * destroying one outright.
 *
 * <p>M6 scope: an explosion that destroys only one half of a double chest
 * doesn't clean up the surviving half's pointer tag (unlike a normal break)
 * — a known, rare edge case left for later polish.
 */
public final class ChestProtectionListener implements Listener {

    private final ChestManager chestManager;
    private final ChestDestructionHandler destructionHandler;

    public ChestProtectionListener(ChestManager chestManager, ChestDestructionHandler destructionHandler) {
        this.chestManager = chestManager;
        this.destructionHandler = destructionHandler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(chestManager::isTaggedChest)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(chestManager::isTaggedChest)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            destroyIfTagged(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            destroyIfTagged(block);
        }
    }

    private void destroyIfTagged(Block block) {
        chestManager.getOrLoadFromBlock(block).ifPresent(chest -> destructionHandler.destroy(chest, block));
    }
}
