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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the physical item representing a portable backpack at a given tier and id. Unlike {@code
 * UpgradeKitRegistry}'s kits, this item's identity (its {@code BACKPACK_ID}) is per-instance, not
 * shared across every item of that tier — see {@code BackpackManager}/{@code BackpackRecipeListener}
 * for why a fresh one is minted on every craft, and preserved (never regenerated) across a tier-up
 * recraft, so the same physical backpack keeps the same storage row.
 *
 * <p>The icon is <b>always</b> a custom-textured player head, never {@link Material#BUNDLE} — a
 * deliberate, permanent decision (see the project's own notes): a real vanilla bundle is genuine,
 * client-driven storage, and a backpack's true contents living in SQLite while the physical item
 * also carries its own independent vanilla inventory was a confirmed item-loss/duplication vector.
 * The admin can override the texture per tier ({@code backpack-heads} in {@code config.yml}); when
 * that's blank, {@link #DEFAULT_HEAD_TEXTURES} supplies this plugin's own baked-in default for that
 * tier, so every backpack is a head one way or the other — there is no material fallback left at all.
 */
public final class BackpackRegistry {

    /**
     * Baked-in default head textures (Base64 {@code textures} profile values from
     * minecraft-heads.com), one per {@link BackpackTier}, used whenever the admin hasn't configured
     * an override in {@code config.yml}'s {@code backpack-heads} section. Kept here rather than on
     * the enum itself since this is purely an item-building detail, not tier progression data.
     */
    private static final Map<BackpackTier, String> DEFAULT_HEAD_TEXTURES = Map.of(
            BackpackTier.LEATHER, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDBiMWI1MzY3NDkxODM5MWEwN2E5ZDAwNTgyYzA1OGY5MjgwYmM1MjZhNzE2Yzc5NmVlNWVhYjRiZTEwYTc2MCJ9fX0=",
            BackpackTier.COPPER, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWU1ODNjYjc3MTU4MWQzYjI3YjIzZjYxN2M3YjhhNDNkY2Q3MjIwNDQ3ZmY5NWZmMTk2MDQxNGQyMzUwYmRiOSJ9fX0=",
            BackpackTier.IRON, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGRhZjhlZGMzMmFmYjQ2MWFlZTA3MTMwNTgwMjMxMDFmOTI0ZTJhN2VmYTg4M2RhZTcyZDVkNTdkNGMwNTNkNyJ9fX0=",
            BackpackTier.GOLD, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Y4NzUyNWFkODRlZmQxNjgwNmEyNmNhMDE5ODRiMjgwZTViYTY0MDM1MDViNmY2Yzk4MDNjMjQ2NDJhYmZjNyJ9fX0=",
            BackpackTier.DIAMOND, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTBkMWIwNzMyYmY3YTcwZGU0ZGMwMTU1OWNjNWM5ODExMDY4ZWY3YjYwOTUwMTAzODI3MDlmOTQwOTM5MjdmNiJ9fX0=",
            BackpackTier.NETHERITE, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM1ZDdjYzA5ZmZmYmNhM2UxYzAwZDQyMWFmYWE0MzJjZjcxZmNiMDk1NTVmNTQ1MjNlNTIyMGQxYWYwZjk3ZCJ9fX0="
    );

    private final ConfigManager configManager;

    public BackpackRegistry(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Builds a fresh, empty backpack item at {@code tier} carrying {@code id} as its {@code BACKPACK_ID}. Does not register anything — purely the item. */
    public ItemStack createBackpack(BackpackTier tier, UUID id) {
        String headTexture = configManager.backpackHeadTexture(tier).orElseGet(() -> DEFAULT_HEAD_TEXTURES.get(tier));
        ItemStack item = CustomHeads.createHead(headTexture);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mochila de " + tier.displayName(), tier.titleColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Clique para abrir.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Capacidade: " + tier.totalCapacity(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
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
