package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.gui.FilterConfigGui;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Opens the Filter upgrade's item-type picker (see {@code FilterConfigGui}) when a player
 * right-clicks the air while holding a Filter item. Click/drag/close handling for the picker
 * itself lives inside {@code FilterConfigGui} via InventoryFramework's own Gui-scoped hooks.
 */
public final class FilterConfigListener implements Listener {

    @EventHandler
    public void onOpenFilterConfig(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        boolean isFilter = UpgradeRegistry.typeOf(inHand).filter(type -> type == UpgradeType.FILTER).isPresent();
        if (!isFilter) {
            return;
        }

        event.setCancelled(true);
        int sourceSlot = player.getInventory().getHeldItemSlot();
        List<Material> current = UpgradeRegistry.filterMaterials(inHand);
        FilterConfigGui.open(player, sourceSlot, current);
    }
}
