package dev.icaro.icaruschests.upgrade;

import dev.icaro.icaruschests.config.ConfigManager;
import dev.icaro.icaruschests.util.CustomHeads;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the item and crafting recipe for each {@link UpgradeType}. An
 * upgrade item is tagged via PDC with its type — installing/removing it from
 * a chest's dedicated upgrade slots (see {@code GuiFactory}/{@code
 * ChestGuiListener}) just moves this same item in and out of that slot.
 *
 * <p>A Filter item additionally carries its own configured accepted-item
 * list in PDC (see {@link #filterMaterials(ItemStack)}/{@link
 * #setFilterMaterials(ItemStack, List)}) — set by right-clicking the air
 * while holding it (see {@code FilterConfigListener}), so the configuration
 * travels with the item into whichever chest it ends up installed on, and
 * survives a restart via {@code chest_upgrade.data_json}.
 */
public final class UpgradeRegistry {

    private final Plugin plugin;
    private final ConfigManager configManager;

    public UpgradeRegistry(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void registerRecipes() {
        for (UpgradeType type : UpgradeType.values()) {
            registerRecipe(type);
        }
    }

    private void registerRecipe(UpgradeType type) {
        NamespacedKey key = new NamespacedKey(plugin, type.key());
        ShapelessRecipe recipe = new ShapelessRecipe(key, createItem(type));
        // Stack upgrades fill the whole 3x3 grid: 1 hopper + 2 ore blocks + 6 raw
        // ingots/gems of that same tier, so the cost stands apart from a tier kit's.
        switch (type) {
            case FILTER -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(3, Material.PAPER);
            }
            case STACK_COPPER -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(2, Material.COPPER_BLOCK);
                recipe.addIngredient(6, Material.COPPER_INGOT);
            }
            case STACK_IRON -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(2, Material.IRON_BLOCK);
                recipe.addIngredient(6, Material.IRON_INGOT);
            }
            case STACK_GOLD -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(2, Material.GOLD_BLOCK);
                recipe.addIngredient(6, Material.GOLD_INGOT);
            }
            case STACK_DIAMOND -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(2, Material.DIAMOND_BLOCK);
                recipe.addIngredient(6, Material.DIAMOND);
            }
            case STACK_NETHERITE -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(2, Material.NETHERITE_BLOCK);
                recipe.addIngredient(6, Material.NETHERITE_INGOT);
            }
        }
        plugin.getServer().addRecipe(recipe);
    }

    /** Builds a fresh item for {@code type} (a Filter starts unconfigured, accepting anything). Does not register a recipe. */
    public ItemStack createItem(UpgradeType type) {
        Optional<String> headTexture = configManager.upgradeHeadTexture(type.name());
        ItemStack item = headTexture.isPresent() ? CustomHeads.createHead(headTexture.get()) : new ItemStack(fallbackMaterial(type));

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Upgrade: " + type.displayName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(type == UpgradeType.FILTER ? filterLore(List.of()) : stackLore(type));
        meta.getPersistentDataContainer().set(NamespacedKeys.UPGRADE_TYPE, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private Material fallbackMaterial(UpgradeType type) {
        return switch (type) {
            case FILTER -> Material.HOPPER;
            case STACK_COPPER -> Material.COPPER_BLOCK;
            case STACK_IRON -> Material.IRON_BLOCK;
            case STACK_GOLD -> Material.GOLD_BLOCK;
            case STACK_DIAMOND -> Material.DIAMOND_BLOCK;
            case STACK_NETHERITE -> Material.NETHERITE_BLOCK;
        };
    }

    private static List<Component> stackLore(UpgradeType type) {
        String multiplier = formatMultiplier(type.stackMultiplier());
        return List.of(
                Component.text("Arraste num slot de upgrade do bau", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Multiplica o limite de stack por " + multiplier + "x.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        );
    }

    private static String formatMultiplier(double multiplier) {
        return multiplier == Math.floor(multiplier) ? String.valueOf((int) multiplier) : String.valueOf(multiplier);
    }

    private static List<Component> filterLore(List<Material> accepted) {
        List<Component> lore = new ArrayList<>(List.of(
                Component.text("Arraste num slot de upgrade do bau", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Segure e clique no ar para escolher", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("os itens aceitos.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        lore.add(accepted.isEmpty()
                ? Component.text("Aceita: qualquer item", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                : Component.text("Aceita: " + accepted.stream().map(UpgradeRegistry::prettyName).collect(Collectors.joining(", ")),
                        NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private static String prettyName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    /** The upgrade an item represents, if it's a valid upgrade item at all. */
    public static Optional<UpgradeType> typeOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.UPGRADE_TYPE, PersistentDataType.STRING);
        return parseType(raw);
    }

    /** Parses a persisted {@code UpgradeType.name()} (e.g. from {@code chest_upgrade.upgrade_type}), tolerating unknown/stale values. */
    public static Optional<UpgradeType> parseType(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UpgradeType.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The materials {@code filterItem} currently accepts; empty means it accepts anything. */
    public static List<Material> filterMaterials(ItemStack filterItem) {
        if (filterItem == null || !filterItem.hasItemMeta()) {
            return List.of();
        }
        String raw = filterItem.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.FILTER_ITEMS, PersistentDataType.STRING);
        return parseFilterMaterials(raw);
    }

    /** Parses a persisted comma-separated material list (e.g. from {@code chest_upgrade.data_json}), tolerating unknown/stale names. */
    public static List<Material> parseFilterMaterials(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Material> materials = new ArrayList<>();
        for (String name : raw.split(",")) {
            try {
                materials.add(Material.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // stale/unknown material name from an older version; just drop it
            }
        }
        return List.copyOf(materials);
    }

    /** Serializes a material list the same way {@link #parseFilterMaterials(String)} reads it back. */
    public static String encodeFilterMaterials(List<Material> materials) {
        return materials.stream().map(Material::name).distinct().collect(Collectors.joining(","));
    }

    /** Rewrites {@code filterItem}'s accepted-materials list (and its lore) in place. */
    public static void setFilterMaterials(ItemStack filterItem, List<Material> materials) {
        ItemMeta meta = filterItem.getItemMeta();
        meta.getPersistentDataContainer().set(NamespacedKeys.FILTER_ITEMS, PersistentDataType.STRING, encodeFilterMaterials(materials));
        meta.lore(filterLore(materials));
        filterItem.setItemMeta(meta);
    }
}
