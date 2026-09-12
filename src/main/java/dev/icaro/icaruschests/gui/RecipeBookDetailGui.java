package dev.icaro.icaruschests.gui;

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

import java.util.Map;

/**
 * Renders a single recipe: a 3x3 crafting grid (see {@link #GRID_SLOTS}, row-major), an arrow
 * pointing at the item it produces, and a "« Voltar" button back to {@code RecipeBookIndexGui} —
 * reached by clicking an icon there (see {@code RecipeBookListener}). Every slot here is purely
 * cosmetic; nothing can be taken, placed, or moved.
 */
public final class RecipeBookDetailGui {

    public static final int SIZE = 54;
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW_SLOT = 22;
    private static final int RESULT_SLOT = 24;
    private static final int BACK_SLOT = 49;

    private RecipeBookDetailGui() {
    }

    public static Inventory open(RecipeBookEntry entry) {
        RecipeBookDetailHolder holder = new RecipeBookDetailHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, entry.title());
        holder.setInventory(inventory);

        for (Map.Entry<Integer, ItemStack> cell : entry.grid().entrySet()) {
            inventory.setItem(GRID_SLOTS[cell.getKey()], cell.getValue());
        }
        inventory.setItem(ARROW_SLOT, arrowIcon());
        inventory.setItem(RESULT_SLOT, entry.result());

        for (int slot = 45; slot < SIZE; slot++) {
            inventory.setItem(slot, filler());
        }
        inventory.setItem(BACK_SLOT, backButton());
        return inventory;
    }

    private static ItemStack arrowIcon() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("« Voltar ao indice", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(NamespacedKeys.RECIPE_NAV, PersistentDataType.STRING, "back");
        item.setItemMeta(meta);
        return item;
    }

    /** Neutral, non-interactive spacer filling the rest of the bottom row. */
    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} is this screen's "back to index" button. */
    public static boolean isBackButton(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return "back".equals(item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.RECIPE_NAV, PersistentDataType.STRING));
    }
}
