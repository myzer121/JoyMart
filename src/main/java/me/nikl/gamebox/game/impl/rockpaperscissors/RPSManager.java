package me.nikl.gamebox.game.impl.rockpaperscissors;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for Rock-Paper-Scissors. Creates a single shared session
 * for the two invited players.
 */
public class RPSManager extends AbstractGameManager<RPSSession> {

    protected final GameRockPaperScissors game;

    public RPSManager(GameRockPaperScissors game) {
        this.game = game;
    }

    @Override
    protected RPSSession createSession(List<Player> players) {
        return new RPSSession(GameBox.getInstance(), game, players);
    }
}
