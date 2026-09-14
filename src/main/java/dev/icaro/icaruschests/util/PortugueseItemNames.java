package dev.icaro.icaruschests.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Portuguese (pt_br) display names for vanilla {@link Material}s, for matching a Search query
 * typed in Portuguese against an item whose only server-known name is its English internal one —
 * Bukkit/Paper never ships client-side translations, so without this the server has no way to
 * know "diamante" means {@code DIAMOND}.
 *
 * <p>Bundled straight from Mojang's own official {@code pt_br.json} client language file (a real
 * asset extracted from an actual Minecraft install, not machine-translated or guessed), keyed
 * exactly by {@link Material#translationKey()} (e.g. {@code "item.minecraft.diamond"}, {@code
 * "block.minecraft.chest"}) — that method already knows whether a given material's *real* in-game
 * translation lives under the {@code item.} or {@code block.} namespace, so the lookup here never
 * needs to guess that itself.
 */
public final class PortugueseItemNames {

    private static final String RESOURCE_PATH = "lang/item_names_pt_br.json";
    private static Map<String, String> byTranslationKey = Collections.emptyMap();

    private PortugueseItemNames() {
    }

    /** Loads the bundled translation table once, at plugin startup. A missing/corrupt resource just means no PT matches — never a startup failure. */
    public static void init(Plugin plugin) {
        try (InputStream stream = plugin.getResource(RESOURCE_PATH)) {
            if (stream == null) {
                plugin.getLogger().warning("Recurso " + RESOURCE_PATH + " nao encontrado no jar - busca em portugues desativada.");
                return;
            }
            Type mapType = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> loaded = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), mapType);
            byTranslationKey = loaded != null ? loaded : Collections.emptyMap();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao carregar " + RESOURCE_PATH, e);
        }
    }

    /** The Portuguese display name for {@code material}, if the bundled table has one. */
    public static Optional<String> of(Material material) {
        return Optional.ofNullable(byTranslationKey.get(material.translationKey()));
    }
}
