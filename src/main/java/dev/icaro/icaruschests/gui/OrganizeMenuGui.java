package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The small, single-row menu opened by a chest GUI's Organize button: one
 * icon per {@link SortType}, plus a cancel button. See {@code
 * ChestOrganizeListener} for what clicking a sort icon actually does — this
 * class only builds the menu's layout.
 */
public final class OrganizeMenuGui {

    private static final int SIZE = 9;
    /** Columns hosting each {@link SortType}, in enum order. */
    private static final int[] SORT_COLUMNS = {2, 4, 6};
    private static final int CANCEL_COLUMN = 8;

    private OrganizeMenuGui() {
    }

    public static Inventory open(IcarusChest chest) {
        OrganizeMenuHolder holder = new OrganizeMenuHolder(chest.getId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Organizar Baú", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        SortType[] types = SortType.values();
        for (int i = 0; i < types.length && i < SORT_COLUMNS.length; i++) {
            inventory.setItem(SORT_COLUMNS[i], sortItem(types[i]));
        }
        inventory.setItem(CANCEL_COLUMN, cancelItem());
        return inventory;
    }

    private static ItemStack sortItem(SortType type) {
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(type.displayName().color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Clique para organizar o baú inteiro,", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("incluindo slots fora de tela e stacks maiores.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(NamespacedKeys.SORT_TYPE, PersistentDataType.STRING, type.key());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack cancelItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cancelar", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
