package me.nikl.gamebox.game.impl.whackamole;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Manages active Whack-a-Mole sessions. Overrides {@link #removeSession(UUID)}
 * to cancel the per-session scheduler task when a player quits, so no task leaks.
 */
public class WhackAMoleManager extends AbstractGameManager<WhackAMoleSession> {

    private final GameWhackAMole game;

    public WhackAMoleManager(GameWhackAMole game) {
        this.game = game;
    }

    @Override
    protected WhackAMoleSession createSession(List<Player> players) {
        return new WhackAMoleSession(GameBox.getInstance(), game, players);
    }

    @Override
    public void removeSession(UUID player) {
        WhackAMoleSession session = sessions.get(player);
        if (session == null) {
            return;
        }
        session.cancelTask();
        super.removeSession(player);
    }
}
