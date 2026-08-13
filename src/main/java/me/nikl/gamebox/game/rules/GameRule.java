package me.nikl.gamebox.game.rules;

/**
 * Configurable rules shared by every game: type, GUI size, entry cost,
 * and whether high scores are tracked.
 */
public class GameRule {

    private final GameType type;
    private int guiSize = 27;
    private double cost = 0;
    private boolean trackHighScore = true;
    private int scorePerToken = 0; // tokens awarded = score * factor
    private double moneyPerToken = 0;

    public GameRule(GameType type) {
        this.type = type;
    }

    public GameType getType() { return type; }

    public int getGuiSize() { return guiSize; }
    public void setGuiSize(int guiSize) { this.guiSize = clampSize(guiSize); }

    private int clampSize(int size) {
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return Math.max(9, Math.min(54, size));
    }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    public boolean isTrackHighScore() { return trackHighScore; }
    public void setTrackHighScore(boolean trackHighScore) { this.trackHighScore = trackHighScore; }

    public int getScorePerToken() { return scorePerToken; }
    public void setScorePerToken(int scorePerToken) { this.scorePerToken = scorePerToken; }

    public double getMoneyPerToken() { return moneyPerToken; }
    public void setMoneyPerToken(double moneyPerToken) { this.moneyPerToken = moneyPerToken; }
}
