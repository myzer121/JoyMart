package me.nikl.gamebox.game.impl.slotmachine;

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
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Slot machine session: three reels spin and land on a prize drawn
 * weighted-randomly from the game's {@link PrizePool}. Each spin costs tokens
 * (configured via {@code settings.spinCostTokens}). Accumulated winnings are
 * recorded as the score when the player cashes out (or leaves).
 *
 * <p>Layout (27 slots): slot 4 = info; slots 11/13/15 = reels; slot 22 = Spin;
 * slot 26 = Cash Out.</p>
 */
public class SlotMachineSession extends me.nikl.gamebox.game.AbstractGameSession {

    private final GameSlotMachine game;
    private final List<Material> symbols = new ArrayList<>();

    private boolean spinning = false;
    private boolean settled = false;
    private int totalWon = 0;
    private int spins = 0;
    private BukkitTask animTask = null;

    private static final int[] REEL_SLOTS = {11, 13, 15};

    public SlotMachineSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.game = (GameSlotMachine) game;
        // Build the reel symbol palette from the prize pool's materials.
        for (PrizePool.Prize p : this.game.getPrizePool().getPrizes()) {
            if (!symbols.contains(p.material)) symbols.add(p.material);
        }
        if (symbols.isEmpty()) {
            symbols.add(Material.IRON_INGOT);
            symbols.add(Material.GOLD_INGOT);
            symbols.add(Material.DIAMOND);
            symbols.add(Material.EMERALD);
        }
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

        // Reels at 11, 13, 15
        for (int s : REEL_SLOTS) {
            inventory.setItem(s, reelItem(symbols.get(0)));
        }

        // Spin button at slot 22
        int cost = game.getSpinCostTokens();
        inventory.setItem(22, Utility.createItem(Material.EMERALD_BLOCK,
                "&a" + game.lang("spin"),
                Utility.list(game.lang("spinHint").replace("%c%", String.valueOf(cost)))));

        // Cash out at slot 26
        inventory.setItem(26, Utility.createItem(Material.GOLD_BLOCK,
                "&6" + game.lang("cashOut"),
                Utility.list(game.lang("cashOutHint"))));
    }

    private ItemStack infoItem() {
        List<String> lore = new ArrayList<>();
        int bal = balance();
        lore.add(game.lang("balance").replace("%b%", String.valueOf(bal)));
        lore.add(game.lang("totalWon").replace("%w%", String.valueOf(totalWon)));
        lore.add(game.lang("spins").replace("%s%", String.valueOf(spins)));
        return Utility.createItem(Material.PAPER, game.lang("title"), lore);
    }

    private int balance() {
        me.nikl.gamebox.data.GBPlayer gb = plugin.getPluginManager().getPlayer(getFirstPlayerId());
        return gb == null ? 0 : gb.getTokens();
    }

    private ItemStack reelItem(Material mat) {
        // Try to find a prize with this material to use its display name.
        String name = "&f" + mat.name();
        for (PrizePool.Prize p : game.getPrizePool().getPrizes()) {
            if (p.material == mat) {
                name = p.name;
                break;
            }
        }
        return Utility.createItem(mat, name, null);
    }

    /** Show the drawn prize's own display item on the center reel. */
    private ItemStack prizeReelItem(PrizePool.Prize prize) {
        if (prize == null) {
            return reelItem(symbols.get(0));
        }
        return Utility.createItem(prize.material, prize.name, new ArrayList<>(prize.lore));
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (settled) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= getInventorySize()) return;

        if (raw == 26) {
            // Cash out
            cashOut(player);
            return;
        }
        if (raw == 22 && !spinning) {
            if (!game.canAffordSpin(player)) {
                player.sendMessage(Utility.color(plugin.lang("prefix") + game.lang("noTokens")
                        .replace("%c%", String.valueOf(game.getSpinCostTokens()))));
                return;
            }
            startSpin(player);
        }
    }

    /** Begin a spin: charge, animate reels, then resolve a weighted prize. */
    private void startSpin(Player player) {
        game.chargeSpin(player);
        spinning = true;
        spins++;
        refresh();

        Random r = ThreadLocalRandom.current();
        final int steps = 10;
        final int[] step = {0};
        // Animate: cycle reels every 3 ticks
        animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            step[0]++;
            for (int s : REEL_SLOTS) {
                Material m = symbols.get(r.nextInt(symbols.size()));
                inventory.setItem(s, reelItem(m));
            }
            if (step[0] >= steps) {
                animTask.cancel();
                animTask = null;
                resolveSpin(player, r);
            }
        }, 2L, 3L);
    }

    /** Draw the weighted prize and apply it. */
    private void resolveSpin(Player player, Random r) {
        PrizePool.Prize prize = game.getPrizePool().draw();
        // Center reel shows the drawn prize's display item (name + lore);
        // left/right reels are cosmetic random symbols.
        inventory.setItem(REEL_SLOTS[1], prizeReelItem(prize));
        inventory.setItem(REEL_SLOTS[0], reelItem(symbols.get(r.nextInt(symbols.size()))));
        inventory.setItem(REEL_SLOTS[2], reelItem(symbols.get(r.nextInt(symbols.size()))));

        // A prize with no tokens, no money, and no commands is a "no prize"
        // result (e.g. the COAL "没中奖" entry). Show the noPrize message
        // instead of "You won: 没中奖 (0 tokens)".
        boolean hasReward = prize != null && (prize.tokens > 0 || prize.money > 0
                || (prize.commands != null && !prize.commands.isEmpty()));
        if (hasReward) {
            game.getPrizePool().award(prize, player);
            totalWon += Math.max(0, prize.tokens);
            player.sendMessage(Utility.color(plugin.lang("prefix") + game.lang("wonPrize")
                    .replace("%p%", Utility.color(prize.name))
                    .replace("%t%", String.valueOf(prize.tokens))));
        } else {
            player.sendMessage(Utility.color(plugin.lang("prefix") + game.lang("noPrize")));
        }
        spinning = false;
        refresh();
    }

    /** Settle accumulated winnings and return to the game gui. */
    private void cashOut(Player player) {
        if (settled) return;
        settled = true;
        if (animTask != null) { animTask.cancel(); animTask = null; }
        game.onGameWonSingle(player, totalWon > 0, totalWon);
        ((SlotMachineManager) game.getGameManager()).endSession(this);
        end();
    }

    /** Settle on forfeit (player closes the inventory without cashing out). */
    @Override
    public void onClose() {
        if (settled) return;
        settled = true;
        if (animTask != null) { animTask.cancel(); animTask = null; }
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null && p.isOnline()) {
            game.onGameWonSingle(p, totalWon > 0, totalWon);
        }
        ((SlotMachineManager) game.getGameManager()).endSession(this);
        // Reopen the game GUI so the player is not left with an empty
        // inventory and no way back into GameBox (soft-lock fix).
        if (p != null && p.isOnline()) {
            end();
        }
    }
}
