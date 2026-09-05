package dev.icaro.icaruschests.config;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin wrapper around {@code config.yml}. Deliberately minimal for the MVP —
 * tier capacities/materials stay compile-time constants in {@code ChestTier}
 * rather than becoming a fully data-driven registry; only operational
 * settings (autosave cadence) are configurable so far.
 */
public final class ConfigManager {

    private static final int DEFAULT_AUTOSAVE_SECONDS = 300;
    private static final long MIN_AUTOSAVE_TICKS = 20L; // 1 second

    private final JavaPlugin plugin;
    private long autosaveIntervalTicks = DEFAULT_AUTOSAVE_SECONDS * 20L;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) {@code config.yml} from disk. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        int seconds = plugin.getConfig().getInt("autosave-interval-seconds", DEFAULT_AUTOSAVE_SECONDS);
        this.autosaveIntervalTicks = Math.max(MIN_AUTOSAVE_TICKS, seconds * 20L);
    }

    public long autosaveIntervalTicks() {
        return autosaveIntervalTicks;
    }
}
