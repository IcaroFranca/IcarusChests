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

    public static void init(JavaPlugin plugin) {
        TIER = new NamespacedKey(plugin, "tier");
        CHEST_ID = new NamespacedKey(plugin, "chest_id");
        UPGRADE_KIT_TIER = new NamespacedKey(plugin, "upgrade_kit_tier");
    }
}
