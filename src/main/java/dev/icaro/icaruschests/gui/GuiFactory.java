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
import java.util.OptionalInt;

/**
 * Builds and refreshes the scrollable GUI representing an {@link IcarusChest}.
 *
 * <p>A single Minecraft chest-type inventory is hard-capped at 54 slots by
 * the client itself — not a Bukkit/Paper limitation, a protocol one — so a
 * tier (or a doubled chest) bigger than that can't fit on one screen without
 * a custom client mod, which is out of scope for a server-only plugin.
 * Instead of paginating, every chest reserves a bottom control row (9 slots,
 * never counted toward the tier's own capacity) hosting scroll buttons, a
 * position indicator, and the chest's pluggable-upgrade slots (see {@code
 * UpgradeRegistry}). When capacity exceeds what fits above that row (45
 * slots), the top area becomes a scrollable window instead of showing
 * everything at once — scrolling redraws the same {@link Inventory} in
 * place, it never closes/reopens the view. A small chest (Normal, Copper,
 * single Iron) just gets a plain window sized to exactly {@code capacity + 9},
 * no scrolling machinery at all.
 */
public final class GuiFactory {

    private static final int CONTENT_WINDOW = 45;
    private static final int CONTROL_ROW_SIZE = 9;
    /** Control-row columns available for upgrade slots — 0, 4 and 8 are reserved for scroll/indicator. */
    private static final int[] UPGRADE_SLOT_COLUMNS = {1, 2, 3, 5, 6, 7};

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
        IcarusChestHolder holder = new IcarusChestHolder(chest.getId(), chest.getTier());
        Inventory inventory = Bukkit.createInventory(holder, guiSize(capacity), title(chest));
        holder.setInventory(inventory);
        holder.setScrollOffset(scrollOffset);

        populate(chest, holder, inventory);
        return inventory;
    }

    /**
     * Repopulates {@code inventory}'s content slots and control row from
     * {@code chest}, using {@code holder}'s current scroll offset. Never
     * recreates the inventory or changes its title/size — safe to call
     * repeatedly on the same open view (scrolling, installing an upgrade).
     */
    public static void populate(IcarusChest chest, IcarusChestHolder holder, Inventory inventory) {
        int capacity = chest.effectiveTotalCapacity();
        int visibleSlots = visibleContentSlots(capacity);
        int offset = holder.getScrollOffset();

        ItemStack[] contents = chest.getContents();
        for (int local = 0; local < visibleSlots; local++) {
            inventory.setItem(local, contents[offset + local]);
        }

        int rowStart = controlRowStart(capacity);
        boolean scrollable = isScrollable(capacity);
        int maxOffset = capacity - CONTENT_WINDOW;
        boolean canScrollUp = scrollable && offset > 0;
        boolean canScrollDown = scrollable && offset < maxOffset;

        for (int column = 0; column < CONTROL_ROW_SIZE; column++) {
            inventory.setItem(rowStart + column, controlItem(chest, column, canScrollUp, canScrollDown, offset, capacity));
        }
    }

    /** Number of content slots currently visible (excludes the control row). */
    public static int visibleSlotCount(IcarusChest chest) {
        return visibleContentSlots(chest.effectiveTotalCapacity());
    }

    /** Whether {@code localSlot} belongs to the reserved control row. */
    public static boolean isControlSlot(IcarusChest chest, int localSlot) {
        return localSlot >= controlRowStart(chest.effectiveTotalCapacity());
    }

    /** The upgrade slot index a control-row slot corresponds to, if that column is active for this chest's tier. */
    public static OptionalInt upgradeSlotIndex(IcarusChest chest, int localSlot) {
        int capacity = chest.effectiveTotalCapacity();
        int rowStart = controlRowStart(capacity);
        if (localSlot < rowStart) {
            return OptionalInt.empty();
        }
        int column = localSlot - rowStart;
        int slotCount = chest.getTier().upgradeSlotCount();
        for (int i = 0; i < UPGRADE_SLOT_COLUMNS.length && i < slotCount; i++) {
            if (UPGRADE_SLOT_COLUMNS[i] == column) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Copies the currently visible content slots of {@code inventory} back
     * into {@code chest}'s backing array at {@code holder}'s scroll offset,
     * marking it dirty. Called on every GUI close and before scrolling, so
     * edits are never lost mid-session.
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

    /** Clamped scroll target for a {@link NavAction}; equal to {@code currentOffset} if the move isn't possible. */
    public static int scrollTarget(IcarusChest chest, int currentOffset, NavAction action) {
        int maxOffset = Math.max(0, chest.effectiveTotalCapacity() - CONTENT_WINDOW);
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

    public static boolean isScrollable(int capacity) {
        return capacity > CONTENT_WINDOW;
    }

    private static int guiSize(int capacity) {
        return isScrollable(capacity) ? (CONTENT_WINDOW + CONTROL_ROW_SIZE) : (capacity + CONTROL_ROW_SIZE);
    }

    private static int visibleContentSlots(int capacity) {
        return isScrollable(capacity) ? CONTENT_WINDOW : capacity;
    }

    private static int controlRowStart(int capacity) {
        return guiSize(capacity) - CONTROL_ROW_SIZE;
    }

    private static ItemStack controlItem(IcarusChest chest, int column, boolean canScrollUp, boolean canScrollDown, int offset, int capacity) {
        if (column == 0 && canScrollUp) {
            return navItem(Material.ARROW, "▲ Rolar para Cima",
                    "Sobe uma fileira. A roda do mouse tambem funciona.", NavAction.SCROLL_UP);
        }
        if (column == 8 && canScrollDown) {
            return navItem(Material.ARROW, "▼ Rolar para Baixo",
                    "Desce uma fileira. A roda do mouse tambem funciona.", NavAction.SCROLL_DOWN);
        }
        if (column == 4) {
            return positionIndicator(offset, capacity);
        }

        int upgradeSlot = upgradeColumnIndex(chest.getTier(), column);
        if (upgradeSlot >= 0) {
            ItemStack installed = chest.getUpgrades()[upgradeSlot];
            return installed != null ? installed : emptyUpgradeSlot();
        }
        return filler();
    }

    private static int upgradeColumnIndex(ChestTier tier, int column) {
        int slotCount = tier.upgradeSlotCount();
        for (int i = 0; i < UPGRADE_SLOT_COLUMNS.length && i < slotCount; i++) {
            if (UPGRADE_SLOT_COLUMNS[i] == column) {
                return i;
            }
        }
        return -1;
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
        int lastVisibleRow = Math.min(totalRows, firstVisibleRow + (CONTENT_WINDOW / 9) - 1);
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Fileiras " + firstVisibleRow + "–" + lastVisibleRow + " de " + totalRows,
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack emptyUpgradeSlot() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Slot de Upgrade Vazio", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Arraste um upgrade aqui para instalar.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
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
