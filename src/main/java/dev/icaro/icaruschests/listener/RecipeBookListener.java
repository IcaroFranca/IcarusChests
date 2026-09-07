package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.gui.RecipeBookGui;
import dev.icaro.icaruschests.gui.RecipeBookRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Opens the read-only recipe book GUI (see {@code RecipeBookGui}) when a player right-clicks
 * while holding the Recipe Book item. Page navigation and click-cancellation are handled inside
 * {@code RecipeBookGui} itself via InventoryFramework's own click routing.
 */
public final class RecipeBookListener implements Listener {

    private final RecipeBookRegistry recipeBookRegistry;

    public RecipeBookListener(RecipeBookRegistry recipeBookRegistry) {
        this.recipeBookRegistry = recipeBookRegistry;
    }

    @EventHandler
    public void onOpenRecipeBook(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
        if (!RecipeBookRegistry.isBookItem(inHand)) {
            return;
        }

        event.setCancelled(true);
        RecipeBookGui.open(event.getPlayer(), recipeBookRegistry);
    }
}
