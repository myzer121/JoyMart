package me.nikl.gamebox;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.nikl.gamebox.data.GBPlayer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI integration. Exposes:
 * <ul>
 *   <li>{@code %gamebox_tokens%} - the player's token balance</li>
 *   <li>{@code %gamebox_highscore_<game>%} - high score for a game</li>
 *   <li>{@code %gamebox_ingame%} - whether the player is in a game session</li>
 *   <li>{@code %gamebox_games%} - number of enabled games</li>
 * </ul>
 */
public class GameBoxPlaceholders extends PlaceholderExpansion {

    private final GameBox plugin;

    public GameBoxPlaceholders(GameBox plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gamebox";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        if (params.equalsIgnoreCase("tokens")) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            return String.valueOf(gb != null ? gb.getTokens() : 0);
        }
        if (params.equalsIgnoreCase("ingame")) {
            if (!(player instanceof Player)) return "false";
            return String.valueOf(new GameBoxAPI(plugin).isInGame(player.getUniqueId()));
        }
        if (params.equalsIgnoreCase("games")) {
            return String.valueOf(plugin.getGameRegistry().getEnabledGames().size());
        }
        if (params.toLowerCase().startsWith("highscore_")) {
            String gameId = params.substring("highscore_".length());
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            return String.valueOf(gb != null ? gb.getHighScore(gameId) : 0L);
        }
        return null;
    }
}
