package dev.icaro.icaruschests.persistence;

import dev.icaro.icaruschests.model.ChestLocation;
import dev.icaro.icaruschests.model.IcarusChest;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
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

    /** Inserts a newly placed (primary) chest, or updates its tier/doubled state if the id already exists (e.g. after an upgrade or link). */
    public CompletableFuture<Void> insert(IcarusChest chest) {
        return database.submit(connection -> {
            long now = System.currentTimeMillis();
            ChestLocation location = chest.getLocation();
            // linked_chest_id is currently unused: double-chest secondaries are pure PDC
            // pointers with no row of their own (see ChestManager), so it's always NULL here.
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO chest(id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at, is_doubled)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        tier = excluded.tier,
                        updated_at = excluded.updated_at,
                        is_doubled = excluded.is_doubled
                    """)) {
                statement.setString(1, chest.getId().toString());
                statement.setString(2, location.worldId().toString());
                statement.setInt(3, location.x());
                statement.setInt(4, location.y());
                statement.setInt(5, location.z());
                statement.setInt(6, chest.getTier().ordinal());
                statement.setString(7, null); // owner_uuid: not tracked until a later milestone
                statement.setLong(8, now);
                statement.setLong(9, now);
                statement.setInt(10, chest.isDoubled() ? 1 : 0);
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

    /**
     * Atomically persists a tier upgrade: the chest's new tier/doubled state AND its resized
     * contents, in one transaction — so a failure partway through can never leave the two tables
     * disagreeing with each other (the chest showing a new tier while its contents blob is still
     * sized for the old one, or vice versa). Used only by {@code TierUpgradeService}; a plain
     * tier-independent content save still goes through {@link #saveContents}.
     */
    public CompletableFuture<Void> saveTierUpgrade(IcarusChest chest) {
        String serializedContents = ItemStackSerializer.serialize(chest.getContents());
        UUID chestId = chest.getId();
        int slotCount = chest.getContents().length;
        ChestLocation location = chest.getLocation();
        int tierOrdinal = chest.getTier().ordinal();
        boolean doubled = chest.isDoubled();
        return database.submitTransaction(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO chest(id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at, is_doubled)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        tier = excluded.tier,
                        updated_at = excluded.updated_at,
                        is_doubled = excluded.is_doubled
                    """)) {
                statement.setString(1, chestId.toString());
                statement.setString(2, location.worldId().toString());
                statement.setInt(3, location.x());
                statement.setInt(4, location.y());
                statement.setInt(5, location.z());
                statement.setInt(6, tierOrdinal);
                statement.setString(7, null);
                statement.setLong(8, now);
                statement.setLong(9, now);
                statement.setInt(10, doubled ? 1 : 0);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO chest_inventory(chest_id, contents_b64, slot_count, saved_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(chest_id) DO UPDATE SET
                        contents_b64 = excluded.contents_b64,
                        slot_count = excluded.slot_count,
                        saved_at = excluded.saved_at
                    """)) {
                statement.setString(1, chestId.toString());
                statement.setString(2, serializedContents);
                statement.setInt(3, slotCount);
                statement.setLong(4, now);
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

    /**
     * Replaces every {@code chest_upgrade} row for this chest with {@code upgradesBySlot}
     * (slot index → the installed upgrade's {@code name()}; empty slots simply aren't present).
     * Kept type-agnostic (plain strings, not the {@code upgrade} package's own enum) to avoid a
     * persistence→upgrade dependency, since {@code upgrade} already depends on {@code persistence}.
     * Transactional: the delete-then-insert pair either both apply or neither does, so a failure
     * partway through can never leave a chest with none of its upgrades persisted.
     */
    public CompletableFuture<Void> saveUpgrades(UUID chestId, Map<Integer, PersistedUpgrade> upgradesBySlot) {
        return database.submitTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM chest_upgrade WHERE chest_id = ?")) {
                delete.setString(1, chestId.toString());
                delete.executeUpdate();
            }
            if (upgradesBySlot.isEmpty()) {
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO chest_upgrade(chest_id, upgrade_type, slot_index, data_json) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<Integer, PersistedUpgrade> entry : upgradesBySlot.entrySet()) {
                    PersistedUpgrade upgrade = entry.getValue();
                    insert.setString(1, chestId.toString());
                    insert.setString(2, upgrade.upgradeType());
                    insert.setInt(3, entry.getKey());
                    if (upgrade.dataJson() == null) {
                        insert.setNull(4, Types.VARCHAR);
                    } else {
                        insert.setString(4, upgrade.dataJson());
                    }
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        });
    }

    /** Empty map when the chest has no upgrades installed (or was never saved). */
    public CompletableFuture<Map<Integer, PersistedUpgrade>> loadUpgrades(UUID chestId) {
        return database.submit(connection -> {
            Map<Integer, PersistedUpgrade> result = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT slot_index, upgrade_type, data_json FROM chest_upgrade WHERE chest_id = ?")) {
                statement.setString(1, chestId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        result.put(resultSet.getInt(1), new PersistedUpgrade(resultSet.getString(2), resultSet.getString(3)));
                    }
                }
            }
            return result;
        });
    }
}
