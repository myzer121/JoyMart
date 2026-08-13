package me.nikl.gamebox.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player leaves the GameBox lobby (closes a GameBox inventory or
 * a game session ends), before their normal inventory is restored.
 */
public class LeftGameBoxEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public LeftGameBoxEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
