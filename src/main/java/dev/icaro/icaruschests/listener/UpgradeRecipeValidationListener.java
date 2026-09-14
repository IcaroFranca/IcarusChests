package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Optional;

/**
 * Vanilla's {@code RecipeChoice} can only match an ingredient by Material
 * (or a short list of them) — it has no way to require "this exact custom
 * item" for something head-shaped carrying its own meta/PDC. So each Stack
 * tier's recipe (besides Copper, the entry one) declares its "previous
 * tier" slot broadly (see {@code UpgradeRegistry}, any player head or ore
 * block), and this listener does the real check itself — by the item's
 * {@code UPGRADE_TYPE} PDC tag, not by comparing items — every time the
 * crafting grid changes, clearing the result unless what's actually sitting
 * there is the correct previous tier.
 */
public final class UpgradeRecipeValidationListener implements Listener {

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return;
        }
        NamespacedKey key = keyed.getKey();
        Optional<UpgradeType> type = UpgradeType.fromRecipeKey(key.getKey());
        if (type.isEmpty()) {
            return; // not one of our upgrade recipes
        }
        Optional<UpgradeType> requiredPrevious = type.get().previousStackTier();
        if (requiredPrevious.isEmpty()) {
            return; // this recipe doesn't require a specific previous-tier item at all
        }

        CraftingInventory inventory = event.getInventory();
        boolean hasCorrectPreviousTier = false;
        for (ItemStack item : inventory.getMatrix()) {
            if (UpgradeRegistry.typeOf(item).filter(found -> found == requiredPrevious.get()).isPresent()) {
                hasCorrectPreviousTier = true;
                break;
            }
        }
        if (!hasCorrectPreviousTier) {
            inventory.setResult(null);
        }
    }
}
