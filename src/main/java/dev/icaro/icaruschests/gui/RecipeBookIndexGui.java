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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renders every known recipe as a single grid of result icons, one click away from its full
 * crafting grid (see {@code RecipeBookDetailGui}/{@code RecipeBookListener}) — opened directly by
 * {@code /icaruschests recipebook} (see {@code IcarusChestsCommand}), replacing the old
 * one-item-then-flip-through-pages book entirely: there's no physical item anymore, and every
 * recipe is visible at once instead of one per page.
 *
 * <p>Each icon keeps the result item's own real name/lore (a kit already reads "Kit de Upgrade:
 * Cobre" with its own usage hint) and just appends a click hint — cloned, never the live registry
 * item, so clicking never risks mutating anything real.
 *
 * <p>Sized dynamically to the smallest 1-6 row inventory that fits every entry (current counts —
 * chest tier kits, pluggable upgrades, backpack tiers, the recolor recipe — sit well under 54);
 * entries beyond the 54th are silently not shown. Revisit with real pagination (a "next page of the
 * index" control) if the recipe count ever gets anywhere near that.
 */
public final class RecipeBookIndexGui {

    private RecipeBookIndexGui() {
    }

    public static Inventory open(List<RecipeBookEntry> entries) {
        RecipeBookIndexHolder holder = new RecipeBookIndexHolder();
        Inventory inventory = Bukkit.createInventory(holder, sizeFor(entries.size()), title(entries.size()));
        holder.setInventory(inventory);
        for (int i = 0; i < entries.size() && i < inventory.getSize(); i++) {
            inventory.setItem(i, icon(entries.get(i), i));
        }
        return inventory;
    }

    private static ItemStack icon(RecipeBookEntry entry, int index) {
        ItemStack item = entry.result().clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.text("Clique para ver a receita.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(NamespacedKeys.RECIPE_ENTRY_INDEX, PersistentDataType.INTEGER, index);
        item.setItemMeta(meta);
        return item;
    }

    private static int sizeFor(int entryCount) {
        int rows = Math.max(1, Math.min(6, (entryCount + 8) / 9));
        return rows * 9;
    }

    private static Component title(int totalEntries) {
        return Component.text("Livro de Receitas (" + totalEntries + " receitas)", NamedTextColor.GOLD);
    }

    /** The entry index a clicked icon represents, if it's one of this GUI's own icons at all. */
    public static Optional<Integer> entryIndexOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.RECIPE_ENTRY_INDEX, PersistentDataType.INTEGER));
    }
}
