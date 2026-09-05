package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.model.ChestLocation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Removes a broken chest block's entry from the {@link ChestManager} cache.
 *
 * <p>M2 scope only: since inventory contents don't exist yet (added in M4),
 * there is nothing to drop besides what vanilla already drops for an empty
 * chest. From M4 onward this listener must also drop the chest's stored
 * {@code contents[]} at the break location and delete its SQLite row, and
 * from M6 onward a double-chest half breaking must take down its linked
 * partner as one logical unit.
 */
public final class ChestBreakListener implements Listener {

    private final ChestManager chestManager;

    public ChestBreakListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ChestLocation location = ChestLocation.of(event.getBlock());
        chestManager.get(location).ifPresent(chest -> chestManager.unregister(location));
    }
}
