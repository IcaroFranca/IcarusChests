package dev.icaro.icaruschests.persistence.migrations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Versioned schema migrations, tracked via a {@code schema_meta} row so
 * future changes (upgrade metadata, attributes, etc.) can be added as new
 * {@code applyVN} steps without ever dropping existing data. Must only be
 * called from {@link dev.icaro.icaruschests.persistence.Database}'s DB
 * thread.
 */
public final class Migrations {

    private static final int CURRENT_VERSION = 1;

    private Migrations() {
    }

    public static void applyAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }

        int version = currentVersion(connection);
        if (version < 1) {
            applyV1(connection);
            setVersion(connection, 1);
        }
        // Future schema changes: "if (version < 2) { applyV2(connection); setVersion(connection, 2); }"
    }

    private static int currentVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM schema_meta WHERE key = 'version'");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Integer.parseInt(resultSet.getString(1)) : 0;
        }
    }

    private static void setVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_meta(key, value) VALUES ('version', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, String.valueOf(version));
            statement.executeUpdate();
        }
    }

    private static void applyV1(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chest (
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
                        UNIQUE (world_uuid, x, y, z)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chest_location ON chest(world_uuid, x, y, z)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chest_inventory (
                        chest_id TEXT PRIMARY KEY REFERENCES chest(id) ON DELETE CASCADE,
                        contents_b64 TEXT NOT NULL,
                        slot_count INTEGER NOT NULL,
                        saved_at INTEGER NOT NULL
                    )
                    """);
            // Reserved for future milestones (pluggable upgrades, misc per-chest
            // attributes) so they don't need a schema migration to land later.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chest_upgrade (
                        chest_id TEXT NOT NULL REFERENCES chest(id) ON DELETE CASCADE,
                        upgrade_type TEXT NOT NULL,
                        slot_index INTEGER NOT NULL,
                        data_json TEXT NULL,
                        PRIMARY KEY (chest_id, slot_index)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chest_attribute (
                        chest_id TEXT NOT NULL REFERENCES chest(id) ON DELETE CASCADE,
                        attr_key TEXT NOT NULL,
                        attr_value TEXT NULL,
                        PRIMARY KEY (chest_id, attr_key)
                    )
                    """);
        }
    }
}
