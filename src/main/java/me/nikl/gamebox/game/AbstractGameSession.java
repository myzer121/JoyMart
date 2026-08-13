package me.nikl.gamebox.game;

import me.nikl.gamebox.GameBox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/**
 * Base class for an active game session (single- or two-player). Owns the
 * Bukkit inventory displayed to the player(s) and exposes hooks for subclasses
 * to render state and react to clicks.
 *
 * <p>Subclasses implement {@link #build()} to populate the inventory and
 * {@link #onClick(InventoryClickEvent, Player)} for game-specific logic. Call
 * {@link #start()} to open the inventory for all participants and
 * {@link #end()} to clean up.</p>
 */
public abstract class AbstractGameSession implements InventoryHolder {

    protected final GameBox plugin;
    protected final Game game;
    protected final List<Player> players;
    protected Inventory inventory;
    protected boolean finished = false;

    /** When true, the second player is an AI (no real Player object). */
    protected boolean vsAi = false;

    /** Fixed UUID used to represent the AI opponent in vsAi sessions. */
    public static final UUID AI_ID = UUID.nameUUIDFromBytes("GameBox-AI".getBytes());
    public static final String AI_NAME = "AI";

    public AbstractGameSession(GameBox plugin, Game game, List<Player> players) {
        this.plugin = plugin;
        this.game = game;
        this.players = players;
    }

    /** Mark this session as an AI match (called by the manager before start). */
    public void setVsAi(boolean vsAi) {
        this.vsAi = vsAi;
    }

    public boolean isVsAi() {
        return vsAi;
    }

    protected abstract int getInventorySize();
    protected abstract String getInventoryTitle();

    /** Build/refresh the inventory contents from current game state. */
    public abstract void build();

    /** React to a click inside this session's inventory. */
    public abstract void onClick(InventoryClickEvent event, Player player);

    /** Open the session for all players and render the first frame. */
    public void start() {
        this.inventory = Bukkit.createInventory(this, getInventorySize(),
                me.nikl.gamebox.utility.Utility.color(getInventoryTitle()));
        build();
        for (Player p : players) {
            p.openInventory(inventory);
        }
    }

    /** Re-render and refresh the open inventory for all players. */
    public void refresh() {
        build();
        for (Player p : players) {
            if (p.getOpenInventory().getTopInventory().getHolder() == this) {
                p.getOpenInventory().getTopInventory().setContents(inventory.getContents());
            } else {
                p.openInventory(inventory);
            }
        }
    }

    /** Whether the given inventory belongs to this session. */
    public boolean isOwnInventory(Inventory inv) {
        return inv != null && inv.getHolder() == this;
    }

    public List<Player> getPlayers() { return players; }
    public Game getGame() { return game; }
    public boolean isFinished() { return finished; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** End the session: subclasses call super.end() after settling rewards. */
    public void end() {
        finished = true;
        // Refund any unsettled bets. In the normal flow, onGameWonMulti has
        // already drained the bet map (winner took the pot), so this is a
        // no-op. On forfeit / disconnect / early exit, the escrowed bets are
        // still pending and must be returned to both players.
        if (!players.isEmpty()) {
            java.util.UUID[] ids = new java.util.UUID[players.size()];
            for (int i = 0; i < players.size(); i++) {
                ids[i] = players.get(i).getUniqueId();
            }
            game.refundBets(ids);
        }
        // Return players to the game gui after a short delay
        for (Player p : players) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    game.getGameGui().open(p);
                }
            }, 40L);
        }
    }

    public Player getOpponent(Player self) {
        for (Player p : players) {
            if (!p.getUniqueId().equals(self.getUniqueId())) return p;
        }
        return null;
    }

    /** Resolve a UUID to a display name, returning "AI" for the AI opponent. */
    public String playerName(UUID uuid) {
        if (uuid.equals(AI_ID)) return AI_NAME;
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : "?";
    }

    public UUID getFirstPlayerId() {
        return players.isEmpty() ? null : players.get(0).getUniqueId();
    }

    /**
     * Called when a player closes the session inventory mid-game (forfeit path,
     * triggered by {@link AbstractGameManager#removeSession}). Default is a
     * no-op; sessions that need to settle accumulated state on early exit
     * (e.g. the slot machine's running total) override this.
     */
    public void onClose() {
        // default: no-op
    }
}
