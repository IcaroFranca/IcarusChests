package dev.icaro.icaruschests.model;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable identifier of a block position, addressed by world UUID rather
 * than {@link World} reference so it stays valid (and hashable/comparable)
 * across world unload/reload cycles.
 */
public record ChestLocation(UUID worldId, int x, int y, int z) {

    public static ChestLocation of(Block block) {
        return new ChestLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /** Resolves the world for this location, if it is currently loaded. */
    public World world() {
        return Bukkit.getWorld(worldId);
    }

    public Block toBlock() {
        World world = world();
        if (world == null) {
            throw new IllegalStateException("World " + worldId + " is not loaded");
        }
        return world.getBlockAt(x, y, z);
    }

    /** Compact string form used to tag a double chest's secondary half with its primary's location (see PDC {@code LINK_TARGET}). */
    public String encode() {
        return worldId + ";" + x + ";" + y + ";" + z;
    }

    public static Optional<ChestLocation> decode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] parts = raw.split(";");
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ChestLocation(
                    UUID.fromString(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
