package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.BlockFaces;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tags a plain {@code Material.CHEST} block as a new {@link IcarusChest} — a fresh standalone
 * primary at {@link ChestTier#NORMAL}, or linked as a double chest's secondary half if adjacent to
 * an existing, still-single primary of the same tier. Used only by {@code ChestPlaceListener},
 * which checks the placed item is a {@code StarterChestRegistry} starter chest before ever calling
 * in here — a plain vanilla chest placed from anywhere else never reaches this class at all.
 */
public final class ChestTaggingService {

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestTaggingService(ChestManager chestManager, ChestRepository chestRepository, Plugin plugin) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    /**
     * The adjacent chest this block should link to as a double chest, if
     * any. Only matches a neighbor that is ITSELF the primary (its resolved
     * chest's location equals its own) — never a neighbor that is already a
     * secondary pointing elsewhere, since vanilla double chests are always
     * exactly two adjacent blocks, never a chain of three or more — and never
     * a primary that's already doubled (it already has its one partner).
     */
    public Optional<IcarusChest> findAdjacentPrimary(Block block) {
        for (BlockFace face : BlockFaces.HORIZONTAL) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != Material.CHEST) {
                continue;
            }
            Optional<IcarusChest> resolved = chestManager.getOrLoadFromBlock(neighbor);
            if (resolved.isEmpty()) {
                continue;
            }
            IcarusChest candidate = resolved.get();
            boolean neighborIsItselfThePrimary = ChestLocation.of(neighbor).equals(candidate.getLocation());
            if (neighborIsItselfThePrimary && !candidate.isDoubled()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /**
     * Tags {@code block} as a new IcarusChest at {@link ChestTier#NORMAL}, or links it as {@code
     * neighborPrimary}'s secondary half if present — callers decide beforehand whether linking is
     * even allowed at all (see {@code ChestPlaceListener}'s tier-mismatch rejection, which runs
     * before this is ever called and simply never calls it in that case).
     */
    public void tagChestBlock(Block block, Optional<IcarusChest> neighborPrimary) {
        if (neighborPrimary.isPresent()) {
            linkAsSecondary(block, neighborPrimary.get());
        } else {
            placeAsStandalone(block);
        }
    }

    private void placeAsStandalone(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        UUID chestId = UUID.randomUUID();

        state.getPersistentDataContainer().set(NamespacedKeys.CHEST_ID, PersistentDataType.STRING, chestId.toString());
        state.getPersistentDataContainer().set(NamespacedKeys.TIER, PersistentDataType.INTEGER, ChestTier.NORMAL.ordinal());
        state.update(true);

        IcarusChest chest = chestManager.register(new IcarusChest(chestId, ChestLocation.of(block), ChestTier.NORMAL));
        chestRepository.insert(chest).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir novo bau " + chestId, ex);
            return null;
        });
    }

    private void linkAsSecondary(Block block, IcarusChest primary) {
        if (block.getState() instanceof TileState state) {
            // No CHEST_ID/TIER here on purpose: this block has no independent
            // identity, it only ever resolves through to the primary.
            state.getPersistentDataContainer().set(
                    NamespacedKeys.LINK_TARGET, PersistentDataType.STRING, primary.getLocation().encode());
            state.update(true);
        }

        // Deferred until the primary's own async hydration (if any is still in flight — its very
        // first touch since a server restart, via the getOrLoadFromBlock call in
        // findAdjacentPrimary) finishes: doing this resize immediately could otherwise race
        // hydrateContentsAsync's own main-thread chest.setContents(loaded) completing a moment
        // later and silently overwriting this doubled array with the still-single-sized one it
        // loaded from disk. See ChestManager's docs on whenReady.
        chestManager.whenReady(primary.getId(), () -> doubleCapacity(primary));
    }

    private void doubleCapacity(IcarusChest primary) {
        // Doubling only ever grows the array, so this copyOf can't lose data —
        // unlike unlinking (ChestBreakListener), which must drop overflow first.
        int doubledCapacity = primary.getTier().totalCapacity() * 2;
        primary.setContents(Arrays.copyOf(primary.getContents(), doubledCapacity));
        primary.setDoubled(true);
        primary.setDirty(true);
        retagDoubled(primary.getLocation().toBlock(), true);
        chestRepository.insert(primary).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir link do bau duplo " + primary.getId(), ex);
            return null;
        });
        chestRepository.saveContents(primary).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir conteudo redimensionado do bau " + primary.getId(), ex);
            return null;
        });
    }

    private void retagDoubled(Block block, boolean doubled) {
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        state.getPersistentDataContainer().set(NamespacedKeys.DOUBLED, PersistentDataType.INTEGER, doubled ? 1 : 0);
        state.update(true);
    }
}
