package me.nikl.gamebox.game.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Reward configuration applied when a single-player game is won (or finishes
 * with a score). Tokens and money are scaled from the score; commands run once.
 */
public class GameRuleRewards {

    private int tokensOnWin = 0;
    private double moneyOnWin = 0;
    private final List<String> commandsOnWin = new ArrayList<>();
    private int tokensOnLose = 0;
    private double moneyOnLose = 0;

    public int getTokensOnWin() { return tokensOnWin; }
    public void setTokensOnWin(int tokensOnWin) { this.tokensOnWin = tokensOnWin; }

    public double getMoneyOnWin() { return moneyOnWin; }
    public void setMoneyOnWin(double moneyOnWin) { this.moneyOnWin = moneyOnWin; }

    public List<String> getCommandsOnWin() { return commandsOnWin; }

    public int getTokensOnLose() { return tokensOnLose; }
    public void setTokensOnLose(int tokensOnLose) { this.tokensOnLose = tokensOnLose; }

    public double getMoneyOnLose() { return moneyOnLose; }
    public void setMoneyOnLose(double moneyOnLose) { this.moneyOnLose = moneyOnLose; }
}
