package dev.icaro.icaruschests.config;

import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thin wrapper around {@code config.yml}. Deliberately minimal for the MVP —
 * tier capacities/materials stay compile-time constants in {@code ChestTier}
 * rather than becoming a fully data-driven registry; operational settings
 * (autosave cadence) and cosmetic overrides (upgrade kit head textures) are
 * what's configurable so far.
 */
public final class ConfigManager {

    private static final int DEFAULT_AUTOSAVE_SECONDS = 300;
    private static final long MIN_AUTOSAVE_TICKS = 20L; // 1 second

    private final JavaPlugin plugin;
    private long autosaveIntervalTicks = DEFAULT_AUTOSAVE_SECONDS * 20L;
    private final Map<ChestTier, String> upgradeKitHeadTextures = new EnumMap<>(ChestTier.class);
    // Keyed by lowercase UpgradeType name rather than the enum itself: the upgrade
    // package already depends on config, so config depending back on upgrade would cycle.
    private final Map<String, String> upgradeHeadTextures = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) {@code config.yml} from disk. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        int seconds = plugin.getConfig().getInt("autosave-interval-seconds", DEFAULT_AUTOSAVE_SECONDS);
        this.autosaveIntervalTicks = Math.max(MIN_AUTOSAVE_TICKS, seconds * 20L);

        upgradeKitHeadTextures.clear();
        ConfigurationSection kitHeads = plugin.getConfig().getConfigurationSection("upgrade-kit-heads");
        if (kitHeads != null) {
            for (ChestTier tier : ChestTier.values()) {
                String texture = kitHeads.getString(tier.name().toLowerCase(), "");
                if (texture != null && !texture.isBlank()) {
                    upgradeKitHeadTextures.put(tier, texture.trim());
                }
            }
        }

        upgradeHeadTextures.clear();
        ConfigurationSection upgradeHeads = plugin.getConfig().getConfigurationSection("upgrade-heads");
        if (upgradeHeads != null) {
            for (String key : upgradeHeads.getKeys(false)) {
                String texture = upgradeHeads.getString(key, "");
                if (texture != null && !texture.isBlank()) {
                    upgradeHeadTextures.put(key.toLowerCase(), texture.trim());
                }
            }
        }
    }

    public long autosaveIntervalTicks() {
        return autosaveIntervalTicks;
    }

    /** The configured custom-head Base64 texture for this tier's upgrade kit, if the admin set one. */
    public Optional<String> upgradeKitHeadTexture(ChestTier tier) {
        return Optional.ofNullable(upgradeKitHeadTextures.get(tier));
    }

    /** The configured custom-head Base64 texture for a pluggable upgrade (matched case-insensitively by name), if set. */
    public Optional<String> upgradeHeadTexture(String upgradeName) {
        return Optional.ofNullable(upgradeHeadTextures.get(upgradeName.toLowerCase()));
    }
}
