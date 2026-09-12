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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tags a plain {@code Material.CHEST} block as a new {@link IcarusChest} — a fresh standalone
 * primary at {@link ChestTier#NORMAL}, or linked as a double chest's secondary half if adjacent to
 * an existing untagged... no, an existing already-tagged, still-single primary of the same tier.
 * Shared by {@code ChestPlaceListener} (a player just placed the block) and {@code
 * NaturalChestListener} (world generation just created it) — both want the exact same
 * tagging/linking/persistence logic, only reached through a different event, and the natural-
 * generation path additionally has real vanilla loot to carry over (see {@link #tagChestBlock}).
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
     *
     * @param naturalLoot a natural (world-generated) chest's own rolled vanilla loot to carry into
     *                     this block's share of the contents array (see {@code
     *                     NaturalChestListener}), sized to exactly {@link ChestTier#NORMAL}'s
     *                     capacity — {@code null} for a player-placed block, which never has any.
     */
    public void tagChestBlock(Block block, Optional<IcarusChest> neighborPrimary, ItemStack[] naturalLoot) {
        if (neighborPrimary.isPresent()) {
            linkAsSecondary(block, neighborPrimary.get(), naturalLoot);
        } else {
            IcarusChest chest = placeAsStandalone(block);
            if (naturalLoot != null && chest != null) {
                injectLoot(chest, naturalLoot, 0);
            }
        }
    }

    private IcarusChest placeAsStandalone(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return null;
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
        return chest;
    }

    private void linkAsSecondary(Block block, IcarusChest primary, ItemStack[] naturalLoot) {
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
        // loaded from disk. See ChestManager's docs on whenReady. Bundling the loot injection into
        // this same deferred callback (rather than doing it eagerly) is what keeps it race-free too.
        chestManager.whenReady(primary.getId(), () -> doubleCapacityAndInject(primary, naturalLoot));
    }

    private void doubleCapacityAndInject(IcarusChest primary, ItemStack[] naturalLoot) {
        // The offset where this specific (secondary) block's own inventory starts within the
        // shared array — doubling always grows it by appending the new half at the end, so this is
        // simply whatever size the array was before growing.
        int offset = primary.effectiveTotalCapacity();
        // Doubling only ever grows the array, so this copyOf can't lose data —
        // unlike unlinking (ChestBreakListener), which must drop overflow first.
        int doubledCapacity = primary.getTier().totalCapacity() * 2;
        primary.setContents(Arrays.copyOf(primary.getContents(), doubledCapacity));
        primary.setDoubled(true);
        if (naturalLoot != null) {
            injectLoot(primary, naturalLoot, offset);
        }
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

    private void injectLoot(IcarusChest chest, ItemStack[] loot, int offset) {
        ItemStack[] contents = chest.getContents();
        System.arraycopy(loot, 0, contents, offset, Math.min(loot.length, contents.length - offset));
    }

    private void retagDoubled(Block block, boolean doubled) {
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        state.getPersistentDataContainer().set(NamespacedKeys.DOUBLED, PersistentDataType.INTEGER, doubled ? 1 : 0);
        state.update(true);
    }
}
