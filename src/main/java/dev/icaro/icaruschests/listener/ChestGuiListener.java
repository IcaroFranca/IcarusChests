package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.gui.IcarusChestHolder;
import dev.icaro.icaruschests.gui.NavAction;
import dev.icaro.icaruschests.model.IcarusChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Handles clicks and closes on IcarusChests page GUIs: intercepts navigation
 * buttons before any normal item-move logic runs, and syncs whatever the
 * player left in the GUI back into the {@code IcarusChest}'s backing array on
 * every close (including the implicit close caused by switching pages).
 */
public final class ChestGuiListener implements Listener {

    private final ChestManager chestManager;

    public ChestGuiListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof IcarusChestHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != topInventory) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (!GuiFactory.isNavItem(clicked)) {
            return;
        }

        // Always cancel clicks on nav buttons: they must never be picked up,
        // moved or consumed like a regular item.
        event.setCancelled(true);

        Optional<NavAction> action = GuiFactory.navAction(clicked);
        if (action.isEmpty() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int newPage = holder.getPage() + (action.get() == NavAction.NEXT ? 1 : -1);
        chestManager.get(holder.getChestId()).ifPresent(chest -> switchPage(player, chest, holder, topInventory, newPage));
    }

    private void switchPage(Player player, IcarusChest chest, IcarusChestHolder holder, Inventory currentTop, int newPage) {
        if (newPage < 0 || newPage >= chest.getTier().pages()) {
            return;
        }
        // Sync the page being left before building the next one, so the next
        // page's GUI (and the chest's backing array) reflect what the player
        // just moved around, not stale data from when this page was opened.
        GuiFactory.syncPageToChest(chest, holder.getPage(), currentTop);
        GuiFactory.open(player, chest, newPage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof IcarusChestHolder holder)) {
            return;
        }
        chestManager.get(holder.getChestId())
                .ifPresent(chest -> GuiFactory.syncPageToChest(chest, holder.getPage(), event.getInventory()));
    }
}
