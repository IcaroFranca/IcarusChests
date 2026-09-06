package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiFactory;
import dev.icaro.icaruschests.gui.IcarusChestHolder;
import dev.icaro.icaruschests.gui.NavAction;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.persistence.PersistedUpgrade;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeSlots;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles clicks, drags and closes on IcarusChests GUIs:
 * <ul>
 *   <li>the scroll buttons sync the visible slots back into the chest before
 *       redrawing the same inventory at the new offset, no close/reopen, so
 *       no flicker (see {@link #onHotbarScroll} for why mouse-wheel scrolling
 *       — the other obvious way to do this — isn't a real alternative);</li>
 *   <li>the chest's upgrade slots accept dragging a matching upgrade item in
 *       (installs it) or clicking with an empty cursor (removes it back to
 *       the cursor) — removing an installed Stack upgrade is refused (with a
 *       message naming what's blocking it) if some stored stack would end up
 *       over the item's normal limit without it;</li>
 *   <li>a chest with a Filter upgrade installed only accepts item types on
 *       that Filter's own configured list (see {@code FilterConfigListener}
 *       — an unconfigured Filter accepts anything), and one with a Stack
 *       upgrade lets an existing stack keep growing past the item's normal
 *       limit, up to that tier's multiplier (see {@code UpgradeType}) — both
 *       cover left/right clicks and shift-clicks from the player's own
 *       inventory, and dragging is at least blocked by the Filter; a slot
 *       whose true amount already exceeds its normal max stack only ever
 *       accepts a left/right click that either withdraws from it (empty
 *       cursor) or tops it off with more of the *same* item — any other
 *       interaction (number-key swap, double-click collect, drop, swap to
 *       offhand, or a left/right-click holding a *different* item) is
 *       refused outright, so none of them can desync the client's view of it
 *       from what the chest actually holds (see {@link
 *       #handleContentSlotClick}).</li>
 *   <li>a slot holding more than an item's normal max stack (a Stack
 *       upgrade) is never handed to the client at its real amount — see
 *       {@code GuiFactory#populate}/{@code #displayItemFor}, which caps what
 *       the player actually sees at that normal max and spells out the true
 *       count in a lore line instead, since Minecraft's item format won't
 *       let a real stack claim a size above 99 (the {@code
 *       minecraft:max_stack_size} data component, since the 1.20.5 item
 *       rewrite) — {@code chest.getContents()} keeps the real number.</li>
 * </ul>
 */
public final class ChestGuiListener implements Listener {

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

        Optional<IcarusChest> maybeChest = chestManager.get(holder.getChestId());
        if (maybeChest.isEmpty()) {
            return;
        }
        IcarusChest chest = maybeChest.get();

        if (event.getClickedInventory() != topInventory) {
            // A click in the player's OWN inventory — the only one of these that can put a new
            // item into the chest is a shift-click, so that's the only one Filter/Stack need to
            // watch for here; anything else (a plain click reordering the player's own items)
            // never touches the chest at all.
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                handleShiftDeposit(event, holder, chest);
            }
            return;
        }

        int slot = event.getSlot();

        if (!GuiFactory.isControlSlot(chest, slot)) {
            handleContentSlotClick(event, holder, chest);
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

    /**
     * A drag can smear a held stack across several content slots at once — vanilla's own math
     * already keeps each slot within the item's normal limit (a drag can't reach a Stack
     * upgrade's higher cap; that only happens via the single-slot paths above), so the only thing
     * left to enforce here is the Filter: cancel the whole drag if it touches the chest's content
     * area with a disallowed item type.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof IcarusChestHolder holder)) {
            return;
        }
        Optional<IcarusChest> maybeChest = chestManager.get(holder.getChestId());
        if (maybeChest.isEmpty()) {
            return;
        }
        IcarusChest chest = maybeChest.get();

        boolean touchesContent = event.getRawSlots().stream()
                .anyMatch(rawSlot -> rawSlot < topInventory.getSize() && !GuiFactory.isControlSlot(chest, rawSlot));
        if (!touchesContent) {
            return;
        }

        UpgradeSlots.filterItem(chest.getUpgrades()).ifPresent(filterItem -> {
            List<Material> accepted = UpgradeRegistry.filterMaterials(filterItem);
            if (!accepted.isEmpty() && !accepted.contains(event.getOldCursor().getType())) {
                event.setCancelled(true);
            }
        });
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
            Optional<UpgradeType> installedType = UpgradeRegistry.typeOf(installed);
            if (installedType.isPresent() && installedType.get().isStackUpgrade()) {
                double multiplierWithoutThis = UpgradeSlots.bestStackMultiplierExcluding(upgrades, slotIndex);
                List<ItemStack> blocking = itemsOverCap(chest, multiplierWithoutThis);
                if (!blocking.isEmpty()) {
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage(blockingRemovalMessage(blocking));
                    }
                    return; // refuse the removal; nothing changes
                }
            }
            upgrades[slotIndex] = null;
            event.setCursor(installed);
        } else {
            return; // slot occupied and cursor holds something else: no-op, already cancelled
        }

        persistUpgrades(chest);
        GuiFactory.populate(chest, holder, event.getView().getTopInventory());
    }

    /** Stored items that would exceed their own normal stack limit under {@code multiplier} — used to guard removing a Stack upgrade. */
    private List<ItemStack> itemsOverCap(IcarusChest chest, double multiplier) {
        List<ItemStack> blocking = new ArrayList<>();
        for (ItemStack item : chest.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            int cap = (int) Math.floor(item.getMaxStackSize() * multiplier);
            if (item.getAmount() > cap) {
                blocking.add(item);
            }
        }
        return blocking;
    }

    private Component blockingRemovalMessage(List<ItemStack> blocking) {
        String itemList = blocking.stream()
                .map(item -> item.getAmount() + "x " + UpgradeRegistry.prettyName(item.getType()))
                .collect(Collectors.joining(", "));
        return Component.text("Não é possível remover: sem esse upgrade, isso passaria do limite normal: " + itemList,
                NamedTextColor.RED);
    }

    private void persistUpgrades(IcarusChest chest) {
        Map<Integer, PersistedUpgrade> bySlot = new HashMap<>();
        ItemStack[] upgrades = chest.getUpgrades();
        for (int i = 0; i < upgrades.length; i++) {
            int slotIndex = i;
            UpgradeRegistry.typeOf(upgrades[i]).ifPresent(type -> {
                String dataJson = type == UpgradeType.FILTER
                        ? UpgradeRegistry.encodeFilterMaterials(UpgradeRegistry.filterMaterials(upgrades[slotIndex]))
                        : null;
                bySlot.put(slotIndex, new PersistedUpgrade(type.name(), dataJson));
            });
        }
        chestRepository.saveUpgrades(chest.getId(), bySlot).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir upgrades do bau " + chest.getId(), ex);
            return null;
        });
    }

    /**
     * Deliberately never touches {@code event.setCurrentItem()} for the slot itself — it mutates
     * {@code chest.getContents()} (the authoritative array) directly and redraws the whole
     * visible window from it via {@link GuiFactory#populate}, exactly like scrolling and
     * installing/removing an upgrade already do. That's what actually gets an amount past 64 to
     * reliably show up: patching the click's own slot through the event was found to be lossy —
     * whatever the live view showed at the next scroll/close, right or stale, is what {@code
     * syncVisibleToChest} would persist, so the two could disagree on what the slot really held.
     */
    private void handleContentSlotClick(InventoryClickEvent event, IcarusChestHolder holder, IcarusChest chest) {
        int globalIndex = holder.getScrollOffset() + event.getSlot();
        ItemStack[] contents = chest.getContents();
        ItemStack slotItem = contents[globalIndex];
        boolean overstacked = slotItem != null && slotItem.getType() != Material.AIR
                && slotItem.getAmount() > slotItem.getMaxStackSize();

        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            if (overstacked) {
                // Every other interaction (number-key swap, double-click collect, drop, swap to
                // offhand, …) assumes a slot's amount never exceeds its normal max stack — letting
                // one through here risks the client's own idea of this slot and the authoritative
                // array disagreeing about what a Stack-upgraded stack really holds. Left/right-click
                // (below) are the only supported way to touch one.
                event.setCancelled(true);
            }
            return; // Filter/Stack cover left/right clicks and shift-clicks (see handleShiftDeposit); drag is handled separately
        }
        double stackMultiplier = UpgradeSlots.bestStackMultiplier(chest.getUpgrades());
        boolean stackUpgraded = stackMultiplier > 1.0;
        Optional<ItemStack> filterItem = UpgradeSlots.filterItem(chest.getUpgrades());
        if (!stackUpgraded && filterItem.isEmpty()) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
        boolean slotEmpty = slotItem == null || slotItem.getType() == Material.AIR;

        if (stackUpgraded && cursorEmpty && !slotEmpty && overstacked) {
            // Withdraw at most a normal stack at a time, leaving the rest — same as the mod this
            // plugin is inspired by. Right-click's own "take half" still can't exceed that either.
            event.setCancelled(true);
            int taking = click == ClickType.LEFT
                    ? Math.min(slotItem.getMaxStackSize(), slotItem.getAmount())
                    : Math.min(slotItem.getMaxStackSize(), (slotItem.getAmount() + 1) / 2);
            int remaining = slotItem.getAmount() - taking;
            contents[globalIndex] = remaining > 0 ? withAmount(slotItem, remaining) : null;
            event.setCursor(withAmount(slotItem, taking));
            chest.setDirty(true);
            GuiFactory.populate(chest, holder, topInventory);
            return;
        }

        if (cursorEmpty) {
            return; // a plain pickup vanilla can already handle correctly
        }

        if (overstacked && !slotItem.isSimilar(cursor)) {
            // The slot holds more than a normal stack of a *different* item than what's on the
            // cursor. Vanilla's default left/right-click here would swap the cursor and the slot —
            // but that swap only ever touches the live view, never chest.getContents(); the real,
            // overstacked item would stay stranded in the authoritative array and come right back
            // on the next populate(), while the player also walks away with what they just
            // "swapped" in — a duplicate. Withdraw the whole overstacked stack first (see above),
            // then place the new item once the slot is actually empty.
            event.setCancelled(true);
            return;
        }

        if (filterItem.isPresent()) {
            List<Material> accepted = UpgradeRegistry.filterMaterials(filterItem.get());
            if (!accepted.isEmpty() && !accepted.contains(cursor.getType())) {
                event.setCancelled(true);
                return;
            }
        }

        if (stackUpgraded && !slotEmpty && slotItem.isSimilar(cursor)) {
            int cap = (int) Math.floor(slotItem.getMaxStackSize() * stackMultiplier);
            int spaceLeft = cap - slotItem.getAmount();
            if (spaceLeft <= 0) {
                event.setCancelled(true);
                return;
            }
            int normalRoom = slotItem.getMaxStackSize() - slotItem.getAmount();
            int depositAmount = click == ClickType.LEFT ? cursor.getAmount() : 1; // right-click deposits exactly 1
            if (depositAmount <= normalRoom) {
                return; // within vanilla's own normal limit already; let default handling proceed
            }
            event.setCancelled(true);
            int toMove = Math.min(spaceLeft, depositAmount);
            contents[globalIndex] = withAmount(slotItem, slotItem.getAmount() + toMove);
            int cursorRemaining = cursor.getAmount() - toMove;
            event.setCursor(cursorRemaining > 0 ? withAmount(cursor, cursorRemaining) : null);
            chest.setDirty(true);
            GuiFactory.populate(chest, holder, topInventory);
        }
    }

    /**
     * A shift-click from the player's own inventory hands the whole clicked stack to vanilla's
     * own "find a slot for this" logic, which knows nothing about the Filter or a Stack upgrade's
     * higher cap — so when either is active, this takes over that distribution by hand instead.
     */
    private void handleShiftDeposit(InventoryClickEvent event, IcarusChestHolder holder, IcarusChest chest) {
        double stackMultiplier = UpgradeSlots.bestStackMultiplier(chest.getUpgrades());
        boolean stackUpgraded = stackMultiplier > 1.0;
        Optional<ItemStack> filterItem = UpgradeSlots.filterItem(chest.getUpgrades());
        if (!stackUpgraded && filterItem.isEmpty()) {
            return; // no special rule active; vanilla's own shift-transfer is already correct
        }

        ItemStack shifted = event.getCurrentItem();
        if (shifted == null || shifted.getType() == Material.AIR) {
            return;
        }

        if (filterItem.isPresent()) {
            List<Material> accepted = UpgradeRegistry.filterMaterials(filterItem.get());
            if (!accepted.isEmpty() && !accepted.contains(shifted.getType())) {
                event.setCancelled(true); // wrong item type: block the shift-click entirely
                return;
            }
        }

        if (!stackUpgraded) {
            return; // filter allows it and there's no higher cap to respect: vanilla's transfer is fine
        }

        event.setCancelled(true);
        ItemStack[] contents = chest.getContents();
        int remaining = shifted.getAmount();

        // First pass: top off any existing matching stack, up to the upgraded cap.
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack existing = contents[i];
            if (existing != null && existing.isSimilar(shifted)) {
                int cap = (int) Math.floor(existing.getMaxStackSize() * stackMultiplier);
                int space = cap - existing.getAmount();
                if (space > 0) {
                    int toMove = Math.min(space, remaining);
                    existing.setAmount(existing.getAmount() + toMove);
                    remaining -= toMove;
                }
            }
        }
        // Second pass: fill empty slots, each up to the upgraded cap.
        int emptySlotCap = (int) Math.floor(shifted.getMaxStackSize() * stackMultiplier);
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null) {
                int toMove = Math.min(emptySlotCap, remaining);
                contents[i] = withAmount(shifted, toMove);
                remaining -= toMove;
            }
        }

        int moved = shifted.getAmount() - remaining;
        if (moved <= 0) {
            return; // chest is entirely full even at the upgraded cap; nothing moved
        }
        chest.setDirty(true);
        event.setCurrentItem(remaining > 0 ? withAmount(shifted, remaining) : null);
        GuiFactory.populate(chest, holder, event.getView().getTopInventory());
    }

    private static ItemStack withAmount(ItemStack base, int amount) {
        ItemStack copy = base.clone();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * Attempts to repurpose hotbar-slot scrolling (mouse wheel, or a direct
     * number-key press) as GUI scrolling while an IcarusChests window is
     * open, since Minecraft doesn't expose a real scrollbar widget to
     * server-controlled container screens. Confirmed (in-game, by the user)
     * to be a dead end on a standard client: scrolling the mouse wheel while
     * any container screen is open doesn't change the selected hotbar slot
     * at all — the client itself never sends the slot-change packet this
     * event depends on, so no server-side plugin can detect that scroll no
     * matter what. Left in (harmless, just unreachable) in case some client
     * setup does still send it; the clickable arrows in the control row are
     * the one navigation path guaranteed to work.
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
