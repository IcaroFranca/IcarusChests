package dev.icaro.icaruschests.backpack;

import dev.icaro.icaruschests.config.ConfigManager;
import dev.icaro.icaruschests.tier.BackpackTier;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.util.CustomHeads;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** Builds a backpack item at {@code tier} carrying {@code id} as its {@code BACKPACK_ID}, with an initial "empty" content summary in its lore. Does not register anything — purely the item. */
    public ItemStack createBackpack(BackpackTier tier, UUID id) {
        Optional<String> headTexture = configManager.backpackHeadTexture(tier);
        ItemStack item = headTexture.isPresent() ? CustomHeads.createHead(headTexture.get()) : new ItemStack(Material.BUNDLE);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mochila de " + tier.displayName(), tier.titleColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(NamespacedKeys.BACKPACK_ID, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(NamespacedKeys.BACKPACK_TIER, PersistentDataType.INTEGER, tier.ordinal());
        item.setItemMeta(meta);
        applyLore(item, tier, new ItemStack[0]);
        sanitize(item);
        return item;
    }

    /** How many distinct item types show up in the lore content summary — see {@link #refreshPreview}. */
    private static final int PREVIEW_LIMIT = 12;

    /**
     * Rebuilds {@code item}'s lore from scratch to reflect {@code contents} — a text summary
     * ("64x Diamante", …), never a real {@link BundleMeta} item list. A vanilla client treats
     * anything actually sitting in a bundle's own {@code minecraft:bundle_contents} component as
     * real, extractable/insertable storage the moment the item is clicked in *any* inventory
     * screen (the player's own, a chest, this plugin's own GUI in the background, anywhere) — an
     * earlier version of this method populated exactly that, which let a plain right-click pull a
     * genuine duplicate of an item out of thin air (the true copy still sitting safely in SQLite,
     * completely unaware vanilla just handed out a second one). Lore is inert text with no such
     * mechanism, so it carries none of that risk while still showing real names and counts.
     * {@link #sanitize} is still called every time regardless, to actively strip out any real
     * bundle contents a backpack built by that earlier, unsafe version might already be carrying.
     */
    public void refreshPreview(ItemStack item, BackpackTier tier, ItemStack[] contents) {
        applyLore(item, tier, contents);
        sanitize(item);
    }

    private void applyLore(ItemStack item, BackpackTier tier, ItemStack[] contents) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Clique para abrir.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Capacidade: " + tier.totalCapacity(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        List<String> summary = contentSummary(contents);
        if (summary.isEmpty()) {
            lore.add(Component.text("Vazia.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Conteúdo:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            for (String line : summary) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /** Distinct item types present, merged by pretty name, in first-seen order — overflow collapses into one final "+N outros" line instead of an unbounded tooltip. */
    private List<String> contentSummary(ItemStack[] contents) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stored : contents) {
            if (stored == null || stored.getType() == Material.AIR) {
                continue;
            }
            counts.merge(UpgradeRegistry.prettyName(stored.getType()), stored.getAmount(), Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown >= PREVIEW_LIMIT) {
                lines.add("+ " + (counts.size() - PREVIEW_LIMIT) + " outro(s) tipo(s)");
                break;
            }
            lines.add(entry.getValue() + "x " + entry.getKey());
            shown++;
        }
        return lines;
    }

    /**
     * Strips any real {@link BundleMeta} content list off {@code item} — a backpack must never
     * actually carry vanilla-functional bundle contents (see {@link #refreshPreview}'s Javadoc for
     * why). Safe and cheap to call unconditionally on any item that might be a backpack; a no-op if
     * it isn't bundle-shaped or already has nothing in it.
     */
    public void sanitize(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return;
        }
        bundleMeta.setItems(List.of());
        item.setItemMeta(bundleMeta);
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
