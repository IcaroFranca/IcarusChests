package dev.icaro.icaruschests.upgrade;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.icaruschests.config.ConfigManager;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the consumable "upgrade kit" item for each {@link ChestTier} that
 * has one, and registers its crafting recipe. A kit is tagged via PDC with
 * the tier it upgrades a chest TO — it is not itself a placeable chest, and
 * applying it is handled by {@link TierUpgradeService}.
 *
 * <p>The icon is a custom-textured player head when the admin configured one
 * for that tier ({@code upgrade-kit-heads} in {@code config.yml} — grab the
 * Base64 value from a site like minecraft-heads.com), falling back to a
 * plain icon of the tier's own upgrade material otherwise.
 */
public final class UpgradeKitRegistry {

    private final Plugin plugin;
    private final ConfigManager configManager;

    public UpgradeKitRegistry(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Registers a shaped crafting recipe for every tier that defines an
     * upgrade material: a chest in the center slot, surrounded by the tier's
     * upgrade material (see {@link ChestTier#recipeShape()}).
     */
    public void registerRecipes() {
        for (ChestTier tier : ChestTier.values()) {
            tier.upgradeMaterial().ifPresent(material -> registerRecipe(tier, material));
        }
    }

    private void registerRecipe(ChestTier tier, Material material) {
        NamespacedKey key = new NamespacedKey(plugin, tier.upgradeKitKey());
        ShapedRecipe recipe = new ShapedRecipe(key, createKit(tier));
        recipe.shape(tier.recipeShape());
        recipe.setIngredient('M', material);
        recipe.setIngredient('C', Material.CHEST);
        plugin.getServer().addRecipe(recipe);
    }

    /** Builds a fresh kit item for {@code tier}. Does not register a recipe. */
    public ItemStack createKit(ChestTier tier) {
        Optional<String> headTexture = configManager.upgradeKitHeadTexture(tier);
        ItemStack item = headTexture.isPresent()
                ? customHeadIcon(headTexture.get())
                : new ItemStack(tier.upgradeMaterial().orElse(Material.CHEST));

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Kit de Upgrade: " + tier.displayName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Shift + botao direito num bau", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("do tier anterior para evoluir.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(NamespacedKeys.UPGRADE_KIT_TIER, PersistentDataType.INTEGER, tier.ordinal());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack customHeadIcon(String base64Texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = (PlayerProfile) Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    /** The tier an item upgrades a chest TO, if it's a valid kit at all. */
    public static Optional<ChestTier> targetTierOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        Integer ordinal = item.getItemMeta().getPersistentDataContainer()
                .get(NamespacedKeys.UPGRADE_KIT_TIER, PersistentDataType.INTEGER);
        return ordinal == null ? Optional.empty() : ChestTier.byOrdinal(ordinal);
    }
}
