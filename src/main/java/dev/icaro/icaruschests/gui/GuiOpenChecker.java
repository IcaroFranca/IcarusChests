package dev.icaro.icaruschests.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Whether any online player currently has a given chest/backpack's GUI open — the one piece of
 * "is this container still in use" state that lives outside {@code ChestManager}/{@code
 * BackpackManager} themselves (neither has any notion of GUIs). Shared by every eviction path that
 * must never drop a container someone is actively looking at: {@code AutosaveTask}'s backpack
 * sweep and {@code ChunkListener}'s chunk-unload chest sweep both go through this rather than each
 * keeping their own copy of the same "scan online players' open inventories" loop.
 */
public final class GuiOpenChecker {

    private GuiOpenChecker() {
    }

    public static boolean isOpenByAnyone(UUID containerId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof IcarusChestHolder holder
                    && holder.getChestId().equals(containerId)) {
                return true;
            }
        }
        return false;
    }
}
