package me.nikl.gamebox.game.impl.bejeweled;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Manages active Bejeweled sessions. Bejeweled has no background scheduler task,
 * so the inherited session cleanup is sufficient.
 */
public class BejeweledManager extends AbstractGameManager<BejeweledSession> {

    private final GameBejeweled game;

    public BejeweledManager(GameBejeweled game) {
        this.game = game;
    }

    @Override
    protected BejeweledSession createSession(List<Player> players) {
        return new BejeweledSession(GameBox.getInstance(), game, players);
    }
}
