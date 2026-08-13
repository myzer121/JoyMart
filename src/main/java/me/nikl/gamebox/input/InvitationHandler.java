package me.nikl.gamebox.input;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.game.Game;
import net.jodah.expiringmap.ExpiringMap;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of two-player game invitations.
 *
 * <p>Flow: a player clicks "invite" → types a name in chat → an
 * {@link Invitation} is created with a configurable expiry → the target
 * receives a clickable JSON / action-bar / title message → on accept both
 * players are placed into the game's start page and the session begins.</p>
 */
public class InvitationHandler {

    private final GameBox plugin;
    private final InviteInputHandler inputHandler;
    /** Pending invitations keyed by the target player's UUID. */
    private final ExpiringMap<UUID, Invitation> pending;
    /** Inviters currently waiting on a chat input. */
    private final java.util.Set<UUID> inviting = ConcurrentHashMap.newKeySet();

    public InvitationHandler(GameBox plugin) {
        this.plugin = plugin;
        this.inputHandler = new InviteInputHandler(plugin, this);
        this.pending = ExpiringMap.<UUID, Invitation>builder()
                .expiration(GameBoxSettings.inviteExpiry, TimeUnit.SECONDS)
                .expirationListener((java.util.UUID target, Invitation invitation) -> {
                    // Refund the inviter's escrowed bet when the invitation expires
                    refundBet(invitation);
                    Player inviter = Bukkit.getPlayer(invitation.inviter);
                    Player targetP = Bukkit.getPlayer(target);
                    if (inviter != null) {
                        inviter.sendMessage(plugin.langPrefixed("invitations.expired")
                                .replace("%player%", targetP != null ? targetP.getName() : "?"));
                    }
                })
                .build();
        Bukkit.getPluginManager().registerEvents(inputHandler, plugin);
    }

    /** Begin the invite flow: prompt the inviter to type a target name. */
    public void beginInvitation(Player inviter, String gameId) {
        beginInvitation(inviter, gameId, 0);
    }

    /**
     * Begin the invite flow with a token bet. When {@code bet > 0}, the bet is
     * deducted from the inviter immediately (escrow) and from the target on
     * accept. The winner takes the full pot; on draw both are refunded.
     *
     * @param inviter  the player sending the invitation
     * @param gameId   the game to play
     * @param bet      tokens each side wagers (0 = no bet)
     */
    public void beginInvitation(Player inviter, String gameId, int bet) {
        inviting.add(inviter.getUniqueId());
        lastInvitedGame.put(inviter.getUniqueId(), gameId);
        lastInvitedBet.put(inviter.getUniqueId(), bet);
        inputHandler.awaitInput(inviter.getUniqueId());
        inviter.sendMessage(plugin.langPrefixed("invitations.timeoutPrompt"));
    }

