package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.nms.NmsUtility;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The main lobby menu: shows one button per enabled game plus a token-shop
 * button. Clicking a game button opens that game's {@link GameGui}.
 */
public class MainGui extends AGui {

    public MainGui(GameBox plugin) {
        super(plugin, plugin.lang("gui.mainTitle"), GameBoxSettings.mainMenuSize);
    }

    @Override
    protected String getDynamicTitle() {
        return plugin.lang("gui.mainTitle");
    }

    @Override
    public void build(Player player) {
        clear();

        // Auto-play a random song when entering the main menu (if music is
        // available and the player isn't already listening to something).
        if (plugin.getMusicPlayer() != null && plugin.getMusicPlayer().getSongCount() > 0) {
            if (plugin.getMusicPlayer().getCurrentIndex(player) < 0) {
                plugin.getMusicPlayer().playRandom(player);
            }
        }

        int slot = 10;
        for (Game game : plugin.getGameRegistry().getEnabledGames()) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2; // skip borders
            if (slot >= getSize()) break;

            Material mat = Utility.matchMaterial(game.getConfig().getString("icon.material", "PAPER"), Material.PAPER);
            String name = game.lang("name");
            List<String> lore = game.lang("lore") != null
                    ? plugin.getLanguageManager().getList("games." + game.getGameId() + ".lore")
                    : java.util.Collections.emptyList();
            ItemStack item = Utility.createItem(mat, name, lore);
            int finalSlot = slot;
            setButton(slot, Button.action("game_" + game.getGameId(), item,
                    p -> openGame(p, game)));
            slot++;
        }

        // Shop button
        if (GameBoxSettings.shopEnabled) {
            GBPlayerTokenLore shopLore = new GBPlayerTokenLore(plugin, player);
            ItemStack shop = Utility.createItem(Material.ENDER_CHEST,
                    plugin.lang("gui.tokenShop"), shopLore.getLore());
            NmsUtility.getInstance().setTag(shop, NmsUtility.Keys.BUTTON, "shop");
            setButton(getSize() - 5, Button.action("shop", shop, p -> {
                plugin.getGuiManager().openShop(p);
            }));
        }

        // Delivery box button — shows pending item count
        {
            int pending = plugin.getDeliveryBoxManager().count(player);
            List<String> boxLore = new ArrayList<>();
            boxLore.add(plugin.lang("gui.deliveryBoxLore"));
            if (pending > 0) {
                boxLore.add(plugin.lang("gui.deliveryBoxPending").replace("%count%", String.valueOf(pending)));
            }
            ItemStack box = Utility.createItem(Material.CHEST,
                    plugin.lang("gui.deliveryBoxButton"), boxLore);
            setButton(getSize() - 4, Button.action("delivery", box,
                    p -> plugin.getGuiManager().openDeliveryBox(p)));
        }

        // Music player button — opens the music control GUI (音符盒)
        if (plugin.getMusicPlayer() != null) {
            ItemStack music = Utility.createItem(Material.NOTE_BLOCK,
                    plugin.lang("gui.musicPlayer"),
                    Utility.list(plugin.lang("gui.musicLore")));
            setButton(getSize() - 3, Button.action("music", music, p -> {
                me.nikl.gamebox.music.MusicPlayerGui gui = plugin.getMusicPlayer().getGui();
                gui.open(p);
                plugin.getGuiManager().track(p.getUniqueId(), gui);
            }));
        }

        // Close button
        ItemStack close = Utility.createItem(Material.BARRIER, plugin.lang("gui.closeButton"), null);
        setButton(GameBoxSettings.closeButtonSlot >= 0 ? GameBoxSettings.closeButtonSlot : getSize() - 1,
                Button.action("close", close, Player::closeInventory));
    }

    private void openGame(Player player, Game game) {
        if (!player.hasPermission("gamebox.play." + game.getGameId())) {
            player.sendMessage(plugin.langPrefixed("messages.noGamePermission"));
            return;
        }
        game.getGameGui().open(player);
    }

    /** Small helper producing a token-aware lore for the shop button. */
    private static class GBPlayerTokenLore {
        private final List<String> lore;
        GBPlayerTokenLore(GameBox plugin, Player player) {
            me.nikl.gamebox.data.GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            int tokens = gb != null ? gb.getTokens() : 0;
            lore = me.nikl.gamebox.utility.Utility.replace(
                    plugin.getLanguageManager().getList("gui.tokenShopLore"),
                    new String[]{"%tokens%", String.valueOf(tokens)});
        }
        List<String> getLore() { return lore; }
    }
}
