package me.nikl.gamebox.game.impl.minesweeper;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the Minesweeper game. Holds a reference to the owning
 * game so it can construct fully-wired sessions.
 */
public class MinesweeperManager extends AbstractGameManager<MinesweeperSession> {

    private final GameMinesweeper game;

    public MinesweeperManager(GameMinesweeper game) {
        this.game = game;
    }

    @Override
    protected MinesweeperSession createSession(List<Player> players) {
        return new MinesweeperSession(GameBox.getInstance(), game, players);
    }
}
