package dev.icaro.icaruschests.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The 9-slot picker a player sees after right-clicking the air while holding a Filter upgrade
 * item: click a slot while holding an item to drop a one-item "ghost" copy of its type there —
 * the picker never touches the player's real inventory or cursor, nothing is ever actually taken.
 * On close, the distinct materials placed here are saved into the Filter item's own PDC (see
 * {@code UpgradeRegistry}), so the configuration travels with the item into whichever chest it's
 * later installed on.
 *
 * <p>Built on InventoryFramework instead of a hand-rolled {@code Inventory} + {@code
 * InventoryHolder} + a globally-registered {@code Listener} matching every open inventory by
 * {@code instanceof FilterConfigHolder}: {@link com.github.stefvanschie.inventoryframework.gui.type.util.Gui}'s
 * own top/bottom/drag/close hooks are already scoped to this one Gui instance, so there's no
 * holder class and no manual "is this event even about my GUI" check left to write.
 */
public final class FilterConfigGui {

    public static final int SIZE = 9;

    private static Plugin plugin;

    private FilterConfigGui() {
    }

    /** Must be called once during {@code onEnable}, before the filter picker is ever opened. */
    public static void init(Plugin plugin) {
        FilterConfigGui.plugin = plugin;
    }

    public static void open(Player player, int sourceHotbarSlot, List<Material> currentMaterials) {
        ChestGui gui = new ChestGui(1, ComponentHolder.of(Component.text("Filtro: itens aceitos", NamedTextColor.GREEN)), plugin) {};
        StaticPane pane = new StaticPane(SIZE, 1);
        gui.addPane(Slot.fromXY(0, 0), pane);

        for (int x = 0; x < currentMaterials.size() && x < SIZE; x++) {
            pane.addItem(new GuiItem(new ItemStack(currentMaterials.get(x)), plugin), x, 0);
        }

        gui.setOnTopClick(event -> {
            event.setCancelled(true); // the picker never gives/takes real items
            ItemStack cursor = event.getCursor();
            boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
            ItemStack current = event.getCurrentItem();
            boolean slotEmpty = current == null || current.getType() == Material.AIR;

            if (!cursorEmpty) {
                addGhostAt(pane, event.getSlot(), cursor.getType());
                gui.update();
            } else if (!slotEmpty) {
                pane.removeItem(event.getSlot(), 0);
                gui.update();
            }
        });

        gui.setOnBottomClick(event -> {
            // Only a shift-click hands us an item to represent — anything else (reordering the
            // player's own stuff below) doesn't touch the picker.
            if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
                return;
            }
            event.setCancelled(true);
            ItemStack shifted = event.getCurrentItem();
            if (shifted != null && shifted.getType() != Material.AIR) {
                addGhostToFirstEmptySlot(pane, shifted.getType());
                gui.update();
            }
        });

        gui.setOnGlobalDrag(event -> event.setCancelled(true)); // ghosts are placed one click at a time; no drag-spreading

        gui.setOnClose(event -> {
            if (!(event.getPlayer() instanceof Player closingPlayer)) {
                return;
            }

            Set<Material> chosen = new LinkedHashSet<>();
            for (int x = 0; x < SIZE; x++) {
                GuiItem item = pane.getItem(Slot.fromXY(x, 0));
                if (item != null) {
                    chosen.add(item.getItem().getType());
                }
            }

            PlayerInventory inventory = closingPlayer.getInventory();
            ItemStack target = inventory.getItem(sourceHotbarSlot);
            boolean stillHoldingFilter = UpgradeRegistry.typeOf(target).filter(type -> type == UpgradeType.FILTER).isPresent();
            if (!stillHoldingFilter) {
                return; // player swapped/dropped the item mid-config; nothing sensible to save it onto
            }

            UpgradeRegistry.setFilterMaterials(target, new ArrayList<>(chosen));
            inventory.setItem(sourceHotbarSlot, target);
            closingPlayer.sendMessage(Component.text(
                    chosen.isEmpty()
                            ? "Filtro configurado para aceitar qualquer item."
                            : "Filtro configurado: " + chosen.size() + " tipo(s) de item aceito(s).",
                    NamedTextColor.GREEN));
        });

        gui.show(player);
    }

    /** Stamps a ghost of {@code material} into slot {@code x}, unless that Material already occupies a different slot. */
    private static void addGhostAt(StaticPane pane, int x, Material material) {
        if (isMaterialElsewhere(pane, material, x)) {
            return;
        }
        pane.addItem(new GuiItem(new ItemStack(material), plugin), x, 0);
    }

    /** Stamps a ghost of {@code material} into the first empty slot, unless that Material is already represented or the picker is full. */
    private static void addGhostToFirstEmptySlot(StaticPane pane, Material material) {
        if (isMaterialElsewhere(pane, material, -1)) {
            return;
        }
        for (int x = 0; x < SIZE; x++) {
            if (pane.getItem(Slot.fromXY(x, 0)) == null) {
                pane.addItem(new GuiItem(new ItemStack(material), plugin), x, 0);
                return;
            }
        }
    }

    private static boolean isMaterialElsewhere(StaticPane pane, Material material, int excludedX) {
        for (int x = 0; x < SIZE; x++) {
            if (x == excludedX) {
                continue;
            }
            GuiItem item = pane.getItem(Slot.fromXY(x, 0));
            if (item != null && item.getItem().getType() == material) {
                return true;
            }
        }
        return false;
    }
}
