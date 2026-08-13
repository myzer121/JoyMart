package me.nikl.gamebox.game.impl.connect4;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Two-player Connect 4. Players alternate dropping coloured pieces into the
 * columns of a shared grid; first to connect four in a row wins.
 */
public class GameConnect4 extends Game {

    private Connect4Manager manager;
    private int cols = 7;
    private int rows = 6;

    public GameConnect4(GameBox plugin) {
        super(plugin, "connect4", GameType.TWO_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        loadMultiRewards(multiRewards, config.getConfigurationSection("rewards"));
        // Clamped to fit a size-54 inventory (6 rows of 9, with two info columns).
        this.cols = Math.min(7, Math.max(1, config.getInt("settings.cols", 7)));
        this.rows = Math.min(6, Math.max(1, config.getInt("settings.rows", 6)));
    }

    @Override
    public void loadLanguage() {
        // The game-specific language file is loaded and merged into the cache by
        // the framework in onEnable() before this hook is called.
    }

    @Override
    public void init() {
        // No additional initialisation required.
    }

    @Override
    public void loadGameManager() {
        this.manager = new Connect4Manager(this);
    }

    @Override
    public Connect4Manager getGameManager() {
        return manager;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // Forming a 3-in-a-row threat awards +2 bonus tokens to encourage tactics.
        if ("threat".equals(event)) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens(2);
            }
        }
    }
}
