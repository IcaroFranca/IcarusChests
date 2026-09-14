package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.config.ConfigManager;
import dev.icaro.icaruschests.tier.ChestTier;
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

/**
 * Builds the "Baú do IcarusChests" kit — a custom-textured head, exactly like every other
 * consumable kit in this plugin — and registers its crafting recipe: a plain {@link
 * Material#CHEST} + a single {@link Material#REDSTONE}, shapeless. Unlike a tier upgrade kit
 * (applied to an *already-tagged* IcarusChest to bump its tier), this one is applied to a plain,
 * not-yet-tagged chest to turn it into a brand-new {@link ChestTier#NORMAL} IcarusChest in the
 * first place — see {@code ChestInteractListener}, which handles the shift-right-click that
 * consumes it, same interaction shape as any other kit.
 *
 * <p>Deliberately never a placeable {@code Material.CHEST} item itself: it needs to be a
 * custom-textured head like every other kit, and a head can't also place as a functional chest
 * block — the mechanic is "apply this kit to an existing chest", not "place this kit as the
 * chest". {@code SpecialItemProtectionListener} still stops the head itself from being placed as
 * a decorative block, same as any other kit.
 */
public final class StarterChestRegistry {

    private static final String RECIPE_KEY = "chest_starter";

    /** Baked-in default (Base64 {@code textures} profile value from minecraft-heads.com), used whenever the admin hasn't configured an override in {@code config.yml}'s {@code chest-starter-head}. */
    private static final String DEFAULT_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmJmNGNkNmQwYmUwOTNiZjhkMDNjZWU3NzJiYzU4MTk2YmM0MjUxODliNTg1Yjk1MWEwMDFlYjIyZDUwMDFjZCJ9fX0=";

    private final Plugin plugin;
    private final ConfigManager configManager;

    public StarterChestRegistry(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void registerRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        ShapelessRecipe recipe = new ShapelessRecipe(key, createStarterChest());
        recipe.addIngredient(Material.CHEST);
        recipe.addIngredient(Material.REDSTONE);
        plugin.getServer().addRecipe(recipe);
    }

    /** Builds a fresh starter kit item. Does not register anything. */
    public ItemStack createStarterChest() {
        String headTexture = configManager.chestStarterHeadTexture().orElse(DEFAULT_HEAD_TEXTURE);
        ItemStack item = CustomHeads.createHead(headTexture);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Baú do IcarusChests", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Shift + botao direito num bau", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("comum para transformar.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(NamespacedKeys.CHEST_STARTER, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} is this starter kit — the only item that turns an existing plain chest into an IcarusChest at all. */
    public static boolean isStarterChest(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(NamespacedKeys.CHEST_STARTER, PersistentDataType.BYTE);
    }
}
