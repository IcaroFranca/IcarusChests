package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.backpack.BackpackRegistry;
import dev.icaro.icaruschests.tier.BackpackTier;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Builds one {@link RecipeBookEntry} per craftable IcarusChests item — every
 * tier's upgrade kit, every pluggable upgrade, and every backpack recipe
 * (base + tier-ups) — for the in-game recipe book GUI (see {@code
 * RecipeBookGui}/{@code RecipeBookListener}). Entries are rebuilt fresh on
 * every open rather than cached, so the shown icons always match whatever's
 * actually registered (custom heads from {@code config.yml} included) even
 * right after a {@code /icaruschests reload}.
 *
 * <p><b>Convention: every new craftable item this plugin ever gets needs an
 * entry added here too</b> — this is the only place a player can see how to
 * make something without already knowing the recipe by heart, and nothing
 * enumerates Bukkit's own registered recipes automatically (see {@code
 * BackpackRecipeListener}'s recipes, none of which show up here on their
 * own).</p>
 */
public final class RecipeBookRegistry {

    private final UpgradeKitRegistry upgradeKitRegistry;
    private final UpgradeRegistry upgradeRegistry;
    private final BackpackRegistry backpackRegistry;

    public RecipeBookRegistry(UpgradeKitRegistry upgradeKitRegistry, UpgradeRegistry upgradeRegistry, BackpackRegistry backpackRegistry) {
        this.upgradeKitRegistry = upgradeKitRegistry;
        this.upgradeRegistry = upgradeRegistry;
        this.backpackRegistry = backpackRegistry;
    }

    /** Every known recipe: tier kits, then upgrades, then backpacks (base tier first, then every tier-up in order). */
    public List<RecipeBookEntry> buildAll() {
        List<RecipeBookEntry> entries = new ArrayList<>();
        for (ChestTier tier : ChestTier.values()) {
            tier.upgradeMaterial().ifPresent(material -> entries.add(tierKitEntry(tier, material)));
        }
        for (UpgradeType type : UpgradeType.values()) {
            entries.add(upgradeEntry(type));
        }
        entries.add(backpackBaseEntry());
        for (BackpackTier tier : BackpackTier.values()) {
            tier.upgradeMaterial().ifPresent(material -> entries.add(backpackTierUpEntry(tier, material)));
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

    /** 8 Leather + 1 Chest → the base ({@link BackpackTier#LEATHER}) backpack — see {@code BackpackRecipeListener}'s base recipe. */
    private RecipeBookEntry backpackBaseEntry() {
        Map<Integer, ItemStack> grid = shapedGrid(new ItemStack(Material.CHEST), new ItemStack(Material.LEATHER));
        Component title = Component.text("Mochila: " + BackpackTier.LEATHER.displayName(), NamedTextColor.LIGHT_PURPLE);
        return new RecipeBookEntry(title, grid, backpackRegistry.createBackpack(BackpackTier.LEATHER, UUID.randomUUID()));
    }

    /** The previous-tier backpack + 8 ore → {@code tier}'s backpack — see {@code BackpackRecipeListener}'s tier-up recipes. */
    private RecipeBookEntry backpackTierUpEntry(BackpackTier tier, Material ore) {
        Optional<BackpackTier> previous = BackpackTier.byOrdinal(tier.ordinal() - 1);
        ItemStack previousSample = backpackRegistry.createBackpack(previous.orElse(BackpackTier.LEATHER), UUID.randomUUID());
        Map<Integer, ItemStack> grid = shapedGrid(previousSample, new ItemStack(ore));
        Component title = Component.text("Mochila: " + tier.displayName(), NamedTextColor.LIGHT_PURPLE);
        return new RecipeBookEntry(title, grid, backpackRegistry.createBackpack(tier, UUID.randomUUID()));
    }

    /** The "MMM"/"MCM"/"MMM" shape every tier kit and every backpack recipe shares: {@code center} surrounded by 8 {@code surrounding}. */
    private Map<Integer, ItemStack> shapedGrid(ItemStack center, ItemStack surrounding) {
        Map<Integer, ItemStack> grid = new LinkedHashMap<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                grid.put(row * 3 + col, (row == 1 && col == 1) ? center : surrounding);
            }
        }
        return grid;
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
