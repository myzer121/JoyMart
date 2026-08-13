package me.nikl.gamebox.game.impl.snake;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the Snake game. Holds a reference to the owning game so
 * it can construct fully-wired sessions.
 */
public class SnakeManager extends AbstractGameManager<SnakeSession> {

    private final GameSnake game;

    public SnakeManager(GameSnake game) {
        this.game = game;
    }

    @Override
    protected SnakeSession createSession(List<Player> players) {
        return new SnakeSession(GameBox.getInstance(), game, players);
    }
}
