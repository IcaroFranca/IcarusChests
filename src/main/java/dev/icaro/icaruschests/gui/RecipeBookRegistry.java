package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.backpack.BackpackRegistry;
import dev.icaro.icaruschests.tier.BackpackTier;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.upgrade.UpgradeKitRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
 * RecipeBookIndexGui}/{@code RecipeBookDetailGui}/{@code RecipeBookListener}).
 * Entries are rebuilt fresh on every open rather than cached, so the shown
 * icons always match whatever's actually registered (custom heads from
 * {@code config.yml} included) even right after a {@code /icaruschests reload}.
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
        entries.add(backpackRecolorEntry());
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

    /**
     * A backpack + any single dye → the same backpack recolored (see {@code
     * BackpackRecipeListener}'s shapeless recolor recipe) — shown with red as the representative
     * example since the recipe book has no way to illustrate "any of 16 colors" at once. Only
     * cosmetic on a plain Bundle-based backpack; falls back to a plain icon in the result slot if
     * this server's Bukkit API predates colored bundles (same reasoning as {@code
     * BackpackRecipeListener#bundleMaterialFor}).
     */
    private RecipeBookEntry backpackRecolorEntry() {
        ItemStack sample = backpackRegistry.createBackpack(BackpackTier.LEATHER, UUID.randomUUID());
        Map<Integer, ItemStack> grid = new LinkedHashMap<>();
        grid.put(0, sample);
        grid.put(1, new ItemStack(Material.RED_DYE));
        ItemStack result = sample.clone();
        try {
            result.setType(Material.valueOf("RED_BUNDLE"));
        } catch (IllegalArgumentException ignored) {
            // predates colored bundles: the plain backpack icon in the result slot still communicates the recipe
        }
        Component title = Component.text("Mochila: Colorir (qualquer corante)", NamedTextColor.LIGHT_PURPLE);
        return new RecipeBookEntry(title, grid, result);
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
}
