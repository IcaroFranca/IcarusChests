package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestDestructionHandler;
import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.util.BlockFaces;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.logging.Level;

/**
 * Breaking the PRIMARY half of a double chest destroys the logical unit as a
 * whole: drops the stored contents, deletes its row, and clears the {@code
 * LINK_TARGET} tag on its secondary so that leftover physical block reverts
 * to a plain, untagged chest. Breaking the SECONDARY instead just unlinks it
 * — the primary survives fully intact, shrunk back to its single-chest
 * capacity (any items that no longer fit are dropped at the broken block).
 */
public final class ChestBreakListener implements Listener {

    private final ChestManager chestManager;
    private final ChestDestructionHandler destructionHandler;
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestBreakListener(ChestManager chestManager, ChestDestructionHandler destructionHandler,
                               ChestRepository chestRepository, Plugin plugin) {
        this.chestManager = chestManager;
        this.destructionHandler = destructionHandler;
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    // HIGH rather than MONITOR: this handler drops items into the world, so
    // it must not run after the event may already have been cancelled by a
    // lower-priority listener (protection plugins, etc).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // getOrLoadFromBlock (not a bare cache lookup) so a chest that was
        // never touched this session still gets recognized before it's gone.
        chestManager.getOrLoadFromBlock(block).ifPresent(primaryChest -> {
            boolean brokeThePrimaryItself = ChestLocation.of(block).equals(primaryChest.getLocation());
            if (brokeThePrimaryItself) {
                destructionHandler.destroy(primaryChest, block);
                clearSecondaryPointer(block, primaryChest);
            } else {
                unlinkSecondary(block, primaryChest);
            }
        });
    }

    private void clearSecondaryPointer(Block brokenPrimaryBlock, IcarusChest primaryChest) {
        String primaryLocationEncoded = primaryChest.getLocation().encode();
        for (BlockFace face : BlockFaces.HORIZONTAL) {
            Block neighbor = brokenPrimaryBlock.getRelative(face);
            if (neighbor.getType() != Material.CHEST || !(neighbor.getState() instanceof TileState state)) {
                continue;
            }
            String linkTarget = state.getPersistentDataContainer().get(NamespacedKeys.LINK_TARGET, PersistentDataType.STRING);
            if (primaryLocationEncoded.equals(linkTarget)) {
                state.getPersistentDataContainer().remove(NamespacedKeys.LINK_TARGET);
                state.update(true);
            }
        }
    }

    /** The secondary block itself just disappears (vanilla handles that) — this only unlinks and shrinks the surviving primary. */
    private void unlinkSecondary(Block secondaryBlock, IcarusChest primary) {
        int shrunkCapacity = primary.getTier().totalCapacity();
        ItemStack[] current = primary.getContents();
        if (current.length > shrunkCapacity) {
            for (int i = shrunkCapacity; i < current.length; i++) {
                ItemStack overflow = current[i];
                if (overflow != null && overflow.getType() != Material.AIR) {
                    secondaryBlock.getWorld().dropItemNaturally(secondaryBlock.getLocation(), overflow);
                }
            }
            primary.setContents(Arrays.copyOf(current, shrunkCapacity));
        }
        primary.setDoubled(false);
        primary.setDirty(true);

        if (primary.getLocation().toBlock().getState() instanceof TileState state) {
            state.getPersistentDataContainer().set(NamespacedKeys.DOUBLED, PersistentDataType.INTEGER, 0);
            state.update(true);
        }
        chestRepository.insert(primary).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir desvinculacao do bau " + primary.getId(), ex);
            return null;
        });
        chestRepository.saveContents(primary).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Falha ao persistir conteudo encolhido do bau " + primary.getId(), ex);
            return null;
        });
    }
}
