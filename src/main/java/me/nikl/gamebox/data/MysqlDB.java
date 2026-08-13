package me.nikl.gamebox.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MySQL storage backed by a HikariCP connection pool. Supports cross-server high scores. */
public class MysqlDB extends DataBase {

    private final GameBox plugin;
    private final HikariDataSource dataSource;

    public MysqlDB(GameBox plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        HikariConfig hikari = new HikariConfig();
        String host = cfg.getString("storage.mysql.host", "localhost");
        int port = cfg.getInt("storage.mysql.port", 3306);
        String database = cfg.getString("storage.mysql.database", "gamebox");
        String user = cfg.getString("storage.mysql.user", "root");
        String pass = cfg.getString("storage.mysql.password", "");
        int poolSize = cfg.getInt("storage.mysql.poolSize", 10);

        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8");
        hikari.setUsername(user);
        hikari.setPassword(pass);
        hikari.setMaximumPoolSize(poolSize);
        hikari.setPoolName("GameBox-Hikari");
        this.dataSource = new HikariDataSource(hikari);

        createTables();
    }

    private void createTables() {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS gb_players ("
                    + "uuid VARCHAR(36) PRIMARY KEY,"
                    + "name VARCHAR(32),"
                    + "tokens INT NOT NULL DEFAULT 0"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS gb_highscores ("
                    + "game_id VARCHAR(32) NOT NULL,"
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "name VARCHAR(32) NOT NULL,"
                    + "score BIGINT NOT NULL,"
                    + "PRIMARY KEY (game_id, uuid)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create GameBox tables: " + e.getMessage());
        }
    }

    @Override
    public GBPlayer loadPlayer(UUID uuid, String name) {
        GBPlayer player = new GBPlayer(uuid, name);
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT tokens FROM gb_players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    player.setTokens(rs.getInt("tokens"));
                } else {
                    try (PreparedStatement ins = con.prepareStatement(
                            "INSERT IGNORE INTO gb_players (uuid, name, tokens) VALUES (?,?,0)")) {
                        ins.setString(1, uuid.toString());
                        ins.setString(2, name);
                        ins.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load player " + uuid + ": " + e.getMessage());
        }
        // load high scores
        Map<String, Long> scores = new HashMap<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT game_id, score FROM gb_highscores WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scores.put(rs.getString("game_id"), rs.getLong("score"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load scores for " + uuid + ": " + e.getMessage());
        }
        player.loadHighScores(scores);
        player.setClean();
        return player;
    }

    @Override
    public void savePlayer(GBPlayer player) {
        try (Connection con = dataSource.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO gb_players (uuid, name, tokens) VALUES (?,?,?) "
                            + "ON DUPLICATE KEY UPDATE name=VALUES(name), tokens=VALUES(tokens)")) {
                ps.setString(1, player.getUuid().toString());
                ps.setString(2, player.getName());
                ps.setInt(3, player.getTokens());
                ps.executeUpdate();
            }
            for (Map.Entry<String, Long> e : player.getHighScores().entrySet()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO gb_highscores (game_id, uuid, name, score) VALUES (?,?,?,?) "
                                + "ON DUPLICATE KEY UPDATE name=VALUES(name), score=VALUES(score)")) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, player.getUuid().toString());
                    ps.setString(3, player.getName());
                    ps.setLong(4, e.getValue());
                    ps.executeUpdate();
                }
            }
            player.setClean();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save player " + player.getUuid() + ": " + e.getMessage());
        }
    }

    @Override
    public void saveAll(Map<UUID, GBPlayer> players) {
        for (GBPlayer p : players.values()) {
            if (p.isDirty()) savePlayer(p);
        }
    }

    @Override
    public int addScore(String gameId, UUID uuid, String name, long score) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO gb_highscores (game_id, uuid, name, score) VALUES (?,?,?,?) "
                             + "ON DUPLICATE KEY UPDATE name=IF(score < VALUES(score), VALUES(name), name), "
                             + "score=IF(score < VALUES(score), VALUES(score), score)")) {
            ps.setString(1, gameId);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setLong(4, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add score: " + e.getMessage());
        }
        return computeRank(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*)+1 AS rank FROM gb_highscores WHERE game_id=? AND score>?")) {
                ps.setString(1, gameId);
                ps.setLong(2, score);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("rank");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to compute rank: " + e.getMessage());
            }
            return 1;
        });
    }

    private int computeRank(java.util.function.Function<Connection, Integer> query) {
        try (Connection con = dataSource.getConnection()) {
            return query.apply(con);
        } catch (SQLException e) {
            return 1;
        }
    }

    @Override
    public List<TopList.Entry> getTopList(String gameId, int limit) {
        List<TopList.Entry> entries = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid, name, score FROM gb_highscores WHERE game_id=? ORDER BY score DESC LIMIT ?")) {
            ps.setString(1, gameId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    entries.add(new TopList.Entry(rank,
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getLong("score")));
                    rank++;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load top list: " + e.getMessage());
        }
        return entries;
    }

    @Override
    public void resetHighScores(String gameId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM gb_highscores WHERE game_id=?")) {
            ps.setString(1, gameId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to reset high scores: " + e.getMessage());
        }
    }

    @Override
    public boolean migrate(DataBase target) {
        // Migration from mysql is typically file -> mysql; here we no-op (handled by FileDB.migrate).
        return true;
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
