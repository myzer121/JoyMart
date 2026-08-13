package me.nikl.gamebox.game.impl.minesweeper;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Minesweeper single-player game: reveal cells on a grid without detonating a
 * mine. Left-click reveals (flood-filling empty regions); right-click flags a
 * suspected mine. Revealing every non-mine cell wins the game.
 */
public class GameMinesweeper extends Game {

    private MinesweeperManager manager;

    public GameMinesweeper(GameBox plugin) {
        super(plugin, "minesweeper", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 54));
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
        this.manager = new MinesweeperManager(this);
    }

    @Override
    public MinesweeperManager getGameManager() {
        return manager;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        if ("mineHit".equals(event)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        }
    }
}
