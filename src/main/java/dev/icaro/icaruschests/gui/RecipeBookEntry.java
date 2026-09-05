package dev.icaro.icaruschests.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * One page of the recipe book: a 3x3 crafting grid (keys 0-8, row-major —
 * see {@code RecipeBookGui#GRID_SLOTS}) mapped to the display item sitting
 * in each cell, and the item that recipe produces. Built by {@code
 * RecipeBookRegistry}, purely for display — these items are never actually
 * placed in a real inventory a player can take from.
 */
public record RecipeBookEntry(Component title, Map<Integer, ItemStack> grid, ItemStack result) {
}
