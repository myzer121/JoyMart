package me.nikl.gamebox.game.impl.slotmachine;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Session manager for the slot machine game.
 */
public class SlotMachineManager extends AbstractGameManager<SlotMachineSession> {

    private final GameSlotMachine game;

    public SlotMachineManager(GameSlotMachine game) {
        this.game = game;
    }

    @Override
    protected SlotMachineSession createSession(List<Player> players) {
        return new SlotMachineSession(GameBox.getInstance(), game, players);
    }
}
