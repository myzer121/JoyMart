package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.PrizeGame;
import me.nikl.gamebox.game.rules.GameType;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;

/**
 * Per-game menu offering: Play (single-player), Invite a player (two-player),
 * High Scores, and a Back button to the main menu.
 */
public class GameGui extends AGui {

    private final Game game;

    public GameGui(GameBox plugin, Game game) {
        super(plugin, game.lang("name"), 27);
        this.game = game;
    }

    @Override
    public void build(Player player) {
        clear();

        Material iconMat = Utility.matchMaterial(game.getConfig().getString("icon.material", "PAPER"), Material.PAPER);
        ItemStack icon = Utility.createItem(iconMat, game.lang("name"),
                plugin.getLanguageManager().getList("games." + game.getGameId() + ".lore"));
        setButton(4, Button.display(icon));

        if (game.getType() == GameType.SINGLE_PLAYER) {
            ItemStack play = Utility.createItem(Material.EMERALD_BLOCK, "&a" + game.lang("play"),
                    Collections.singletonList(plugin.lang("gui.playLore")));
            setButton(11, Button.action("play", play, this::startSingle));
        } else if (game.getType() == GameType.MULTI_PLAYER) {
            // Multi-player (3+): offer 2P, 3P, and Vs AI
            ItemStack invite2 = Utility.createItem(Material.EMERALD_BLOCK, "&a" + game.lang("invite2"),
                    Collections.singletonList(plugin.lang("gui.inviteLore")));
            setButton(10, Button.action("invite2", invite2, this::startInvite));

            ItemStack invite3 = Utility.createItem(Material.DIAMOND_BLOCK, "&b" + game.lang("invite3"),
                    Collections.singletonList(plugin.lang("gui.invite3Lore")));
            setButton(12, Button.action("invite3", invite3, this::startMultiInvite));

            ItemStack vsAi = Utility.createItem(Material.REDSTONE_BLOCK, "&c" + game.lang("vsAi"),
                    Collections.singletonList(plugin.lang("gui.vsAiLore")));
            setButton(16, Button.action("vsai", vsAi, this::startVsAi));

            ItemStack scores = Utility.createItem(Material.BOOK, "&e" + game.lang("highscores"),
                    Collections.singletonList(plugin.lang("gui.highscoresLore")));
            setButton(22, Button.action("scores", scores,
                    p -> plugin.getGuiManager().openTopList(p, game.getGameId())));

            // Game settings editor (admin only): edit property prices/rents etc.
            if (player.hasPermission("gamebox.admin.games")) {
                ItemStack editSettings = Utility.createItem(Material.REPEATER,
                        "&c\u2699 " + plugin.lang("gui.editSettings"),
                        Collections.singletonList("&7" + plugin.lang("gui.editSettingsLore")));
                setButton(20, Button.action("editsettings", editSettings, p -> {
                    GameSettingsEditor editor = new GameSettingsEditor(plugin, game, this);
                    editor.build(p);
                    p.openInventory(editor.getInventory());
                    plugin.getGuiManager().track(p.getUniqueId(), editor);
                }));
            }

            ItemStack back = Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null);
            setButton(26, Button.action("back", back, p -> plugin.getGuiManager().openMain(p)));
            return;
        } else {
            // Two-player: offer both "invite a friend" and "play vs AI"
            ItemStack invite = Utility.createItem(Material.EMERALD_BLOCK, "&a" + game.lang("invite"),
                    Collections.singletonList(plugin.lang("gui.inviteLore")));
            setButton(11, Button.action("invite", invite, this::startInvite));

            ItemStack vsAi = Utility.createItem(Material.REDSTONE_BLOCK, "&c" + game.lang("vsAi"),
                    Collections.singletonList(plugin.lang("gui.vsAiLore")));
            setButton(15, Button.action("vsai", vsAi, this::startVsAi));

            // Move high scores to slot 22
            ItemStack scores = Utility.createItem(Material.BOOK, "&e" + game.lang("highscores"),
                    Collections.singletonList(plugin.lang("gui.highscoresLore")));
            setButton(22, Button.action("scores", scores,
                    p -> plugin.getGuiManager().openTopList(p, game.getGameId())));

            // Back and close
            ItemStack back = Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null);
            setButton(26, Button.action("back", back, p -> plugin.getGuiManager().openMain(p)));
            return;
        }

        ItemStack scores = Utility.createItem(Material.BOOK, "&e" + game.lang("highscores"),
                Collections.singletonList(plugin.lang("gui.highscoresLore")));
        setButton(15, Button.action("scores", scores,
                p -> plugin.getGuiManager().openTopList(p, game.getGameId())));

        // Prize editor (admin only) for prize-pool games (lottery, slot machine)
        if (game instanceof PrizeGame && player.hasPermission("gamebox.admin.games")) {
            ItemStack editPrizes = Utility.createItem(Material.GOLDEN_APPLE,
                    plugin.lang("gui.editPrizes"),
                    Collections.singletonList("&7" + plugin.lang("gui.editPrizesLore")));
            setButton(20, Button.action("editprizes", editPrizes,
                    p -> ((PrizeGame) game).openPrizeEditor(p)));
        }

        // Game settings editor (admin only): edit cost and token rewards via GUI
        if (player.hasPermission("gamebox.admin.games")) {
            ItemStack editSettings = Utility.createItem(Material.REPEATER,
                    "&c\u2699 " + plugin.lang("gui.editSettings"),
                    Collections.singletonList("&7" + plugin.lang("gui.editSettingsLore")));
            setButton(21, Button.action("editsettings", editSettings, p -> {
                GameSettingsEditor editor = new GameSettingsEditor(plugin, game, this);
                editor.build(p);
                p.openInventory(editor.getInventory());
                plugin.getGuiManager().track(p.getUniqueId(), editor);
            }));
        }

        // Back
        ItemStack back = Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null);
        setButton(22, Button.action("back", back, p -> plugin.getGuiManager().openMain(p)));

        // Close
        ItemStack close = Utility.createItem(Material.BARRIER, plugin.lang("gui.closeButton"), null);
        setButton(26, Button.action("close", close, Player::closeInventory));
    }

    private void startSingle(Player player) {
        if (!game.canAfford(player)) {
            player.sendMessage(plugin.langPrefixed("messages.cannotAfford")
                    .replace("%cost%", String.valueOf(game.getRule().getCost())));
            return;
        }
        game.chargeCost(player);
        game.getGameManager().startGame(java.util.Collections.singletonList(player));
    }

    private void startInvite(Player player) {
        // Open the GUI invite page (player head selection + bet selector)
        // instead of asking the inviter to type a name in chat.
        InviteGui inviteGui = new InviteGui(plugin, game);
        inviteGui.open(player);
        plugin.getGuiManager().track(player.getUniqueId(), inviteGui);
    }

    private void startVsAi(Player player) {
        if (!game.canAfford(player)) {
            player.sendMessage(plugin.langPrefixed("messages.cannotAfford")
                    .replace("%cost%", String.valueOf(game.getRule().getCost())));
            return;
        }
        game.chargeCost(player);
        player.closeInventory();
        game.getGameManager().startGameVsAi(player);
    }

    /** Open the 3-player selection GUI (host picks 2 opponents). */
    private void startMultiInvite(Player player) {
        if (!game.canAfford(player)) {
            player.sendMessage(plugin.langPrefixed("messages.cannotAfford")
                    .replace("%cost%", String.valueOf(game.getRule().getCost())));
            return;
        }
        game.chargeCost(player);
        MultiPlayerSelectGui selectGui = new MultiPlayerSelectGui(plugin, game, player);
        selectGui.open(player);
        plugin.getGuiManager().track(player.getUniqueId(), selectGui);
    }
}
