package me.nikl.gamebox.game.impl.chess;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the Chess game. Holds a reference to the owning game so
 * it can construct fully-wired sessions.
 */
public class ChessManager extends AbstractGameManager<ChessSession> {

    private final GameChess game;

    public ChessManager(GameChess game) {
        this.game = game;
    }

    @Override
    protected ChessSession createSession(List<Player> players) {
        return new ChessSession(GameBox.getInstance(), game, players);
    }
}
