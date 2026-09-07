package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.gui.IcarusChestHolder;
import dev.icaro.icaruschests.model.IcarusBackpack;
import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.model.StorageContainer;
import dev.icaro.icaruschests.persistence.ChestRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * This avoids needing {@code dirty} to be a thread-safe field.
 */
public final class AutosaveTask implements Runnable {

    private final ChestManager chestManager;
    private final BackpackManager backpackManager;
    private final ChestRepository chestRepository;
    private final Logger logger;

    public AutosaveTask(ChestManager chestManager, BackpackManager backpackManager, ChestRepository chestRepository, Logger logger) {
        this.chestManager = chestManager;
        this.backpackManager = backpackManager;
        this.chestRepository = chestRepository;
        this.logger = logger;
    }

    @Override
    public void run() {
        for (IcarusChest chest : chestManager.all()) {
            saveIfDirty(chest, "bau");
        }
        for (IcarusBackpack backpack : backpackManager.all()) {
            saveIfDirty(backpack, "mochila");
        }
        // A placed chest gets evicted on ChunkUnloadEvent instead — a backpack belongs to no
        // chunk, so this periodic sweep is its only way out of memory. Safe to run right after the
        // saves above: saveContents() already serialized each dirty one's contents onto the async
        // pipeline before this loop ever runs, so evicting the in-memory object here can't lose
        // anything still in flight to disk.
        backpackManager.evictIdle(this::isOpenByAnyone);
    }

    private boolean isOpenByAnyone(UUID backpackId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof IcarusChestHolder holder
                    && holder.getChestId().equals(backpackId)) {
                return true;
            }
        }
        return false;
    }

    private void saveIfDirty(StorageContainer container, String kindLabel) {
        if (!container.isDirty()) {
            return;
        }
        // Optimistically clear before the async save starts; a save
        // failure re-marks it dirty so the next sweep retries.
        container.setDirty(false);
        chestRepository.saveContents(container).exceptionally(ex -> {
            container.setDirty(true);
            logger.log(Level.WARNING, "Falha ao salvar " + kindLabel + " " + container.getId(), ex);
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
        if (!container.isDirty()) {
            return;
        }
        container.setDirty(false);
        pending.add(chestRepository.saveContents(container).exceptionally(ex -> {
            logger.log(Level.WARNING, "Falha ao salvar " + kindLabel + " " + container.getId() + " no desligamento", ex);
            return null;
        }));
    }
}
