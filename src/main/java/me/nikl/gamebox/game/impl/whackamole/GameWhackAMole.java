package me.nikl.gamebox.game.impl.whackamole;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.GameManager;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Whack-a-Mole: a time-limited single-player game. Moles pop up in a 3x3 grid
 * of holes for short intervals; clicking a mole before it hides scores a point.
 * When the timer runs out the session settles the score with the framework.
 */
public class GameWhackAMole extends Game {

    private WhackAMoleManager manager;

    private int durationSeconds = 30;
    private int popIntervalTicks = 20;

    public GameWhackAMole(GameBox plugin) {
        super(plugin, "whackamole", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 27));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        loadRewards(rewards, config.getConfigurationSection("rewards"));

        this.durationSeconds = Math.max(1, config.getInt("settings.durationSeconds", 30));
        this.popIntervalTicks = Math.max(1, config.getInt("settings.popIntervalTicks", 20));
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
        this.manager = new WhackAMoleManager(this);
    }

    @Override
    public GameManager<?> getGameManager() {
        return manager;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getPopIntervalTicks() {
        return popIntervalTicks;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        if ("golden".equals(event)) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens(5);
            }
        }
    }
}
