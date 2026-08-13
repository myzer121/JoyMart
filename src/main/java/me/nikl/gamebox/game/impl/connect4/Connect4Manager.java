package me.nikl.gamebox.game.impl.connect4;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for {@link GameConnect4}. Creates a shared
 * {@link Connect4Session} for the two invited players.
 */
public class Connect4Manager extends AbstractGameManager<Connect4Session> {

    private final GameConnect4 game;

    public Connect4Manager(GameConnect4 game) {
        this.game = game;
    }

    @Override
    protected Connect4Session createSession(List<Player> players) {
        return new Connect4Session(GameBox.getInstance(), game, players);
    }
}
