package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.BlockFaces;
import dev.icaro.icaruschests.util.NamespacedKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tags every newly placed chest block as an {@link IcarusChest} at
 * {@link ChestTier#NORMAL} and persists its metadata row — unless it's
 * placed directly adjacent to an existing IcarusChest, in which case it
 * either links to it as a double chest's secondary half (same tier) or the
 * placement is rejected outright (different tier: vanilla would still
 * visually merge them, which would be misleading since the plugin only ever
 * treats it as a matched-tier pairing).
 */
public final class ChestPlaceListener implements Listener {

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final Plugin plugin;

    public ChestPlaceListener(ChestManager chestManager, ChestRepository chestRepository, Plugin plugin) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.plugin = plugin;
    }

    // HIGH rather than MONITOR: this handler mutates the block's PDC, so it
    // must not run after the event may already have been cancelled by a
    // lower-priority listener (protection plugins, etc).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CHEST) {
            return;
        }

        Optional<IcarusChest> neighborPrimary = findAdjacentPrimary(block);
        if (neighborPrimary.isPresent() && neighborPrimary.get().getTier() != ChestTier.NORMAL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Nao e possivel unir a um bau de tier " + neighborPrimary.get().getTier().displayName() + ".",
                    NamedTextColor.RED));
            return;
        }

        if (neighborPrimary.isPresent()) {
            linkAsSecondary(block, neighborPrimary.get());
            return;
        }

        placeAsStandalone(block);
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
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        // No CHEST_ID/TIER here on purpose: this block has no independent
        // identity, it only ever resolves through to the primary.
        state.getPersistentDataContainer().set(
                NamespacedKeys.LINK_TARGET, PersistentDataType.STRING, primary.getLocation().encode());
        state.update(true);
    }

    /**
     * The adjacent chest this block should link to as a double chest, if
     * any. Only matches a neighbor that is ITSELF the primary (its resolved
     * chest's location equals its own) — never a neighbor that is already a
     * secondary pointing elsewhere, since vanilla double chests are always
     * exactly two adjacent blocks, never a chain of three or more.
     */
    private Optional<IcarusChest> findAdjacentPrimary(Block block) {
        for (BlockFace face : BlockFaces.HORIZONTAL) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != Material.CHEST) {
                continue;
            }
            Optional<IcarusChest> resolved = chestManager.getOrLoadFromBlock(neighbor);
            if (resolved.isPresent() && ChestLocation.of(neighbor).equals(resolved.get().getLocation())) {
                return resolved;
            }
        }
        return Optional.empty();
    }
}
