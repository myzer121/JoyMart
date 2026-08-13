package me.nikl.gamebox.game.impl.maze;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the maze game.
 */
public class MazeManager extends AbstractGameManager<MazeSession> {

    private final GameMaze game;

    public MazeManager(GameMaze game) {
        this.game = game;
    }

    @Override
    protected MazeSession createSession(List<Player> players) {
        return new MazeSession(GameBox.getInstance(), game, players);
    }
}
