package me.nikl.gamebox.game.impl.whackamole;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A single Whack-a-Mole round. Owns a 3x3 hole grid rendered in a size-27
 * inventory. A repeating task pops moles at random holes and counts down the
 * remaining time; clicking a mole scores a point. When time expires the result
 * is settled through {@link me.nikl.gamebox.game.Game#onGameWonSingle}.
 */
public class WhackAMoleSession extends AbstractGameSession {

    private static final int INFO_SLOT = 0;
    private static final int[] HOLE_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};

    private final int popIntervalTicks;
    private final long durationTicks;

    private final Set<Integer> moleSlots = new HashSet<>();
    private final Set<Integer> goldenSlots = new HashSet<>();
    private long score = 0;
    private int combo = 0;
    private long timeLeftTicks;
    private BukkitTask task;
    private boolean taskStarted = false;
    private final Random random = new Random();

    public WhackAMoleSession(GameBox plugin, GameWhackAMole game, List<Player> players) {
        super(plugin, game, players);
        this.popIntervalTicks = game.getPopIntervalTicks();
        this.durationTicks = (long) game.getDurationSeconds() * 20L;
        this.timeLeftTicks = this.durationTicks;
    }

    @Override
    protected int getInventorySize() {
        return 27;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        ItemStack bg = backgroundPane();
        ItemStack hole = holeItem();
        ItemStack mole = moleItem();
        for (int i = 0; i < 27; i++) {
            if (i == INFO_SLOT) {
                inventory.setItem(i, infoItem());
            } else if (isHole(i)) {
                if (moleSlots.contains(i)) {
                    inventory.setItem(i, goldenSlots.contains(i) ? goldenMoleItem() : mole);
                } else {
                    inventory.setItem(i, hole);
                }
            } else {
                inventory.setItem(i, bg);
            }
        }
        if (!taskStarted) {
            taskStarted = true;
            startTask();
        }
    }

    private boolean isHole(int slot) {
        for (int h : HOLE_SLOTS) {
            if (h == slot) return true;
        }
        return false;
    }

    private void startTask() {
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                Player p = players.isEmpty() ? null : players.get(0);
                if (p == null || !p.isOnline()) {
                    cancel();
                    return;
                }
                timeLeftTicks -= popIntervalTicks;
                if (timeLeftTicks <= 0) {
                    timeLeftTicks = 0;
                    refresh();
                    finishGame();
                    return;
                }
                popMoles();
                refresh();
            }
        }.runTaskTimer(plugin, popIntervalTicks, popIntervalTicks);
    }

    private void popMoles() {
        moleSlots.clear();
        goldenSlots.clear();
        int n = 1 + random.nextInt(3); // 1..3 moles at a time
        int[] shuffled = HOLE_SLOTS.clone();
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = t;
        }
        for (int i = 0; i < n && i < shuffled.length; i++) {
            int slot = shuffled[i];
            moleSlots.add(slot);
            if (random.nextDouble() < 0.1) { // 10% chance of a golden mole
                goldenSlots.add(slot);
            }
        }
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (finished) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= getInventorySize()) return;
        if (slot == INFO_SLOT || !isHole(slot)) return;
        if (moleSlots.contains(slot)) {
            boolean golden = goldenSlots.remove(slot);
            moleSlots.remove(slot);
            combo++;
            long delta = 1;
            if (combo % 3 == 0) { // every 3-hit combo grants +2 bonus
                delta += 2;
            }
            score += delta;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, golden ? 1.6f : 2f);
            game.onScoreChange(player, score, delta);
            if (combo % 5 == 0) {
                game.onGameEvent(player, "combo", 5);
            }
            if (golden) {
                game.onGameEvent(player, "golden", 1);
            }
        } else {
            // Missed swing: break the combo streak.
            combo = 0;
        }
        refresh();
    }

    private void finishGame() {
        if (finished) return;
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null && p.isOnline()) {
            game.onGameWonSingle(p, true, score);
        }
        ((WhackAMoleManager) game.getGameManager()).endSession(this);
        end();
    }

    /** Cancel the scheduler task if it is still running. */
    public void cancelTask() {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
            task = null;
        }
    }

    @Override
    public void end() {
        cancelTask();
        super.end();
    }

    // ---- Items ----

    private ItemStack backgroundPane() {
        return Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private ItemStack holeItem() {
        List<String> lore = new ArrayList<>();
        lore.add(game.lang("holeLore"));
        return Utility.createItem(Material.COARSE_DIRT, game.lang("holeName"), lore);
    }

    private ItemStack moleItem() {
        List<String> lore = new ArrayList<>();
        lore.add(game.lang("moleLore"));
        return Utility.createItem(Material.BROWN_CONCRETE, game.lang("moleName"), lore);
    }

    private ItemStack goldenMoleItem() {
        List<String> lore = new ArrayList<>();
        lore.add(game.lang("moleLore"));
        return Utility.createItem(Material.GOLD_BLOCK, "&6&l\u2605 " + game.lang("moleName"), lore);
    }

    private ItemStack infoItem() {
        List<String> lore = new ArrayList<>();
        lore.add(Utility.replace(game.lang("infoScore"),
                new String[]{"%score%", String.valueOf(score)}));
        lore.add(Utility.replace(game.lang("infoTime"),
                new String[]{"%time%", String.valueOf((timeLeftTicks + 19) / 20)}));
        return Utility.createItem(Material.CLOCK, game.lang("infoName"), lore);
    }
}
