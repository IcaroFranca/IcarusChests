package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * In-memory registry of known {@link IcarusChest}s, keyed both by id and by
 * location for O(1) lookup either way.
 *
 * <p>The block's own PDC tags remain the fast, synchronous way to tell "is
 * this one of ours, and what's its id/tier" (survives restarts on their own,
 * since tile entity PDC is persisted by the server with the chunk). SQLite is
 * the authority for contents: a cache miss reconstructs the chest
 * synchronously from PDC first (so callers on the main thread get an answer
 * immediately, with a correctly-sized but possibly-blank contents array),
 * then kicks off an async load from {@link ChestRepository} that fills in the
 * real contents once it completes. A GUI opened in the handful of ticks
 * between those two steps (only possible right after a fresh server start)
 * would see it as empty; this is a known, rare edge case for now.
 */
public final class ChestManager {

    private final Map<UUID, IcarusChest> byId = new ConcurrentHashMap<>();
    private final Map<ChestLocation, UUID> idByLocation = new ConcurrentHashMap<>();
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestManager(ChestRepository chestRepository, Plugin plugin) {
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    public IcarusChest register(IcarusChest chest) {
        byId.put(chest.getId(), chest);
        idByLocation.put(chest.getLocation(), chest.getId());
        return chest;
    }

    public Optional<IcarusChest> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<IcarusChest> get(ChestLocation location) {
        UUID id = idByLocation.get(location);
        return id == null ? Optional.empty() : get(id);
    }

    public void unregister(ChestLocation location) {
        UUID id = idByLocation.remove(location);
        if (id != null) {
            byId.remove(id);
        }
    }

    /** All chests currently held in memory (touched at least once this run). Used by the autosave sweep. */
    public Collection<IcarusChest> all() {
        return byId.values();
    }

    /**
     * Returns the cached chest at this location, falling back to
     * reconstructing it from the block's own PDC tags when not cached (e.g.
     * first touch after a server restart). Returns empty if the block is not
     * tagged as an IcarusChest at all.
     */
    public Optional<IcarusChest> getOrLoadFromBlock(Block block) {
        ChestLocation location = ChestLocation.of(block);
        Optional<IcarusChest> cached = get(location);
        if (cached.isPresent()) {
            return cached;
        }
        return loadFromBlock(block);
    }

    private Optional<IcarusChest> loadFromBlock(Block block) {
        // Chests are TileState (PersistentDataHolder), not just plain BlockState;
        // any other block (e.g. a player targeting stone) simply isn't one of ours.
        if (!(block.getState() instanceof TileState state)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();

        String chestIdRaw = pdc.get(NamespacedKeys.CHEST_ID, PersistentDataType.STRING);
        Integer tierOrdinal = pdc.get(NamespacedKeys.TIER, PersistentDataType.INTEGER);
        if (chestIdRaw == null || tierOrdinal == null) {
            return Optional.empty();
        }

        Optional<ChestTier> tier = ChestTier.byOrdinal(tierOrdinal);
        if (tier.isEmpty()) {
            return Optional.empty();
        }

        IcarusChest chest = new IcarusChest(UUID.fromString(chestIdRaw), ChestLocation.of(block), tier.get());
        register(chest);
        hydrateContentsAsync(chest);
        return Optional.of(chest);
    }

    private void hydrateContentsAsync(IcarusChest chest) {
        chestRepository.loadContents(chest.getId(), chest.getTier().totalCapacity())
                .thenAccept(loaded -> loaded.ifPresent(contents ->
                        Bukkit.getScheduler().runTask(plugin, () -> chest.setContents(contents))))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Falha ao carregar conteudo persistido do bau " + chest.getId(), ex);
                    return null;
                });
    }
}
