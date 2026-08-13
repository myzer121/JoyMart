package me.nikl.gamebox.game.impl.dinorun;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.GameManager;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Dino Run single-player game: a side-scrolling endless runner inspired by the
 * Chrome browser's offline dino game. The dino runs in place at the left of the
 * playfield while cacti scroll in from the right; the player clicks the Jump
 * button (or any column-0 slot in the playfield) to leap over them. The world
 * speeds up gradually as the score grows.
 */
public class GameDinoRun extends Game {

    private DinoRunManager manager;

    /** Ticks between world scroll steps (lower = faster). */
    private int scrollIntervalTicks = 6;
    /** Minimum gap (in scroll steps) between consecutive obstacles. */
    private int minObstacleGap = 4;
    /** Maximum gap (in scroll steps) between consecutive obstacles. */
    private int maxObstacleGap = 9;

    public GameDinoRun(GameBox plugin) {
        super(plugin, "dinorun", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 54));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        rule.setTrackHighScore(config.getBoolean("trackHighScore", true));
        double moneyPerToken = config.getDouble("moneyPerToken", 0.0);
        if (moneyPerToken > 0) rule.setMoneyPerToken(moneyPerToken);
        loadRewards(rewards, config.getConfigurationSection("rewards"));

        this.scrollIntervalTicks = Math.max(2, config.getInt("settings.scrollIntervalTicks", 6));
        this.minObstacleGap = Math.max(2, config.getInt("settings.minObstacleGap", 4));
        this.maxObstacleGap = Math.max(minObstacleGap + 1,
                config.getInt("settings.maxObstacleGap", 9));
    }

    @Override
    public void loadLanguage() {
        // merged by framework
    }

    @Override
    public void init() {
        // no-op
    }

    @Override
    public void loadGameManager() {
        this.manager = new DinoRunManager(this);
    }

    @Override
    public GameManager<?> getGameManager() {
        return manager;
    }

    public int getScrollIntervalTicks() { return scrollIntervalTicks; }
    public int getMinObstacleGap() { return minObstacleGap; }
    public int getMaxObstacleGap() { return maxObstacleGap; }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // Every 10 obstacles cleared grants a bonus token.
        if ("cleared".equals(event) && value > 0 && value % 10 == 0) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens(3);
            }
        }
    }
}
