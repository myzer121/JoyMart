package me.nikl.gamebox.game.impl.twentyfortyeight;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * 2048 single-player game: slide numbered tiles on a 4x4 grid, merge equal
 * tiles, and try to reach the 2048 tile. The game ends when no move is
 * possible; reaching 2048 counts as a win.
 */
public class Game2048 extends Game {

    private GameManager2048 manager;

    public Game2048(GameBox plugin) {
        super(plugin, "2048", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 45));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        rule.setTrackHighScore(config.getBoolean("trackHighScore", true));
        double moneyPerToken = config.getDouble("moneyPerToken", 0.0);
        if (moneyPerToken > 0) {
            rule.setMoneyPerToken(moneyPerToken);
        }
        loadRewards(rewards, config.getConfigurationSection("rewards"));
    }

    @Override
    public void loadLanguage() {
        // Language file is loaded and merged by the framework; nothing to do.
    }

    @Override
    public void init() {
        // No additional setup required.
    }

    @Override
    public void loadGameManager() {
        this.manager = new GameManager2048(this);
    }

    @Override
    public GameManager2048 getGameManager() {
        return manager;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        if ("won2048".equals(event)) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens(50);
            }
        }
    }
}
