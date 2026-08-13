package me.nikl.gamebox.game.impl.maze;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;

/**
 * Maze single-player game: navigate a randomly generated maze from the
 * top-left corner to the bottom-right goal in as few moves as possible. Walls
 * are carved via recursive backtracker; the player clicks an adjacent open
 * cell to move. Lower move counts yield higher scores.
 */
public class GameMaze extends Game {

    private MazeManager manager;
    private int gridSize = 6;

    public GameMaze(GameBox plugin) {
        super(plugin, "maze", GameType.SINGLE_PLAYER);
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

        gridSize = config.getInt("settings.gridSize", 6);
        if (gridSize < 3) gridSize = 3;
        if (gridSize > 6) gridSize = 6; // must fit in a 6-row inventory
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
        this.manager = new MazeManager(this);
    }

    @Override
    public MazeManager getGameManager() {
        return manager;
    }

    public int getGridSize() { return gridSize; }
}
