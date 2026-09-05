package dev.icaro.icaruschests.chest;

import dev.icaro.icaruschests.model.IcarusChest;
import dev.icaro.icaruschests.persistence.ChestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically sweeps {@link ChestManager} for chests marked dirty (edited
 * since their last save) and persists them. Scheduled to run on the main
 * thread ({@code runTaskTimer}, not {@code Async}): the sweep itself is cheap
 * (just checking/flipping the {@code dirty} flag, which is otherwise only
 * ever touched on the main thread by the GUI listeners), and the actual slow
 * I/O still happens off-thread inside {@link ChestRepository}/{@code
 * Database}. This avoids needing {@code dirty} to be a thread-safe field.
 */
public final class AutosaveTask implements Runnable {

    private final ChestManager chestManager;
    private final ChestRepository chestRepository;
    private final Logger logger;

    public AutosaveTask(ChestManager chestManager, ChestRepository chestRepository, Logger logger) {
        this.chestManager = chestManager;
        this.chestRepository = chestRepository;
        this.logger = logger;
    }

    @Override
    public void run() {
        for (IcarusChest chest : chestManager.all()) {
            if (!chest.isDirty()) {
                continue;
            }
            // Optimistically clear before the async save starts; a save
            // failure re-marks it dirty so the next sweep retries.
            chest.setDirty(false);
            chestRepository.saveContents(chest).exceptionally(ex -> {
                chest.setDirty(true);
                logger.log(Level.WARNING, "Falha ao salvar bau " + chest.getId(), ex);
                return null;
            });
        }
    }

    /** Saves every currently-dirty chest, blocking up to {@code timeoutSeconds}. Only for use during {@code onDisable}. */
    public void flush(long timeoutSeconds) {
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (IcarusChest chest : chestManager.all()) {
            if (!chest.isDirty()) {
                continue;
            }
            chest.setDirty(false);
            pending.add(chestRepository.saveContents(chest).exceptionally(ex -> {
                logger.log(Level.WARNING, "Falha ao salvar bau " + chest.getId() + " no desligamento", ex);
                return null;
            }));
        }
        try {
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Timeout aguardando saves pendentes no desligamento", e);
        }
    }
}