    /**
     * Directly send an invitation to a specific target player (no chat input).
     * Used by the {@link me.nikl.gamebox.inventory.InviteGui} which already
     * knows the target. Escrows the inviter's bet up front; the target's bet
     * is deducted on accept.
     *
     * @param inviter the player sending the invitation
     * @param target  the player being invited
     * @param gameId   the game to play
     * @param bet      tokens each side wagers (0 = no bet)
     * @return true if the invitation was sent
     */
    public boolean sendInvitationTo(Player inviter, Player target, String gameId, int bet) {
        Game game = plugin.getGameRegistry().getGame(gameId);
        if (game == null) {
            inviter.sendMessage(plugin.langPrefixed("messages.gameNotFound"));
            return false;
        }
        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            inviter.sendMessage(plugin.langPrefixed("invitations.selfInvite"));
            return false;
        }
        if (isInAnyGame(target.getUniqueId())) {
            inviter.sendMessage(plugin.langPrefixed("invitations.busy"));
            return false;
        }
        if (pending.containsKey(target.getUniqueId())) {
            inviter.sendMessage(plugin.langPrefixed("invitations.alreadyPending"));
            return false;
        }
        // Escrow the inviter's bet up front (refunded on expire/decline).
        if (bet > 0) {
            me.nikl.gamebox.data.GBPlayer gbInviter = plugin.getPluginManager().getPlayer(inviter.getUniqueId());
            if (gbInviter == null || !gbInviter.removeTokens(bet)) {
                inviter.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                        .replace("%tokens%", String.valueOf(bet))
                        .replace("%balance%", gbInviter != null ? String.valueOf(gbInviter.getTokens()) : "0"));
                return false;
            }
        }
        Invitation invitation = new Invitation(inviter.getUniqueId(), target.getUniqueId(), gameId, bet);
        pending.put(target.getUniqueId(), invitation);
        sendInvitation(inviter, target, game);
        inviter.sendMessage(plugin.langPrefixed("invitations.sent").replace("%player%", target.getName()));
        return true;
    }

    /** Called (sync) by the input handler once a name has been typed. */
    public void resolveAndSend(Player inviter, String targetName) {
        inviting.remove(inviter.getUniqueId());
        Game game = resolveGameForInviter(inviter);
        if (game == null) {
            inviter.sendMessage(plugin.langPrefixed("messages.gameNotFound"));
            return;
        }

        if (inviter.getName().equalsIgnoreCase(targetName)) {
            inviter.sendMessage(plugin.langPrefixed("invitations.selfInvite"));
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            inviter.sendMessage(plugin.langPrefixed("messages.notOnline"));
            return;
        }

        if (plugin.getPluginManager().isInGameBox(target.getUniqueId())
                && plugin.getGameRegistry().getGame(game.getGameId()) != null) {
            // allow inviting players already in lobby; but if they're mid-session, mark busy
        }
        if (isInAnyGame(target.getUniqueId())) {
            inviter.sendMessage(plugin.langPrefixed("invitations.busy"));
            return;
        }

        if (pending.containsKey(target.getUniqueId())) {
            inviter.sendMessage(plugin.langPrefixed("invitations.alreadyPending"));
            return;
        }

        int bet = lastInvitedBet.getOrDefault(inviter.getUniqueId(), 0);
        // Deduct the inviter's bet immediately (escrow). Refunded if the
        // invitation expires or is declined.
        if (bet > 0) {
            me.nikl.gamebox.data.GBPlayer gbInviter = plugin.getPluginManager().getPlayer(inviter.getUniqueId());
            if (gbInviter == null || !gbInviter.removeTokens(bet)) {
                inviter.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                        .replace("%tokens%", String.valueOf(bet))
                        .replace("%balance%", gbInviter != null ? String.valueOf(gbInviter.getTokens()) : "0"));
                return;
            }
        }

        Invitation invitation = new Invitation(inviter.getUniqueId(), target.getUniqueId(), game.getGameId(), bet);
        pending.put(target.getUniqueId(), invitation);
        sendInvitation(inviter, target, game);
        inviter.sendMessage(plugin.langPrefixed("invitations.sent").replace("%player%", target.getName()));
    }

    private Game resolveGameForInviter(Player inviter) {
        // The inviter's currently selected game is tracked via a transient field on the input.
        // We instead resolve from the last game gui they opened; fall back to first 2p game.
        String last = lastInvitedGame.remove(inviter.getUniqueId());
        if (last != null) return plugin.getGameRegistry().getGame(last);
        for (Game g : plugin.getGameRegistry().getEnabledGames()) {
            if (g.getType() == me.nikl.gamebox.game.rules.GameType.TWO_PLAYER) return g;
        }
        return null;
    }

    private final java.util.Map<UUID, String> lastInvitedGame = new ConcurrentHashMap<>();
    /** Pending bet amount per inviter (set when the invite flow begins). */
    private final java.util.Map<UUID, Integer> lastInvitedBet = new ConcurrentHashMap<>();

    /** Record which game an inviter wants to play (called by GameGui before begin). */
    public void setInvitedGame(Player inviter, String gameId) {
        lastInvitedGame.put(inviter.getUniqueId(), gameId);
    }

    private boolean isInAnyGame(UUID uuid) {
        for (Game g : plugin.getGameRegistry().getEnabledGames()) {
            if (g.getGameManager().isInGame(uuid)) return true;
        }
        return false;
    }

    private void sendInvitation(Player inviter, Player target, Game game) {
        Invitation invitation = pending.get(target.getUniqueId());
        int bet = invitation != null ? invitation.bet : 0;
        String base = plugin.lang("invitations.received")
                .replace("%player%", inviter.getName())
                .replace("%game%", game.lang("name"));
        if (bet > 0) {
            base += " " + plugin.lang("invitations.betInfo").replace("%bet%", String.valueOf(bet));
        }

        switch (GameBoxSettings.inviteStyle.toLowerCase()) {
            case "actionbar":
                target.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new TextComponent(me.nikl.gamebox.utility.Utility.color(base
                                + " " + plugin.lang("invitations.accept") + " /gamebox accept")));
                break;
            case "title":
                target.sendTitle(me.nikl.gamebox.utility.Utility.color(base),
                        me.nikl.gamebox.utility.Utility.color(plugin.lang("invitations.acceptHover")), 10, 60, 10);
                break;
            case "json":
            default:
                TextComponent msg = new TextComponent(
                        me.nikl.gamebox.utility.Utility.color(base + " "));
                TextComponent accept = new TextComponent(
                        me.nikl.gamebox.utility.Utility.color(plugin.lang("invitations.accept")));
                accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/gamebox accept " + inviter.getUniqueId()));
                accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(me.nikl.gamebox.utility.Utility.color(
                                plugin.lang("invitations.acceptHover"))).create()));
                TextComponent sep = new TextComponent(" ");
                TextComponent decline = new TextComponent(
                        me.nikl.gamebox.utility.Utility.color(plugin.lang("invitations.decline")));
                decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/gamebox decline " + inviter.getUniqueId()));
                decline.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(me.nikl.gamebox.utility.Utility.color(
                                plugin.lang("invitations.declineHover"))).create()));
                msg.addExtra(accept);
                msg.addExtra(sep);
                msg.addExtra(decline);
                target.spigot().sendMessage(msg);
                break;
        }
    }

    /** Target accepts an invitation from the given inviter uuid. */
    public boolean accept(Player target, UUID inviterUuid) {
        Invitation invitation = pending.get(target.getUniqueId());
        if (invitation == null || !invitation.inviter.equals(inviterUuid)) {
            target.sendMessage(plugin.langPrefixed("invitations.targetExpired")
                    .replace("%player%", "?"));
            return false;
        }
        pending.remove(target.getUniqueId());

        Player inviter = Bukkit.getPlayer(inviterUuid);
        if (inviter == null) {
            // Refund inviter's bet since they're offline
            refundBet(invitation);
            target.sendMessage(plugin.langPrefixed("messages.notOnline"));
            return false;
        }

        // Deduct the target's bet (escrow). If they can't afford it, refund
        // the inviter and abort.
        if (invitation.bet > 0) {
            me.nikl.gamebox.data.GBPlayer gbTarget = plugin.getPluginManager().getPlayer(target.getUniqueId());
            if (gbTarget == null || !gbTarget.removeTokens(invitation.bet)) {
                refundBet(invitation);
                target.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                        .replace("%tokens%", String.valueOf(invitation.bet))
                        .replace("%balance%", gbTarget != null ? String.valueOf(gbTarget.getTokens()) : "0"));
                inviter.sendMessage(plugin.langPrefixed("invitations.declined").replace("%player%", target.getName()));
                return false;
            }
        }

        inviter.sendMessage(plugin.langPrefixed("invitations.accepted").replace("%player%", target.getName()));

        Game game = plugin.getGameRegistry().getGame(invitation.gameId);
        if (game == null) {
            // Refund both bets
            refundBet(invitation);
            me.nikl.gamebox.data.GBPlayer gbTarget = plugin.getPluginManager().getPlayer(target.getUniqueId());
            if (gbTarget != null && invitation.bet > 0) gbTarget.addTokens(invitation.bet);
            target.sendMessage(plugin.langPrefixed("messages.gameDisabled"));
            return false;
        }

        // Register the bet with the game so it can be settled on game end.
        if (invitation.bet > 0) {
            game.setBet(inviter.getUniqueId(), invitation.bet);
            game.setBet(target.getUniqueId(), invitation.bet);
        }

        // Enter both into GameBox and start the session
        if (!plugin.getPluginManager().isInGameBox(inviter.getUniqueId())) {
            plugin.getPluginManager().enterGameBox(inviter);
        }
        if (!plugin.getPluginManager().isInGameBox(target.getUniqueId())) {
            plugin.getPluginManager().enterGameBox(target);
        }
        game.getGameManager().startGame(java.util.Arrays.asList(inviter, target));
        return true;
    }

    /** Target declines an invitation. */
    public boolean decline(Player target, UUID inviterUuid) {
        Invitation invitation = pending.get(target.getUniqueId());
        if (invitation == null || !invitation.inviter.equals(inviterUuid)) return false;
        pending.remove(target.getUniqueId());
        // Refund the inviter's escrowed bet
        refundBet(invitation);
        Player inviter = Bukkit.getPlayer(inviterUuid);
        if (inviter != null) {
            inviter.sendMessage(plugin.langPrefixed("invitations.declined").replace("%player%", target.getName()));
        }
        return true;
    }

    /** Refund the inviter's escrowed bet for an invitation that didn't start. */
    private void refundBet(Invitation invitation) {
        if (invitation.bet <= 0) return;
        me.nikl.gamebox.data.GBPlayer gbInviter = plugin.getPluginManager().getPlayer(invitation.inviter);
        if (gbInviter != null) {
            gbInviter.addTokens(invitation.bet);
        }
    }

    public boolean hasPending(UUID target) {
        return pending.containsKey(target);
    }

    /** Simple invitation record. */
    public static class Invitation {
        final UUID inviter;
        final UUID target;
        final String gameId;
        final int bet;
        final long createdAt;

        Invitation(UUID inviter, UUID target, String gameId, int bet) {
            this.inviter = inviter;
            this.target = target;
            this.gameId = gameId;
            this.bet = bet;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
