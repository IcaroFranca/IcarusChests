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
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * installed on. The GUI is only a type-picker, not real storage: whatever
 * was placed there is handed straight back to the player when it closes.
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
                returnItem(player, item);
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

    private void returnItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
}
