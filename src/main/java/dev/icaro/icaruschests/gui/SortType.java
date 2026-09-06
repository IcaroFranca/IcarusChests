package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.Optional;

/**
 * The three sort criteria offered by the Organize menu (see {@code
 * OrganizeMenuGui}/{@code ChestOrganizeListener}). Each compares two non-null,
 * non-air {@link ItemStack}s from a chest's authoritative {@code
 * chest.getContents()} array — so {@link #QUANTITY} always compares a Stack
 * upgrade's true amount (which can exceed the item's normal max stack), never
 * a display-capped stand-in.
 */
public enum SortType {

    /** Alphabetical by {@link UpgradeRegistry#prettyName(Material)}. */
    NAME("name", "Por Nome", Material.NAME_TAG, Comparator.comparing(item -> UpgradeRegistry.prettyName(item.getType()))),

    /** Highest true amount first. */
    QUANTITY("quantity", "Por Quantidade", Material.HOPPER,
            Comparator.comparingInt(ItemStack::getAmount).reversed()),

    /**
     * Approximates a "by category" grouping using {@link Material}'s own declared enum order,
     * which roughly follows the game's own block/item registration order — not a faithful replica
     * of the creative-inventory tabs (that grouping isn't exposed by the public API), but a
     * reasonable, deterministic proxy that doesn't require hand-maintaining a category table.
     */
    CATEGORY("category", "Por Categoria", Material.CRAFTING_TABLE,
            Comparator.comparingInt(item -> item.getType().ordinal()));

    private final String key;
    private final String label;
    private final Material icon;
    private final Comparator<ItemStack> comparator;

    SortType(String key, String label, Material icon, Comparator<ItemStack> comparator) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.comparator = comparator;
    }

    public String key() {
        return key;
    }

    public Component displayName() {
        return Component.text(label);
    }

    public Material icon() {
        return icon;
    }

    /** Orders two chest-slot items under this criterion; ties keep their original relative order (a stable sort). */
    public Comparator<ItemStack> comparator() {
        return comparator;
    }

    public static Optional<SortType> fromKey(String raw) {
        for (SortType type : values()) {
            if (type.key.equals(raw)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** The {@link SortType} an Organize-menu item represents, if it's one of the three sort icons (not the cancel button). */
    public static Optional<SortType> fromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.SORT_TYPE, PersistentDataType.STRING);
        return raw == null ? Optional.empty() : fromKey(raw);
    }
}
