package me.nikl.gamebox.game.impl.lottery;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.PrizePool;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lottery session: the player picks {@code pickCount} numbers from a pool, then
 * draws the same count of winning numbers. The match count selects a prize tier
 * from the game's {@link PrizePool} (prize index = matches).
 *
 * <p>Layout (45 slots): slot 4 = info; numbers occupy slots {@code 8+N} (so
 * 1..9 in the second row, 10..16 in the third); slot 31 = Draw; slot 35 = Close.</p>
 */
public class LotterySession extends me.nikl.gamebox.game.AbstractGameSession {

    private final GameLottery game;
    private final int poolSize;
    private final int pickCount;

    private final Set<Integer> picks = new HashSet<>();
    private final Set<Integer> winners = new HashSet<>();
    private boolean drawn = false;
    private boolean settled = false;
    private int matches = 0;
    private BukkitTask endTask;

    public LotterySession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.game = (GameLottery) game;
        this.poolSize = this.game.getPoolSize();
        this.pickCount = this.game.getPickCount();
    }

    @Override
    protected int getInventorySize() {
        return Math.max(27, game.getRule().getGuiSize());
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        if (inventory == null) return;
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getInventorySize(); i++) inventory.setItem(i, filler);

        // Info at slot 4
        inventory.setItem(4, infoItem());

        // Numbers at slots 8+N
        for (int n = 1; n <= poolSize; n++) {
            int slot = 8 + n;
            if (slot >= getInventorySize()) break;
            inventory.setItem(slot, numberItem(n));
        }

        // Draw / Reset at slot 31
        int drawSlot = 31;
        if (drawSlot < getInventorySize()) {
            if (!drawn && picks.size() == pickCount) {
                inventory.setItem(drawSlot, Utility.createItem(Material.EMERALD_BLOCK,
                        "&a" + game.lang("draw"), Utility.list(game.lang("drawHint"))));
            } else if (!drawn) {
                inventory.setItem(drawSlot, Utility.createItem(Material.GRAY_STAINED_GLASS_PANE,
                        "&7" + game.lang("draw"), Utility.list(
                        game.lang("picksNeeded").replace("%n%", String.valueOf(pickCount - picks.size())))));
            } else {
                inventory.setItem(drawSlot, Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
            }
        }

        // Close at slot 35
        int closeSlot = 35;
        if (closeSlot < getInventorySize()) {
            inventory.setItem(closeSlot, Utility.createItem(Material.BARRIER,
                    plugin.lang("gui.closeButton"), null));
        }
    }

    private ItemStack infoItem() {
        List<String> lore = new ArrayList<>();
        if (!drawn) {
            lore.add(game.lang("picksSoFar").replace("%p%", String.valueOf(picks.size()))
                    .replace("%n%", String.valueOf(pickCount)));
            lore.add("");
            lore.add(game.lang("pickHint"));
            return Utility.createItem(Material.PAPER, game.lang("title"), lore);
        }
        // Drawn: show result
        PrizePool.Prize prize = game.getPrizePool().get(matches);
        lore.add(game.lang("matches").replace("%m%", String.valueOf(matches))
                .replace("%n%", String.valueOf(pickCount)));
        if (prize != null) {
            lore.add(game.lang("prizeWon").replace("%t%", String.valueOf(prize.tokens)));
            if (prize.money > 0) lore.add(game.lang("moneyWon").replace("%m%", String.valueOf(prize.money)));
        }
        return Utility.createItem(Material.GOLDEN_APPLE,
                game.lang(matches > 0 ? "youWon" : "noMatch"), lore);
    }

    private ItemStack numberItem(int n) {
        boolean picked = picks.contains(n);
        boolean isWinner = winners.contains(n);
        Material mat;
        String name;
        if (drawn) {
            if (picked && isWinner) { mat = Material.LIME_WOOL; name = "&a" + n + " &7(" + game.lang("match") + ")"; }
            else if (picked) { mat = Material.GRAY_WOOL; name = "&7" + n + " &8(" + game.lang("miss") + ")"; }
            else if (isWinner) { mat = Material.RED_WOOL; name = "&c" + n + " &7(" + game.lang("winner") + ")"; }
            else { mat = Material.IRON_BARS; name = "&8" + n; }
        } else if (picked) {
            mat = Material.LIME_WOOL;
            name = "&a" + n;
        } else {
            mat = Material.PAPER;
            name = "&f" + n;
        }
        return Utility.createItem(mat, name, null);
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (settled) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= getInventorySize()) return;

        // Close button
        if (raw == 35) {
            end();
            return;
        }

        if (drawn) return;

        // Number click?
        int n = raw - 8;
        if (n >= 1 && n <= poolSize) {
            if (picks.contains(n)) {
                picks.remove(n);
            } else if (picks.size() < pickCount) {
                picks.add(n);
            } else {
                player.sendMessage(Utility.color(game.lang("picksFull")));
                return;
            }
            refresh();
            return;
        }

        // Draw button
        if (raw == 31 && picks.size() == pickCount) {
            doDraw(player);
        }
    }

    /** Draw the winning numbers and settle. */
    private void doDraw(Player player) {
        drawn = true;
        // Pick pickCount distinct winners from the pool
        Random r = ThreadLocalRandom.current();
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= poolSize; i++) pool.add(i);
        winners.clear();
        for (int i = 0; i < pickCount && !pool.isEmpty(); i++) {
            int idx = r.nextInt(pool.size());
            winners.add(pool.remove(idx));
        }
        // Count matches
        matches = 0;
        for (int w : winners) if (picks.contains(w)) matches++;

        // Award the prize tier (index = matches)
        PrizePool.Prize prize = game.getPrizePool().get(matches);
        if (prize != null) {
            game.getPrizePool().award(prize, player);
        }
        refresh();

        // Broadcast result message
        player.sendMessage(Utility.color(plugin.lang("prefix") + game.lang("resultMessage")
                .replace("%m%", String.valueOf(matches)).replace("%n%", String.valueOf(pickCount))));

        // Settle: score = matches; won = at least one match
        settled = true;
        game.onGameWonSingle(player, matches > 0, matches);
        ((LotteryManager) game.getGameManager()).endSession(this);

        // Return to game gui after a delay so the player sees the result
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::end, 100L);
    }

    @Override
    public void end() {
        if (endTask != null) { endTask.cancel(); endTask = null; }
        super.end();
    }
}
