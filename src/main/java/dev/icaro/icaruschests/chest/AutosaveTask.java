package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.gui.GuiOpenChecker;
import dev.icaro.icaruschests.model.IcarusBackpack;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.model.StorageContainer;
import dev.icaro.icaruschests.persistence.ChestRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically sweeps {@link ChestManager} and {@link BackpackManager} for storage marked dirty
 * (edited since its last save) and persists it. Scheduled to run on the main thread ({@code
 * runTaskTimer}, not {@code Async}): the sweep itself is cheap (just checking/flipping the {@code
 * dirty} flag, which is otherwise only ever touched on the main thread by the GUI listeners), and
 * the actual slow I/O still happens off-thread inside {@link ChestRepository}/{@code Database}.
 * This avoids needing {@code dirty} to be a thread-safe field — which is also exactly why a save
 * failure's {@code exceptionally()} (running on the DB executor thread, not this task's own main-
 * thread tick) can't just flip {@code dirty} back on directly; see {@link #saveIfDirty}.
 */
public final class AutosaveTask implements Runnable {

    private final ChestManager chestManager;
    private final BackpackManager backpackManager;
    private final ChestRepository chestRepository;
    private final Logger logger;
    private final Plugin plugin;

    public AutosaveTask(ChestManager chestManager, BackpackManager backpackManager, ChestRepository chestRepository, Logger logger, Plugin plugin) {
        this.chestManager = chestManager;
        this.backpackManager = backpackManager;
        this.chestRepository = chestRepository;
        this.logger = logger;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (IcarusChest chest : chestManager.all()) {
            saveIfDirty(chest, "bau");
        }
        for (IcarusBackpack backpack : backpackManager.all()) {
            saveIfDirty(backpack, "mochila");
        }
        // A placed chest gets evicted on ChunkUnloadEvent instead (see ChunkListener) — a backpack
        // belongs to no chunk, so this periodic sweep is its only way out of memory. Safe to run
        // right after the saves above: saveContents() already serialized each dirty one's contents
        // onto the async pipeline before this loop ever runs, so evicting the in-memory object here
        // can't lose anything still in flight to disk.
        backpackManager.evictIdle(GuiOpenChecker::isOpenByAnyone);
    }

    private void saveIfDirty(StorageContainer container, String kindLabel) {
        if (!container.isDirty() || container.isContentsLoadFailed()) {
            // A load-failed container must never be written back — its in-memory contents are just
            // the blank placeholder from a deserialization failure, not the real (still-recoverable)
            // data still sitting in the corrupted SQLite row. See StorageContainer's docs.
            return;
        }
        // Optimistically clear before the async save starts; a save
        // failure re-marks it dirty so the next sweep retries.
        container.setDirty(false);
        chestRepository.saveContents(container).exceptionally(ex -> {
            logger.log(Level.WARNING, "Falha ao salvar " + kindLabel + " " + container.getId(), ex);
            // Scheduled on the main thread rather than set directly here: this callback runs on
            // whatever thread completed the failed future — the DB executor thread, not this task's
            // own main-thread tick — and dirty is otherwise only ever touched on the main thread.
            Bukkit.getScheduler().runTask(plugin, () -> container.setDirty(true));
            return null;
        });
    }

    /** Saves every currently-dirty chest/backpack, blocking up to {@code timeoutSeconds}. Only for use during {@code onDisable}. */
    public void flush(long timeoutSeconds) {
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (IcarusChest chest : chestManager.all()) {
            flushIfDirty(chest, "bau", pending);
        }
        for (IcarusBackpack backpack : backpackManager.all()) {
            flushIfDirty(backpack, "mochila", pending);
        }
        try {
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Timeout aguardando saves pendentes no desligamento", e);
        }
    }

    private void flushIfDirty(StorageContainer container, String kindLabel, List<CompletableFuture<Void>> pending) {
        if (!container.isDirty() || container.isContentsLoadFailed()) {
            return; // see saveIfDirty's identical guard
        }
        container.setDirty(false);
        pending.add(chestRepository.saveContents(container).exceptionally(ex -> {
            logger.log(Level.WARNING, "Falha ao salvar " + kindLabel + " " + container.getId() + " no desligamento", ex);
            return null;
        }));
    }
}
