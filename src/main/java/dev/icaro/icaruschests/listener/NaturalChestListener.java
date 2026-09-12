package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.chest.ChestTaggingService;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import java.util.Optional;
import java.util.Random;

/**
 * Tags every naturally generated chest — dungeons, mineshafts, villages, temples, shipwrecks,
 * bastions, ancient cities, buried treasure, ... — as an {@link IcarusChest} the very first time
 * its chunk ever generates, exactly like a player-placed one (see {@code ChestPlaceListener}, which
 * shares the actual tagging/linking logic via {@code ChestTaggingService}): same {@link
 * ChestTier#NORMAL} tier, same double-chest linking rules for a structure that happens to place two
 * adjacent chests.
 *
 * <p>A natural chest still carries a vanilla {@link LootTable} — the game normally rolls that the
 * very first time ANYTHING accesses its real container, a player opening it or even just a hopper
 * underneath pulling from it — but an IcarusChest's block-level vanilla inventory stays permanently
 * empty on purpose (see {@code ChestManager}'s docs) and its GUI is never the vanilla one, so that
 * roll would simply never happen on its own and the loot would vanish for good. Instead, {@link
 * #rollAndClearLoot} rolls it here, into a scratch inventory, and the result is copied straight into
 * the new {@link IcarusChest}'s own contents (see {@code ChestTaggingService#tagChestBlock}) — then
 * the vanilla loot table is cleared from the block so nothing can roll it a second, redundant time
 * later (that hopper-underneath case included, which would otherwise be a duplication vector: a
 * second roll landing in the block's real, otherwise-inert inventory).
 */
public final class NaturalChestListener implements Listener {

    private final ChestManager chestManager;
    private final ChestTaggingService taggingService;

    public NaturalChestListener(ChestManager chestManager, ChestTaggingService taggingService) {
        this.chestManager = chestManager;
        this.taggingService = taggingService;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return; // only the chunk's very first generation ever creates natural chests
        }
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state.getType() != Material.CHEST) {
                continue;
            }
            Block block = state.getBlock();
            if (chestManager.isTaggedChest(block)) {
                continue; // already one of ours somehow — nothing to do
            }

            ItemStack[] rolledLoot = rollAndClearLoot(state);
            Optional<IcarusChest> neighborPrimary = taggingService.findAdjacentPrimary(block);
            taggingService.tagChestBlock(block, neighborPrimary, rolledLoot);
        }
    }

    /**
     * Rolls {@code state}'s vanilla loot table (if it still has one — an unopened natural chest
     * always does) into a scratch, {@link ChestTier#NORMAL}-sized inventory and clears it from the
     * block, so nothing can roll it again later. Returns {@code null} if this chest never had a
     * loot table at all.
     */
    private ItemStack[] rollAndClearLoot(BlockState state) {
        if (!(state instanceof Lootable lootable) || !lootable.hasLootTable()) {
            return null;
        }
        LootTable lootTable = lootable.getLootTable();
        long seed = lootable.getSeed();
        Random random = seed != 0 ? new Random(seed) : new Random();
        LootContext context = new LootContext.Builder(state.getLocation()).build();

        Inventory scratch = Bukkit.createInventory(null, ChestTier.NORMAL.totalCapacity());
        lootTable.fillInventory(scratch, random, context);

        lootable.setLootTable(null);
        state.update(true);
        return scratch.getContents();
    }
}
