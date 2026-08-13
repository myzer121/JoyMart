package me.nikl.gamebox.commands.admin;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.DataBase;
import me.nikl.gamebox.data.FileDB;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.data.MysqlDB;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin commands: reload config, manage tokens, toggle games, check language,
 * migrate storage, and reset high scores.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "tokens", "games", "language", "migrate", "reset", "shop");

    private final GameBox plugin;

    public AdminCommand(GameBox plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gamebox.admin")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return true;
        }
        try {
            if (args == null || args.length == 0) {
                showUsage(sender);
                return true;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "reload" -> onReload(sender);
                case "tokens" -> onTokens(sender, restArgs(args));
                case "games" -> onGames(sender, restArgs(args));
                case "language", "lang" -> onLanguage(sender);
                case "migrate" -> {
                    if (restArgs(args).length > 0) onMigrate(sender, restArgs(args)[0]);
                    else sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                            "&7/gba migrate <yaml|mysql>"));
                }
                case "reset", "resethighscores" -> {
                    if (restArgs(args).length > 0) onReset(sender, restArgs(args)[0]);
                    else sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                            "&7/gba reset <game>"));
                }
                case "shop" -> onShop(sender);
                default -> showUsage(sender);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing /gameboxadmin: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                    "&cAn internal error occurred while executing this command."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("gamebox.admin")) return new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            List<String> result = new ArrayList<>(SUBCOMMANDS);
            if (!prefix.isEmpty()) {
                result.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            }
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("games") || args[0].equalsIgnoreCase("reset"))) {
            String prefix = args[1].toLowerCase();
            List<String> ids = new ArrayList<>(plugin.getGameRegistry().getGameIds());
            if (!prefix.isEmpty()) {
                ids.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            }
            return ids;
        }
        // Tab-complete online player names for the tokens subcommand
        if (args.length == 2 && args[0].equalsIgnoreCase("tokens")) {
            String prefix = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                if (prefix.isEmpty() || p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        // Tab-complete token actions
        if (args.length == 3 && args[0].equalsIgnoreCase("tokens")) {
            String prefix = args[2].toLowerCase();
            List<String> actions = Arrays.asList("get", "set", "give", "take");
            if (!prefix.isEmpty()) {
                actions = actions.stream().filter(a -> a.startsWith(prefix)).toList();
            }
            return new ArrayList<>(actions);
        }
        return new ArrayList<>();
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&6===== GameBox Admin ====="));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba reload &8- &fReload config"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba tokens <player> <get|set|give|take> [amount]"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba games <game> <enable|disable>"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba language &8- &fCheck language"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba migrate <yaml|mysql>"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba reset <game> &8- &fReset high scores"));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&7/gba shop &8- &fOpen shop admin GUI"));
    }

    private void onReload(CommandSender sender) {
        if (!sender.hasPermission("gamebox.admin.reload")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(plugin.langPrefixed("messages.reloaded"));
    }

    private void onTokens(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gamebox.admin.tokens")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                    "&7/gba tokens <player> <get|set|give|take> [amount]"));
            return;
        }
        // Try online player first — avoids the blocking Mojang API lookup
        // that Bukkit.getOfflinePlayer(name) performs for unknown names.
        org.bukkit.entity.Player onlineTarget = Bukkit.getPlayerExact(args[0]);
        java.util.UUID targetUuid;
        String targetName = args[0];
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            // Fall back to OfflinePlayer for offline players.
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            targetUuid = target.getUniqueId();
        }
        // Use the cached in-memory GBPlayer when available so modifications
        // are visible to the online player immediately. Only load from DB
        // when the player is not cached.
        GBPlayer gb = plugin.getPluginManager().getPlayerIfLoaded(targetUuid);
        if (gb == null) {
            gb = plugin.getDataBase().loadPlayer(targetUuid, targetName);
        }
        String action = args[1].toLowerCase();
        int amount = args.length >= 3 ? me.nikl.gamebox.utility.Utility.parseInt(args[2], 0) : 0;
        switch (action) {
            case "get" -> sender.sendMessage(plugin.langPrefixed("messages.tokensBalance")
                    .replace("%amount%", String.valueOf(gb.getTokens())));
            case "set" -> {
                gb.setTokens(amount);
                sender.sendMessage(plugin.langPrefixed("messages.tokensSet")
                        .replace("%player%", args[0]).replace("%amount%", String.valueOf(amount)));
            }
            case "give" -> {
                gb.addTokens(amount);
                sender.sendMessage(plugin.langPrefixed("messages.tokensGiven")
                        .replace("%player%", args[0]).replace("%amount%", String.valueOf(amount)));
            }
            case "take" -> {
                gb.removeTokens(amount);
                sender.sendMessage(plugin.langPrefixed("messages.tokensTaken")
                        .replace("%player%", args[0]).replace("%amount%", String.valueOf(amount)));
            }
            default -> sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                    "&cUnknown action: " + action));
        }
        if (gb.isDirty()) plugin.getDataBase().savePlayer(gb);
    }

    private void onGames(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gamebox.admin.games")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                    "&7/gba games <game> <enable|disable>"));
            return;
        }
        String gameId = plugin.getGameRegistry().resolveSubCommand(args[0]);
        if (gameId == null) gameId = args[0];
        boolean enable = args[1].equalsIgnoreCase("enable");
        boolean ok = plugin.getGameRegistry().setEnabled(gameId, enable);
        sender.sendMessage(ok
                ? me.nikl.gamebox.utility.Utility.color(
                        "&aGame " + gameId + " " + (enable ? "enabled" : "disabled"))
                : me.nikl.gamebox.utility.Utility.color(
                        "&cCould not change game " + gameId));
    }

    private void onLanguage(CommandSender sender) {
        if (!sender.hasPermission("gamebox.admin.language")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        int keys = plugin.getLanguageManager().getList("messages.noPermission").size();
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&aLanguage: &f" + me.nikl.gamebox.GameBoxSettings.language));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&aPrefix: &r" + me.nikl.gamebox.GameBoxSettings.PREFIX));
        sender.sendMessage(me.nikl.gamebox.utility.Utility.color(
                "&aSample noPermission key resolved (keys checked: " + keys + ")"));
    }

    private void onMigrate(CommandSender sender, String target) {
        if (!sender.hasPermission("gamebox.admin.migrate")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        sender.sendMessage(plugin.langPrefixed("messages.dataMigrating"));
        DataBase source = plugin.getDataBase();
        DataBase dest;
        if ("mysql".equalsIgnoreCase(target)) {
            dest = new MysqlDB(plugin);
        } else {
            dest = new FileDB(plugin);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            if (source instanceof FileDB) {
                ok = source.migrate(dest);
            } else {
                ok = true;
            }
            dest.shutdown();
            final boolean result = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(result
                        ? plugin.langPrefixed("messages.dataMigrated")
                        : me.nikl.gamebox.utility.Utility.color("&cMigration failed."));
            });
        });
    }

    private void onReset(CommandSender sender, String gameId) {
        if (!sender.hasPermission("gamebox.admin.reset")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        String resolved = plugin.getGameRegistry().resolveSubCommand(gameId);
        if (resolved == null) resolved = gameId;
        plugin.getDataBase().resetHighScores(resolved);
        sender.sendMessage(plugin.langPrefixed("messages.highscoresReset")
                .replace("%game%", resolved));
    }

    private void onShop(CommandSender sender) {
        if (!sender.hasPermission("gamebox.admin.shop")) {
            sender.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(plugin.langPrefixed("messages.playerOnly"));
            return;
        }
        plugin.getGuiManager().openShopAdmin((org.bukkit.entity.Player) sender);
    }

    private String[] restArgs(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }
}
