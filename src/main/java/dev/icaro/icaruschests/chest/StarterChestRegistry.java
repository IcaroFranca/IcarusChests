package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.tier.ChestTier;
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
 * Builds the one item that turns a placed chest block into a brand-new {@link ChestTier#NORMAL}
 * IcarusChest, and registers its crafting recipe: a plain {@link Material#CHEST} + a single
 * {@link Material#REDSTONE}, shapeless. A plain vanilla chest — crafted the ordinary 8-plank way,
 * looted, given, or placed by anything else — is never touched by this plugin on its own; only
 * placing THIS item's result starts the tier system for that block at all (see {@code
 * ChestPlaceListener}, which checks {@link #isStarterChest} before doing anything).
 *
 * <p>The item is a plain, unremarkable {@link Material#CHEST} itself (no custom head — there's
 * nothing to differentiate visually before it's placed, same reasoning {@link ChestTier#NORMAL}
 * itself needs no upgrade material of its own) with just a name/lore explaining what it does and
 * a PDC marker identifying it.
 */
public final class StarterChestRegistry {

    private static final String RECIPE_KEY = "chest_starter";

    private final Plugin plugin;

    public StarterChestRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void registerRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        ShapelessRecipe recipe = new ShapelessRecipe(key, createStarterChest());
        recipe.addIngredient(Material.CHEST);
        recipe.addIngredient(Material.REDSTONE);
        plugin.getServer().addRecipe(recipe);
    }

    /** Builds a fresh starter chest item. Does not register anything. */
    public ItemStack createStarterChest() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Baú do IcarusChests", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Coloque para criar um baú com tiers.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Nasce no tier Normal.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(NamespacedKeys.CHEST_STARTER, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} is this starter chest — the only item that turns a placed chest block into an IcarusChest at all. */
    public static boolean isStarterChest(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(NamespacedKeys.CHEST_STARTER, PersistentDataType.BYTE);
    }
}
