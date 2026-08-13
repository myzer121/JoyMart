package me.nikl.gamebox.game.impl.bejeweled;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.GameManager;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Bejeweled: a single-player match-3 game. An 8x6 grid of coloured gems is
 * rendered in a size-54 inventory. The player swaps adjacent gems to form lines
 * of three or more, which clear, cascade, and score. The game ends when the move
 * limit is reached.
 */
public class GameBejeweled extends Game {

    private BejeweledManager manager;

    private int rows = 6;
    private int cols = 8;
    private int moveLimit = 20;

    public GameBejeweled(GameBox plugin) {
        super(plugin, "bejeweled", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 54));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        loadRewards(rewards, config.getConfigurationSection("rewards"));

        this.rows = Math.max(1, Math.min(6, config.getInt("settings.rows", 6)));
        this.cols = Math.max(1, Math.min(8, config.getInt("settings.cols", 8)));
        this.moveLimit = Math.max(1, config.getInt("settings.moveLimit", 20));
    }

    @Override
    public void loadLanguage() {
        // Language is loaded and merged into the cache by the framework.
    }

    @Override
    public void init() {
        // No pre-computation needed.
    }

    @Override
    public void loadGameManager() {
        this.manager = new BejeweledManager(this);
    }

    @Override
    public GameManager<?> getGameManager() {
        return manager;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public int getMoveLimit() {
        return moveLimit;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        if ("cascade".equals(event) && value > 0) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens((int) value);
            }
        }
    }
}
