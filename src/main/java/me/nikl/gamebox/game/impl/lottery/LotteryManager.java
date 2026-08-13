package me.nikl.gamebox.game.impl.lottery;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the lottery game.
 */
public class LotteryManager extends AbstractGameManager<LotterySession> {

    private final GameLottery game;

    public LotteryManager(GameLottery game) {
        this.game = game;
    }

    @Override
    protected LotterySession createSession(List<Player> players) {
        return new LotterySession(GameBox.getInstance(), game, players);
    }
}
