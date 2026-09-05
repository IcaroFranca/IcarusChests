package dev.icaro.icaruschests.gui;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * Builds and refreshes the paginated GUI representing an {@link IcarusChest}.
 *
 * <p>A single Minecraft chest-type inventory is hard-capped at 54 slots by
 * the client itself — not a Bukkit/Paper limitation, a protocol one — so
 * there is no way to show a bigger tier (or a doubled chest) on one screen
 * without a custom client mod, which is out of scope for a server-only
 * plugin. Pages ({@link IcarusChest#effectivePageSizes()}) are front-loaded
 * (earlier pages as large as possible) and not necessarily uniform in size.
 * Navigation buttons occupy the first ("« Página Anterior", when not on the
 * first page) and/or last ("Próxima Página »", when not on the last page)
 * slot of the bottom row of each page; those slot indices are skipped
 * entirely when copying to/from the backing {@code contents[]} array,
 * permanently costing one usable slot each on multi-page tiers.
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
        int[] pageSizes = chest.effectivePageSizes();
        int size = pageSizes[page];
        boolean hasPrevious = page > 0;
        boolean hasNext = page < pageSizes.length - 1;
        int previousSlot = previousSlot(size);
        int nextSlot = nextSlot(size);
        int base = pageOffset(pageSizes, page);

        IcarusChestHolder holder = new IcarusChestHolder(chest.getId(), chest.getTier(), page);
        Inventory inventory = Bukkit.createInventory(holder, size, title(chest, page, pageSizes.length));
        holder.setInventory(inventory);

        ItemStack[] contents = chest.getContents();
        for (int local = 0; local < size; local++) {
            if ((hasPrevious && local == previousSlot) || (hasNext && local == nextSlot)) {
                continue;
            }
            inventory.setItem(local, contents[base + local]);
        }

        if (hasPrevious) {
            inventory.setItem(previousSlot, navItem(Material.ARROW, "« Página Anterior",
                    "Volta para a página " + page + ".", NavAction.PREVIOUS));
        }
        if (hasNext) {
            inventory.setItem(nextSlot, navItem(Material.ARROW, "Próxima Página »",
                    "Avança para a página " + (page + 2) + ".", NavAction.NEXT));
        }

        return inventory;
    }

    /**
     * Copies the current, non-navigation contents of {@code inventory} back
     * into {@code chest}'s backing array for {@code page}, marking it dirty.
     * Called on every GUI close and before switching pages, so edits are
     * never lost mid-session.
     */
    public static void syncPageToChest(IcarusChest chest, int page, Inventory inventory) {
        int[] pageSizes = chest.effectivePageSizes();
        int size = pageSizes[page];
        boolean hasPrevious = page > 0;
        boolean hasNext = page < pageSizes.length - 1;
        int previousSlot = previousSlot(size);
        int nextSlot = nextSlot(size);
        int base = pageOffset(pageSizes, page);

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

    /** Sum of every page's size before {@code page} — the global index its first slot maps to. */
    private static int pageOffset(int[] pageSizes, int page) {
        int offset = 0;
        for (int i = 0; i < page; i++) {
            offset += pageSizes[i];
        }
        return offset;
    }

    private static int previousSlot(int size) {
        return size - 9;
    }

    private static int nextSlot(int size) {
        return size - 1;
    }

    private static Component title(IcarusChest chest, int page, int totalPages) {
        ChestTier tier = chest.getTier();
        String label = "[" + tier.displayName() + "]" + (chest.isDoubled() ? " Baú Duplo" : " Baú");
        Component title = Component.text(label, NamedTextColor.GOLD);
        if (totalPages > 1) {
            title = title.append(Component.text(" — Página " + (page + 1) + "/" + totalPages, NamedTextColor.YELLOW));
        }
        return title;
    }

    private static ItemStack navItem(Material material, String name, String loreLine, NavAction action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(loreLine, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(NamespacedKeys.NAV_ACTION, PersistentDataType.STRING, action.key());
        item.setItemMeta(meta);
        return item;
    }
}
