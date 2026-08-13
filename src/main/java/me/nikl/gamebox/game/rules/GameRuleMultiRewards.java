package me.nikl.gamebox.game.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Reward configuration for two-player games: separate rewards for the winner
 * and the loser, plus commands run for each respectively.
 */
public class GameRuleMultiRewards {

    private int tokensWinner = 0;
    private double moneyWinner = 0;
    private final List<String> commandsWinner = new ArrayList<>();

    private int tokensLoser = 0;
    private double moneyLoser = 0;
    private final List<String> commandsLoser = new ArrayList<>();

    private int tokensDraw = 0;
    private double moneyDraw = 0;

    public int getTokensWinner() { return tokensWinner; }
    public void setTokensWinner(int v) { this.tokensWinner = v; }
    public double getMoneyWinner() { return moneyWinner; }
    public void setMoneyWinner(double v) { this.moneyWinner = v; }
    public List<String> getCommandsWinner() { return commandsWinner; }

    public int getTokensLoser() { return tokensLoser; }
    public void setTokensLoser(int v) { this.tokensLoser = v; }
    public double getMoneyLoser() { return moneyLoser; }
    public void setMoneyLoser(double v) { this.moneyLoser = v; }
    public List<String> getCommandsLoser() { return commandsLoser; }

    public int getTokensDraw() { return tokensDraw; }
    public void setTokensDraw(int v) { this.tokensDraw = v; }
    public double getMoneyDraw() { return moneyDraw; }
    public void setMoneyDraw(double v) { this.moneyDraw = v; }
}
