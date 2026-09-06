package dev.icaro.icaruschests.util;

import dev.icaro.icaruschests.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Level;

/**
 * Exports every custom-head Base64 texture IcarusChests currently uses (tier
 * upgrade kits, pluggable upgrades, control-row buttons) into a Geyser
 * "custom skulls" mappings file, so Bedrock players (via Geyser/Floodgate)
 * see the actual configured skin instead of a blank default head.
 *
 * <p>Without this, Geyser has no way to know these textures exist: it only
 * ever registers skulls it's told about up front, at its OWN startup (see
 * Geyser's {@code GeyserDefineCustomSkullsEvent} javadoc — "Called on
 * Geyser's startup") — nothing a plugin does at runtime after that point
 * reaches it. Confirmed straight from Geyser's own source
 * ({@code MappingsConfigReader}/{@code SkullMappingsReader_v1}): it scans
 * every {@code *.json} file under its own {@code custom_mappings} folder,
 * each expected to look like:
 * <pre>{@code
 * {
 *   "format_version": 1,
 *   "skulls": { "profile": [ "<base64 texture>", ... ] }
 * }
 * }</pre>
 * That's plain file I/O, so this never needs a compile- or run-time
 * dependency on Geyser's own jar/API — it just writes the file if Geyser
 * happens to be installed, and does nothing at all otherwise.
 *
 * <p>Geyser itself must be restarted (not just IcarusChests) to actually
 * pick up a changed file, since — per that same javadoc — the scan only
 * happens once, at Geyser's own boot.
 */
public final class GeyserSkullExport {

    private static final String GEYSER_PLUGIN_NAME = "Geyser-Spigot";
    private static final String MAPPINGS_FILE_NAME = "icaruschests-skulls.json";

    private GeyserSkullExport() {
    }

    public static void export(Plugin icarusChests, ConfigManager configManager) {
        Plugin geyser = Bukkit.getPluginManager().getPlugin(GEYSER_PLUGIN_NAME);
        if (geyser == null) {
            return; // Geyser isn't installed on this server: nothing to export to
        }
        Set<String> textures = configManager.allConfiguredHeadTextures();
        if (textures.isEmpty()) {
            return;
        }

        Path mappingsDir = geyser.getDataFolder().toPath().resolve("custom_mappings");
        Path mappingsFile = mappingsDir.resolve(MAPPINGS_FILE_NAME);
        try {
            Files.createDirectories(mappingsDir);
            Files.writeString(mappingsFile, toJson(textures), StandardCharsets.UTF_8);
            icarusChests.getLogger().info("Exportadas " + textures.size()
                    + " textura(s) de cabeça customizada para o Geyser (" + mappingsFile
                    + ") — reinicie o Geyser (nao so o IcarusChests) para os jogadores Bedrock verem as texturas certas.");
        } catch (IOException e) {
            icarusChests.getLogger().log(Level.WARNING,
                    "Falha ao exportar texturas de cabeça customizada para o Geyser", e);
        }
    }

    /** Base64 texture values only ever contain {@code [A-Za-z0-9+/=]}, so no JSON-string escaping is needed. */
    private static String toJson(Set<String> textures) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"format_version\": 1,\n  \"skulls\": {\n    \"profile\": [\n");
        int index = 0;
        for (String texture : textures) {
            json.append("      \"").append(texture).append('"');
            if (++index < textures.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("    ]\n  }\n}\n");
        return json.toString();
    }
}
