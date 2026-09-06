package dev.icaro.icaruschests.config;

import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

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
    // Keyed by lowercase button name ("search", "organize") rather than an enum for the same
    // reason as above — the gui package already depends on config.
    private final Map<String, String> controlHeadTextures = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) {@code config.yml} from disk. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mergeNewDefaults();

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

        controlHeadTextures.clear();
        ConfigurationSection controlHeads = plugin.getConfig().getConfigurationSection("control-heads");
        if (controlHeads != null) {
            for (String key : controlHeads.getKeys(false)) {
                String texture = controlHeads.getString(key, "");
                if (texture != null && !texture.isBlank()) {
                    controlHeadTextures.put(key.toLowerCase(), texture.trim());
                }
            }
        }
    }

    /**
     * Fills in any key the jar's bundled {@code config.yml} defines but the
     * admin's on-disk file doesn't have yet (e.g. a texture added in a newer
     * plugin version) and persists the result. {@link JavaPlugin#saveDefaultConfig()}
     * only ever writes the file once, the very first time it's missing
     * entirely — a {@code config.yml} created by an older version of the
     * plugin would otherwise never pick up keys added later without the
     * admin deleting it by hand. A key that already has a value on disk
     * (blank included) is left exactly as it is; only genuinely missing
     * keys get filled in.
     */
    private void mergeNewDefaults() {
        try (InputStream defaultStream = plugin.getResource("config.yml")) {
            if (defaultStream == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            FileConfiguration config = plugin.getConfig();
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            plugin.saveConfig();
            plugin.reloadConfig();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao mesclar novos valores padrao no config.yml", e);
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

    /** The configured custom-head Base64 texture for a control-row button ({@code "search"}/{@code "organize"}), if set. */
    public Optional<String> controlHeadTexture(String buttonName) {
        return Optional.ofNullable(controlHeadTextures.get(buttonName.toLowerCase()));
    }
}
