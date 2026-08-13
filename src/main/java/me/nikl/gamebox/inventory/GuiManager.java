package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for GameBox GUIs. Owns the {@link MainGui} and {@link Shop},
 * and lets games register their {@link GameGui}. Also tracks which GUI each
 * player currently has open so the {@link PluginManager} can route clicks.
 */
public class GuiManager {

    private final GameBox plugin;
    private final MainGui mainGui;
    private final Shop shop;
    private final ShopAdmin shopAdmin;
    private final DeliveryBoxGui deliveryBoxGui;
    private final Map<String, GameGui> gameGuis = new HashMap<>();
    private final Map<java.util.UUID, AGui> openGuis = new HashMap<>();

    public GuiManager(GameBox plugin) {
        this.plugin = plugin;
        this.mainGui = new MainGui(plugin);
        this.shop = new Shop(plugin);
        this.shopAdmin = new ShopAdmin(plugin);
        this.deliveryBoxGui = new DeliveryBoxGui(plugin);
    }

    public void registerGameGui(String gameId, GameGui gui) {
        gameGuis.put(gameId, gui);
    }

    public void unregisterGameGui(String gameId) {
        gameGuis.remove(gameId);
    }

    public GameGui getGameGui(String gameId) {
        return gameGuis.get(gameId);
    }

    /** Open the main menu for a player and mark them as inside GameBox. */
    public void openMain(Player player) {
        if (!player.hasPermission("gamebox.use")) {
            player.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        plugin.getPluginManager().enterGameBox(player);
        mainGui.build(player);
        player.openInventory(mainGui.getInventory());
        openGuis.put(player.getUniqueId(), mainGui);
    }

    public void openShop(Player player) {
        shop.build(player);
        player.openInventory(shop.getInventory());
        openGuis.put(player.getUniqueId(), shop);
    }

    /** Open the shop admin (上架/下架) GUI. Caller must already be a player. */
    public void openShopAdmin(Player player) {
        if (!player.hasPermission(ShopAdmin.PERM)) {
            player.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        // Temporarily restore the player's real inventory ONCE when entering
        // the admin flow. This lets them pick up items to add to the shop.
        // The inventory is re-cleared when they leave via ShopAdmin's back
        // button. Previously, tempRestoreInventory was called in
        // ShopAdminCategory.open() on every navigation, which overwrote
        // the player's current inventory state and caused items to vanish.
        plugin.getPluginManager().tempRestoreInventory(player);
        shopAdmin.build(player);
        player.openInventory(shopAdmin.getInventory());
        openGuis.put(player.getUniqueId(), shopAdmin);
    }

    public void openTopList(Player player, String gameId) {
        TopListPage page = new TopListPage(plugin, gameId);
        page.build(player);
        player.openInventory(page.getInventory());
        openGuis.put(player.getUniqueId(), page);
    }

    public void openGameGui(Player player, Game game) {
        game.getGameGui().open(player);
        openGuis.put(player.getUniqueId(), game.getGameGui());
    }

    /** Record that a player has a specific gui open. */
    public void track(java.util.UUID uuid, AGui gui) {
        openGuis.put(uuid, gui);
    }

    public AGui getOpenGui(java.util.UUID uuid) {
        return openGuis.get(uuid);
    }

    /** Read-only view of all currently-open GUIs (player UUID -> GUI). */
    public Map<java.util.UUID, AGui> getOpenGuis() {
        return java.util.Collections.unmodifiableMap(openGuis);
    }

    public void forget(java.util.UUID uuid) {
        openGuis.remove(uuid);
    }

    /** Determine the AGui instance backing an inventory, if it is a GameBox gui. */
    public AGui resolve(InventoryHolder holder) {
        if (holder instanceof AGui) return (AGui) holder;
        return null;
    }

    public MainGui getMainGui() { return mainGui; }
    public Shop getShop() { return shop; }

    /**
     * Open the delivery box GUI for a player. If the player is currently in a
     * GameBox session, they are removed first so their real inventory is
     * restored (the delivery box needs the real inventory to be visible and
     * interactable for item pickup).
     */
    public void openDeliveryBox(Player player) {
        if (plugin.getPluginManager().isInGameBox(player.getUniqueId())) {
            plugin.getPluginManager().leaveGameBox(player);
        }
        deliveryBoxGui.build(player);
        player.openInventory(deliveryBoxGui.getInventory());
        openGuis.put(player.getUniqueId(), deliveryBoxGui);
    }
}
