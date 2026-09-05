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

import java.util.Optional;

/**
 * Handles clicks and closes on IcarusChests GUIs: the entire control row of a
 * scrollable chest (the real scroll buttons, the position indicator, and its
 * filler slots alike) is off-limits to normal item placement, and clicking
 * an actual scroll button syncs the currently visible slots back into the
 * chest before redrawing the same inventory at the new offset — no
 * close/reopen, so no flicker. Every other slot behaves like a normal chest.
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

        Optional<IcarusChest> maybeChest = chestManager.get(holder.getChestId());
        if (maybeChest.isEmpty()) {
            return;
        }
        IcarusChest chest = maybeChest.get();

        if (!GuiFactory.isControlSlot(chest, event.getSlot())) {
            return; // a normal content slot: let default item-move logic run
        }

        // The whole control row is off-limits, real button or filler alike.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Optional<NavAction> action = GuiFactory.navAction(event.getCurrentItem());
        if (action.isEmpty()) {
            return;
        }

        int newOffset = GuiFactory.scrollTarget(chest, holder.getScrollOffset(), action.get());
        if (newOffset == holder.getScrollOffset()) {
            return; // already at that edge
        }

        GuiFactory.syncVisibleToChest(chest, holder, topInventory);
        GuiFactory.scrollTo(chest, holder, topInventory, newOffset);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof IcarusChestHolder holder)) {
            return;
        }
        chestManager.get(holder.getChestId())
                .ifPresent(chest -> GuiFactory.syncVisibleToChest(chest, holder, event.getInventory()));
    }
}
