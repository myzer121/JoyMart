package me.nikl.gamebox.data;

import java.util.List;
import java.util.UUID;

/**
 * Wrapper around a high-score list for a single game.
 */
public class TopList {

    private final String gameId;
    private List<Entry> entries;

    public TopList(String gameId) {
        this.gameId = gameId;
    }

    public String getGameId() {
        return gameId;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public int size() {
        return entries == null ? 0 : entries.size();
    }

    /** A single ranked entry in a high-score table. */
    public static class Entry {
        private final int rank;
        private final UUID owner;
        private final String name;
        private final long score;

        public Entry(int rank, java.util.UUID owner, String name, long score) {
            this.rank = rank;
            this.owner = owner;
            this.name = name;
            this.score = score;
        }

        public int getRank() { return rank; }
        public java.util.UUID getOwner() { return owner; }
        public String getName() { return name; }
        public long getScore() { return score; }
    }
}
