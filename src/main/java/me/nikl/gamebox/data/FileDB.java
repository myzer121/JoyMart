package me.nikl.gamebox.data;

import me.nikl.gamebox.GameBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** YAML file based storage. Each player gets a file under players/, scores under scores/. */
public class FileDB extends DataBase {

    private final GameBox plugin;
    private final File playersDir;
    private final File scoresDir;

    public FileDB(GameBox plugin) {
        this.plugin = plugin;
        this.playersDir = new File(plugin.getDataFolder(), "data/players");
        this.scoresDir = new File(plugin.getDataFolder(), "data/scores");
        if (!playersDir.exists()) playersDir.mkdirs();
        if (!scoresDir.exists()) scoresDir.mkdirs();
    }

    private File playerFile(UUID uuid) {
        return new File(playersDir, uuid.toString() + ".yml");
    }

    private File scoreFile(String gameId) {
        return new File(scoresDir, gameId + ".yml");
    }

    @Override
    public GBPlayer loadPlayer(UUID uuid, String name) {
        GBPlayer player = new GBPlayer(uuid, name);
        File file = playerFile(uuid);
        if (file.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            player.setTokens(cfg.getInt("tokens", 0));
            Map<String, Long> scores = new HashMap<>();
            ConfigurationSection sec = cfg.getConfigurationSection("highscores");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    scores.put(key, sec.getLong(key));
                }
            }
            player.loadHighScores(scores);
            player.setClean();
        }
        return player;
    }

    @Override
    public void savePlayer(GBPlayer player) {
        File file = playerFile(player.getUuid());
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("name", player.getName());
        cfg.set("tokens", player.getTokens());
        for (Map.Entry<String, Long> e : player.getHighScores().entrySet()) {
            cfg.set("highscores." + e.getKey(), e.getValue());
        }
        try {
            cfg.save(file);
            player.setClean();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save player " + player.getUuid() + ": " + ex.getMessage());
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
        File file = scoreFile(gameId);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String key = uuid.toString();
        long existing = cfg.getLong(key + ".score", -1);
        if (score > existing) {
            cfg.set(key + ".name", name);
            cfg.set(key + ".score", score);
            try {
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save score: " + e.getMessage());
            }
        }
        return computeRank(gameId, uuid, score);
    }

    private int computeRank(String gameId, UUID uuid, long score) {
        List<TopList.Entry> entries = getTopList(gameId, Integer.MAX_VALUE);
        int rank = 1;
        for (TopList.Entry e : entries) {
            if (e.getOwner().equals(uuid)) return e.getRank();
            if (score > e.getScore()) {
                return rank;
            }
            rank++;
        }
        return rank;
    }

    @Override
    public List<TopList.Entry> getTopList(String gameId, int limit) {
        File file = scoreFile(gameId);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<TopList.Entry> entries = new ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            String name = cfg.getString(key + ".name", "Unknown");
            long score = cfg.getLong(key + ".score", 0);
            try {
                UUID uuid = UUID.fromString(key);
                entries.add(new TopList.Entry(0, uuid, name, score));
            } catch (IllegalArgumentException ignored) {}
        }
        entries.sort(Comparator.comparingLong(TopList.Entry::getScore).reversed());
        List<TopList.Entry> ranked = new ArrayList<>();
        int rank = 1;
        for (TopList.Entry e : entries) {
            ranked.add(new TopList.Entry(rank, e.getOwner(), e.getName(), e.getScore()));
            rank++;
        }
        if (ranked.size() > limit) {
            ranked = ranked.stream().limit(limit).collect(Collectors.toList());
        }
        return ranked;
    }

    @Override
    public void resetHighScores(String gameId) {
        File file = scoreFile(gameId);
        if (file.exists()) file.delete();
    }

    @Override
    public boolean migrate(DataBase target) {
        if (!(target instanceof MysqlDB)) return false;
        File[] files = scoresDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return true;
        for (File file : files) {
            String gameId = file.getName().replace(".yml", "");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            for (String key : cfg.getKeys(false)) {
                String name = cfg.getString(key + ".name", "Unknown");
                long score = cfg.getLong(key + ".score", 0);
                try {
                    target.addScore(gameId, UUID.fromString(key), name, score);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return true;
    }

    @Override
    public void shutdown() {
        // nothing for file storage
    }
}
