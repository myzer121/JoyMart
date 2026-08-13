package me.nikl.gamebox.game;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base implementation of {@link GameManager} that stores sessions per player and
 * routes inventory clicks. Subclasses only need to provide
 * {@link #createSession(List)}.
 */
public abstract class AbstractGameManager<S extends AbstractGameSession> implements GameManager<S> {

    protected final Map<UUID, S> sessions = new HashMap<>();

    /** Create a new session for the given players (already validated by caller). */
    protected abstract S createSession(List<Player> players);

    @Override
    public void startGame(List<Player> players) {
        S session = createSession(players);
        for (Player p : players) {
            sessions.put(p.getUniqueId(), session);
        }
        session.start();
    }

    @Override
    public void startGameVsAi(Player player) {
        S session = createSession(java.util.Collections.singletonList(player));
        session.setVsAi(true);
        sessions.put(player.getUniqueId(), session);
        session.start();
    }

    @Override
    public boolean isInGame(UUID player) {
        return sessions.containsKey(player);
    }

    @Override
    public S getSession(UUID player) {
        return sessions.get(player);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event, Player player) {
        S session = sessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }
        // Only react when the click is in the session's own inventory
        if (!session.isOwnInventory(event.getInventory())) {
            event.setCancelled(true);
            return;
        }
        session.onClick(event, player);
    }

    @Override
    public void removeSession(UUID player) {
        S session = sessions.remove(player);
        if (session == null) return;
        // Remove all participants of the same session
        session.getPlayers().forEach(p -> sessions.remove(p.getUniqueId()));
        // Give the session a chance to settle accumulated state on forfeit.
        try {
            session.onClose();
        } catch (Exception ignored) {
        }
        // Reopen the game GUI so the player is not left with an empty
        // inventory and no way back into GameBox (soft-lock fix). Sessions
        // that already called end() in onClose() will have finished=true,
        // but end() is safe to call again — it just reschedules the reopen.
        if (!session.isFinished()) {
            session.end();
        }
    }

    @Override
    public Collection<S> getSessions() {
        return sessions.values();
    }

    /** Cleanly remove a specific session for all its players. */
    public void endSession(S session) {
        for (Player p : session.getPlayers()) {
            sessions.remove(p.getUniqueId());
        }
    }
}
