package me.nikl.gamebox.game;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Manages active game sessions for a single game: creates sessions, routes
 * inventory clicks, and reports whether a player is currently playing.
 *
 * @param <S> the session type the manager holds
 */
public interface GameManager<S> {

    /** Start a new session for the given players. */
    void startGame(List<Player> players);

    /** Start a new session where the (single) human player faces the AI. */
    void startGameVsAi(Player player);

    /** Whether the given player is currently in an active session. */
    boolean isInGame(UUID player);

    /** Get the active session for a player, or null. */
    S getSession(UUID player);

    /** Route an inventory click to the player's session. */
    void onInventoryClick(InventoryClickEvent event, Player player);

    /** Remove and clean up a player's session (e.g. on quit). */
    void removeSession(UUID player);

    /** All currently active sessions. */
    Collection<S> getSessions();
}
