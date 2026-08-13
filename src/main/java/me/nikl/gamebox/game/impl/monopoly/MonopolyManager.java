package me.nikl.gamebox.game.impl.monopoly;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;

import java.util.List;

import org.bukkit.entity.Player;

/**
 * Session manager for Monopoly. Creates a single shared session for the 2-3
 * invited players (or 1 human + AI).
 */
public class MonopolyManager extends AbstractGameManager<MonopolySession> {

    protected final GameMonopoly game;

    public MonopolyManager(GameMonopoly game) {
        this.game = game;
    }

    @Override
    protected MonopolySession createSession(List<Player> players) {
        return new MonopolySession(GameBox.getInstance(), game, players);
    }
}
