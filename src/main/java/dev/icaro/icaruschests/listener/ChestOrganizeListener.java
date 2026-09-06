package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.gui.OrganizeMenuHolder;
import dev.icaro.icaruschests.gui.SortType;
import dev.icaro.icaruschests.model.IcarusChest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the small menu a chest GUI's Organize button opens: picking a
 * {@link SortType} reorders the chest's *entire* {@code chest.getContents()}
 * — every slot, including ones currently scrolled out of view, and every
 * stack's true amount (a Stack upgrade's real quantity, never the
 * display-capped stand-in {@code GuiFactory} hands the client) — since that
 * array is already the full, authoritative picture regardless of what the
 * viewer happens to be scrolled to. The chest GUI is always closed before
 * this menu opens (see {@code ChestGuiListener}), so its content is already
 * fully synced by the time a sort runs here — no extra flush needed.
 */
public final class ChestOrganizeListener implements Listener {

    private final ChestManager chestManager;

    public ChestOrganizeListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler
    public void onOrganizeMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OrganizeMenuHolder holder)) {
            return;
        }
        event.setCancelled(true); // a picker, not a real inventory: nothing here is ever meant to move
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // clicked their own inventory while the menu was open: not one of our buttons
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SortType.fromItem(event.getCurrentItem()).ifPresent(type ->
                chestManager.get(holder.getChestId()).ifPresent(chest -> sortChest(chest, type)));
        player.closeInventory(); // any click here — a sort icon or the cancel button — dismisses the menu
    }

    /**
     * Hands the player back to their chest GUI once the menu closes, however
     * it closes (a sort, the cancel button, or plain ESC) — a bare organize
     * menu closing should never just leave the player staring at nothing.
     * Reads the target chest straight off the holder (not a per-player
     * pending map) so even a direct ESC, with no click at all, still returns
     * the player to their chest.
     */
    @EventHandler
    public void onOrganizeMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof OrganizeMenuHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        chestManager.get(holder.getChestId()).ifPresent(chest -> GuiFactory.open(player, chest));
    }

    private void sortChest(IcarusChest chest, SortType type) {
        ItemStack[] contents = chest.getContents();
        List<ItemStack> items = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }
        items.sort(type.comparator()); // List.sort is a stable sort: ties keep their original order

        ItemStack[] sorted = new ItemStack[contents.length];
        for (int i = 0; i < items.size(); i++) {
            sorted[i] = items.get(i);
        }
        chest.setContents(sorted);
        chest.setDirty(true);
    }
}
