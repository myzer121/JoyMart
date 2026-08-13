package me.nikl.gamebox.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cached player data: tokens, high scores per game.
 * Persisted by {@link DataBase} on a timer and on quit.
 */
public class GBPlayer {

    private final UUID uuid;
    private String name;
    private int tokens;
    private final Map<String, Long> highScores = new HashMap<>();
    private boolean dirty = false;

    public GBPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.tokens = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = Math.max(0, tokens);
        this.dirty = true;
    }

    public boolean addTokens(int amount) {
        if (amount < 0 && tokens + amount < 0) {
            return false;
        }
        this.tokens += amount;
        if (this.tokens < 0) this.tokens = 0;
        this.dirty = true;
        return true;
    }

    public boolean removeTokens(int amount) {
        if (amount > tokens) return false;
        this.tokens -= amount;
        this.dirty = true;
        return true;
    }

    public long getHighScore(String gameId) {
        return highScores.getOrDefault(gameId, 0L);
    }

    public boolean setHighScore(String gameId, long score) {
        Long current = highScores.get(gameId);
        if (current == null || score > current) {
            highScores.put(gameId, score);
            dirty = true;
            return true;
        }
        return false;
    }

    public Map<String, Long> getHighScores() {
        return highScores;
    }

    /** Merge loaded high scores from storage (does not mark dirty). */
    public void loadHighScores(Map<String, Long> scores) {
        this.highScores.putAll(scores);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setClean() {
        this.dirty = false;
    }
}
