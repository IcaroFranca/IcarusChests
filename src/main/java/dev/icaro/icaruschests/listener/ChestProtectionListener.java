package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestDestructionHandler;
import dev.icaro.icaruschests.chest.ChestManager;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Keeps IcarusChests consistent against world events that would otherwise
 * silently desync a block's position/existence from its PDC tags and
 * database row: pistons pushing/pulling a tagged block, explosions
 * destroying one outright, and vanilla's own item-transport machinery
 * (hoppers, hopper minecarts, droppers feeding a hopper chain, …) reading or
 * writing the tagged chest block's real, otherwise-untouched vanilla
 * inventory — which the plugin's own GUI never looks at, so anything moved
 * through it that way would be invisible to players and could silently
 * duplicate or swallow items relative to what the custom inventory shows.
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

    // MONITOR, not the default priority: another plugin's own block-protection logic (removing a
    // protected block from event.blockList(), or cancelling the event outright) may run at any
    // priority up to and including HIGH, so reading the block list any earlier risks dropping a
    // tagged chest's items/row even though the block itself ends up surviving the explosion.
    // ignoreCancelled still applies at MONITOR: a fully-cancelled explosion destroys nothing here.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            destroyIfTagged(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            destroyIfTagged(block);
        }
    }

    private void destroyIfTagged(Block block) {
        chestManager.getOrLoadFromBlock(block).ifPresent(chest -> destructionHandler.destroy(chest, block));
    }

    /**
     * The chest block a tagged IcarusChest occupies is still a real vanilla
     * {@code CHEST} with its own (always-empty-by-design) inventory — hoppers
     * and hopper minecarts address that vanilla inventory directly and know
     * nothing about the plugin's own {@code IcarusChest#getContents()}, so
     * without this they'd read/write a second, hidden storage location on
     * the very same block.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isTaggedChestInventory(event.getSource()) || isTaggedChestInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isTaggedChestInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            return isTaggedChestInventory(doubleChest.getLeftSide()) || isTaggedChestInventory(doubleChest.getRightSide());
        }
        if (holder instanceof Chest chestHolder) {
            return chestManager.isTaggedChest(chestHolder.getBlock());
        }
        return false;
    }
}
