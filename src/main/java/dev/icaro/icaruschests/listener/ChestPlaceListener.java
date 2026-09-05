package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;
import dev.icaro.icaruschests.tier.ChestTier;
import dev.icaro.icaruschests.util.NamespacedKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Tags every newly placed chest block as an {@link IcarusChest} at
 * {@link ChestTier#NORMAL} and persists its metadata row.
 *
 * <p>Double-chest adjacency (linking same-tier neighbors, blocking mixed-tier
 * neighbors) is deferred to M6 — a chest placed next to an existing
 * IcarusChest today simply becomes its own independent, tagged-but-unlinked
 * NORMAL chest. Vanilla will still visually merge the two chests into a
 * double chest; only the plugin's own capacity/GUI logic is unaware of that
 * pairing until M6.
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

        // Chests are TileState (PersistentDataHolder), not just plain BlockState.
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
}
