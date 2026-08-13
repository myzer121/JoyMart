package me.nikl.gamebox.game.impl.snake;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.GameManager;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Snake single-player game: control a snake on a 5x9 grid, eat food to grow,
 * and avoid hitting the walls or your own tail. The snake moves automatically
 * on a timer; the player clicks directional buttons (or the bottom-row control
 * slots) to steer it. Speed gradually increases as the snake grows longer.
 */
public class GameSnake extends Game {

    private SnakeManager manager;

    /** Initial move interval in ticks (20 ticks = 1 second). */
    private int startIntervalTicks = 20;
    /** Minimum move interval once speed has ramped up. */
    private int minIntervalTicks = 6;
    /** Ticks removed from the interval each time food is eaten. */
    private int speedupTicks = 1;

    public GameSnake(GameBox plugin) {
        super(plugin, "snake", GameType.SINGLE_PLAYER);
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

        this.startIntervalTicks = Math.max(2, config.getInt("settings.startIntervalTicks", 20));
        this.minIntervalTicks = Math.max(2, config.getInt("settings.minIntervalTicks", 6));
        this.speedupTicks = Math.max(0, config.getInt("settings.speedupTicks", 1));
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
        this.manager = new SnakeManager(this);
    }

    @Override
    public GameManager<?> getGameManager() {
        return manager;
    }

    public int getStartIntervalTicks() { return startIntervalTicks; }
    public int getMinIntervalTicks() { return minIntervalTicks; }
    public int getSpeedupTicks() { return speedupTicks; }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // Every 5 foods eaten grants a bonus token.
        if ("food".equals(event) && value > 0 && value % 5 == 0) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens(2);
            }
        }
    }
}
