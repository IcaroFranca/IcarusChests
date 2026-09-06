package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Refuses to let any of the plugin's own special items — a tier upgrade kit,
 * a pluggable upgrade (Filter/Stack), or a control-row button (Search/
 * Organize) — be placed as a block. Most of these render as a {@code
 * PLAYER_HEAD} custom head (see {@code CustomHeads}), which is otherwise a
 * perfectly placeable vanilla block; placing one would turn a functional item
 * into a decorative world block, silently discarding whatever the plugin's
 * own PDC data on it meant (which upgrade, which tier, …). The same check
 * covers an admin's still-unconfigured fallback icon too (an ore block for a
 * Stack upgrade, a hopper for Filter, …), since those carry the exact same
 * PDC tags — not just the head variant.
 */
public final class SpecialItemProtectionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isProtectedSpecialItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedSpecialItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(NamespacedKeys.UPGRADE_KIT_TIER, PersistentDataType.INTEGER)
                || pdc.has(NamespacedKeys.UPGRADE_TYPE, PersistentDataType.STRING)
                || pdc.has(NamespacedKeys.CONTROL_BUTTON, PersistentDataType.STRING);
    }
}
