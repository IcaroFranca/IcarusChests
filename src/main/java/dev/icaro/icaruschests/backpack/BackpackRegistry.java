package dev.icaro.icaruschests.backpack;

import dev.icaro.icaruschests.config.ConfigManager;
import dev.icaro.icaruschests.tier.BackpackTier;
import dev.icaro.icaruschests.util.CustomHeads;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the physical item representing a portable backpack at a given tier and id. Unlike {@code
 * UpgradeKitRegistry}'s kits, this item's identity (its {@code BACKPACK_ID}) is per-instance, not
 * shared across every item of that tier — see {@code BackpackManager}/{@code BackpackRecipeListener}
 * for why a fresh one is minted on every craft, and preserved (never regenerated) across a tier-up
 * recraft, so the same physical backpack keeps the same storage row.
 *
 * <p>The icon is a custom-textured player head when the admin configured one for that tier ({@code
 * backpack-heads} in {@code config.yml}), falling back to a plain vanilla {@link Material#BUNDLE}
 * otherwise — deliberately not a placeable material, so an unconfigured backpack needs no
 * block-placement protection of its own (a configured head still does; see {@code
 * SpecialItemProtectionListener}).
 */
public final class BackpackRegistry {

    private final ConfigManager configManager;

    public BackpackRegistry(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Builds a backpack item at {@code tier} carrying {@code id} as its {@code BACKPACK_ID}. Does not register anything — purely the item. */
    public ItemStack createBackpack(BackpackTier tier, UUID id) {
        Optional<String> headTexture = configManager.backpackHeadTexture(tier);
        ItemStack item = headTexture.isPresent() ? CustomHeads.createHead(headTexture.get()) : new ItemStack(Material.BUNDLE);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mochila de " + tier.displayName(), tier.titleColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Clique para abrir.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Capacidade: " + tier.totalCapacity(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(NamespacedKeys.BACKPACK_ID, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(NamespacedKeys.BACKPACK_TIER, PersistentDataType.INTEGER, tier.ordinal());
        item.setItemMeta(meta);
        return item;
    }

    /** The {@code BackpackTier} an item is at, if it's a backpack at all. */
    public static Optional<BackpackTier> tierOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        Integer ordinal = item.getItemMeta().getPersistentDataContainer()
                .get(NamespacedKeys.BACKPACK_TIER, PersistentDataType.INTEGER);
        return ordinal == null ? Optional.empty() : BackpackTier.byOrdinal(ordinal);
    }
}
