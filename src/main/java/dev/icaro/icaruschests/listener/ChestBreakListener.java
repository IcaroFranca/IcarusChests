package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestDestructionHandler;
import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.util.BlockFaces;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Breaking either half of a double chest destroys the logical unit as a
 * whole: drops the (shared) stored contents, deletes the primary's row, and
 * — if the primary itself was the half broken — clears the {@code
 * LINK_TARGET} tag on its secondary so that leftover physical block reverts
 * to being a plain, untagged chest instead of a dangling pointer. Breaking
 * the secondary instead leaves the primary fully intact as a single chest.
 */
public final class ChestBreakListener implements Listener {

    private final ChestManager chestManager;
    private final ChestDestructionHandler destructionHandler;

    public ChestBreakListener(ChestManager chestManager, ChestDestructionHandler destructionHandler) {
        this.chestManager = chestManager;
        this.destructionHandler = destructionHandler;
    }

    // HIGH rather than MONITOR: this handler drops items into the world, so
    // it must not run after the event may already have been cancelled by a
    // lower-priority listener (protection plugins, etc).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // getOrLoadFromBlock (not a bare cache lookup) so a chest that was
        // never touched this session still gets recognized before it's gone.
        chestManager.getOrLoadFromBlock(block).ifPresent(primaryChest -> {
            destructionHandler.destroy(primaryChest, block);
            clearSecondaryPointerIfPrimaryBroken(block, primaryChest);
        });
    }

    private void clearSecondaryPointerIfPrimaryBroken(Block brokenBlock, IcarusChest primaryChest) {
        if (!ChestLocation.of(brokenBlock).equals(primaryChest.getLocation())) {
            return; // the broken block was the secondary; the primary never referenced it, nothing to clean up
        }
        String primaryLocationEncoded = primaryChest.getLocation().encode();
        for (BlockFace face : BlockFaces.HORIZONTAL) {
            Block neighbor = brokenBlock.getRelative(face);
            if (neighbor.getType() != Material.CHEST || !(neighbor.getState() instanceof TileState state)) {
                continue;
            }
            String linkTarget = state.getPersistentDataContainer().get(NamespacedKeys.LINK_TARGET, PersistentDataType.STRING);
            if (primaryLocationEncoded.equals(linkTarget)) {
                state.getPersistentDataContainer().remove(NamespacedKeys.LINK_TARGET);
                state.update(true);
            }
        }
    }
}
