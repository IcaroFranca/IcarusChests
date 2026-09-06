package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.persistence.PersistedUpgrade;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * In-memory registry of known {@link IcarusChest}s (always "primaries" — see
 * below), keyed both by id and by location for O(1) lookup either way.
 *
 * <p>The block's own PDC tags remain the fast, synchronous way to tell "is
 * this one of ours, and what's its id/tier" (survives restarts on their own,
 * since tile entity PDC is persisted by the server with the chunk). SQLite is
 * the authority for contents and upgrades: a cache miss reconstructs the
 * chest synchronously from PDC first (so callers on the main thread get an
 * answer immediately, with a correctly-sized but possibly-blank contents
 * array), then kicks off two async loads from {@link ChestRepository} —
 * contents and upgrades — that fill in the real state once both complete.
 * That combined completion is tracked per chest id (see {@link
 * #isReady(UUID)}/{@link #whenReady}); anything that reads, mutates, or
 * persists a chest right after obtaining it (opening its GUI, applying a
 * tier-upgrade kit) goes through {@link #whenReady} first, so it can never
 * act on a still-blank array a moment before hydration silently replaces it
 * — only right after a fresh server start does this actually defer anything,
 * and only by however long the two SQLite reads take (typically well under a
 * tick). Breaking a chest in that same handful-of-ticks window is the one
 * caller that doesn't wait; a narrow, acknowledged edge case for now.
 *
 * <p><b>Double chests:</b> a chest linked into a double chest has exactly one
 * {@link IcarusChest} (the "primary", whichever half existed first). Its
 * partner ("secondary") block carries only a {@code LINK_TARGET} PDC tag
 * pointing at the primary's location — no id, no tier, no cache entry, no
 * database row of its own. {@link #getOrLoadFromBlock(Block)} transparently
 * resolves a secondary's block to the primary's {@code IcarusChest}, so every
 * other caller (GUI, upgrades, breaking) never needs to know which physical
 * half it was actually given.
 */
public final class ChestManager {

    private final Map<UUID, IcarusChest> byId = new ConcurrentHashMap<>();
    private final Map<ChestLocation, UUID> idByLocation = new ConcurrentHashMap<>();
    /** Chests currently mid-hydration (see {@link #loadFromBlock}); absent/done means safe to use. */
    private final Map<UUID, CompletableFuture<Void>> pendingHydration = new ConcurrentHashMap<>();
    private final ChestRepository chestRepository;
    private final UpgradeRegistry upgradeRegistry;
    private final Plugin plugin;

    public ChestManager(ChestRepository chestRepository, UpgradeRegistry upgradeRegistry, Plugin plugin) {
        this.chestRepository = chestRepository;
        this.upgradeRegistry = upgradeRegistry;
        this.plugin = plugin;
    }

    /** Async-hydrates {@code chest}'s upgrade slots from SQLite; call once right after registering a freshly reconstructed chest. */
    private CompletableFuture<Void> hydrateUpgradesAsync(IcarusChest chest) {
        return chestRepository.loadUpgrades(chest.getId())
                .thenCompose(bySlot -> runOnMainThread(() -> applyLoadedUpgrades(chest, bySlot)))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Falha ao carregar upgrades do bau " + chest.getId(), ex);
                    return null;
                });
    }

    /**
     * Runs {@code action} on the main thread and returns a future that completes only once it
     * actually ran — {@code Bukkit.getScheduler().runTask()} alone merely schedules it for a
     * later tick, so a plain {@code thenAccept(... runTask ...)} would let its own future resolve
     * before the scheduled work executes, defeating the whole point of {@link #whenReady}.
     */
    private CompletableFuture<Void> runOnMainThread(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private void applyLoadedUpgrades(IcarusChest chest, Map<Integer, PersistedUpgrade> bySlot) {
        ItemStack[] upgrades = chest.getUpgrades();
        bySlot.forEach((slotIndex, persisted) -> {
            if (slotIndex < 0 || slotIndex >= upgrades.length) {
                return;
            }
            UpgradeRegistry.parseType(persisted.upgradeType()).ifPresent(type -> {
                ItemStack item = upgradeRegistry.createItem(type);
                if (type == UpgradeType.FILTER && persisted.dataJson() != null) {
                    UpgradeRegistry.setFilterMaterials(item, UpgradeRegistry.parseFilterMaterials(persisted.dataJson()));
                }
                upgrades[slotIndex] = item;
            });
        });
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
     * Returns the (primary) chest for this block — resolving through a
     * double chest's secondary pointer if needed — falling back to
     * reconstructing it from PDC tags when not cached (e.g. first touch
     * after a server restart). Empty if the block isn't part of an
     * IcarusChest at all.
     */
    public Optional<IcarusChest> getOrLoadFromBlock(Block block) {
        ChestLocation location = ChestLocation.of(block);
        Optional<IcarusChest> cached = get(location);
        if (cached.isPresent()) {
            return cached;
        }
        return loadFromBlock(block);
    }

    /** Fast, PDC-only check for "is this a chest block we tagged" — no registration, no hydration, no world lookups. */
    public boolean isTaggedChest(Block block) {
        if (block.getType() != Material.CHEST || !(block.getState() instanceof TileState state)) {
            return false;
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        return pdc.has(NamespacedKeys.CHEST_ID, PersistentDataType.STRING)
                || pdc.has(NamespacedKeys.LINK_TARGET, PersistentDataType.STRING);
    }

    private Optional<IcarusChest> loadFromBlock(Block block) {
        // Chests are TileState (PersistentDataHolder), not just plain BlockState;
        // any other block (e.g. a player targeting stone) simply isn't one of ours.
        if (!(block.getState() instanceof TileState state)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();

        String linkTarget = pdc.get(NamespacedKeys.LINK_TARGET, PersistentDataType.STRING);
        if (linkTarget != null) {
            return resolveLinkTarget(linkTarget);
        }

        String chestIdRaw = pdc.get(NamespacedKeys.CHEST_ID, PersistentDataType.STRING);
        Integer tierOrdinal = pdc.get(NamespacedKeys.TIER, PersistentDataType.INTEGER);
        if (chestIdRaw == null || tierOrdinal == null) {
            return Optional.empty();
        }

        Optional<UUID> chestId = parseUuid(chestIdRaw);
        if (chestId.isEmpty()) {
            plugin.getLogger().log(Level.WARNING,
                    "Bloco em " + ChestLocation.of(block) + " tem CHEST_ID invalido no PDC: \"" + chestIdRaw + "\"; ignorando.");
            return Optional.empty();
        }

        Optional<ChestTier> tier = ChestTier.byOrdinal(tierOrdinal);
        if (tier.isEmpty()) {
            return Optional.empty();
        }

        IcarusChest chest = new IcarusChest(chestId.get(), ChestLocation.of(block), tier.get());
        Integer doubledFlag = pdc.get(NamespacedKeys.DOUBLED, PersistentDataType.INTEGER);
        if (doubledFlag != null && doubledFlag == 1) {
            chest.setDoubled(true);
            // Blank placeholder at the right (doubled) size; hydrateContentsAsync below fills in the real contents.
            chest.setContents(new ItemStack[chest.effectiveTotalCapacity()]);
        }
        register(chest);
        UUID chestId = chest.getId();
        CompletableFuture<Void> hydration = CompletableFuture.allOf(hydrateContentsAsync(chest), hydrateUpgradesAsync(chest));
        pendingHydration.put(chestId, hydration);
        hydration.whenComplete((ignoredResult, ignoredException) -> pendingHydration.remove(chestId, hydration));
        return Optional.of(chest);
    }

    /** Tolerates a corrupted/manually-edited {@code CHEST_ID} tag instead of letting {@code UUID.fromString} throw and break the whole event. */
    private Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<IcarusChest> resolveLinkTarget(String encodedLocation) {
        Optional<ChestLocation> primaryLocation = ChestLocation.decode(encodedLocation);
        if (primaryLocation.isEmpty()) {
            return Optional.empty();
        }
        World world = primaryLocation.get().world();
        if (world == null) {
            return Optional.empty(); // primary's world isn't loaded; shouldn't normally happen for an adjacent block
        }
        // A secondary always points directly at a true primary (see
        // ChestPlaceListener) — no pointer chains are ever created, so this
        // single extra hop can't recurse further.
        return getOrLoadFromBlock(primaryLocation.get().toBlock());
    }

    private CompletableFuture<Void> hydrateContentsAsync(IcarusChest chest) {
        return chestRepository.loadContents(chest.getId(), chest.effectiveTotalCapacity())
                .thenCompose(loaded -> loaded.isPresent()
                        ? runOnMainThread(() -> chest.setContents(loaded.get()))
                        : CompletableFuture.<Void>completedFuture(null))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Falha ao carregar conteudo persistido do bau " + chest.getId(), ex);
                    return null;
                });
    }

    /** Whether {@code chestId} is fully hydrated (or was never mid-hydration to begin with) — safe to read/mutate/persist. */
    public boolean isReady(UUID chestId) {
        CompletableFuture<Void> pending = pendingHydration.get(chestId);
        return pending == null || pending.isDone();
    }

    /**
     * Runs {@code action} on the main thread once {@code chestId} is fully hydrated — immediately,
     * if it already is (the overwhelmingly common case: anything but the first touch after a
     * server restart). Callers that read/mutate/persist a chest right after {@link
     * #getOrLoadFromBlock} (opening its GUI, applying a tier-upgrade kit) should always go through
     * this rather than acting on the chest directly, so a slow first load can never let them work
     * with a still-blank array that a moment later gets silently replaced by the real one.
     */
    public void whenReady(UUID chestId, Runnable action) {
        CompletableFuture<Void> pending = pendingHydration.get(chestId);
        if (pending == null || pending.isDone()) {
            action.run();
            return;
        }
        pending.whenComplete((ignoredResult, ignoredException) -> Bukkit.getScheduler().runTask(plugin, action));
    }
}
