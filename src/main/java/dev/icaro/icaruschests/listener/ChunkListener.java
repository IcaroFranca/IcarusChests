package dev.icaro.icaruschests.listener;

import dev.icaro.icaruschests.chest.ChestManager;
import dev.icaro.icaruschests.gui.GuiOpenChecker;
import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Evicts a cached {@link IcarusChest} from {@link ChestManager} once its chunk unloads.
 *
 * <p>{@code AutosaveTask}/{@code BackpackManager}'s own docs have always described this as already
 * happening — but nothing ever registered a {@link ChunkUnloadEvent} listener anywhere in the
 * plugin, so in reality every chest any player ever touched stayed cached in {@code ChestManager}
 * for the rest of the server's uptime, chunk loaded or not. This is what actually implements it.
 *
 * <p>Only evicts a chest that is safe to forget right now: not {@link IcarusChest#isDirty()} (an
 * unsaved chest is left for the next {@code AutosaveTask} sweep to flush and clear first — evicting
 * it here would just drop the pending write on the floor instead of persisting it), fully hydrated
 * (see {@link ChestManager#isReady}, so an in-flight async load can't be orphaned mid-flight), and
 * not currently open in anyone's GUI (mirrors {@code BackpackManager#evictIdle}'s own check, via
 * {@link GuiOpenChecker} — a chest's chunk staying loaded while its GUI is open is the overwhelming
 * case, but not a guarantee Bukkit makes). A chest that fails any of these simply stays cached until
 * its chunk unloads again later, or a save/close makes it eligible in the meantime.
 *
 * <p>Deliberately a plain O(n) scan over every currently cached chest per chunk unload rather than
 * a dedicated chunk→chest index — {@code ChestManager} keeps no such index, and chest counts are
 * expected to stay small enough (hundreds, not millions) for this to be unnoticeable; revisit if
 * that stops being true.
 */
public final class ChunkListener implements Listener {

    private final ChestManager chestManager;

    public ChunkListener(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        List<IcarusChest> toEvict = new ArrayList<>();
        for (IcarusChest chest : chestManager.all()) {
            if (isInChunk(chest.getLocation(), chunk) && isSafeToEvict(chest)) {
                toEvict.add(chest);
            }
        }
        // Collected first, then removed — mutating ChestManager's backing map while iterating its
        // live view (chestManager.all()) directly would be fragile even though ConcurrentHashMap
        // itself tolerates it.
        for (IcarusChest chest : toEvict) {
            chestManager.unregister(chest.getLocation());
        }
    }

    private boolean isSafeToEvict(IcarusChest chest) {
        return !chest.isDirty()
                && chestManager.isReady(chest.getId())
                && !GuiOpenChecker.isOpenByAnyone(chest.getId());
    }

    private boolean isInChunk(ChestLocation location, Chunk chunk) {
        return location.worldId().equals(chunk.getWorld().getUID())
                && (location.x() >> 4) == chunk.getX()
                && (location.z() >> 4) == chunk.getZ();
    }
}
