package me.nikl.gamebox.input;

import me.nikl.gamebox.GameBox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for chat input when a player is in "invite mode" (they clicked the
 * invite button and now need to type a target name). Captures the next message,
 * resolves the target, and hands off to {@link InvitationHandler}.
 */
public class InviteInputHandler implements Listener {

    private final GameBox plugin;
    private final InvitationHandler handler;
    private final Set<UUID> awaitingInput = ConcurrentHashMap.newKeySet();

    public InviteInputHandler(GameBox plugin, InvitationHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    /** Mark a player as awaiting a name in chat. */
    public void awaitInput(UUID inviter) {
        awaitingInput.add(inviter);
    }

    public boolean isAwaiting(UUID uuid) {
        return awaitingInput.contains(uuid);
    }

    public void cancel(UUID uuid) {
        awaitingInput.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isAwaiting(player.getUniqueId())) return;

        event.setCancelled(true);
        awaitingInput.remove(player.getUniqueId());

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("quit") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.langPrefixed("invitations.timeoutPrompt"));
            return;
        }

        final String finalMessage = message;
        // Resolve on main thread (player lookup must be sync)
        Bukkit.getScheduler().runTask(plugin, () -> handler.resolveAndSend(player, finalMessage));
    }
}
