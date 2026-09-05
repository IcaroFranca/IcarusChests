package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of known {@link IcarusChest}s, keyed both by id and by
 * location for O(1) lookup either way.
 *
 * <p>M2 scope: this is the only source of truth (no SQLite yet), populated
 * eagerly on place/break and lazily via {@link #loadFromBlock(Block)} when a
 * block is touched but not yet cached (e.g. after a server restart, since
 * this map is never persisted itself — only the block's PDC survives a
 * restart). From M4 onward, SQLite becomes the authority for tier/contents
 * and this cache is populated from it instead of solely from the block PDC.
 */
public final class ChestManager {

    private final Map<UUID, IcarusChest> byId = new ConcurrentHashMap<>();
    private final Map<ChestLocation, UUID> idByLocation = new ConcurrentHashMap<>();

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
        BlockState state = block.getState();
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
        return Optional.of(chest);
    }
}
