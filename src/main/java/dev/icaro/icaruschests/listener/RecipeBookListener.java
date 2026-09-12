package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.gui.RecipeBookDetailGui;
import dev.icaro.icaruschests.gui.RecipeBookDetailHolder;
import dev.icaro.icaruschests.gui.RecipeBookEntry;
import dev.icaro.icaruschests.gui.RecipeBookIndexGui;
import dev.icaro.icaruschests.gui.RecipeBookIndexHolder;
import dev.icaro.icaruschests.gui.RecipeBookRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Optional;

/**
 * Handles clicks inside the recipe book's two screens — the index (every recipe's result icon at
 * once, see {@code RecipeBookIndexGui}) and the detail view (one recipe's crafting grid, see
 * {@code RecipeBookDetailGui}) — opened directly by {@code /icaruschests recipebook} (see {@code
 * IcarusChestsCommand}); there's no physical book item to right-click anymore. Every click in
 * either screen is cancelled — both are read-only, for browsing, not storage.
 */
public final class RecipeBookListener implements Listener {

    private final RecipeBookRegistry recipeBookRegistry;

    public RecipeBookListener(RecipeBookRegistry recipeBookRegistry) {
        this.recipeBookRegistry = recipeBookRegistry;
    }

    @EventHandler
    public void onIndexClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeBookIndexHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Optional<Integer> index = RecipeBookIndexGui.entryIndexOf(event.getCurrentItem());
        if (index.isEmpty()) {
            return;
        }
        List<RecipeBookEntry> entries = recipeBookRegistry.buildAll();
        if (index.get() < 0 || index.get() >= entries.size()) {
            return; // stale index from a recipe list that shrank since this screen was opened
        }
        player.openInventory(RecipeBookDetailGui.open(entries.get(index.get())));
    }

    @EventHandler
    public void onDetailClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeBookDetailHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (RecipeBookDetailGui.isBackButton(event.getCurrentItem())) {
            player.openInventory(RecipeBookIndexGui.open(recipeBookRegistry.buildAll()));
        }
    }
}
