package me.nikl.gamebox.game.impl.tictactoe;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Two-player Tic-Tac-Toe. Players alternate placing X / O marks on a 3x3
 * board; the first to align three wins, a full board with no winner is a draw.
 */
public class GameTicTacToe extends Game {

    private TicTacToeManager manager;

    public GameTicTacToe(GameBox plugin) {
        super(plugin, "tictactoe", GameType.TWO_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        loadMultiRewards(multiRewards, config.getConfigurationSection("rewards"));
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
        this.manager = new TicTacToeManager(this);
    }

    @Override
    public TicTacToeManager getGameManager() {
        return manager;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // Forming a 2-in-a-row threat awards +1 bonus token.
        if ("threat".equals(event)) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens(1);
            }
        }
    }
}
