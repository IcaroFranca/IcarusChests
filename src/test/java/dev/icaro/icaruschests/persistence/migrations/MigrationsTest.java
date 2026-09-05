package dev.icaro.icaruschests.persistence.migrations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
