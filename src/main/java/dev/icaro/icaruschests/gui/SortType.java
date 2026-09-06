package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;

/**
 * The three sort criteria the Organize control button cycles through, one per click (see {@code
 * ChestGuiListener#handleControlButtonClick}). Each compares two non-null, non-air {@link
 * ItemStack}s from a chest's authoritative {@code chest.getContents()} array — so {@link #QUANTITY}
 * always compares a Stack upgrade's true amount (which can exceed the item's normal max stack),
 * never a display-capped stand-in.
 */
public enum SortType {

    /** Alphabetical by {@link UpgradeRegistry#prettyName(Material)}. */
    NAME("Por Nome", Comparator.comparing(item -> UpgradeRegistry.prettyName(item.getType()))),

    /** Highest true amount first. */
    QUANTITY("Por Quantidade", Comparator.comparingInt(ItemStack::getAmount).reversed()),

    /**
     * Approximates a "by category" grouping using {@link Material}'s own declared enum order,
     * which roughly follows the game's own block/item registration order — not a faithful replica
     * of the creative-inventory tabs (that grouping isn't exposed by the public API), but a
     * reasonable, deterministic proxy that doesn't require hand-maintaining a category table.
     */
    CATEGORY("Por Categoria", Comparator.comparingInt(item -> item.getType().ordinal()));

    private final String label;
    private final Comparator<ItemStack> comparator;

    SortType(String label, Comparator<ItemStack> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    public Component displayName() {
        return Component.text(label);
    }

    /** Orders two chest-slot items under this criterion; ties keep their original relative order (a stable sort). */
    public Comparator<ItemStack> comparator() {
        return comparator;
    }
}
