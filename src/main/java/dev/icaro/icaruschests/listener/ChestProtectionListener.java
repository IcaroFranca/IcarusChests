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
 * destroying one outright, and a hopper (or hopper minecart, or a dropper
 * feeding one) pulling items back OUT of a tagged chest through its real,
 * otherwise-untouched vanilla inventory — which the plugin's own GUI never
 * looks at, so anything read through it that way would be invisible to
 * players while silently vanishing from what the custom inventory shows.
 *
 * <p>The other direction — a hopper pushing items INTO a tagged chest — is
 * deliberately not blocked here: {@code ChestHopperListener} handles that
 * one on its own, translating the push into a real insertion against {@code
 * IcarusChest#getContents()} instead of the vanilla inventory. Extraction
 * stays unsupported for now (a deliberate scope decision, not a gap).
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
     * {@code CHEST} with its own (always-empty-by-design) inventory — a
     * hopper or hopper minecart pulling FROM it addresses that vanilla
     * inventory directly and knows nothing about the plugin's own {@code
     * IcarusChest#getContents()}, so without this it would read a second,
     * always-empty storage location on the very same block instead of
     * actually extracting anything. Only the source side is checked here —
     * see the class javadoc for why the destination side is handled
     * elsewhere.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isTaggedChestInventory(event.getSource())) {
            event.setCancelled(true);
        }
    }

    private boolean isTaggedChestInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        return isTaggedChestHolder(inventory.getHolder());
    }

    /**
     * {@link DoubleChest#getLeftSide()}/{@code getRightSide()} return the side's
     * {@link InventoryHolder} directly (each a plain {@link Chest}), not an
     * {@link Inventory} — so the double-chest recursion has to branch on
     * holders, not inventories.
     */
    private boolean isTaggedChestHolder(InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest) {
            return isTaggedChestHolder(doubleChest.getLeftSide()) || isTaggedChestHolder(doubleChest.getRightSide());
        }
        if (holder instanceof Chest chestHolder) {
            return chestManager.isTaggedChest(chestHolder.getBlock());
        }
        return false;
    }
}
