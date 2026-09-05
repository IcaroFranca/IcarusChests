package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.upgrade.UpgradeKitRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one {@link RecipeBookEntry} per craftable IcarusChests item — every
 * tier's upgrade kit, then every pluggable upgrade — for the in-game recipe
 * book GUI (see {@code RecipeBookGui}/{@code RecipeBookListener}). Entries
 * are rebuilt fresh on every open rather than cached, so the shown icons
 * always match whatever's actually registered (custom heads from {@code
 * config.yml} included) even right after a {@code /icaruschests reload}.
 */
public final class RecipeBookRegistry {

    private final UpgradeKitRegistry upgradeKitRegistry;
    private final UpgradeRegistry upgradeRegistry;

    public RecipeBookRegistry(UpgradeKitRegistry upgradeKitRegistry, UpgradeRegistry upgradeRegistry) {
        this.upgradeKitRegistry = upgradeKitRegistry;
        this.upgradeRegistry = upgradeRegistry;
    }

    /** Every known recipe, tier kits first (in tier order) then upgrades (in {@code UpgradeType} order). */
    public List<RecipeBookEntry> buildAll() {
        List<RecipeBookEntry> entries = new ArrayList<>();
        for (ChestTier tier : ChestTier.values()) {
            tier.upgradeMaterial().ifPresent(material -> entries.add(tierKitEntry(tier, material)));
        }
        for (UpgradeType type : UpgradeType.values()) {
            entries.add(upgradeEntry(type));
        }
        return entries;
    }

    private RecipeBookEntry tierKitEntry(ChestTier tier, Material material) {
        String[] shape = tier.recipeShape();
        Map<Integer, ItemStack> grid = new LinkedHashMap<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack item = switch (shape[row].charAt(col)) {
                    case 'C' -> new ItemStack(Material.CHEST);
                    case 'M' -> new ItemStack(material);
                    default -> null;
                };
                if (item != null) {
                    grid.put(row * 3 + col, item);
                }
            }
        }
        Component title = Component.text("Kit de Upgrade: " + tier.displayName(), NamedTextColor.LIGHT_PURPLE);
        return new RecipeBookEntry(title, grid, upgradeKitRegistry.createKit(tier));
    }

    private RecipeBookEntry upgradeEntry(UpgradeType type) {
        List<ItemStack> ingredients = upgradeRegistry.recipeIngredientItems(type);
        Map<Integer, ItemStack> grid = new LinkedHashMap<>();
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            grid.put(i, ingredients.get(i));
        }
        Component title = Component.text("Upgrade: " + type.displayName(), NamedTextColor.LIGHT_PURPLE);
        return new RecipeBookEntry(title, grid, upgradeRegistry.createItem(type));
    }

    /** The physical item players right-click to open the recipe book GUI (see {@code RecipeBookListener}). */
    public static ItemStack createBookItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Livro de Receitas", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Clique com o botao direito", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("para ver todas as receitas.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(NamespacedKeys.RECIPE_BOOK, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} is the recipe book item (see {@link #createBookItem()}). */
    public static boolean isBookItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(NamespacedKeys.RECIPE_BOOK, PersistentDataType.BYTE);
    }
}
