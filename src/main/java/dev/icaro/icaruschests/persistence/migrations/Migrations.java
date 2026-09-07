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

    private static final int CURRENT_VERSION = 3;

    private Migrations() {
    }

    public static void applyAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }

        int version = currentVersion(connection);
        if (version < 1) {
            applyVersioned(connection, 1, Migrations::applyV1);
        }
        if (version < 2) {
            applyVersioned(connection, 2, Migrations::applyV2);
        }
        if (version < 3) {
            // Not run through applyVersioned: this step needs PRAGMA foreign_keys toggled off
            // and back on, which SQLite refuses to do while a transaction is open — applyVersioned
            // always has one open (that's how it gets its atomicity). See applyV3's own docs.
            applyV3(connection);
        }
    }

    /**
     * Runs one migration step and its {@code schema_meta} version bump as a single transaction —
     * SQLite's DDL is fully transactional, so a failure partway through (e.g. an {@code ALTER
     * TABLE} that can't apply) rolls back the schema change too, instead of leaving {@code
     * schema_meta} claiming a version whose actual schema change never took effect.
     */
    private static void applyVersioned(Connection connection, int version, SqlStep step) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            step.run(connection);
            setVersion(connection, version);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @FunctionalInterface
    private interface SqlStep {
        void run(Connection connection) throws SQLException;
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

    /** Adds double-chest tracking: {@code is_doubled} mirrors the primary block's PDC {@code DOUBLED} tag for debuggability/consistency. */
    private static void applyV2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE chest ADD COLUMN is_doubled INTEGER NOT NULL DEFAULT 0");
        }
    }

    /**
     * Makes {@code chest} able to hold a portable backpack's row too, not just a placed chest's:
     * adds a {@code kind} column ({@code 'CHEST'}/{@code 'BACKPACK'}, disambiguating which enum
     * {@code tier}'s ordinal is into) and drops the {@code NOT NULL} on the location columns,
     * since a backpack has no block position at all. {@code chest_inventory}/{@code chest_upgrade}
     * need no changes whatsoever — they were already keyed purely by {@code chest_id} with no
     * location dependency (by original design, precisely so this day would need no migration on
     * them: see the project plan's very first note on why a chest's id, not its coordinates, is
     * its primary key).
     *
     * <p>SQLite has no {@code ALTER COLUMN ... DROP NOT NULL}, so this follows SQLite's own
     * documented procedure for schema changes a plain {@code ALTER TABLE} can't express: create a
     * replacement table, copy every row across (existing rows all become {@code kind='CHEST'},
     * their real coordinates preserved), drop the original, and rename the replacement into its
     * place — {@code chest_inventory}/{@code chest_upgrade}'s {@code REFERENCES chest(id)} survive
     * this untouched, since SQLite rewrites a renamed table's foreign-key *references* to it
     * automatically, and no row of theirs is ever touched here, only {@code chest} itself.
     *
     * <p>Foreign key enforcement has to be switched off around exactly this (never inside a
     * transaction — SQLite silently no-ops a {@code PRAGMA foreign_keys} change made mid-transaction,
     * which is exactly why this bypasses {@link #applyVersioned}'s own transaction wrapper): with
     * it left on, briefly dropping {@code chest} while {@code chest_inventory}/{@code chest_upgrade}
     * still reference it risks the drop being treated as a cascading delete of every row in both —
     * real contents/upgrades this step must never touch.
     */
    private static void applyV3(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(true); // PRAGMA foreign_keys is a no-op mid-transaction; make sure none is open
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=OFF");
            }
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE chest_new (
                            id TEXT PRIMARY KEY,
                            world_uuid TEXT NULL,
                            x INTEGER NULL,
                            y INTEGER NULL,
                            z INTEGER NULL,
                            tier INTEGER NOT NULL,
                            linked_chest_id TEXT NULL REFERENCES chest_new(id) ON DELETE SET NULL,
                            owner_uuid TEXT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            is_doubled INTEGER NOT NULL DEFAULT 0,
                            kind TEXT NOT NULL DEFAULT 'CHEST',
                            UNIQUE (world_uuid, x, y, z)
                        )
                        """);
                statement.execute("""
                        INSERT INTO chest_new (id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at, is_doubled, kind)
                        SELECT id, world_uuid, x, y, z, tier, linked_chest_id, owner_uuid, created_at, updated_at, is_doubled, 'CHEST'
                        FROM chest
                        """);
                statement.execute("DROP TABLE chest");
                statement.execute("ALTER TABLE chest_new RENAME TO chest");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_chest_location ON chest(world_uuid, x, y, z)");
                setVersion(connection, 3);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } finally {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }
}
