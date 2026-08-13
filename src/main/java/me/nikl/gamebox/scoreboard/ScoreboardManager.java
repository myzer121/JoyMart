package me.nikl.gamebox.scoreboard;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.data.GBPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight per-player scoreboard shown while in GameBox: displays the
 * player's tokens, currently played game, score, and high score.
 *
 * <p>Each player gets a fresh scoreboard so other plugins' scoreboards are not
 * disturbed. {@link #clear(Player)} restores the previous scoreboard (or the
 * main scoreboard if none).</p>
 */
public class ScoreboardManager {

    public static final String OBJECTIVE_NAME = "gamebox";

    private final GameBox plugin;
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    private final Map<UUID, Scoreboard> active = new HashMap<>();

    public ScoreboardManager(GameBox plugin) {
        this.plugin = plugin;
    }

    /** Show (or refresh) the in-game scoreboard for a player. */
    public void show(Player player, String gameId, long score, boolean won) {
        if (!GameBoxSettings.scoreboardEnabled) return;

        // Save the previous scoreboard the first time we touch a player
        if (!previous.containsKey(player.getUniqueId())) {
            previous.put(player.getUniqueId(), player.getScoreboard());
        }

        Scoreboard board = active.computeIfAbsent(player.getUniqueId(), u -> {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective(OBJECTIVE_NAME, "dummy",
                    me.nikl.gamebox.utility.Utility.color("&6&lGameBox"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            return sb;
        });

        // Reset entries
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) return;
        obj.setDisplayName(me.nikl.gamebox.utility.Utility.color("&6&l" + gameId.toUpperCase()));

        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        int tokens = gb != null ? gb.getTokens() : 0;
        long high = gb != null ? gb.getHighScore(gameId) : 0;

        setLine(obj, ChatColor.AQUA + "Game:", 5);
        setLine(obj, ChatColor.WHITE + gameId, 4);
        setLine(obj, ChatColor.AQUA + "Score:", 3);
        setLine(obj, ChatColor.WHITE + String.valueOf(score), 2);
        setLine(obj, ChatColor.AQUA + "Best:", 1);
        setLine(obj, ChatColor.GOLD + String.valueOf(high) + ChatColor.RESET + " | " + ChatColor.YELLOW + tokens + "T", 0);

        player.setScoreboard(board);
    }

    private void setLine(Objective obj, String text, int score) {
        // Ensure unique entry names (Scoreboard deduplicates by entry)
        obj.getScore(text).setScore(score);
    }

    /** Restore the player's previous scoreboard. */
    public void clear(Player player) {
        Scoreboard prev = previous.remove(player.getUniqueId());
        active.remove(player.getUniqueId());
        if (prev != null) {
            player.setScoreboard(prev);
        } else if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Schedule a clear after the given delay (ticks). */
    public void clearLater(Player player, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) clear(player);
        }, delayTicks);
    }

    public boolean isActive(UUID uuid) {
        return active.containsKey(uuid);
    }
}
