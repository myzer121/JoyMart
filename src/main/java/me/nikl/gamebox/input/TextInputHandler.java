package me.nikl.gamebox.input;

import me.nikl.gamebox.GameBox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * General-purpose chat input handler. When a player is in "text input mode",
 * their next chat message is captured (the event is cancelled so it is not
 * broadcast) and delivered to a callback on the main thread.
 *
 * <p>Used by the shop item editor to let admins type a new display name for
 * a shop item.</p>
 */
public class TextInputHandler implements Listener {

    private final GameBox plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public TextInputHandler(GameBox plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Begin awaiting a chat message from {@code player}. The next non-empty
     * message they send is passed to {@code callback} on the main thread.
     */
    public void await(Player player, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
    }

    public boolean isAwaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void cancel(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = pending.get(player.getUniqueId());
        if (callback == null) return;

        event.setCancelled(true);
        pending.remove(player.getUniqueId());

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("quit")) {
            return;
        }
        // Deliver on the main thread (Bukkit API is not thread-safe)
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(message));
    }
}
