package dev.icaro.icaruschests.persistence;

import dev.icaro.icaruschests.persistence.migrations.Migrations;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns the single JDBC connection to the plugin's SQLite database and the
 * single-thread executor all reads/writes are serialized through, so the
 * server's main thread never blocks on disk I/O and SQLite never sees
 * concurrent writers from this plugin.
 *
 * <p>Everything else in {@code persistence} (namely {@link
 * dev.icaro.icaruschests.chest.ChestRepository}) submits work here instead of
 * touching the connection directly.
 */
public final class Database {

    private final JavaPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "IcarusChests-DB");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Opens the connection, applies PRAGMAs and runs pending migrations. */
    public CompletableFuture<Void> open() {
        return CompletableFuture.runAsync(this::openAndMigrate, executor);
    }

    private void openAndMigrate() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new SQLException("Could not create plugin data folder: " + dataFolder);
            }
            String url = "jdbc:sqlite:" + new File(dataFolder, "icaruschests.db").getAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA foreign_keys=ON");
            }
            Migrations.applyAll(connection);
        } catch (SQLException e) {
            throw new CompletionException(e);
        }
    }

    /** Runs {@code work} on the DB thread against the shared connection, returning its result. */
    public <T> CompletableFuture<T> submit(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Runs {@code work} on the DB thread against the shared connection. */
    public CompletableFuture<Void> submit(SqlConsumer work) {
        return CompletableFuture.runAsync(() -> {
            try {
                work.accept(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Closes the connection and shuts down the DB thread, waiting up to {@code timeoutSeconds}. */
    public void close(long timeoutSeconds) {
        executor.execute(() -> {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close SQLite connection cleanly: " + e.getMessage());
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
