package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Opens the custom tiered GUI instead of the vanilla chest inventory when a
 * player right-clicks an IcarusChest.
 *
 * <p>M3 scope only: the shift-click-with-upgrade-kit flow (M5) is not yet
 * implemented, so right-clicking always opens the GUI regardless of what the
 * player is holding.
 */
public final class ChestInteractListener implements Listener {

    private final ChestManager chestManager;

    public ChestInteractListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            // The hand check avoids handling this event twice per click
            // (Bukkit fires it once per hand for a single right-click).
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {
            return;
        }

        chestManager.getOrLoadFromBlock(block).ifPresent(chest -> {
            event.setCancelled(true);
            // Always opens page 0; remembering a player's last-viewed page is
            // a nice-to-have left for later polish.
            GuiFactory.open(event.getPlayer(), chest, 0);
        });
    }
}
