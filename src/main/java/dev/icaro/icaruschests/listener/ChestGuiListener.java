package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.gui.IcarusChestHolder;
import dev.icaro.icaruschests.gui.NavAction;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeSlots;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Level;

/**
 * Handles clicks and closes on IcarusChests GUIs:
 * <ul>
 *   <li>the scroll buttons (or the mouse wheel — see {@link #onHotbarScroll})
 *       sync the visible slots back into the chest before redrawing the same
 *       inventory at the new offset, no close/reopen, so no flicker;</li>
 *   <li>the chest's upgrade slots accept dragging a matching upgrade item in
 *       (installs it) or clicking with an empty cursor (removes it back to
 *       the cursor);</li>
 *   <li>a chest with the Filter upgrade only accepts one item type at a time
 *       (whichever is already stored), and one with Stack lets an existing
 *       stack keep growing past the normal limit — both only for direct
 *       left-clicks in this first version; shift-clicking from the player's
 *       own inventory still follows normal vanilla stacking rules.</li>
 * </ul>
 */
public final class ChestGuiListener implements Listener {

    /** Flat cap a Stack-upgraded slot can hold, regardless of the item's own normal max stack size. */
    private static final int STACK_UPGRADE_CAP = 1600;

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestGuiListener(ChestManager chestManager, ChestRepository chestRepository, Plugin plugin) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof IcarusChestHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != topInventory) {
            return; // a shift-click FROM the player's own inventory: see class docs for the current limitation
        }

        Optional<IcarusChest> maybeChest = chestManager.get(holder.getChestId());
        if (maybeChest.isEmpty()) {
            return;
        }
        IcarusChest chest = maybeChest.get();
        int slot = event.getSlot();

        if (!GuiFactory.isControlSlot(chest, slot)) {
            handleContentSlotClick(event, chest);
            return;
        }

        OptionalInt upgradeSlot = GuiFactory.upgradeSlotIndex(chest, slot);
        if (upgradeSlot.isPresent()) {
            event.setCancelled(true);
            handleUpgradeSlotClick(event, holder, chest, upgradeSlot.getAsInt());
            return;
        }

        // The rest of the control row (scroll buttons, indicator, filler) is off-limits either way.
        event.setCancelled(true);
        handleNavClick(event, holder, chest, topInventory);
    }

    private void handleNavClick(InventoryClickEvent event, IcarusChestHolder holder, IcarusChest chest, Inventory topInventory) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Optional<NavAction> action = GuiFactory.navAction(event.getCurrentItem());
        if (action.isEmpty()) {
            return;
        }
        scroll(player, chest, holder, topInventory, action.get());
    }

    private void scroll(Player player, IcarusChest chest, IcarusChestHolder holder, Inventory topInventory, NavAction action) {
        int newOffset = GuiFactory.scrollTarget(chest, holder.getScrollOffset(), action);
        if (newOffset == holder.getScrollOffset()) {
            return; // already at that edge
        }
        GuiFactory.syncVisibleToChest(chest, holder, topInventory);
        GuiFactory.scrollTo(chest, holder, topInventory, newOffset);
    }

    private void handleUpgradeSlotClick(InventoryClickEvent event, IcarusChestHolder holder, IcarusChest chest, int slotIndex) {
        ItemStack[] upgrades = chest.getUpgrades();
        ItemStack installed = upgrades[slotIndex];
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;

        if (installed == null) {
            if (cursorEmpty) {
                return; // nothing to install, nothing to remove
            }
            Optional<UpgradeType> type = UpgradeRegistry.typeOf(cursor);
            if (type.isEmpty()) {
                return; // whatever's on the cursor isn't an upgrade item
            }
            ItemStack toInstall = cursor.clone();
            toInstall.setAmount(1);
            upgrades[slotIndex] = toInstall;

            int remaining = cursor.getAmount() - 1;
            event.setCursor(remaining > 0 ? withAmount(cursor, remaining) : null);
        } else if (cursorEmpty) {
            upgrades[slotIndex] = null;
            event.setCursor(installed);
        } else {
            return; // slot occupied and cursor holds something else: no-op, already cancelled
        }

        persistUpgrades(chest);
        GuiFactory.populate(chest, holder, event.getView().getTopInventory());
    }

    private void persistUpgrades(IcarusChest chest) {
        Map<Integer, String> bySlot = new HashMap<>();
        ItemStack[] upgrades = chest.getUpgrades();
        for (int i = 0; i < upgrades.length; i++) {
            int slotIndex = i;
            UpgradeRegistry.typeOf(upgrades[i]).ifPresent(type -> bySlot.put(slotIndex, type.name()));
        }
        chestRepository.saveUpgrades(chest.getId(), bySlot).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir upgrades do bau " + chest.getId(), ex);
            return null;
        });
    }

    private void handleContentSlotClick(InventoryClickEvent event, IcarusChest chest) {
        if (event.getClick() != ClickType.LEFT) {
            return; // Filter/Stack only cover plain left-click in this first version
        }
        boolean stackUpgraded = UpgradeSlots.has(chest.getUpgrades(), UpgradeType.STACK);
        boolean filtered = UpgradeSlots.has(chest.getUpgrades(), UpgradeType.FILTER);
        if (!stackUpgraded && !filtered) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack slotItem = event.getCurrentItem();
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
        boolean slotEmpty = slotItem == null || slotItem.getType() == Material.AIR;

        if (stackUpgraded && cursorEmpty && !slotEmpty && slotItem.getAmount() > 64) {
            // Withdraw at most 64 at a time, leaving the rest — same as the mod this plugin is inspired by.
            event.setCancelled(true);
            int taking = Math.min(64, slotItem.getAmount());
            int remaining = slotItem.getAmount() - taking;
            event.setCurrentItem(remaining > 0 ? withAmount(slotItem, remaining) : null);
            event.setCursor(withAmount(slotItem, taking));
            return;
        }

        if (cursorEmpty) {
            return; // a plain pickup vanilla can already handle correctly
        }

        if (filtered) {
            Optional<Material> establishedType = firstStoredType(chest);
            if (establishedType.isPresent() && establishedType.get() != cursor.getType()) {
                event.setCancelled(true);
                return;
            }
        }

        if (stackUpgraded && !slotEmpty && slotItem.isSimilar(cursor)) {
            int spaceLeft = STACK_UPGRADE_CAP - slotItem.getAmount();
            if (spaceLeft <= 0) {
                event.setCancelled(true);
                return;
            }
            int normalRoom = slotItem.getMaxStackSize() - slotItem.getAmount();
            if (cursor.getAmount() <= normalRoom) {
                return; // within vanilla's own normal limit already; let default handling proceed
            }
            event.setCancelled(true);
            int toMove = Math.min(spaceLeft, cursor.getAmount());
            event.setCurrentItem(withAmount(slotItem, slotItem.getAmount() + toMove));
            int cursorRemaining = cursor.getAmount() - toMove;
            event.setCursor(cursorRemaining > 0 ? withAmount(cursor, cursorRemaining) : null);
        }
    }

    private Optional<Material> firstStoredType(IcarusChest chest) {
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return Optional.of(item.getType());
            }
        }
        return Optional.empty();
    }

    private static ItemStack withAmount(ItemStack base, int amount) {
        ItemStack copy = base.clone();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * Repurposes hotbar-slot scrolling (mouse wheel, or a direct number-key
     * press) as GUI scrolling while an IcarusChests window is open — Minecraft
     * doesn't expose a scrollbar widget to server-controlled container
     * screens, so this is the closest thing to it. A number-key press lands
     * here too since both fire the same event; a jump of more than one slot
     * isn't a wheel tick, so it's just swallowed rather than guessing a
     * direction.
     */
    @EventHandler(ignoreCancelled = true)
    public void onHotbarScroll(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof IcarusChestHolder holder)) {
            return;
        }
        Optional<IcarusChest> maybeChest = chestManager.get(holder.getChestId());
        if (maybeChest.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        int forwardDelta = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        NavAction action;
        if (forwardDelta == 1) {
            action = NavAction.SCROLL_DOWN;
        } else if (forwardDelta == 8) {
            action = NavAction.SCROLL_UP;
        } else {
            return;
        }

        scroll(player, maybeChest.get(), holder, player.getOpenInventory().getTopInventory(), action);
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
