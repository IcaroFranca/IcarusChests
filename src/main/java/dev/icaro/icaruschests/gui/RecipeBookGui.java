package dev.icaro.icaruschests.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * Renders the read-only recipe book: a 3x3 crafting grid, an arrow pointing at the item it
 * produces, and a bottom row with previous/next-recipe buttons and a page indicator — one recipe
 * per page. See {@code RecipeBookRegistry} for the page content. Built on InventoryFramework
 * ({@link ChestGui}/{@link StaticPane}/{@link GuiItem}) instead of a hand-rolled {@code Inventory}
 * + {@code InventoryHolder}: page state and click routing live in the {@link GuiItem} closures
 * below rather than in a separate holder class and a click listener that decodes a
 * PersistentDataContainer tag on every click.
 */
public final class RecipeBookGui {

    private static final int ROWS = 6;
    /** Grid cell {@code (x, y)} for each of {@code RecipeBookEntry#grid()}'s 0-8 keys (row-major). */
    private static final int[][] GRID_XY = {
            {1, 1}, {2, 1}, {3, 1},
            {1, 2}, {2, 2}, {3, 2},
            {1, 3}, {2, 3}, {3, 3},
    };
    private static final int ARROW_X = 4;
    private static final int RESULT_X = 6;
    private static final int CENTER_Y = 2;
    private static final int BOTTOM_Y = 5;
    private static final int PREVIOUS_X = 0;
    private static final int PAGE_INDICATOR_X = 4;
    private static final int NEXT_X = 8;

    private static Plugin plugin;

    private RecipeBookGui() {
    }

    /** Must be called once during {@code onEnable}, before the recipe book is ever opened. */
    public static void init(Plugin plugin) {
        RecipeBookGui.plugin = plugin;
    }

    /** Builds the recipe book (starting on its first page) and opens it for {@code player}. */
    public static void open(Player player, RecipeBookRegistry registry) {
        build(registry, 0).show(player);
    }

    private static ChestGui build(RecipeBookRegistry registry, int page) {
        List<RecipeBookEntry> entries = registry.buildAll();
        // The trailing {} is load-bearing, not stray: IF 0.12.1's Gui constructor unconditionally
        // reflects over this.getClass().getDeclaredMethods() looking for @TopClick/@BottomClick/etc.
        // annotations, and for every declared method with zero parameters it reads
        // getParameterTypes()[0] without checking parameterCount first — an ArrayIndexOutOfBoundsException
        // on ChestGui's own no-arg methods (update(), getRows(), isDirtyRows()...) if instantiated
        // directly. An anonymous subclass has no declared methods of its own, so the reflective scan
        // finds nothing and the bug never triggers.
        ChestGui gui = new ChestGui(ROWS, ComponentHolder.of(title(entries.size())), plugin) {};
        gui.setOnGlobalClick(event -> event.setCancelled(true)); // read-only: nothing here can be taken, placed or moved

        StaticPane pane = new StaticPane(9, ROWS);
        gui.addPane(Slot.fromXY(0, 0), pane);
        populate(gui, pane, registry, entries, page);
        return gui;
    }

    /**
     * Redraws {@code pane} for {@code page} in place. Rebuilds the entry list fresh on every
     * navigation click (not just on open), same as before — so icons stay in sync with whatever's
     * currently registered (custom heads from {@code config.yml} included) even if the player
     * navigates right after a {@code /icaruschests reload}.
     */
    private static void populate(ChestGui gui, StaticPane pane, RecipeBookRegistry registry,
                                  List<RecipeBookEntry> entries, int page) {
        pane.clear();
        RecipeBookEntry entry = entries.get(page);

        for (Map.Entry<Integer, ItemStack> cell : entry.grid().entrySet()) {
            int[] xy = GRID_XY[cell.getKey()];
            pane.addItem(new GuiItem(cell.getValue(), plugin), xy[0], xy[1]);
        }
        pane.addItem(new GuiItem(arrowIcon(), plugin), ARROW_X, CENTER_Y);
        pane.addItem(new GuiItem(entry.result(), plugin), RESULT_X, CENTER_Y);

        for (int x = 0; x < 9; x++) {
            pane.addItem(new GuiItem(filler(), plugin), x, BOTTOM_Y);
        }
        if (page > 0) {
            pane.addItem(new GuiItem(navIcon("« Receita anterior"), event -> {
                event.setCancelled(true);
                navigate(gui, pane, registry, page - 1);
            }, plugin), PREVIOUS_X, BOTTOM_Y);
        }
        if (page < entries.size() - 1) {
            pane.addItem(new GuiItem(navIcon("Próxima receita »"), event -> {
                event.setCancelled(true);
                navigate(gui, pane, registry, page + 1);
            }, plugin), NEXT_X, BOTTOM_Y);
        }
        pane.addItem(new GuiItem(pageIndicator(entry.title(), page, entries.size()), plugin), PAGE_INDICATOR_X, BOTTOM_Y);
    }

    private static void navigate(ChestGui gui, StaticPane pane, RecipeBookRegistry registry, int page) {
        List<RecipeBookEntry> entries = registry.buildAll();
        int clamped = Math.max(0, Math.min(entries.size() - 1, page));
        populate(gui, pane, registry, entries, clamped);
        gui.update();
    }

    private static Component title(int totalEntries) {
        return Component.text("Livro de Receitas (" + totalEntries + " receitas)", NamedTextColor.GOLD);
    }

    private static ItemStack arrowIcon() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navIcon(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pageIndicator(Component recipeTitle, int page, int total) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(recipeTitle.decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Receita " + (page + 1) + " de " + total, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    /** Neutral, non-interactive spacer filling the rest of the bottom row. */
    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
