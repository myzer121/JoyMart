package me.nikl.gamebox.listeners;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.inventory.AGui;
import me.nikl.gamebox.inventory.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The single Bukkit listener that routes all events to the right component:
 * inventory clicks → active game session or open {@link AGui}, closes → lobby
 * exit, join/quit → data load/save, plus lobby-item interaction handling.
 */
public class GameBoxListener implements Listener {

    private final GameBox plugin;

    public GameBoxListener(GameBox plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // First: is the player in an active game session? Games own their inventory.
        for (Game game : plugin.getGameRegistry().getEnabledGames()) {
            if (game.getGameManager().isInGame(player.getUniqueId())) {
                game.getGameManager().onInventoryClick(event, player);
                return;
            }
        }

        // Otherwise: is this a GameBox AGui?
        InventoryHolder holder = event.getInventory().getHolder();
        AGui gui = plugin.getGuiManager().resolve(holder);
        if (gui != null) {
            gui.handleClick(event);
            plugin.getPluginManager().playClick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        InventoryHolder holder = event.getInventory().getHolder();

        // Closing a game session inventory mid-game = forfeit (avoids soft-lock)
        if (holder instanceof me.nikl.gamebox.game.AbstractGameSession) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Game g : plugin.getGameRegistry().getEnabledGames()) {
                    if (g.getGameManager().isInGame(player.getUniqueId())) {
                        g.getGameManager().removeSession(player.getUniqueId());
                        break;
                    }
                }
            });
            return;
        }

        AGui gui = plugin.getGuiManager().resolve(holder);
        if (gui != null) {
            // Clear any pending shop confirmation when any GameBox GUI is
            // closed (covers both Shop and ShopCategory closures).
            plugin.getGuiManager().getShop().clearPending(player.getUniqueId());
            // Delayed to avoid closing-while-opening race
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // If the player's currently open inventory is no longer a GameBox gui,
                // treat it as leaving the lobby.
                InventoryHolder current = player.getOpenInventory().getTopInventory().getHolder();
                AGui stillOpen = plugin.getGuiManager().resolve(current);
                boolean inGame = false;
                for (Game g : plugin.getGameRegistry().getEnabledGames()) {
                    if (g.getGameManager().isInGame(player.getUniqueId())) { inGame = true; break; }
                }
                // Don't leave GameBox if the player is awaiting chat input (e.g.
                // renaming a shop item): the inventory was closed intentionally
                // and will be reopened by the text-input callback.
                boolean awaitingInput = plugin.getTextInputHandler() != null
                        && plugin.getTextInputHandler().isAwaiting(player.getUniqueId());
                // Don't leave GameBox if the player is in the admin flow
                // (ShopAdmin / PrizePoolEditor). Their inventory was
                // temporarily restored and a premature leaveGameBox would
                // wipe the saved-inventory entry, causing all items to vanish.
                boolean inAdmin = plugin.getPluginManager().isInAdminFlow(player.getUniqueId());
                if (stillOpen == null && !inGame && !awaitingInput && !inAdmin
                        && plugin.getPluginManager().isInGameBox(player.getUniqueId())) {
                    plugin.getPluginManager().leaveGameBox(player);
                }
                // If the player is in the admin flow but closed their GUI
                // (e.g. pressed ESC) without awaiting chat input, clean up
                // the admin state and return them to the shop GUI so they
                // don't get stuck with no GUI and a restored inventory.
                if (inAdmin && !awaitingInput && stillOpen == null
                        && plugin.getPluginManager().isInGameBox(player.getUniqueId())) {
                    plugin.getPluginManager().reClearInventory(player);
                    plugin.getPluginManager().setInAdminFlow(player.getUniqueId(), false);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()
                                && plugin.getPluginManager().isInGameBox(player.getUniqueId())) {
                            plugin.getGuiManager().openShop(player);
                        }
                    });
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // If the player is in an active game session, cancel all drags.
        for (Game game : plugin.getGameRegistry().getEnabledGames()) {
            if (game.getGameManager().isInGame(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        // If this is a GameBox AGui, cancel all drags.
        InventoryHolder holder = event.getInventory().getHolder();
        AGui gui = plugin.getGuiManager().resolve(holder);
        if (gui != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPluginManager().loadOnJoin(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.getPluginManager().giveLobbyItem(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Remove from any active game sessions
        for (Game game : plugin.getGameRegistry().getEnabledGames()) {
            if (game.getGameManager().isInGame(player.getUniqueId())) {
                game.getGameManager().removeSession(player.getUniqueId());
            }
        }
        plugin.getPluginManager().saveOnQuit(player);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (plugin.getPluginManager().isInGameBox(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (plugin.getPluginManager().isLobbyItem(item)) {
            event.setCancelled(true);
            plugin.getGuiManager().openMain(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getPluginManager().isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
