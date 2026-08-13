package me.nikl.gamebox.game.impl.dinorun;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the Dino Run game. Holds a reference to the owning game
 * so it can construct fully-wired sessions.
 */
public class DinoRunManager extends AbstractGameManager<DinoRunSession> {

    private final GameDinoRun game;

    public DinoRunManager(GameDinoRun game) {
        this.game = game;
    }

    @Override
    protected DinoRunSession createSession(List<Player> players) {
        return new DinoRunSession(GameBox.getInstance(), game, players);
    }
}
