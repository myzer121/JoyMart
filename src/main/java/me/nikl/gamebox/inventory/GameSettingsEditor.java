package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.PrizeGame;
import me.nikl.gamebox.game.impl.monopoly.GameMonopoly;
import me.nikl.gamebox.game.impl.monopoly.MonopolyProperty;
import me.nikl.gamebox.game.rules.GameType;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin GUI for editing a game's cost and token-reward settings in-game.
 *
 * <p>What can be edited depends on the game type:</p>
 * <ul>
 *   <li><b>Lottery</b> — money cost per ticket ({@code cost} field)</li>
 *   <li><b>Slot machine</b> — token cost per spin ({@code settings.spinCostTokens})</li>
 *   <li><b>Single-player games</b> — tokens awarded on win / on lose</li>
 *   <li><b>Two-player games</b> — tokens for winner / loser / draw</li>
 *   <li><b>Monopoly</b> — start money, pass-GO bonus, jail fee, plus per-property
 *       price and rent (paginated editor)</li>
 * </ul>
 *
 * <p>All edits are written back to {@code games/<id>/config.yml} via
 * {@link Game#saveGameConfig()} and the in-memory {@code rule} / {@code rewards}
 * objects are updated immediately so changes take effect without a reload.</p>
 */
public class GameSettingsEditor extends AGui {

    private final Game game;
    private final GameGui parentGui;
    private int monopolyPage = 0;
    private static final int PROPS_PER_PAGE = 4;

    public GameSettingsEditor(GameBox plugin, Game game, GameGui parentGui) {
        super(plugin, Utility.color("&8&l" + game.lang("name") + " &7设置"), 54);
        this.game = game;
        this.parentGui = parentGui;
    }

    @Override
    public void build(Player player) {
        clear();

        boolean isLottery = game.getGameId().equals("lottery");
        boolean isSlotMachine = game.getGameId().equals("slotmachine");
        boolean isSingle = game.getType() == GameType.SINGLE_PLAYER;
        boolean isMonopoly = game instanceof GameMonopoly;

        if (isMonopoly) {
            buildMonopolyEditor(player);
            return;
        }

        int row = 0; // row index (0-based); each row occupies 5 slots

        // --- Cost section ---
        if (isLottery) {
            // Money cost per ticket
            double cost = game.getConfig().getDouble("cost", 0.0);
            buildNumberRow(player, 9 + row * 5, "cost_money", "&6票价 (金币)",
                    (int) Math.round(cost), 1,
                    delta -> {
                        double newCost = Math.max(0, cost + delta);
                        game.getConfig().set("cost", newCost);
                        game.getRule().setCost(newCost);
                        game.saveGameConfig();
                    });
            row++;
        }

        if (isSlotMachine) {
            // Token cost per spin
            int spinCost = game.getConfig().getInt("settings.spinCostTokens", 2);
            buildNumberRow(player, 9 + row * 5, "spin_cost", "&6每次旋转 (代币)",
                    spinCost, 1,
                    delta -> {
                        int newCost = Math.max(0, spinCost + delta);
                        game.getConfig().set("settings.spinCostTokens", newCost);
                        game.saveGameConfig();
                        // Reload settings so the in-memory spinCostTokens is updated.
                        game.loadSettings();
                    });
            row++;
        }

        // --- Token rewards ---
        if (isSingle) {
            // Win tokens
            int winTokens = game.getConfig().getInt("rewards.win.tokens", 0);
            buildNumberRow(player, 9 + row * 5, "win_tokens", "&a胜利代币",
                    winTokens, 1,
                    delta -> {
                        int newVal = Math.max(0, winTokens + delta);
                        game.getConfig().set("rewards.win.tokens", newVal);
                        if (game.getRewards() != null) game.getRewards().setTokensOnWin(newVal);
                        game.saveGameConfig();
                    });
            row++;

            // Lose tokens
            int loseTokens = game.getConfig().getInt("rewards.lose.tokens", 0);
            buildNumberRow(player, 9 + row * 5, "lose_tokens", "&c失败代币",
                    loseTokens, 1,
                    delta -> {
                        int newVal = Math.max(0, loseTokens + delta);
                        game.getConfig().set("rewards.lose.tokens", newVal);
                        if (game.getRewards() != null) game.getRewards().setTokensOnLose(newVal);
                        game.saveGameConfig();
                    });
            row++;
        } else {
            // Two-player: winner / loser / draw tokens
            int winTokens = game.getConfig().getInt("rewards.winner.tokens", 0);
            buildNumberRow(player, 9 + row * 5, "win_tokens", "&a胜者代币",
                    winTokens, 1,
                    delta -> {
                        int newVal = Math.max(0, winTokens + delta);
                        game.getConfig().set("rewards.winner.tokens", newVal);
                        if (game.getMultiRewards() != null) game.getMultiRewards().setTokensWinner(newVal);
                        game.saveGameConfig();
                    });
            row++;

            int loseTokens = game.getConfig().getInt("rewards.loser.tokens", 0);
            buildNumberRow(player, 9 + row * 5, "lose_tokens", "&c败者代币",
                    loseTokens, 1,
                    delta -> {
                        int newVal = Math.max(0, loseTokens + delta);
                        game.getConfig().set("rewards.loser.tokens", newVal);
                        if (game.getMultiRewards() != null) game.getMultiRewards().setTokensLoser(newVal);
                        game.saveGameConfig();
                    });
            row++;

            int drawTokens = game.getConfig().getInt("rewards.draw.tokens", 0);
            buildNumberRow(player, 9 + row * 5, "draw_tokens", "&e平局代币",
                    drawTokens, 1,
                    delta -> {
                        int newVal = Math.max(0, drawTokens + delta);
                        game.getConfig().set("rewards.draw.tokens", newVal);
                        if (game.getMultiRewards() != null) game.getMultiRewards().setTokensDraw(newVal);
                        game.saveGameConfig();
                    });
            row++;
        }

        // Info item at slot 4
        ConfigurationSection iconSec = game.getConfig().getConfigurationSection("icon");
        Material iconMat = Utility.matchMaterial(
                iconSec != null ? iconSec.getString("material", "PAPER") : "PAPER", Material.PAPER);
        setButton(4, Button.display(Utility.createItem(iconMat,
                "&e" + game.lang("name"), Utility.list(
                        "&7游戏: &f" + game.getGameId(),
                        "&7类型: &f" + game.getType(),
                        "&7点击 -/+ 调整数值"))));

        // Back button
        setButton(getSize() - 1, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> {
                    parentGui.build(p);
                    p.openInventory(parentGui.getInventory());
                    plugin.getGuiManager().track(p.getUniqueId(), parentGui);
                }));
    }

    /**
     * Monopoly-specific editor: start money / pass-GO bonus / jail fee, plus
     * a paginated list of property prices and rents. Each property row has
     * [-10][-1] price [+1][+10] then [-10][-1] rent [+1][+10].
     */
    private void buildMonopolyEditor(Player player) {
        // Filler
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getSize(); i++) setItem(i, filler);

        // Title
        setButton(4, Button.display(Utility.createItem(Material.GOLD_BLOCK,
                "&6&l大富翁 设置", Utility.list("&7点击 -/+ 调整数值",
                        "&7共 &f17 &7处房产 (分页编辑)"))));

        // --- Global settings (rows 1-2: slots 9-13, 18-22) ---
        int startMoney = game.getConfig().getInt("settings.startMoney", 2000);
        buildNumberRow(player, 9, "startmoney", "&6初始资金",
                startMoney, 1,
                delta -> {
                    int v = Math.max(0, startMoney + delta);
                    game.getConfig().set("settings.startMoney", v);
                    game.saveGameConfig();
                    game.loadSettings();
                });

        int passGo = game.getConfig().getInt("settings.passGoBonus", 200);
        buildNumberRow(player, 18, "passgo", "&6经过起点奖励",
                passGo, 1,
                delta -> {
                    int v = Math.max(0, passGo + delta);
                    game.getConfig().set("settings.passGoBonus", v);
                    game.saveGameConfig();
                    game.loadSettings();
                });

        int jailFee = game.getConfig().getInt("settings.jailFee", 50);
        buildNumberRow(player, 27, "jailfee", "&6出狱费用",
                jailFee, 1,
                delta -> {
                    int v = Math.max(0, jailFee + delta);
                    game.getConfig().set("settings.jailFee", v);
                    game.saveGameConfig();
                    game.loadSettings();
                });

        // --- Property editor (paginated) ---
        // Properties list from config (read fresh each build so edits show)
        java.util.List<?> list = game.getConfig().getList("properties");
        int total = list != null ? list.size() : 0;
        int totalPages = Math.max(1, (total + PROPS_PER_PAGE - 1) / PROPS_PER_PAGE);
        if (monopolyPage >= totalPages) monopolyPage = totalPages - 1;
        if (monopolyPage < 0) monopolyPage = 0;

        int startIdx = monopolyPage * PROPS_PER_PAGE;
        int endIdx = Math.min(startIdx + PROPS_PER_PAGE, total);

        // Each property occupies one row (9 slots):
        //   [info] [-10][-1][price][+1][+10] [-1][rent][+1]
        int baseRow = 4; // start at row 4 (slot 36) — rows 0-3 are globals + title
        for (int i = startIdx; i < endIdx; i++) {
            int rowSlot = 9 * (baseRow + (i - startIdx));
            if (rowSlot + 8 >= getSize()) break; // safety
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) list.get(i);
            String name = String.valueOf(map.get("name"));
            int price = ((Number) map.get("price")).intValue();
            int rent = ((Number) map.get("rent")).intValue();
            final int propIdx = i;

            // Info (slot 0 of row)
            setButton(rowSlot, Button.display(Utility.createItem(Material.PAPER,
                    Utility.color(name), Utility.list(
                            "&7价格: &f" + price, "&7租金: &f" + rent))));

            // Price: -10 -1 [price] +1 +10 (slots 1-5)
            setButton(rowSlot + 1, Button.action("p" + propIdx + "_prm10",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-10", null),
                    p -> changeProperty(propIdx, -10, true)));
            setButton(rowSlot + 2, Button.action("p" + propIdx + "_prm1",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                    p -> changeProperty(propIdx, -1, true)));
            setButton(rowSlot + 3, Button.display(Utility.createItem(Material.GOLD_INGOT,
                    "&e价格: &f" + price, null)));
            setButton(rowSlot + 4, Button.action("p" + propIdx + "_prp1",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                    p -> changeProperty(propIdx, +1, true)));
            setButton(rowSlot + 5, Button.action("p" + propIdx + "_prp10",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10", null),
                    p -> changeProperty(propIdx, +10, true)));

            // Rent: -1 [rent] +1 (slots 6-8, compressed)
            setButton(rowSlot + 6, Button.action("p" + propIdx + "_rtm1",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                    p -> changeProperty(propIdx, -1, false)));
            setButton(rowSlot + 7, Button.display(Utility.createItem(Material.EMERALD,
                    "&e租金: &f" + rent, null)));
            setButton(rowSlot + 8, Button.action("p" + propIdx + "_rtp1",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                    p -> changeProperty(propIdx, +1, false)));
        }

        // Pagination buttons
        if (monopolyPage > 0) {
            setButton(45, Button.action("prevpage",
                    Utility.createItem(Material.ARROW, "&a上一页", null),
                    p -> { monopolyPage--; build(p); }));
        }
        setButton(49, Button.display(Utility.createItem(Material.BOOK,
                "&e第 &f" + (monopolyPage + 1) + " &e/ &f" + totalPages + " &e页", null)));
        if (monopolyPage + 1 < totalPages) {
            setButton(53, Button.action("nextpage",
                    Utility.createItem(Material.ARROW, "&a下一页", null),
                    p -> { monopolyPage++; build(p); }));
        }

        // Back button
        setButton(getSize() - 5, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> {
                    parentGui.build(p);
                    p.openInventory(parentGui.getInventory());
                    plugin.getGuiManager().track(p.getUniqueId(), parentGui);
                }));
    }

    /** Change a property's price or rent by a delta and persist. */
    private void changeProperty(int propIdx, int delta, boolean isPrice) {
        java.util.List<?> list = game.getConfig().getList("properties");
        if (list == null || propIdx >= list.size()) return;
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) list.get(propIdx);
        if (isPrice) {
            int v = Math.max(0, ((Number) map.get("price")).intValue() + delta);
            game.getConfig().set("properties." + propIdx + ".price", v);
        } else {
            int v = Math.max(0, ((Number) map.get("rent")).intValue() + delta);
            game.getConfig().set("properties." + propIdx + ".rent", v);
        }
        game.saveGameConfig();
        game.loadSettings();
        // Refresh the open GUI for all viewers
        for (java.util.Map.Entry<java.util.UUID, AGui> e :
                plugin.getGuiManager().getOpenGuis().entrySet()) {
            if (e.getValue() == this) {
                Player p = plugin.getServer().getPlayer(e.getKey());
                if (p != null) build(p);
            }
        }
    }

    /**
     * Build a single editable number row: [-10] [-1] [current value] [+1] [+10]
     * starting at the given base slot. The 5 slots are laid out horizontally.
     *
     * @param baseSlot   leftmost slot of the row
     * @param id         unique button id prefix
     * @param label      display label for the value
     * @param current    current value
     * @param step       minimum step (always 1, kept for clarity)
     * @param onChange   callback receiving the delta to apply
     */
    private void buildNumberRow(Player player, int baseSlot, String id, String label,
                                int current, int step, java.util.function.IntConsumer onChange) {
        // -10
        setButton(baseSlot, Button.action(id + "_m10",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-10", null),
                p -> { onChange.accept(-10); build(p); }));
        // -1
        setButton(baseSlot + 1, Button.action(id + "_m1",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                p -> { onChange.accept(-1); build(p); }));
        // Current value (display)
        setButton(baseSlot + 2, Button.display(
                Utility.createItem(Material.GOLD_BLOCK, "&e" + label + ": &f" + current, null)));
        // +1
        setButton(baseSlot + 3, Button.action(id + "_p1",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                p -> { onChange.accept(+1); build(p); }));
        // +10
        setButton(baseSlot + 4, Button.action(id + "_p10",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10", null),
                p -> { onChange.accept(+10); build(p); }));
    }
}
