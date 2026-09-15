package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.util.BlockFaces;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Stops a plain chest from ever being placed directly adjacent to an existing IcarusChest.
 * Vanilla would still visually/functionally merge the two blocks into a double chest purely from
 * block adjacency — nothing to do with our own tagging — mixing a real, usable vanilla inventory
 * (the plain chest) with the tagged chest's own real inventory (permanently empty by design; its
 * actual contents live in {@code IcarusChest#getContents()}, never there).
 *
 * <p>Turning a chest into an IcarusChest no longer happens at placement at all — see {@code
 * ChestInteractListener}, which applies a {@code StarterChestRegistry} kit to an already-placed,
 * plain chest instead (each half of a double chest costs its own kit, applied one at a time — see
 * {@code ChestTaggingService#findAdjacentPrimary}). This listener exists purely to keep an already
 * *fully* IcarusChest pair from ever growing a stray plain third neighbor after the fact.
 */
public final class ChestPlaceListener implements Listener {

    private final ChestManager chestManager;

    public ChestPlaceListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CHEST) {
            return;
        }
        for (BlockFace face : BlockFaces.HORIZONTAL) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() == Material.CHEST && chestManager.isTaggedChest(neighbor)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "Nao e possivel colocar um bau comum do lado de um bau do IcarusChests.",
                        NamedTextColor.RED));
                return;
            }
        }
    }
}
