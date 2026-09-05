package dev.icaro.icaruschests.persistence;

import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async CRUD over the {@code chest}/{@code chest_inventory} tables. Every
 * method returns immediately with a {@link CompletableFuture} that completes
 * on {@link Database}'s dedicated DB thread — callers on the main thread must
 * never {@code .join()}/{@code .get()} these without a timeout.
 */
public final class ChestRepository {

    private final Database database;

    public ChestRepository(Database database) {
        this.database = database;
    }

    /** Inserts a newly placed chest, or updates tier/link if the id already exists (e.g. re-registered after a reload). */
    public CompletableFuture<Void> insert(IcarusChest chest) {
        return database.submit(connection -> {
            long now = System.currentTimeMillis();
            ChestLocation location = chest.getLocation();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO chest(id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        tier = excluded.tier,
                        linked_chest_id = excluded.linked_chest_id,
                        updated_at = excluded.updated_at
                    """)) {
                statement.setString(1, chest.getId().toString());
                statement.setString(2, location.worldId().toString());
                statement.setInt(3, location.x());
                statement.setInt(4, location.y());
                statement.setInt(5, location.z());
                statement.setInt(6, chest.getTier().ordinal());
                statement.setString(7, chest.getLinkedChestId() != null ? chest.getLinkedChestId().toString() : null);
                statement.setString(8, null); // owner_uuid: not tracked until a later milestone
                statement.setLong(9, now);
                statement.setLong(10, now);
                statement.executeUpdate();
            }
        });
    }

    /** Deletes the chest row; {@code ON DELETE CASCADE} takes its {@code chest_inventory} row with it. */
    public CompletableFuture<Void> delete(UUID chestId) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chest WHERE id = ?")) {
                statement.setString(1, chestId.toString());
                statement.executeUpdate();
            }
        });
    }

    /** Upserts the chest's current contents; serialization happens on the calling thread before the DB hop. */
    public CompletableFuture<Void> saveContents(IcarusChest chest) {
        String serialized = ItemStackSerializer.serialize(chest.getContents());
        UUID chestId = chest.getId();
        int slotCount = chest.getContents().length;
        return database.submit(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO chest_inventory(chest_id, contents_b64, slot_count, saved_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(chest_id) DO UPDATE SET
                        contents_b64 = excluded.contents_b64,
                        slot_count = excluded.slot_count,
                        saved_at = excluded.saved_at
                    """)) {
                statement.setString(1, chestId.toString());
                statement.setString(2, serialized);
                statement.setInt(3, slotCount);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE chest SET updated_at = ? WHERE id = ?")) {
                statement.setLong(1, now);
                statement.setString(2, chestId.toString());
                statement.executeUpdate();
            }
        });
    }

    /** Empty when the chest has never been saved (e.g. placed but not yet closed/autosaved). */
    public CompletableFuture<Optional<ItemStack[]>> loadContents(UUID chestId, int expectedSize) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT contents_b64 FROM chest_inventory WHERE chest_id = ?")) {
                statement.setString(1, chestId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(ItemStackSerializer.deserialize(resultSet.getString(1), expectedSize));
                }
            }
        });
    }
}
