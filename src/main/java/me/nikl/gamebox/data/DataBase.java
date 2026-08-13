package me.nikl.gamebox.data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction for the persistence layer.
 * Two implementations: {@link FileDB} (YAML) and {@link MysqlDB} (HikariCP).
 */
public abstract class DataBase {

    /** Load or create the cached GBPlayer for a uuid. */
    public abstract GBPlayer loadPlayer(UUID uuid, String name);

    /** Save a single player's tokens and high scores. */
    public abstract void savePlayer(GBPlayer player);

    /** Save all dirty players. */
    public abstract void saveAll(Map<UUID, GBPlayer> players);

    /** Add a score to the global high-score table for a game (returns updated rank). */
    public abstract int addScore(String gameId, UUID uuid, String name, long score);

    /** Get the top entries for a game. */
    public abstract List<TopList.Entry> getTopList(String gameId, int limit);

    /** Reset the high-score table for a game. */
    public abstract void resetHighScores(String gameId);

    /** Migrate between storage types (e.g. yaml -> mysql). */
    public abstract boolean migrate(DataBase target);

    /** Release resources (close pool / flush files). */
    public abstract void shutdown();
}
