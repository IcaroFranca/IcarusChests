package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Builds and refreshes the paginated GUI representing an {@link IcarusChest}.
 *
 * <p>Each page is its own {@link Inventory} of {@code tier.slotsPerPage()}
 * slots. Navigation buttons occupy the first ("« Página Anterior", when not
 * on the first page) and/or last ("Próxima Página »", when not on the last
 * page) slot of the bottom row; those slot indices are skipped entirely when
 * copying to/from the backing {@code contents[]} array, permanently costing
 * one usable slot each on multi-page tiers (documented in the project plan).
 */
public final class GuiFactory {

    private GuiFactory() {
    }

    /** Builds the GUI for {@code page} and opens it for {@code player}. */
    public static Inventory open(Player player, IcarusChest chest, int page) {
        Inventory inventory = build(chest, page);
        player.openInventory(inventory);
        return inventory;
    }

    public static Inventory build(IcarusChest chest, int page) {
        ChestTier tier = chest.getTier();
        int size = tier.slotsPerPage();
        boolean hasPrevious = page > 0;
        boolean hasNext = page < tier.pages() - 1;
        int previousSlot = previousSlot(size);
        int nextSlot = nextSlot(size);

        IcarusChestHolder holder = new IcarusChestHolder(chest.getId(), tier, page);
        Inventory inventory = Bukkit.createInventory(holder, size, title(tier, page));
        holder.setInventory(inventory);

        ItemStack[] contents = chest.getContents();
        int base = page * size;
        for (int local = 0; local < size; local++) {
            if ((hasPrevious && local == previousSlot) || (hasNext && local == nextSlot)) {
                continue;
            }
            inventory.setItem(local, contents[base + local]);
        }

        if (hasPrevious) {
            inventory.setItem(previousSlot, navItem(Material.ARROW, "« Página Anterior", NavAction.PREVIOUS));
        }
        if (hasNext) {
            inventory.setItem(nextSlot, navItem(Material.ARROW, "Próxima Página »", NavAction.NEXT));
        }

        return inventory;
    }

    /**
     * Copies the current, non-navigation contents of {@code inventory} back
     * into {@code chest}'s backing array for {@code page}, marking it dirty.
     * Called on every GUI close and before switching pages, so edits are
     * never lost mid-session (persistence to disk is still M4).
     */
    public static void syncPageToChest(IcarusChest chest, int page, Inventory inventory) {
        ChestTier tier = chest.getTier();
        int size = tier.slotsPerPage();
        boolean hasPrevious = page > 0;
        boolean hasNext = page < tier.pages() - 1;
        int previousSlot = previousSlot(size);
        int nextSlot = nextSlot(size);
        int base = page * size;

        ItemStack[] contents = chest.getContents();
        for (int local = 0; local < size; local++) {
            if ((hasPrevious && local == previousSlot) || (hasNext && local == nextSlot)) {
                continue;
            }
            contents[base + local] = inventory.getItem(local);
        }
        chest.setDirty(true);
    }

    public static boolean isNavItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(NamespacedKeys.NAV_ACTION, PersistentDataType.STRING);
    }

    public static Optional<NavAction> navAction(ItemStack item) {
        if (!isNavItem(item)) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.NAV_ACTION, PersistentDataType.STRING);
        return NavAction.parse(raw);
    }

    private static int previousSlot(int size) {
        return size - 9;
    }

    private static int nextSlot(int size) {
        return size - 1;
    }

    private static Component title(ChestTier tier, int page) {
        Component title = Component.text("[" + tier.displayName() + "] Baú", NamedTextColor.GOLD);
        if (tier.pages() > 1) {
            title = title.append(Component.text(" — Página " + (page + 1) + "/" + tier.pages(), NamedTextColor.YELLOW));
        }
        return title;
    }

    private static ItemStack navItem(Material material, String name, NavAction action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.getPersistentDataContainer().set(NamespacedKeys.NAV_ACTION, PersistentDataType.STRING, action.key());
        item.setItemMeta(meta);
        return item;
    }
}
