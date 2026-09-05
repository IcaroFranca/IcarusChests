package dev.icaro.icaruschests.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Builds the 9-slot picker a player sees after right-clicking the air while
 * holding a Filter upgrade item: click a slot while holding an item to drop
 * a one-item ghost copy of its type there (the real item never leaves the
 * player's hand — see {@code FilterConfigListener}), close the window, done.
 */
public final class FilterConfigGui {

    public static final int SIZE = 9;

    private FilterConfigGui() {
    }

    public static Inventory build(int sourceHotbarSlot, List<Material> currentMaterials) {
        FilterConfigHolder holder = new FilterConfigHolder(sourceHotbarSlot);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("Filtro: itens aceitos", NamedTextColor.GREEN));
        holder.setInventory(inventory);
        for (int i = 0; i < currentMaterials.size() && i < SIZE; i++) {
            inventory.setItem(i, new ItemStack(currentMaterials.get(i)));
        }
        return inventory;
    }
}
