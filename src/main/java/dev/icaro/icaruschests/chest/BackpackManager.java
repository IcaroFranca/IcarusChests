package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.IcarusBackpack;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.persistence.PersistedUpgrade;
import dev.icaro.icaruschests.tier.BackpackTier;
import dev.icaro.icaruschests.upgrade.UpgradeRegistry;
import dev.icaro.icaruschests.upgrade.UpgradeType;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * In-memory registry of known {@link IcarusBackpack}s, keyed by id only — unlike {@link
 * ChestManager} there's no location to key by, since a backpack's identity lives entirely on the
 * carried item's own PDC (see {@link #idOf}). Reuses {@link ChestRepository} as-is for content and
 * upgrade persistence (both already keyed purely by id, with no notion of a location baked in),
 * and follows the exact same lazy-hydration shape {@code ChestManager} does — a cache miss
 * reconstructs a correctly-tiered but possibly-blank backpack synchronously from the item's own
 * PDC, then kicks off the same two async SQLite loads (contents, upgrades) that fill in the real
 * state once both complete; see {@link #whenReady} for why anything that acts on a freshly
 * resolved backpack should go through it first.
 */
public final class BackpackManager {

    private final Map<UUID, IcarusBackpack> byId = new ConcurrentHashMap<>();
    /** Backpacks currently mid-hydration (see {@link #loadFromItem}); absent/done means safe to use. */
    private final Map<UUID, CompletableFuture<Void>> pendingHydration = new ConcurrentHashMap<>();
    private final ChestRepository chestRepository;
    private final UpgradeRegistry upgradeRegistry;
    private final Plugin plugin;

    public BackpackManager(ChestRepository chestRepository, UpgradeRegistry upgradeRegistry, Plugin plugin) {
        this.chestRepository = chestRepository;
        this.upgradeRegistry = upgradeRegistry;
        this.plugin = plugin;
    }

    public Optional<IcarusBackpack> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Registers a brand-new backpack (just crafted — see {@code BackpackRecipeListener}) as fully hydrated, empty, and cached. */
    public void register(IcarusBackpack backpack) {
        byId.put(backpack.getId(), backpack);
    }

    /**
     * If {@code backpackId} happens to already be cached this session, resizes it in place to
     * {@code newTier}'s capacity right away (preserving every item by index, same as a chest's own
     * tier upgrade) and marks it dirty so the next autosave/close persists the wider contents blob.
     * Does nothing if it isn't cached — the item's own PDC already carries the new tier by the time
     * this is called (see {@code BackpackRecipeListener}), so the next time anything actually loads
     * it, {@link dev.icaro.icaruschests.persistence.ChestRepository#loadContents} resizes to fit
     * whatever capacity it's asked for regardless; nothing here is load-bearing for correctness,
     * only for a same-session reopen to see the bump immediately rather than a session later.
     */
    public void bumpTierIfCached(UUID backpackId, BackpackTier newTier) {
        IcarusBackpack backpack = byId.get(backpackId);
        if (backpack == null) {
            return;
        }
        backpack.setContents(Arrays.copyOf(backpack.getContents(), newTier.totalCapacity()));
        backpack.setUpgrades(Arrays.copyOf(backpack.getUpgrades(), newTier.upgradeSlotCount()));
        backpack.setTier(newTier);
        backpack.setDirty(true);
    }

    /** All backpacks currently held in memory (touched at least once this run). Used by the autosave sweep. */
    public Collection<IcarusBackpack> all() {
        return byId.values();
    }

    /** The {@code BACKPACK_ID} an item's PDC carries, if it's a backpack of ours at all (a corrupted/foreign tag reads as "not one"). */
    public static Optional<UUID> idOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.BACKPACK_ID, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Resolves the backpack a tagged item's PDC points at, hydrating from SQLite on first touch. Empty if {@code item} isn't a backpack at all. */
    public Optional<IcarusBackpack> getOrLoad(ItemStack item) {
        Optional<UUID> id = idOf(item);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Optional<IcarusBackpack> cached = get(id.get());
        if (cached.isPresent()) {
            return cached;
        }
        return loadFromItem(item, id.get());
    }

    private Optional<IcarusBackpack> loadFromItem(ItemStack item, UUID id) {
        Integer tierOrdinal = item.getItemMeta().getPersistentDataContainer().get(NamespacedKeys.BACKPACK_TIER, PersistentDataType.INTEGER);
        if (tierOrdinal == null) {
            return Optional.empty();
        }
        Optional<BackpackTier> tier = BackpackTier.byOrdinal(tierOrdinal);
        if (tier.isEmpty()) {
            return Optional.empty();
        }

        IcarusBackpack backpack = new IcarusBackpack(id, tier.get());
        byId.put(id, backpack);
        CompletableFuture<Void> hydration = CompletableFuture.allOf(hydrateContentsAsync(backpack), hydrateUpgradesAsync(backpack));
        pendingHydration.put(id, hydration);
        hydration.whenComplete((ignoredResult, ignoredException) -> pendingHydration.remove(id, hydration));
        return Optional.of(backpack);
    }

    private CompletableFuture<Void> hydrateContentsAsync(IcarusBackpack backpack) {
        return chestRepository.loadContents(backpack.getId(), backpack.effectiveTotalCapacity())
                .thenCompose(loaded -> loaded.isPresent()
                        ? runOnMainThread(() -> backpack.setContents(loaded.get()))
                        : CompletableFuture.<Void>completedFuture(null))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Falha ao carregar conteudo persistido da mochila " + backpack.getId(), ex);
                    return null;
                });
    }

    private CompletableFuture<Void> hydrateUpgradesAsync(IcarusBackpack backpack) {
        return chestRepository.loadUpgrades(backpack.getId())
                .thenCompose(bySlot -> runOnMainThread(() -> applyLoadedUpgrades(backpack, bySlot)))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Falha ao carregar upgrades da mochila " + backpack.getId(), ex);
                    return null;
                });
    }

    private void applyLoadedUpgrades(IcarusBackpack backpack, Map<Integer, PersistedUpgrade> bySlot) {
        ItemStack[] upgrades = backpack.getUpgrades();
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

    /** See {@code ChestManager}'s identical method — same reasoning applies verbatim here. */
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

    /** Whether {@code backpackId} is fully hydrated (or was never mid-hydration to begin with) — safe to read/mutate/persist. */
    public boolean isReady(UUID backpackId) {
        CompletableFuture<Void> pending = pendingHydration.get(backpackId);
        return pending == null || pending.isDone();
    }

    /** Runs {@code action} on the main thread once {@code backpackId} is fully hydrated — see {@code ChestManager#whenReady}'s identical reasoning. */
    public void whenReady(UUID backpackId, Runnable action) {
        CompletableFuture<Void> pending = pendingHydration.get(backpackId);
        if (pending == null || pending.isDone()) {
            action.run();
            return;
        }
        pending.whenComplete((ignoredResult, ignoredException) -> Bukkit.getScheduler().runTask(plugin, action));
    }
}
