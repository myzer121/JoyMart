package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.PrizeGame;
import me.nikl.gamebox.game.PrizePool;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Admin GUI for editing a {@link PrizeGame}'s {@link PrizePool}. Lets admins
 * add prizes (from the cursor), remove prizes, and tune each prize's token /
 * money / weight values. All edits are persisted to the game's config.yml.
 *
 * <p>Cursor handling for "Add Prize" is deferred to the next tick — the same
 * fix used by {@link ShopAdmin} — so the held item never visually disappears.</p>
 */
public class PrizePoolEditor {

    /** Open the prize-list page for the given prize game. */
    public static void open(GameBox plugin, Player player, PrizeGame game) {
        // Restore inventory ONCE when entering the prize editor flow.
        // Previously this was called in PrizeListPage.open() on every
        // navigation, causing the same item-vanishing bug as ShopAdmin.
        plugin.getPluginManager().tempRestoreInventory(player);
        PrizeListPage page = new PrizeListPage(plugin, game);
        page.open(player);
        plugin.getGuiManager().track(player.getUniqueId(), page);
    }

    /** The prize-list page: shows all prizes + an add slot + back. */
    public static class PrizeListPage extends AGui {
        private final PrizeGame game;
        private final PrizePool pool;

        PrizeListPage(GameBox plugin, PrizeGame game) {
            super(plugin, Utility.color(plugin.lang("gui.editPrizes")), 54);
            this.game = game;
            this.pool = game.getPrizePool();
        }

        @Override
        public boolean allowPlayerInventoryInteraction() {
            // Admins need to pick up items from their own inventory onto the
            // cursor, then click the "add" slot to add a new prize.
            return true;
        }

        @Override
        public void open(Player player) {
            // Inventory restore is handled once at PrizePoolEditor.open()
            // (the static entry point), not here on every page navigation.
            super.open(player);
        }

