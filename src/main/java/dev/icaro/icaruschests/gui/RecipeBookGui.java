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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Renders one page of the read-only recipe book: a 3x3 crafting grid (see
 * {@link #GRID_SLOTS}, row-major), an arrow pointing at the item it
 * produces, and a bottom row with previous/next-recipe buttons and a page
 * indicator — one recipe per page. See {@code RecipeBookRegistry} for the
 * page content and {@code RecipeBookListener} for input handling; every
 * slot here is purely cosmetic; nothing can be taken, placed, or moved.
 */
public final class RecipeBookGui {

    public static final int SIZE = 54;
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW_SLOT = 22;
    private static final int RESULT_SLOT = 24;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_INDICATOR_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private RecipeBookGui() {
    }

    public static Inventory open(List<RecipeBookEntry> entries, int page) {
        RecipeBookHolder holder = new RecipeBookHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title(entries.size()));
        holder.setInventory(inventory);
        holder.setPage(page);
        populate(inventory, entries, page);
        return inventory;
    }

    /** Redraws {@code inventory} for {@code page} in place — used both on open and after a nav click. */
    public static void populate(Inventory inventory, List<RecipeBookEntry> entries, int page) {
        inventory.clear();
        RecipeBookEntry entry = entries.get(page);

        for (Map.Entry<Integer, ItemStack> cell : entry.grid().entrySet()) {
            inventory.setItem(GRID_SLOTS[cell.getKey()], cell.getValue());
        }
        inventory.setItem(ARROW_SLOT, arrowIcon());
        inventory.setItem(RESULT_SLOT, entry.result());

        for (int slot = PREVIOUS_SLOT; slot < SIZE; slot++) {
            inventory.setItem(slot, filler());
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, navItem("« Receita anterior", "prev"));
        }
        if (page < entries.size() - 1) {
            inventory.setItem(NEXT_SLOT, navItem("Próxima receita »", "next"));
        }
        inventory.setItem(PAGE_INDICATOR_SLOT, pageIndicator(entry.title(), page, entries.size()));
    }

    private static Component title(int totalEntries) {
        return Component.text("Livro de Receitas (" + totalEntries + " receitas)", NamedTextColor.GOLD);
    }

    private static ItemStack arrowIcon() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navItem(String name, String direction) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(NamespacedKeys.RECIPE_NAV, PersistentDataType.STRING, direction);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pageIndicator(Component recipeTitle, int page, int total) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(recipeTitle.decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Receita " + (page + 1) + " de " + total, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
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

    /** The direction a clicked nav item requests ({@code "prev"}/{@code "next"}), if it is one. */
    public static Optional<String> navDirection(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.RECIPE_NAV, PersistentDataType.STRING));
    }
}
