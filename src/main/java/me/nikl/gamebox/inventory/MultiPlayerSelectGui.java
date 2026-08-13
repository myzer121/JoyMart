package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GUI for selecting 2 opponents to start a 3-player game. The host clicks on
 * player heads to select/deselect them; once exactly 2 are chosen, the "Start"
 * button becomes available.
 */
public class MultiPlayerSelectGui extends AGui {

    private final Game game;
    private final Player host;
    private final List<Player> selected = new ArrayList<>();

    public MultiPlayerSelectGui(GameBox plugin, Game game, Player host) {
        super(plugin, Utility.color("&8&l选择对手 - 3人模式"), 54);
        this.game = game;
        this.host = host;
    }

    @Override
    public void build(Player player) {
        clear();
        selected.clear(); // rebuild selection state each time

        // Fill with filler
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getSize(); i++) {
            setItem(i, filler);
        }

        // Title
        setButton(4, Button.display(Utility.createItem(Material.DIAMOND_BLOCK,
                "&b&l选择 2 名对手", Utility.list("&7点击玩家头颅选择", "&7选满 2 人后点击开始"))));

        // List online players (excluding host and players in games)
        int slot = 10;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(host)) continue;
            if (plugin.getPluginManager().isInGameBox(online.getUniqueId())) continue;
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot += 2;
            if (slot >= 45) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName(Utility.color("&a" + online.getName()));
                meta.setLore(Utility.color(Utility.list(
                        "&7点击选择此玩家",
                        "&8在线")));
                head.setItemMeta(meta);
            }
            final Player target = online;
            setButton(slot, Button.action("player_" + slot, head, p -> toggleSelect(p, target)));
            slot++;
        }

        // Start button (slot 49)
        setButton(49, Button.action("start",
                Utility.createItem(Material.EMERALD_BLOCK, "&a&l开始游戏",
                        Utility.list("&7已选择: " + selected.size() + "/2")),
                p -> {
                    if (selected.size() != 2) {
                        p.sendMessage(Utility.color(plugin.lang("prefix") + "&c请选择 2 名对手!"));
                        return;
                    }
                    List<Player> players = new ArrayList<>();
                    players.add(host);
                    players.addAll(selected);
                    p.closeInventory();
                    game.getGameManager().startGame(players);
                }));

        // Back button
        setButton(45, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> {
                    game.refundCost(host);
                    game.getGameGui().open(p);
                    plugin.getGuiManager().track(p.getUniqueId(), game.getGameGui());
                }));
    }

    private void toggleSelect(Player clicker, Player target) {
        if (selected.contains(target)) {
            selected.remove(target);
            clicker.sendMessage(Utility.color("&e取消选择: &f" + target.getName()));
        } else {
            if (selected.size() >= 2) {
                clicker.sendMessage(Utility.color(plugin.lang("prefix") + "&c已选满 2 人, 请先取消一个!"));
                return;
            }
            selected.add(target);
            clicker.sendMessage(Utility.color("&a已选择: &f" + target.getName() + " &a(" + selected.size() + "/2)"));
        }
        // Update start button
        setButton(49, Button.action("start",
                Utility.createItem(selected.size() == 2 ? Material.EMERALD_BLOCK : Material.GRAY_STAINED_GLASS_PANE,
                        "&a&l开始游戏",
                        Utility.list("&7已选择: " + selected.size() + "/2")),
                p -> {
                    if (selected.size() != 2) {
                        p.sendMessage(Utility.color(plugin.lang("prefix") + "&c请选择 2 名对手!"));
                        return;
                    }
                    List<Player> players = new ArrayList<>();
                    players.add(host);
                    players.addAll(selected);
                    p.closeInventory();
                    game.getGameManager().startGame(players);
                }));
    }
}
