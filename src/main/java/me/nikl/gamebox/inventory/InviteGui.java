package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for inviting a player to a two-player game. Lists all online players as
 * clickable heads and provides a token-bet selector (−/+ buttons). Clicking a
 * player's head sends an invitation with the currently selected bet amount.
 *
 * <p>Layout (size 54):</p>
 * <ul>
 *   <li>Slots 0–44: online player heads</li>
 *   <li>Slot 45: −100 bet</li>
 *   <li>Slot 46: −10 bet</li>
 *   <li>Slot 47: −1 bet</li>
 *   <li>Slot 48: bet amount display</li>
 *   <li>Slot 49: +1 bet</li>
 *   <li>Slot 50: +10 bet</li>
 *   <li>Slot 51: +100 bet</li>
 *   <li>Slot 53: back button</li>
 * </ul>
 */
public class InviteGui extends AGui {

    private final Game game;
    private int currentBet = 0;

    public InviteGui(GameBox plugin, Game game) {
        super(plugin, plugin.lang("gui.inviteTitle")
                .replace("%game%", game.lang("name")), 54);
        this.game = game;
    }

    @Override
    public void build(Player viewer) {
        clear();

        // --- Online player heads (slots 0–44) ---
        int slot = 0;
        int maxSlots = 45;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= maxSlots) break;
            if (online.getUniqueId().equals(viewer.getUniqueId())) continue;
            // Skip players already in a game session
            if (game.getGameManager().isInGame(online.getUniqueId())) continue;

            ItemStack head = createPlayerHead(online, viewer);
            final Player target = online;
            setButton(slot, Button.action("player_" + online.getUniqueId(), head,
                    p -> sendInvite(p, target)));
            slot++;
        }

        // --- Bet controls (row 6, slots 45–53) ---
        GBPlayer gb = plugin.getPluginManager().getPlayer(viewer.getUniqueId());
        int balance = gb != null ? gb.getTokens() : 0;

        setButton(45, Button.action("bet_m100",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-100", null),
                p -> adjustBet(p, -100)));
        setButton(46, Button.action("bet_m10",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-10", null),
                p -> adjustBet(p, -10)));
        setButton(47, Button.action("bet_m1",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                p -> adjustBet(p, -1)));

        // Bet display
        List<String> betLore = new ArrayList<>();
        betLore.add(plugin.lang("gui.betYourBalance").replace("%tokens%", String.valueOf(balance)));
        if (currentBet > 0) {
            betLore.add(plugin.lang("gui.betWinnerTakes"));
        } else {
            betLore.add(plugin.lang("gui.betNone"));
        }
        setButton(48, Button.display(
                Utility.createItem(Material.GOLD_BLOCK,
                        "&e" + plugin.lang("gui.betLabel") + ": &f" + currentBet, betLore)));

        setButton(49, Button.action("bet_p1",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                p -> adjustBet(p, +1)));
        setButton(50, Button.action("bet_p10",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10", null),
                p -> adjustBet(p, +10)));
        setButton(51, Button.action("bet_p100",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+100", null),
                p -> adjustBet(p, +100)));

        // Back button
        setButton(53, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> plugin.getGuiManager().openGameGui(p, game)));
    }

    private void adjustBet(Player player, int delta) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        int balance = gb != null ? gb.getTokens() : 0;
        int newBet = Math.max(0, currentBet + delta);
        // Cannot bet more than the inviter's current balance
        if (newBet > balance) newBet = balance;
        if (newBet != currentBet) {
            currentBet = newBet;
            build(player);
        }
    }

    private void sendInvite(Player inviter, Player target) {
        // Final balance check before sending
        GBPlayer gb = plugin.getPluginManager().getPlayer(inviter.getUniqueId());
        if (gb != null && currentBet > gb.getTokens()) {
            inviter.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                    .replace("%tokens%", String.valueOf(currentBet))
                    .replace("%balance%", String.valueOf(gb.getTokens())));
            return;
        }
        // Send the invitation directly to the chosen target (no chat input).
        plugin.getInvitationHandler().sendInvitationTo(inviter, target, game.getGameId(), currentBet);
        inviter.closeInventory();
    }

    /** Create a player-head ItemStack showing the target's skin and bet info. */
    @SuppressWarnings("deprecation")
    private ItemStack createPlayerHead(Player target, Player viewer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(Utility.color("&a" + target.getName()));
            List<String> lore = new ArrayList<>();
            GBPlayer gb = plugin.getPluginManager().getPlayer(target.getUniqueId());
            int targetBalance = gb != null ? gb.getTokens() : 0;
            lore.add(plugin.lang("gui.betTheirBalance").replace("%tokens%", String.valueOf(targetBalance)));
            if (currentBet > 0) {
                lore.add(plugin.lang("gui.betAmount").replace("%amount%", String.valueOf(currentBet)));
                lore.add(plugin.lang("gui.betClickToInvite"));
            } else {
                lore.add(plugin.lang("gui.betClickToInviteNoBet"));
            }
            meta.setLore(Utility.color(lore));
            head.setItemMeta(meta);
        }
        return head;
    }
}
