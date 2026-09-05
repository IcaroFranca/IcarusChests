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

import java.util.List;
import java.util.Optional;

/**
 * Builds the item and crafting recipe for each {@link UpgradeType}. An
 * upgrade item is tagged via PDC with its type — installing/removing it from
 * a chest's dedicated upgrade slots (see {@code GuiFactory}/{@code
 * ChestGuiListener}) just moves this same item in and out of that slot.
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
        switch (type) {
            case FILTER -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(3, Material.PAPER);
            }
            case STACK -> {
                recipe.addIngredient(Material.HOPPER);
                recipe.addIngredient(4, Material.BUNDLE);
            }
        }
        plugin.getServer().addRecipe(recipe);
    }

    /** Builds a fresh item for {@code type}. Does not register a recipe. */
    public ItemStack createItem(UpgradeType type) {
        Optional<String> headTexture = configManager.upgradeHeadTexture(type.name());
        ItemStack item = headTexture.isPresent() ? CustomHeads.createHead(headTexture.get()) : new ItemStack(fallbackMaterial(type));

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Upgrade: " + type.displayName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(description(type));
        meta.getPersistentDataContainer().set(NamespacedKeys.UPGRADE_TYPE, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private Material fallbackMaterial(UpgradeType type) {
        return switch (type) {
            case FILTER -> Material.HOPPER;
            case STACK -> Material.BUNDLE;
        };
    }

    private List<Component> description(UpgradeType type) {
        String line = switch (type) {
            case FILTER -> "So aceita um tipo de item por vez no bau.";
            case STACK -> "Permite guardar mais de um stack por slot.";
        };
        return List.of(
                Component.text("Arraste num slot de upgrade do bau", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        );
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
}
