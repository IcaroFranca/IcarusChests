package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestTaggingService;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.tier.ChestTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

/**
 * Tags every newly placed chest block as an {@link IcarusChest} at
 * {@link ChestTier#NORMAL} and persists its metadata row — unless it's
 * placed directly adjacent to an existing IcarusChest, in which case it
 * either links to it as a double chest's secondary half (same tier, and only
 * if that neighbor doesn't already have a partner — vanilla double chests
 * are always exactly two blocks) or the placement is rejected outright
 * (different tier: vanilla would still visually merge them, which would be
 * misleading since the plugin only ever treats it as a matched-tier
 * pairing). Linking doubles the primary's capacity on the spot.
 *
 * <p>The actual tagging/linking logic lives in {@link ChestTaggingService},
 * shared with {@code NaturalChestListener} — this class only owns the
 * player-placement-specific parts: rejecting a mismatched-tier neighbor with
 * a cancellation and a message (there's no event to cancel and no player to
 * message for a chest that world generation just created).
 */
public final class ChestPlaceListener implements Listener {

    private final ChestTaggingService taggingService;

    public ChestPlaceListener(ChestTaggingService taggingService) {
        this.taggingService = taggingService;
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

        Optional<IcarusChest> neighborPrimary = taggingService.findAdjacentPrimary(block);
        if (neighborPrimary.isPresent() && neighborPrimary.get().getTier() != ChestTier.NORMAL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Nao e possivel unir a um bau de tier " + neighborPrimary.get().getTier().displayName() + ".",
                    NamedTextColor.RED));
            return;
        }

        taggingService.tagChestBlock(block, neighborPrimary, null);
    }
}
