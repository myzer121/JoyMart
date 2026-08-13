package me.nikl.gamebox.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player opens the GameBox main menu (enters the lobby).
 * Useful for running configured enter-commands or other integrations.
 */
public class EnterGameBoxEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public EnterGameBoxEvent(Player player) {
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
