package me.nikl.gamebox.game.impl.battleship;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for {@link GameBattleship}. Creates a shared
 * {@link BattleshipSession} for the two invited players.
 */
public class BattleshipManager extends AbstractGameManager<BattleshipSession> {

    private final GameBattleship game;

    public BattleshipManager(GameBattleship game) {
        this.game = game;
    }

    @Override
    protected BattleshipSession createSession(List<Player> players) {
        return new BattleshipSession(GameBox.getInstance(), game, players);
    }
}
