package me.nikl.gamebox.game.impl.tictactoe;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for Tic-Tac-Toe. Creates a single shared session for the
 * two invited players.
 */
public class TicTacToeManager extends AbstractGameManager<TicTacToeSession> {

    protected final GameTicTacToe game;

    public TicTacToeManager(GameTicTacToe game) {
        this.game = game;
    }

    @Override
    protected TicTacToeSession createSession(List<Player> players) {
        return new TicTacToeSession(GameBox.getInstance(), game, players);
    }
}
