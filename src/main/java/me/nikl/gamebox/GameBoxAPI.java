package me.nikl.gamebox;

import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.data.TopList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Public API for other plugins to interact with GameBox.
 *
 * <p>Three ways to obtain the instance:</p>
 * <ol>
 *   <li>Static singleton: {@code GameBoxAPI.getInstance()} (returns null before GameBox enables).</li>
 *   <li>Bukkit services manager: {@code ServicesManager.load(GameBoxAPI.class)} (registered on enable).</li>
 *   <li>Direct cast: {@code ((GameBox) Bukkit.getPluginManager().getPlugin("GameBox")).getApi()}.</li>
 * </ol>
 */
public class GameBoxAPI {

    private static GameBoxAPI instance;

    private final GameBox plugin;

    public GameBoxAPI(GameBox plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static GameBoxAPI getInstance() {
        return instance;
    }

    // ---------------- Tokens ----------------

    /** Get a player's token balance (0 if unknown). */
    public int getTokens(UUID uuid) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        return gb != null ? gb.getTokens() : 0;
    }

    /** Add tokens to a player. Returns false if it would go negative. */
    public boolean addTokens(UUID uuid, int amount) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        if (gb == null) return false;
        return gb.addTokens(amount);
    }

    /** Remove tokens from a player. Returns false if insufficient. */
    public boolean removeTokens(UUID uuid, int amount) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        if (gb == null) return false;
        return gb.removeTokens(amount);
    }

    /** Set a player's token balance (clamped to >= 0). */
    public void setTokens(UUID uuid, int amount) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        if (gb != null) gb.setTokens(amount);
    }

    /** Convenience: pay tokens if (and only if) the player can afford them. */
    public boolean payIfCanAfford(UUID uuid, int cost) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        if (gb == null || gb.getTokens() < cost) return false;
        return gb.removeTokens(cost);
    }

    // ---------------- State queries ----------------

    /** Whether the player currently has the GameBox menu / a game open. */
    public boolean isInGameBox(UUID uuid) {
        return plugin.getPluginManager().isInGameBox(uuid);
    }

    /** Whether the player is in an active game session. */
    public boolean isInGame(UUID uuid) {
        return plugin.getGameRegistry().getEnabledGames().stream()
                .anyMatch(g -> g.getGameManager().isInGame(uuid));
    }

    /** Whether the player is in a session of a specific game. */
    public boolean isInGame(UUID uuid, String gameId) {
        me.nikl.gamebox.game.Game g = plugin.getGameRegistry().getGame(gameId);
        return g != null && g.getGameManager().isInGame(uuid);
    }

    // ---------------- High scores ----------------

    /** Get a player's high score for a game (0 if none). */
    public long getHighScore(UUID uuid, String gameId) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        return gb != null ? gb.getHighScore(gameId) : 0L;
    }

    /** Force-set a player's high score (does not write to the global leaderboard). */
    public void setHighScore(UUID uuid, String gameId, long score) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(uuid);
        if (gb != null) gb.setHighScore(gameId, score);
    }

    /** Submit a score to the global leaderboard. Returns the player's resulting rank. */
    public int submitScore(String gameId, UUID uuid, String name, long score) {
        int rank = plugin.getDataBase().addScore(gameId, uuid, name, score);
        me.nikl.gamebox.inventory.TopListPage.invalidate(gameId);
        return rank;
    }

    /** Fetch the top entries for a game (cached 30s). */
    public List<TopList.Entry> getTopList(String gameId, int limit) {
        return plugin.getDataBase().getTopList(gameId, limit);
    }

    /** Reset the leaderboard for a game. */
    public void resetHighScores(String gameId) {
        plugin.getDataBase().resetHighScores(gameId);
        me.nikl.gamebox.inventory.TopListPage.invalidate(gameId);
    }

    // ---------------- Game registry ----------------

    /** Whether a built-in game id is enabled. */
    public boolean isGameEnabled(String gameId) {
        return plugin.getGameRegistry().isGameEnabled(gameId);
    }

    /** All currently enabled built-in game ids. */
    public java.util.Set<String> getEnabledGameIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (me.nikl.gamebox.game.Game g : plugin.getGameRegistry().getEnabledGames()) {
            ids.add(g.getGameId());
        }
        return ids;
    }

    /** All registered game ids (enabled or not). */
    public java.util.Set<String> getRegisteredGameIds() {
        return new java.util.HashSet<>(plugin.getGameRegistry().getGameIds());
    }

    // ---------------- Menu / actions ----------------

    /** Open the GameBox main menu for an online player. */
    public void openMainMenu(Player player) {
        plugin.getGuiManager().openMain(player);
    }

    /** Open the high-score page for a game. */
    public void openTopList(Player player, String gameId) {
        plugin.getGuiManager().openTopList(player, gameId);
    }

    /** Force a player into the GameBox state (saves inventory, fires enter event). */
    public void enterGameBox(Player player) {
        plugin.getPluginManager().enterGameBox(player);
    }

    /** Force a player out of the GameBox state (restores inventory, fires leave event). */
    public void leaveGameBox(Player player) {
        plugin.getPluginManager().leaveGameBox(player);
    }

    /** Force-save a player's data now. */
    public void savePlayer(UUID uuid) {
        GBPlayer gb = plugin.getPluginManager().getPlayerIfLoaded(uuid);
        if (gb != null && gb.isDirty()) {
            plugin.getDataBase().savePlayer(gb);
        }
    }

    /** Force-save all loaded players. */
    public void saveAll() {
        plugin.getPluginManager().saveAll();
    }

    /** Reload all GameBox configuration (games, language, shop). */
    public void reload() {
        plugin.reload();
    }

    public static GameBox getPlugin() {
        return (GameBox) Bukkit.getPluginManager().getPlugin("GameBox");
    }
}
