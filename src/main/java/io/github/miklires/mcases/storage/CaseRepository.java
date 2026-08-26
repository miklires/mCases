package io.github.miklires.mcases.storage;

import io.github.miklires.mcases.casebox.CaseDefinition;
import io.github.miklires.mcases.casebox.RewardDefinition;
import io.github.miklires.mcases.casebox.RewardSelector;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CaseRepository implements AutoCloseable {
    private final String jdbcUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mcases-db-", 0).factory());

    public CaseRepository(Path databaseFile) {
        this(prepareUrl(databaseFile));
    }

    private static String prepareUrl(Path databaseFile) {
        try { Files.createDirectories(databaseFile.toAbsolutePath().getParent()); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Cannot create data directory", exception); }
        return "jdbc:h2:file:" + databaseFile.toAbsolutePath().toString().replace('\\', '/') + ";AUTO_SERVER=TRUE";
    }

    CaseRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        try {
            Class.forName("org.h2.Driver");
            migrate();
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("Cannot initialize mCases storage", exception);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    private void migrate() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INT PRIMARY KEY)");
            statement.executeUpdate("MERGE INTO schema_version (version) KEY(version) VALUES (1)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS case_keys (
                      player_id UUID NOT NULL, case_id VARCHAR(64) NOT NULL, amount INT NOT NULL,
                      PRIMARY KEY (player_id, case_id), CHECK (amount >= 0))
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS case_pity (
                      player_id UUID NOT NULL, case_id VARCHAR(64) NOT NULL, openings INT NOT NULL,
                      PRIMARY KEY (player_id, case_id), CHECK (openings >= 0))
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_rewards (
                      transaction_id UUID PRIMARY KEY, player_id UUID NOT NULL, case_id VARCHAR(64) NOT NULL,
                      reward_id VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL, delivered BOOLEAN NOT NULL DEFAULT FALSE)
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS pending_player_idx ON pending_rewards(player_id, delivered)");
        }
    }

    public CompletableFuture<Integer> keyBalance(UUID playerId, String caseId) {
        return submit(() -> {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT amount FROM case_keys WHERE player_id=? AND case_id=?")) {
                statement.setObject(1, playerId);
                statement.setString(2, caseId);
                try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
            }
        });
    }

    public CompletableFuture<Void> addKeys(UUID playerId, String caseId, int delta) {
        if (delta == 0) return CompletableFuture.completedFuture(null);
        return submit(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                int current = lockBalance(connection, playerId, caseId);
                int next = Math.addExact(current, delta);
                if (next < 0) throw new IllegalArgumentException("Key balance cannot be negative");
                setBalance(connection, playerId, caseId, next);
                connection.commit();
            }
            return null;
        });
    }

    public CompletableFuture<PreparedOpen> prepareOpen(
            UUID playerId, CaseDefinition definition, RewardSelector selector) {
        return submit(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try {
                    int balance = lockBalance(connection, playerId, definition.id());
                    if (balance < 1) throw new NoKeyException(definition.id());
                    int pity = lockPity(connection, playerId, definition.id());
                    RewardDefinition reward = selector.select(definition, pity);
                    UUID transactionId = UUID.randomUUID();
                    setBalance(connection, playerId, definition.id(), balance - 1);
                    setPity(connection, playerId, definition.id(), reward.rarity().isPityEligible() ? 0 : pity + 1);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO pending_rewards(transaction_id,player_id,case_id,reward_id,created_at,delivered) VALUES(?,?,?,?,?,FALSE)")) {
                        statement.setObject(1, transactionId);
                        statement.setObject(2, playerId);
                        statement.setString(3, definition.id());
                        statement.setString(4, reward.id());
                        statement.setLong(5, Instant.now().toEpochMilli());
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return new PreparedOpen(transactionId, playerId, definition.id(), reward);
                } catch (Throwable throwable) {
                    connection.rollback();
                    throw throwable;
                }
            }
        });
    }

    public CompletableFuture<Void> markDelivered(UUID transactionId) {
        return submit(() -> {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                    "UPDATE pending_rewards SET delivered=TRUE WHERE transaction_id=?")) {
                statement.setObject(1, transactionId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<PendingReward>> pending(UUID playerId) {
        return submit(() -> {
            List<PendingReward> pending = new ArrayList<>();
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT transaction_id,case_id,reward_id,created_at FROM pending_rewards WHERE player_id=? AND delivered=FALSE ORDER BY created_at")) {
                statement.setObject(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) pending.add(new PendingReward(
                            result.getObject(1, UUID.class), playerId, result.getString(2), result.getString(3), result.getLong(4)));
                }
            }
            return List.copyOf(pending);
        });
    }

    private static int lockBalance(Connection connection, UUID playerId, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM case_keys WHERE player_id=? AND case_id=? FOR UPDATE")) {
            statement.setObject(1, playerId); statement.setString(2, caseId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private static int lockPity(Connection connection, UUID playerId, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT openings FROM case_pity WHERE player_id=? AND case_id=? FOR UPDATE")) {
            statement.setObject(1, playerId); statement.setString(2, caseId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private static void setBalance(Connection connection, UUID playerId, String caseId, int amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "MERGE INTO case_keys(player_id,case_id,amount) KEY(player_id,case_id) VALUES(?,?,?)")) {
            statement.setObject(1, playerId); statement.setString(2, caseId); statement.setInt(3, amount); statement.executeUpdate();
        }
    }

    private static void setPity(Connection connection, UUID playerId, String caseId, int openings) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "MERGE INTO case_pity(player_id,case_id,openings) KEY(player_id,case_id) VALUES(?,?,?)")) {
            statement.setObject(1, playerId); statement.setString(2, caseId); statement.setInt(3, openings); statement.executeUpdate();
        }
    }

    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try { return task.run(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException("Storage operation failed", exception); }
        }, executor);
    }

    @Override public void close() { executor.close(); }

    @FunctionalInterface private interface SqlTask<T> { T run() throws Exception; }

    public record PendingReward(UUID transactionId, UUID playerId, String caseId, String rewardId, long createdAt) {}
}
