package me.nikl.gamebox;

import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.events.EnterGameBoxEvent;
import me.nikl.gamebox.events.LeftGameBoxEvent;
import me.nikl.gamebox.input.InvitationHandler;
import me.nikl.gamebox.inventory.AGui;
import me.nikl.gamebox.inventory.GuiManager;
import me.nikl.gamebox.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core runtime manager: holds the in-memory {@link GBPlayer} cache, saves and
 * restores player inventories around GameBox sessions, runs the auto-save task,
 * and exposes sound-effect helpers.
 *
 * <p>Event routing (clicks, closes, joins) lives in the listeners package and
 * delegates here.</p>
 */
public class PluginManager {

    private final GameBox plugin;
    private final Map<UUID, GBPlayer> players = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Saved inventories for players currently inside a GameBox session.
     * Uses a concurrent map because the auto-save task and quit/leave
     * handling can race on different threads.
     */
    private final Map<UUID, ItemStack[]> savedInventories = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Items purchased from the token shop (or otherwise earned) while the
     * player is inside a GameBox session. These CANNOT be added directly to
     * the player's inventory because {@link #enterGameBox} cleared it; adding
     * to the cleared inventory would be wiped out by {@link #restoreInventory}.
     * Instead we queue them here and merge them into the restored inventory
     * (or drop at the player's feet if full) when the player leaves GameBox.
     */
    private final Map<UUID, java.util.List<ItemStack>> pendingItems = new java.util.concurrent.ConcurrentHashMap<>();
    private InvitationHandler invitationHandler;
    private int autoSaveTaskId = -1;
    private me.nikl.gamebox.scoreboard.ScoreboardManager scoreboardManager;

    /**
     * Players currently in the ShopAdmin (or PrizePoolEditor) flow. While this
     * flag is set, the InventoryCloseEvent handler must NOT call
     * leaveGameBox(), because the admin GUIs temporarily restore the
     * player's real inventory and closing/reopening during navigation
     * (e.g. chat input for category names) would trigger a premature
     * leaveGameBox → restoreInventory that wipes the saved-inventory entry.
     */
    private final Set<UUID> inAdminFlow = new java.util.concurrent.ConcurrentHashMap().newKeySet();

    public PluginManager(GameBox plugin) {
        this.plugin = plugin;
        this.scoreboardManager = new me.nikl.gamebox.scoreboard.ScoreboardManager(plugin);
    }

    public void setInvitationHandler(InvitationHandler handler) {
        this.invitationHandler = handler;
    }

    public me.nikl.gamebox.scoreboard.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    // ---------------- Player data (lazy, cached) ----------------

    /**
     * Lazy-load (or fetch cached) GBPlayer data. Returns the in-memory cached
     * instance whenever possible; only hits the database on first access after
     * join or after an explicit {@link #invalidate(UUID)}.
     */
    public GBPlayer getPlayer(UUID uuid) {
        GBPlayer p = players.get(uuid);
        if (p == null) {
            Player online = Bukkit.getPlayer(uuid);
            String name = online != null ? online.getName() : uuid.toString();
            p = plugin.getDataBase().loadPlayer(uuid, name);
            players.put(uuid, p);
        }
        return p;
    }

    /** Look up a player without forcing a database load. */
    public GBPlayer getPlayerIfLoaded(UUID uuid) {
        return players.get(uuid);
    }

    public GBPlayer loadOnJoin(Player player) {
        GBPlayer p = plugin.getDataBase().loadPlayer(player.getUniqueId(), player.getName());
        players.put(player.getUniqueId(), p);
        return p;
    }

    /** Drop the in-memory cache entry for a player (next get reloads). */
    public void invalidate(UUID uuid) {
        players.remove(uuid);
    }

    public void saveOnQuit(Player player) {
        GBPlayer p = players.remove(player.getUniqueId());
        if (p != null && p.isDirty()) {
            plugin.getDataBase().savePlayer(p);
        }
        // Clear scoreboard if active
        if (scoreboardManager.isActive(player.getUniqueId())) {
            scoreboardManager.clear(player);
        }
        // Restore inventory if they were in GameBox
        if (savedInventories.containsKey(player.getUniqueId())) {
            restoreInventory(player);
        }
        plugin.getGuiManager().forget(player.getUniqueId());
    }

    public void saveAll() {
        plugin.getDataBase().saveAll(players);
    }

    // ---------------- Scoreboard shortcuts ----------------

    public void updateGameScoreboard(Player player, String gameId, long score, boolean won) {
        scoreboardManager.show(player, gameId, score, won);
    }

    public void clearGameScoreboardLater(Player player, long delayTicks) {
        scoreboardManager.clearLater(player, delayTicks);
    }

    // ---------------- Inventory save/restore ----------------

    /** Entering GameBox: clear inventory so only the GUI matters. */
    public void enterGameBox(Player player) {
        if (!savedInventories.containsKey(player.getUniqueId())) {
            PlayerInventory inv = player.getInventory();
            savedInventories.put(player.getUniqueId(), inv.getContents().clone());
            inv.clear();
        }
        // Enter event + configured commands
        Bukkit.getPluginManager().callEvent(new EnterGameBoxEvent(player));
        runCustomCommands(GameBoxSettings.commandsOnEnter, player);

        // Title
        player.sendTitle(
                me.nikl.gamebox.utility.Utility.color(plugin.lang(player, "titles.enter")),
                me.nikl.gamebox.utility.Utility.color(plugin.lang(player, "titles.enterSubtitle")),
                10, 40, 10);
    }

    /** Leaving GameBox: restore inventory, fire the left event. */
    public void leaveGameBox(Player player) {
        restoreInventory(player);
        Bukkit.getPluginManager().callEvent(new LeftGameBoxEvent(player));
        runCustomCommands(GameBoxSettings.commandsOnLeave, player);
        plugin.getGuiManager().forget(player.getUniqueId());
    }

    public void restoreInventory(Player player) {
        ItemStack[] saved = savedInventories.remove(player.getUniqueId());
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
        // Merge any pending items (e.g. shop purchases made during the
        // GameBox session). The player's real inventory has just been
        // restored, so addItem now targets the correct inventory. Any
        // overflow (full inventory) is dropped at the player's feet so
        // purchased items are never lost.
        java.util.List<ItemStack> pending = pendingItems.remove(player.getUniqueId());
        if (pending != null && !pending.isEmpty()) {
            PlayerInventory inv = player.getInventory();
            for (ItemStack stack : pending) {
                if (stack == null || stack.getType() == Material.AIR) continue;
                java.util.Map<Integer, ItemStack> overflow = inv.addItem(stack);
                if (!overflow.isEmpty()) {
                    org.bukkit.Location loc = player.getLocation();
                    for (ItemStack drop : overflow.values()) {
                        player.getWorld().dropItemNaturally(loc, drop);
                    }
                    plugin.getLogger().info("Shop item overflow for " + player.getName()
                            + ": dropped " + overflow.size() + " stack(s) at their location.");
                }
            }
            plugin.getLogger().info("Delivered " + pending.size()
                    + " pending item stack(s) to " + player.getName() + " on inventory restore.");
        }
    }

    /**
     * Queue an item to be delivered to the player when they leave GameBox.
     *
     * <p>While a player is in a GameBox session their real inventory has been
     * saved and cleared (see {@link #enterGameBox}). Calling
     * {@code player.getInventory().addItem()} directly would add to the
     * cleared inventory and be wiped on restore. This method queues the item
     * so it is merged into the real inventory on {@link #restoreInventory}.</p>
     *
     * <p>If the player is NOT in a GameBox session, the item is delivered
     * immediately (with overflow dropped at their feet).</p>
     */
    public void addPendingItem(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType() == Material.AIR) return;
        UUID uuid = player.getUniqueId();
        if (savedInventories.containsKey(uuid)) {
            // Player is in GameBox: queue for delivery on exit
            pendingItems.computeIfAbsent(uuid, k -> new java.util.ArrayList<>()).add(stack.clone());
            plugin.getLogger().info("Queued shop item for " + player.getName()
                    + " (" + stack.getType() + " x" + stack.getAmount() + "); will deliver on GameBox exit.");
        } else {
            // Player is not in GameBox: deliver immediately
            java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                org.bukkit.Location loc = player.getLocation();
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(loc, drop);
                }
            }
            plugin.getLogger().info("Delivered shop item to " + player.getName()
                    + " (" + stack.getType() + " x" + stack.getAmount() + ") immediately.");
        }
    }

    /**
     * Convenience: queue (or deliver) {@code amount} of {@code material} to
     * the player, with debug logging of the resolution step.
     */
    public void addPendingItem(Player player, Material material, int amount) {
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Shop: cannot give item, material is null or AIR.");
            return;
        }
        int amt = Math.max(1, amount);
        ItemStack stack = new ItemStack(material, amt);
        plugin.getLogger().info("Shop: giving " + material + " x" + amt + " to " + player.getName());
        addPendingItem(player, stack);
    }

    /**
     * Temporarily restore a player's inventory for admin GUI work (adding shop
     * items, adding prizes) without removing them from GameBox. The saved
     * inventory record is kept so {@link #leaveGameBox} still works later.
     * Call {@link #reClearInventory} when the admin returns to a normal GUI.
     */
    public void tempRestoreInventory(Player player) {
        ItemStack[] saved = savedInventories.get(player.getUniqueId());
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
    }

    /**
     * Re-clear a player's inventory after {@link #tempRestoreInventory} when
     * they leave the admin GUI and return to a normal GameBox GUI.
     *
     * <p>Before clearing, the current inventory contents are saved back to
     * {@link #savedInventories} so that any changes the player made (picking
     * up items, moving things around) are preserved. When the player
     * eventually leaves GameBox, {@link #restoreInventory} will restore
     * the updated inventory rather than the stale original.</p>
     */
    public void reClearInventory(Player player) {
        UUID uuid = player.getUniqueId();
        if (savedInventories.containsKey(uuid)) {
            // Save current inventory (with any admin-flow changes) back
            // so restoreInventory on GameBox exit gets the latest state.
            savedInventories.put(uuid, player.getInventory().getContents().clone());
            player.getInventory().clear();
        }
    }

    /** Mark a player as being inside the admin flow (ShopAdmin / PrizePoolEditor). */
    public void setInAdminFlow(UUID uuid, boolean value) {
        if (value) inAdminFlow.add(uuid);
        else inAdminFlow.remove(uuid);
    }

    public boolean isInAdminFlow(UUID uuid) {
        return inAdminFlow.contains(uuid);
    }

    public boolean isInGameBox(UUID uuid) {
        return savedInventories.containsKey(uuid);
    }

    private void runCustomCommands(java.util.List<String> commands, Player player) {
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
    }

    // ---------------- Auto-save ----------------

    public void startAutoSave() {
        if (autoSaveTaskId != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        if (GameBoxSettings.autoSaveInterval <= 0) return;
        autoSaveTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll,
                GameBoxSettings.autoSaveInterval * 20L,
                GameBoxSettings.autoSaveInterval * 20L).getTaskId();
    }

    public void shutdown() {
        if (autoSaveTaskId != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        saveAll();
        // Restore inventories for anyone still online in GameBox
        for (UUID uuid : new java.util.ArrayList<>(savedInventories.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) restoreInventory(p);
        }
    }

    // ---------------- Sound effects ----------------

    public void playSound(Player player, String soundName) {
        if (!GameBoxSettings.soundsEnabled || soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 0.7f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void playClick(Player player) { playSound(player, GameBoxSettings.soundClick); }
    public void playWinEffects(Player player) {
        playSound(player, GameBoxSettings.soundWin);
        player.sendTitle(
                me.nikl.gamebox.utility.Utility.color(plugin.lang(player, "titles.win")),
                me.nikl.gamebox.utility.Utility.color(plugin.lang(player, "titles.winSubtitle")),
                10, 50, 10);
    }
    public void playLoseEffects(Player player) {
        playSound(player, GameBoxSettings.soundLose);
        player.sendTitle(
                me.nikl.gamebox.utility.Utility.color(plugin.lang(player, "titles.lose")),
                me.nikl.gamebox.utility.Utility.color(plugin.lang(player, "titles.loseSubtitle")),
                10, 50, 10);
    }

    // ---------------- Lobby item ----------------

    public ItemStack createLobbyItem() {
        Material mat = me.nikl.gamebox.utility.Utility.matchMaterial(
                GameBoxSettings.lobbyMaterial, Material.NETHER_STAR);
        ItemStack item = me.nikl.gamebox.utility.Utility.createItem(mat,
                GameBoxSettings.lobbyName, null);
        me.nikl.gamebox.nms.NmsUtility.getInstance().setTag(
                item, me.nikl.gamebox.nms.NmsUtility.Keys.GAMEBOX_ITEM, "lobby");
        return item;
    }

    public void giveLobbyItem(Player player) {
        if (!GameBoxSettings.lobbyEnabled) return;
        if (!GameBoxSettings.lobbyWorlds.contains(player.getWorld().getName())) return;
        if (GameBoxSettings.lobbySlot >= 0 && GameBoxSettings.lobbySlot < 9) {
            player.getInventory().setItem(GameBoxSettings.lobbySlot, createLobbyItem());
        }
    }

    public boolean isLobbyItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR
                && me.nikl.gamebox.nms.NmsUtility.getInstance().hasTagValue(
                        item, me.nikl.gamebox.nms.NmsUtility.Keys.GAMEBOX_ITEM, "lobby");
    }

    public InvitationHandler getInvitationHandler() { return invitationHandler; }
}
