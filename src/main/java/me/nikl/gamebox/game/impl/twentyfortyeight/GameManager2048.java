package me.nikl.gamebox.game.impl.twentyfortyeight;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the 2048 game. Stores a reference to the owning game so
 * it can construct fully-wired sessions.
 */
public class GameManager2048 extends AbstractGameManager<GameSession2048> {

    private final Game2048 game;

    public GameManager2048(Game2048 game) {
        this.game = game;
    }

    @Override
    protected GameSession2048 createSession(List<Player> players) {
        return new GameSession2048(GameBox.getInstance(), game, players);
    }
}
