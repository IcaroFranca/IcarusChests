package dev.icaro.icaruschests.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central registry of {@link NamespacedKey}s used to tag blocks and items
 * with plugin-owned {@code PersistentDataContainer} data. Must be
 * {@link #init(JavaPlugin)}-ed once during {@code onEnable} before any key
 * is read.
 */
public final class NamespacedKeys {

    private NamespacedKeys() {
    }

    /** Tag on a chest tile entity's PDC identifying its {@code ChestTier} ordinal. */
    public static NamespacedKey TIER;

    /** Tag on a chest tile entity's PDC identifying its unique {@code IcarusChest} id (UUID string). */
    public static NamespacedKey CHEST_ID;

    /** Tag on an upgrade kit item's PDC identifying the target {@code ChestTier} ordinal. */
    public static NamespacedKey UPGRADE_KIT_TIER;

    /** Tag on a GUI navigation button item's PDC identifying it as a page-switch control. */
    public static NamespacedKey NAV_ACTION;

    /**
     * Tag on a double chest's secondary half pointing at its primary's
     * encoded {@code ChestLocation} (see {@link dev.icaro.icaruschests.model.ChestLocation#encode()}).
     * A block carrying this tag has no {@link #CHEST_ID}/{@link #TIER} of its
     * own — it always resolves through to the primary.
     */
    public static NamespacedKey LINK_TARGET;

    /** Tag on a double chest primary's own PDC: {@code 1} while it has a linked secondary, absent/{@code 0} otherwise. */
    public static NamespacedKey DOUBLED;

    public static void init(JavaPlugin plugin) {
        TIER = new NamespacedKey(plugin, "tier");
        CHEST_ID = new NamespacedKey(plugin, "chest_id");
        UPGRADE_KIT_TIER = new NamespacedKey(plugin, "upgrade_kit_tier");
        NAV_ACTION = new NamespacedKey(plugin, "nav_action");
        LINK_TARGET = new NamespacedKey(plugin, "link_target");
        DOUBLED = new NamespacedKey(plugin, "doubled");
    }
}