        @Override
        public void build(Player player) {
            clear();
            List<PrizePool.Prize> prizes = pool.getPrizes();
            int slot = 0;
            int maxSlots = getSize() - 9;
            for (int i = 0; i < prizes.size() && slot < maxSlots; i++) {
                PrizePool.Prize p = prizes.get(i);
                List<String> lore = new ArrayList<>();
                lore.add(plugin.lang("prize.weight").replace("%w%", String.valueOf(p.weight)));
                lore.add(plugin.lang("prize.tokens").replace("%t%", String.valueOf(p.tokens)));
                lore.add(plugin.lang("prize.money").replace("%m%", String.valueOf(p.money)));
                lore.add(plugin.lang("prize.commands").replace("%c%", String.valueOf(p.commands.size())));
                lore.add("");
                lore.add(plugin.lang("prize.clickToEdit"));
                ItemStack stack = Utility.createItem(p.material, p.name, lore);
                final int idx = i;
                setButton(slot, Button.action("prize_" + i, stack,
                        pl -> openEdit(pl, idx)));
                slot++;
            }

            // Add prize slot (cursor item)
            int addSlot = getSize() - 5;
            ItemStack addBtn = Utility.createItem(Material.WRITTEN_BOOK,
                    "&a&l+ " + plugin.lang("prize.add"),
                    Utility.list(
                            plugin.lang("gui.shopAddHint1"),
                            plugin.lang("prize.addHint2"),
                            "",
                            plugin.lang("prize.addDefaults")));
            setButton(addSlot, new Button("add", addBtn, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                if (!p.hasPermission("gamebox.admin.games")) {
                    p.sendMessage(plugin.langPrefixed("messages.noPermission"));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    ItemStack cursor = p.getItemOnCursor();
                    if (cursor == null || cursor.getType() == Material.AIR) {
                        p.sendMessage(plugin.langPrefixed("messages.shopAddNoItem"));
                        return;
                    }
                    pool.addFromItem(cursor);
                    pool.save();
                    // Consume one from cursor
                    if (cursor.getAmount() > 1) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        p.setItemOnCursor(cursor);
                    } else {
                        p.setItemOnCursor(null);
                    }
                    p.sendMessage(plugin.langPrefixed("prize.added"));
                    build(p);
                });
            }));

            // Back to the game gui
            setButton(getSize() - 1, Button.action("back",
                    Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                    p -> {
                        // Re-clear inventory since we're leaving the admin
                        // item-interaction GUI.
                        plugin.getPluginManager().reClearInventory(p);
                        ((me.nikl.gamebox.game.Game) game).getGameGui().open(p);
                    }));
        }

        private void openEdit(Player player, int index) {
            PrizeEditPage edit = new PrizeEditPage(plugin, game, this, index);
            edit.open(player);
            plugin.getGuiManager().track(player.getUniqueId(), edit);
        }
    }

    /** The per-prize edit page: tune tokens / money / weight + remove. */
    public static class PrizeEditPage extends AGui {
        private final PrizeGame game;
        private final PrizePool pool;
        private final PrizeListPage listPage;
        private final int index;

        PrizeEditPage(GameBox plugin, PrizeGame game, PrizeListPage listPage, int index) {
            super(plugin, Utility.color(plugin.lang("prize.editTitle")), 45);
            this.game = game;
            this.pool = game.getPrizePool();
            this.listPage = listPage;
            this.index = index;
        }

        @Override
        public void build(Player player) {
            clear();
            PrizePool.Prize p = pool.get(index);
            if (p == null) {
                listPage.open(player);
                return;
            }
            // Display at slot 4
            List<String> lore = new ArrayList<>();
            lore.add(plugin.lang("prize.weight").replace("%w%", String.valueOf(p.weight)));
            lore.add(plugin.lang("prize.tokens").replace("%t%", String.valueOf(p.tokens)));
            lore.add(plugin.lang("prize.money").replace("%m%", String.valueOf(p.money)));
            setButton(4, Button.display(Utility.createItem(p.material, p.name, lore)));

            // Tokens row: 11=-10, 12=-1, 13=current, 14=+1, 15=+10
            adjustRow(11, 12, 13, 14, 15, plugin.lang("prize.tokensLabel"),
                    p.tokens, dv -> p.tokens = Math.max(0, p.tokens + dv));

            // Money row: 20=-10, 21=-1, 22=current, 23=+1, 24=+10
            adjustRow(20, 21, 22, 23, 24, plugin.lang("prize.moneyLabel"),
                    (int) Math.round(p.money), dv -> p.money = Math.max(0, p.money + dv));

            // Weight row: 29=-10, 30=-1, 31=current, 32=+1, 33=+10
            adjustRow(29, 30, 31, 32, 33, plugin.lang("prize.weightLabel"),
                    p.weight, dv -> p.weight = Math.max(0, p.weight + dv));

            // Remove
            setButton(40, Button.action("remove",
                    Utility.createItem(Material.BARRIER, "&c" + plugin.lang("prize.remove"),
                            Utility.list(plugin.lang("prize.removeHint"))),
                    pl -> {
                        pool.remove(index);
                        pool.save();
                        pl.sendMessage(plugin.langPrefixed("prize.removed"));
                        listPage.open(pl);
                        plugin.getGuiManager().track(pl.getUniqueId(), listPage);
                    }));

            // Back
            setButton(44, Button.action("back",
                    Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                    pl -> listPage.open(pl)));
        }

        /**
         * Build a row of -10 / -1 / current / +1 / +10 buttons at the given
         * slots. The current-value slot is display-only; the others mutate the
         * prize via {@code mutator}, persist, and rebuild.
         */
        private void adjustRow(int sMinus10, int sMinus1, int sCurrent, int sPlus1, int sPlus10,
                               String label, int current, Consumer<Integer> mutator) {
            setButton(sMinus10, Button.action("m10",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-10", null),
                    p -> apply(p, mutator, -10)));
            setButton(sMinus1, Button.action("m1",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                    p -> apply(p, mutator, -1)));
            setButton(sCurrent, Button.display(
                    Utility.createItem(Material.GOLD_BLOCK,
                            "&e" + label + ": &f" + current, null)));
            setButton(sPlus1, Button.action("p1",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                    p -> apply(p, mutator, +1)));
            setButton(sPlus10, Button.action("p10",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10", null),
                    p -> apply(p, mutator, +10)));
        }

        private void apply(Player p, Consumer<Integer> mutator, int delta) {
            mutator.accept(delta);
            pool.save();
            build(p);
        }
    }
}
