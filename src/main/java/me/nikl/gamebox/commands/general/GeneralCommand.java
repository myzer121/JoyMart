package me.nikl.gamebox.commands.general;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Player-facing commands. Everything is routed through a single
 * {@link CommandExecutor} entry point so that unknown first-arguments are
 * treated as game sub-commands (e.g. {@code /gamebox 2048} starts 2048
 * directly).
 *
 * <p>Recognised keywords: {@code help}, {@code info}, {@code token[s]},
 * {@code accept <uuid>}, {@code decline <uuid>}. Anything else is resolved as a
 * game sub-command from games.yml.</p>
 */
public class GeneralCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_KEYWORDS = Arrays.asList(
            "help", "info", "token", "tokens", "box", "delivery", "deliverybox",
            "accept", "decline");

    private final GameBox plugin;

    public GeneralCommand(GameBox plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args == null || args.length == 0) {
                if (requirePlayer(sender)) openMain((Player) sender);
                return true;
            }

            String sub = args[0].toLowerCase();
            switch (sub) {
                case "help" -> showHelp(sender);
                case "info" -> showInfo(sender);
                case "token", "tokens" -> {
                    if (requirePlayer(sender)) showTokens((Player) sender);
                }
                case "box", "delivery", "deliverybox" -> {
                    if (requirePlayer(sender)) openDeliveryBox((Player) sender);
                }
                case "accept" -> {
                    if (requirePlayer(sender) && args.length > 1) handleAccept((Player) sender, args[1]);
                }
                case "decline" -> {
                    if (requirePlayer(sender) && args.length > 1) handleDecline((Player) sender, args[1]);
                }
                default -> {
                    if (requirePlayer(sender)) startGame((Player) sender, args[0]);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing /gamebox: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                    "&cAn internal error occurred while executing this command."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            List<String> result = new ArrayList<>(SUB_KEYWORDS);
            result.addAll(plugin.getGameRegistry().getGameIds());
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            if (!prefix.isEmpty()) {
                result.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            }
            return result;
        }
        return new ArrayList<>();
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.langPrefixed("messages.playerOnly"));
            return false;
        }
        return true;
    }

    private void openMain(Player player) {
        plugin.getGuiManager().openMain(player);
    }

    private void openDeliveryBox(Player player) {
        plugin.getGuiManager().openDeliveryBox(player);
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&6===== GameBox v" + plugin.getDescription().getVersion() + " ====="));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color("&7/gamebox &8- &fOpen the menu"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color("&7/gamebox <game> &8- &fStart a game"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color("&7/gamebox token &8- &fCheck tokens"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color("&7/gamebox box &8- &fOpen delivery box"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color("&7/gamebox info &8- &fPlugin info"));
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&6GameBox &7v" + plugin.getDescription().getVersion()));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7Storage: &f" + me.nikl.gamebox.GameBoxSettings.storageType));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7Games: &f" + String.join(", ", plugin.getGameRegistry().getEnabledGames().stream()
                        .map(Game::getGameId).toList())));
    }

    private void showTokens(Player player) {
        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        int amount = gb != null ? gb.getTokens() : 0;
        player.sendMessage(plugin.langPrefixed("messages.tokensBalance")
                .replace("%amount%", String.valueOf(amount)));
    }

    private void handleAccept(Player player, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            plugin.getInvitationHandler().accept(player, uuid);
        } catch (IllegalArgumentException e) {
            player.sendMessage(plugin.langPrefixed("messages.notOnline"));
        }
    }

    private void handleDecline(Player player, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            plugin.getInvitationHandler().decline(player, uuid);
        } catch (IllegalArgumentException e) {
            player.sendMessage(plugin.langPrefixed("messages.notOnline"));
        }
    }

    private void startGame(Player player, String gameArg) {
        String resolved = plugin.getGameRegistry().resolveSubCommand(gameArg);
        if (resolved == null) {
            player.sendMessage(plugin.langPrefixed("messages.unknownGame").replace("%game%", gameArg));
            return;
        }
        Game game = plugin.getGameRegistry().getGame(resolved);
        if (game == null) {
            player.sendMessage(plugin.langPrefixed("messages.gameDisabled"));
            return;
        }
        if (!player.hasPermission("gamebox.play." + resolved)) {
            player.sendMessage(plugin.langPrefixed("messages.noGamePermission"));
            return;
        }
        plugin.getPluginManager().enterGameBox(player);
        plugin.getGuiManager().openGameGui(player, game);
    }
}
