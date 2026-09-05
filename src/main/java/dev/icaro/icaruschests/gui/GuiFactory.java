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
 * Builds and refreshes the scrollable GUI representing an {@link IcarusChest}.
 *
 * <p>A single Minecraft chest-type inventory is hard-capped at 54 slots by
 * the client itself — not a Bukkit/Paper limitation, a protocol one — so a
 * tier (or a doubled chest) bigger than that can't fit on one screen without
 * a custom client mod, which is out of scope for a server-only plugin.
 * Instead of paginating, a chest whose capacity exceeds 54 gets a fixed
 * 54-slot window: the top 5 rows (45 slots) show a slice of the backing
 * array, scrollable one row (9 slots) at a time via the bottom row's
 * controls. Scrolling updates the same {@link Inventory} in place — it never
 * closes/reopens the view — so the control row can't change size and none of
 * the tier's own capacity is ever sacrificed to navigation, unlike the old
 * paginated design. A chest whose capacity already fits in 54 slots (Normal,
 * Copper, single Iron) just gets a plain inventory of exactly that size, no
 * scrolling machinery at all.
 */
public final class GuiFactory {

    private static final int WINDOW_SIZE = 54;
    private static final int CONTROL_ROW_SIZE = 9;
    private static final int VISIBLE_WITH_CONTROLS = WINDOW_SIZE - CONTROL_ROW_SIZE;

    private GuiFactory() {
    }

    /** Builds the GUI (starting scrolled to the top) and opens it for {@code player}. */
    public static Inventory open(Player player, IcarusChest chest) {
        Inventory inventory = build(chest, 0);
        player.openInventory(inventory);
        return inventory;
    }

    public static Inventory build(IcarusChest chest, int scrollOffset) {
        int capacity = chest.effectiveTotalCapacity();
        int guiSize = isScrollable(capacity) ? WINDOW_SIZE : capacity;

        IcarusChestHolder holder = new IcarusChestHolder(chest.getId(), chest.getTier());
        Inventory inventory = Bukkit.createInventory(holder, guiSize, title(chest));
        holder.setInventory(inventory);
        holder.setScrollOffset(scrollOffset);

        populate(chest, holder, inventory);
        return inventory;
    }

    /**
     * Repopulates {@code inventory}'s content slots (and control row, if
     * scrollable) from {@code chest}, using {@code holder}'s current scroll
     * offset. Never recreates the inventory or changes its title/size —
     * safe to call repeatedly on the same open view (see scrolling in
     * {@code ChestGuiListener}).
     */
    public static void populate(IcarusChest chest, IcarusChestHolder holder, Inventory inventory) {
        int capacity = chest.effectiveTotalCapacity();
        boolean scrollable = isScrollable(capacity);
        int visibleSlots = scrollable ? VISIBLE_WITH_CONTROLS : capacity;
        int offset = holder.getScrollOffset();

        ItemStack[] contents = chest.getContents();
        for (int local = 0; local < visibleSlots; local++) {
            inventory.setItem(local, contents[offset + local]);
        }

        if (scrollable) {
            int maxOffset = capacity - VISIBLE_WITH_CONTROLS;
            boolean canScrollUp = offset > 0;
            boolean canScrollDown = offset < maxOffset;
            for (int column = 0; column < CONTROL_ROW_SIZE; column++) {
                inventory.setItem(controlSlot(column), controlItem(column, canScrollUp, canScrollDown, offset, capacity));
            }
        }
    }

    /** Number of content slots (excludes the control row, when present) currently visible in {@code inventory}. */
    public static int visibleSlotCount(IcarusChest chest) {
        int capacity = chest.effectiveTotalCapacity();
        return isScrollable(capacity) ? VISIBLE_WITH_CONTROLS : capacity;
    }

    public static boolean isScrollable(int capacity) {
        return capacity > WINDOW_SIZE;
    }

    /** Whether {@code localSlot} belongs to the reserved control row (only meaningful when the chest is scrollable). */
    public static boolean isControlSlot(IcarusChest chest, int localSlot) {
        return isScrollable(chest.effectiveTotalCapacity()) && localSlot >= VISIBLE_WITH_CONTROLS;
    }

    /**
     * Copies the currently visible (non-control) slots of {@code inventory}
     * back into {@code chest}'s backing array at {@code holder}'s scroll
     * offset, marking it dirty. Called on every GUI close and before
     * scrolling, so edits are never lost mid-session.
     */
    public static void syncVisibleToChest(IcarusChest chest, IcarusChestHolder holder, Inventory inventory) {
        int visibleSlots = visibleSlotCount(chest);
        int offset = holder.getScrollOffset();
        ItemStack[] contents = chest.getContents();
        for (int local = 0; local < visibleSlots; local++) {
            contents[offset + local] = inventory.getItem(local);
        }
        chest.setDirty(true);
    }

    /** Clamped scroll target for a {@link NavAction}, or the current offset if the move isn't possible. */
    public static int scrollTarget(IcarusChest chest, int currentOffset, NavAction action) {
        int maxOffset = chest.effectiveTotalCapacity() - VISIBLE_WITH_CONTROLS;
        int delta = action == NavAction.SCROLL_DOWN ? 9 : -9;
        return Math.max(0, Math.min(maxOffset, currentOffset + delta));
    }

    /** Moves {@code holder} to {@code newOffset} and redraws {@code inventory} in place. Caller must sync the old offset first. */
    public static void scrollTo(IcarusChest chest, IcarusChestHolder holder, Inventory inventory, int newOffset) {
        holder.setScrollOffset(newOffset);
        populate(chest, holder, inventory);
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

    private static int controlSlot(int column) {
        return VISIBLE_WITH_CONTROLS + column;
    }

    private static ItemStack controlItem(int column, boolean canScrollUp, boolean canScrollDown, int offset, int capacity) {
        if (column == 0 && canScrollUp) {
            return navItem(Material.ARROW, "▲ Rolar para Cima", "Sobe uma fileira.", NavAction.SCROLL_UP);
        }
        if (column == 8 && canScrollDown) {
            return navItem(Material.ARROW, "▼ Rolar para Baixo", "Desce uma fileira.", NavAction.SCROLL_DOWN);
        }
        if (column == 4) {
            return positionIndicator(offset, capacity);
        }
        return filler();
    }

    private static Component title(IcarusChest chest) {
        ChestTier tier = chest.getTier();
        String label = "[" + tier.displayName() + "]" + (chest.isDoubled() ? " Baú Duplo" : " Baú");
        return Component.text(label, tier.titleColor());
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

    private static ItemStack positionIndicator(int offset, int capacity) {
        int totalRows = capacity / 9;
        int firstVisibleRow = offset / 9 + 1;
        int lastVisibleRow = Math.min(totalRows, firstVisibleRow + (VISIBLE_WITH_CONTROLS / 9) - 1);
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Fileiras " + firstVisibleRow + "–" + lastVisibleRow + " de " + totalRows,
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** Neutral, non-interactive spacer so shift-clicks from the player's own inventory never land in the control row. */
    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
