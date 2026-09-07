package dev.icaro.icaruschests.persistence.migrations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real SQL against a genuine (in-memory) SQLite connection —
 * no mocking — so a broken {@code CREATE TABLE}/upsert statement fails here
 * instead of only at runtime on a live server.
 */
class MigrationsTest {

    private Connection connection;

    @BeforeEach
    void openInMemoryDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        // Matches Database.java's own PRAGMA setup — enforcement OFF (SQLite's default) would let
        // v3MigrationPreservesExistingChestsAndAllowsLocationlessBackpacks pass even if applyV3
        // forgot to toggle foreign_keys around its table rebuild, since nothing would actually be
        // checking the constraint either way.
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
        }
    }

    @AfterEach
    void closeConnection() throws SQLException {
        connection.close();
    }

    @Test
    void applyAllCreatesEveryExpectedTable() throws SQLException {
        Migrations.applyAll(connection);

        Set<String> tables = tableNames();
        assertTrue(tables.contains("schema_meta"));
        assertTrue(tables.contains("chest"));
        assertTrue(tables.contains("chest_inventory"));
        assertTrue(tables.contains("chest_upgrade"));
        assertTrue(tables.contains("chest_attribute"));
    }

    @Test
    void applyAllIsIdempotent() {
        assertDoesNotThrow(() -> {
            Migrations.applyAll(connection);
            Migrations.applyAll(connection); // must not fail re-running against an already-migrated DB
        });
    }

    /**
     * Simulates a real, already-running server's database at schema version 2 (pre-backpack) —
     * hand-written to match {@code applyV1}/{@code applyV2}'s exact original shape, with one real
     * chest row already saved in it, the way an actual live install would look right before
     * updating to a version that adds backpacks. Then applies the real {@link Migrations#applyAll}
     * on top, exercising the exact V3 rebuild an actual upgrade runs, and checks the pre-existing
     * chest survives it byte-for-byte (id, location, tier) with kind defaulted to {@code 'CHEST'} —
     * and that a backpack row (no location at all) can coexist with it afterward without tripping
     * the location UNIQUE constraint.
     */
    @Test
    void v3MigrationPreservesExistingChestsAndAllowsLocationlessBackpacks() throws SQLException {
        UUID existingChestId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        seedVersion2DatabaseWithOneChest(existingChestId, worldId);

        Migrations.applyAll(connection); // a real install's upgrade path: only V3 actually runs

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT world_uuid, x, y, z, tier, kind FROM chest WHERE id = '" + existingChestId + "'")) {
            assertTrue(resultSet.next(), "the pre-existing chest row must survive the V3 rebuild");
            assertEquals(worldId.toString(), resultSet.getString("world_uuid"));
            assertEquals(5, resultSet.getInt("x"));
            assertEquals(64, resultSet.getInt("y"));
            assertEquals(-10, resultSet.getInt("z"));
            assertEquals(1, resultSet.getInt("tier"));
            assertEquals("CHEST", resultSet.getString("kind"));
        }

        // A backpack row has no location whatsoever — must not violate the UNIQUE(world_uuid,x,y,z)
        // constraint against the existing chest (or against another backpack: SQL treats every
        // NULL as distinct from every other value, including another NULL, for uniqueness).
        UUID backpackId = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO chest(id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at, is_doubled, kind)
                VALUES (?, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, 0, 'BACKPACK')
                """)) {
            insert.setString(1, backpackId.toString());
            assertDoesNotThrow(insert::executeUpdate, "a locationless backpack row must be insertable after the V3 rebuild");
        }

        // chest_inventory/chest_upgrade still reference chest(id) correctly after the table swap —
        // an insert against the surviving chest's id must still succeed under FK enforcement.
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO chest_inventory(chest_id, contents_b64, slot_count, saved_at) VALUES (?, '', 27, 0)")) {
            insert.setString(1, existingChestId.toString());
            assertDoesNotThrow(insert::executeUpdate, "chest_inventory's FK to chest(id) must still resolve after the rename");
        }
    }

    private void seedVersion2DatabaseWithOneChest(UUID chestId, UUID worldId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO schema_meta(key, value) VALUES ('version', '2')");
            statement.execute("""
                    CREATE TABLE chest (
                        id TEXT PRIMARY KEY,
                        world_uuid TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        tier INTEGER NOT NULL,
                        linked_chest_id TEXT NULL REFERENCES chest(id) ON DELETE SET NULL,
                        owner_uuid TEXT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        is_doubled INTEGER NOT NULL DEFAULT 0,
                        UNIQUE (world_uuid, x, y, z)
                    )
                    """);
            statement.execute("CREATE INDEX idx_chest_location ON chest(world_uuid, x, y, z)");
            statement.execute("""
                    CREATE TABLE chest_inventory (
                        chest_id TEXT PRIMARY KEY REFERENCES chest(id) ON DELETE CASCADE,
                        contents_b64 TEXT NOT NULL,
                        slot_count INTEGER NOT NULL,
                        saved_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE chest_upgrade (
                        chest_id TEXT NOT NULL REFERENCES chest(id) ON DELETE CASCADE,
                        upgrade_type TEXT NOT NULL,
                        slot_index INTEGER NOT NULL,
                        data_json TEXT NULL,
                        PRIMARY KEY (chest_id, slot_index)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE chest_attribute (
                        chest_id TEXT NOT NULL REFERENCES chest(id) ON DELETE CASCADE,
                        attr_key TEXT NOT NULL,
                        attr_value TEXT NULL,
                        PRIMARY KEY (chest_id, attr_key)
                    )
                    """);
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO chest(id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at, is_doubled)
                VALUES (?, ?, 5, 64, -10, 1, NULL, NULL, 1000, 1000, 0)
                """)) {
            insert.setString(1, chestId.toString());
            insert.setString(2, worldId.toString());
            insert.executeUpdate();
        }
    }

    private Set<String> tableNames() throws SQLException {
        Set<String> names = new HashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                names.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return names;
    }
}
