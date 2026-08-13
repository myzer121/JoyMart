package me.nikl.gamebox.game.impl.rockpaperscissors;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Two-player Rock-Paper-Scissors played as a best-of-N match. Each round both
 * players secretly pick a weapon; the round winner earns a point and the first
 * player to reach a majority of rounds wins the match.
 */
public class GameRockPaperScissors extends Game {

    private RPSManager manager;
    private int bestOf = 3;

    public GameRockPaperScissors(GameBox plugin) {
        super(plugin, "rockpaperscissors", GameType.TWO_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        loadMultiRewards(multiRewards, config.getConfigurationSection("rewards"));
        int configured = config.getInt("settings.bestOf", 3);
        this.bestOf = configured < 1 ? 1 : configured;
    }

    @Override
    public void loadLanguage() {
        // Language file is loaded and merged into the cache by the framework.
    }

    @Override
    public void init() {
        // No additional setup required.
    }

    @Override
    public void loadGameManager() {
        this.manager = new RPSManager(this);
    }

    @Override
    public RPSManager getGameManager() {
        return manager;
    }

    public int getBestOf() {
        return bestOf;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // A win streak awards `value` bonus tokens (e.g. 2 at a 2-streak, 3 at a 3-streak).
        if ("streak".equals(event) && value > 0) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens((int) value);
            }
        }
    }
}
