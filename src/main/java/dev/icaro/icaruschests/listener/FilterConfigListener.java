package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.gui.FilterConfigGui;
import dev.icaro.icaruschests.gui.FilterConfigHolder;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets a player configure which item types a Filter upgrade item accepts by
 * right-clicking the air while holding it: opens a 9-slot picker GUI (see
 * {@code FilterConfigGui}), and on close saves the distinct item types
 * placed there into the item's own PDC (see {@code UpgradeRegistry}) — so
 * the configuration travels with the item into whichever chest it's later
 * installed on.
 *
 * <p>The picker never touches the player's real inventory: clicking a slot
 * with an item on the cursor (or shift-clicking an item from the player's
 * own inventory below) stamps a one-item "ghost" copy of that Material into
 * a slot — the cursor and the player's real inventory are never touched, so
 * nothing is ever actually taken. Clicking an occupied slot with an empty
 * cursor just erases the ghost there — nothing is given back, since nothing
 * was ever really moved in the first place. Each Material can only occupy
 * one slot at a time (a duplicate placement attempt is silently ignored) —
 * this is a type picker, not storage.
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
        player.openInventory(FilterConfigGui.build(sourceSlot, current));
    }

    @EventHandler
    public void onFilterConfigClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FilterConfigHolder)) {
            return;
        }
        Inventory gui = event.getInventory();

        if (event.getClickedInventory() != gui) {
            // A click in the player's own inventory below: only a shift-click hands us an item to
            // represent — anything else (reordering their own stuff) doesn't touch the picker.
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                event.setCancelled(true);
                ItemStack shifted = event.getCurrentItem();
                if (shifted != null && shifted.getType() != Material.AIR) {
                    addGhostToFirstEmptySlot(gui, shifted.getType());
                }
            }
            return;
        }

        event.setCancelled(true);
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
        ItemStack current = event.getCurrentItem();
        boolean slotEmpty = current == null || current.getType() == Material.AIR;

        if (!cursorEmpty) {
            addGhostAt(gui, event.getSlot(), cursor.getType());
        } else if (!slotEmpty) {
            gui.setItem(event.getSlot(), null);
        }
    }

    @EventHandler
    public void onFilterConfigDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FilterConfigHolder) {
            event.setCancelled(true); // ghosts are placed one click at a time; no drag-spreading
        }
    }

    /** Stamps a ghost of {@code material} into {@code slot}, unless that Material already occupies a different slot. */
    private void addGhostAt(Inventory gui, int slot, Material material) {
        if (isMaterialElsewhere(gui, material, slot)) {
            return;
        }
        gui.setItem(slot, new ItemStack(material));
    }

    /** Stamps a ghost of {@code material} into the first empty slot, unless that Material is already represented or the picker is full. */
    private void addGhostToFirstEmptySlot(Inventory gui, Material material) {
        if (isMaterialElsewhere(gui, material, -1)) {
            return;
        }
        for (int i = 0; i < FilterConfigGui.SIZE; i++) {
            ItemStack item = gui.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                gui.setItem(i, new ItemStack(material));
                return;
            }
        }
    }

    private boolean isMaterialElsewhere(Inventory gui, Material material, int excludedSlot) {
        for (int i = 0; i < FilterConfigGui.SIZE; i++) {
            if (i == excludedSlot) {
                continue;
            }
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onCloseFilterConfig(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof FilterConfigHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory gui = event.getInventory();
        Set<Material> chosen = new LinkedHashSet<>();
        for (int i = 0; i < FilterConfigGui.SIZE; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                chosen.add(item.getType());
            }
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack target = inventory.getItem(holder.getSourceHotbarSlot());
        boolean stillHoldingFilter = UpgradeRegistry.typeOf(target).filter(type -> type == UpgradeType.FILTER).isPresent();
        if (!stillHoldingFilter) {
            return; // player swapped/dropped the item mid-config; nothing sensible to save it onto
        }

        UpgradeRegistry.setFilterMaterials(target, new ArrayList<>(chosen));
        inventory.setItem(holder.getSourceHotbarSlot(), target);
        player.sendMessage(Component.text(
                chosen.isEmpty()
                        ? "Filtro configurado para aceitar qualquer item."
                        : "Filtro configurado: " + chosen.size() + " tipo(s) de item aceito(s).",
                NamedTextColor.GREEN));
    }
}
