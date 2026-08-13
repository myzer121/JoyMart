package me.nikl.gamebox.game.impl.rockpaperscissors;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared two-player Rock-Paper-Scissors session.
 *
 * <p>Flow per round: both players click one of three weapon buttons to lock in
 * a secret choice (recorded server-side, only a "ready" indicator is shown).
 * Once both have chosen, both picks are revealed, the round winner scores a
 * point, and after a short delay the next round begins. The first player to
 * reach a majority of rounds wins the match.</p>
 */
public class RPSSession extends AbstractGameSession {

    private enum Choice { ROCK, PAPER, SCISSORS }

    /** Slot -> weapon. */
    private static final int SLOT_ROCK = 11;
    private static final int SLOT_PAPER = 13;
    private static final int SLOT_SCISSORS = 15;

    private final UUID p1;
    private final UUID p2;
    private final int bestOf;
    private final int targetWins;

    private int round = 1;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Choice> choices = new HashMap<>();

    // Live score reported via onScoreChange: round win +2, round tie +1 each.
    private int score1 = 0;
    private int score2 = 0;
    // Consecutive round wins per player, for the "streak" event.
    private int streak1 = 0;
    private int streak2 = 0;

    private boolean revealed = false;
    /** -1 = not revealed, 0 = tie, 1 = player 1 won the round, 2 = player 2 won the round. */
    private int lastResult = -1;

    private BukkitTask pendingTask = null;

    public RPSSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.p1 = players.get(0).getUniqueId();
        this.p2 = players.size() > 1 ? players.get(1).getUniqueId() : AI_ID;
        if (players.size() == 1) this.vsAi = true;
        this.scores.put(p1, 0);
        this.scores.put(p2, 0);
        this.bestOf = ((GameRockPaperScissors) game).getBestOf();
        this.targetWins = (bestOf + 1) / 2;
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
        ItemStack glass = Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getInventorySize(); i++) {
            inventory.setItem(i, glass);
        }
        inventory.setItem(4, infoItem());
        if (revealed) {
            inventory.setItem(SLOT_ROCK, revealItem(p1));
            inventory.setItem(SLOT_SCISSORS, revealItem(p2));
            inventory.setItem(SLOT_PAPER, resultItem());
        } else {
            inventory.setItem(SLOT_ROCK, choiceButton(Choice.ROCK));
            inventory.setItem(SLOT_PAPER, choiceButton(Choice.PAPER));
            inventory.setItem(SLOT_SCISSORS, choiceButton(Choice.SCISSORS));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (finished || revealed) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= getInventorySize()) {
            return;
        }
        Choice choice = choiceForSlot(slot);
        if (choice == null) {
            return;
        }
        choices.put(player.getUniqueId(), choice);
        if (choices.containsKey(p1) && choices.containsKey(p2)) {
            reveal();
        } else if (vsAi && choices.containsKey(p1)) {
            // AI picks after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, this::aiPick, 20L);
        } else {
            refresh();
        }
    }

    /** AI picks a random weapon and triggers reveal. */
    private void aiPick() {
        if (finished || revealed) return;
        Choice[] vals = Choice.values();
        choices.put(p2, vals[new java.util.Random().nextInt(vals.length)]);
        if (choices.containsKey(p1) && choices.containsKey(p2)) {
            reveal();
        }
    }

    private void reveal() {
        revealed = true;
        Choice c1 = choices.get(p1);
        Choice c2 = choices.get(p2);
        lastResult = compare(c1, c2);
        if (lastResult == 1) {
            scores.put(p1, scores.get(p1) + 1);
        } else if (lastResult == 2) {
            scores.put(p2, scores.get(p2) + 1);
        }

        // Round settlement: report live score (win +2, tie +1 each) via
        // onScoreChange, and track consecutive wins for the "streak" event.
        Player p1Player = Bukkit.getPlayer(p1);
        Player p2Player = Bukkit.getPlayer(p2);
        if (lastResult == 0) {
            score1 += 1;
            score2 += 1;
            streak1 = 0;
            streak2 = 0;
            if (p1Player != null) game.onScoreChange(p1Player, score1, 1);
            if (p2Player != null) game.onScoreChange(p2Player, score2, 1);
        } else if (lastResult == 1) {
            score1 += 2;
            streak1 += 1;
            streak2 = 0;
            if (p1Player != null) {
                game.onScoreChange(p1Player, score1, 2);
                if (streak1 >= 2) {
                    game.onGameEvent(p1Player, "streak", streak1);
                }
            }
        } else if (lastResult == 2) {
            score2 += 2;
            streak2 += 1;
            streak1 = 0;
            if (p2Player != null) {
                game.onScoreChange(p2Player, score2, 2);
                if (streak2 >= 2) {
                    game.onGameEvent(p2Player, "streak", streak2);
                }
            }
        }

        refresh();

        if (lastResult == 0) {
            broadcast(game.lang("tie"));
        } else {
            UUID roundWinner = lastResult == 1 ? p1 : p2;
            broadcast(Utility.replace(game.lang("roundWin"),
                    new String[]{"%player%", playerName(roundWinner)}));
        }

        if (scores.get(p1) >= targetWins || scores.get(p2) >= targetWins) {
            pendingTask = Bukkit.getScheduler().runTaskLater(plugin, this::finishMatch, 40L);
        } else {
            pendingTask = Bukkit.getScheduler().runTaskLater(plugin, this::nextRound, 40L);
        }
    }

    private void nextRound() {
        pendingTask = null;
        if (finished || !isActive()) {
            return;
        }
        if (lastResult != 0) {
            round++; // tie: replay the same round number
        }
        choices.clear();
        revealed = false;
        lastResult = -1;
        refresh();
    }

    private void finishMatch() {
        pendingTask = null;
        if (finished || !isActive()) {
            return;
        }
        int s1 = scores.get(p1);
        int s2 = scores.get(p2);
        UUID winner;
        UUID loser;
        boolean draw;
        if (s1 > s2) {
            winner = p1;
            loser = p2;
            draw = false;
        } else if (s2 > s1) {
            winner = p2;
            loser = p1;
            draw = false;
        } else {
            winner = p1;
            loser = p2;
            draw = true;
        }

        finished = true;
        game.onGameWonMulti(winner, loser, draw);
        ((RPSManager) game.getGameManager()).endSession(this);
        refresh();

        if (draw) {
            broadcast(game.lang("draw"));
        } else {
            broadcast(Utility.replace(game.lang("win"), new String[]{"%player%", playerName(winner)}));
        }
        end();
    }

    @Override
    public void end() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        super.end();
    }

    // ---- Rendering helpers ----

    private ItemStack infoItem() {
        String name;
        List<String> lore = new ArrayList<>();
        if (finished) {
            int s1 = scores.get(p1);
            int s2 = scores.get(p2);
            if (s1 > s2) {
                name = Utility.replace(game.lang("win"), new String[]{"%player%", playerName(p1)});
            } else if (s2 > s1) {
                name = Utility.replace(game.lang("win"), new String[]{"%player%", playerName(p2)});
            } else {
                name = game.lang("draw");
            }
        } else {
            name = Utility.replace(game.lang("round"),
                    new String[]{"%round%", String.valueOf(round)},
                    new String[]{"%bestOf%", String.valueOf(bestOf)});
        }
        lore.add(Utility.replace(game.lang("score"),
                new String[]{"%player%", playerName(p1)},
                new String[]{"%score%", String.valueOf(scores.get(p1))}));
        lore.add(Utility.replace(game.lang("score"),
                new String[]{"%player%", playerName(p2)},
                new String[]{"%score%", String.valueOf(scores.get(p2))}));
        if (!finished && !revealed) {
            lore.add(Utility.replace(game.lang("status"),
                    new String[]{"%player%", playerName(p1)},
                    new String[]{"%status%", choices.containsKey(p1) ? game.lang("ready") : game.lang("waiting")}));
            lore.add(Utility.replace(game.lang("status"),
                    new String[]{"%player%", playerName(p2)},
                    new String[]{"%status%", choices.containsKey(p2) ? game.lang("ready") : game.lang("waiting")}));
        } else if (revealed) {
            if (lastResult == 0) {
                lore.add(game.lang("tie"));
            } else if (lastResult == 1) {
                lore.add(Utility.replace(game.lang("roundWin"), new String[]{"%player%", playerName(p1)}));
            } else if (lastResult == 2) {
                lore.add(Utility.replace(game.lang("roundWin"), new String[]{"%player%", playerName(p2)}));
            }
        }
        return Utility.createItem(Material.BOOK, name, lore);
    }

    private ItemStack choiceButton(Choice choice) {
        return Utility.createItem(material(choice), choiceName(choice),
                Collections.singletonList(game.lang("clickToPick")));
    }

    private ItemStack revealItem(UUID player) {
        Choice choice = choices.get(player);
        return Utility.createItem(material(choice), playerName(player),
                Collections.singletonList(choiceName(choice)));
    }

    private ItemStack resultItem() {
        switch (lastResult) {
            case 0:
                return Utility.createItem(Material.YELLOW_CONCRETE, game.lang("tie"), null);
            case 1:
                return Utility.createItem(Material.GREEN_CONCRETE,
                        Utility.replace(game.lang("roundWin"), new String[]{"%player%", playerName(p1)}), null);
            case 2:
                return Utility.createItem(Material.GREEN_CONCRETE,
                        Utility.replace(game.lang("roundWin"), new String[]{"%player%", playerName(p2)}), null);
            default:
                return Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        }
    }

    private Choice choiceForSlot(int slot) {
        switch (slot) {
            case SLOT_ROCK:
                return Choice.ROCK;
            case SLOT_PAPER:
                return Choice.PAPER;
            case SLOT_SCISSORS:
                return Choice.SCISSORS;
            default:
                return null;
        }
    }

    private Material material(Choice choice) {
        switch (choice) {
            case ROCK:
                return Material.STONE;
            case PAPER:
                return Material.PAPER;
            case SCISSORS:
                return Material.SHEARS;
            default:
                return Material.STONE;
        }
    }

    private String choiceName(Choice choice) {
        switch (choice) {
            case ROCK:
                return game.lang("rock");
            case PAPER:
                return game.lang("paper");
            case SCISSORS:
                return game.lang("scissors");
            default:
                return "";
        }
    }

    /** Whether this session is still registered with its manager (false once a player quits). */
    private boolean isActive() {
        return game.getGameManager().isInGame(p1);
    }

    /** @return 1 if a beats b, 2 if b beats a, 0 on a tie. */
    private int compare(Choice a, Choice b) {
        if (a == b) {
            return 0;
        }
        if ((a == Choice.ROCK && b == Choice.SCISSORS)
                || (a == Choice.SCISSORS && b == Choice.PAPER)
                || (a == Choice.PAPER && b == Choice.ROCK)) {
            return 1;
        }
        return 2;
    }

    private void broadcast(String message) {
        String colored = Utility.color(message);
        for (Player p : players) {
            if (p.isOnline()) {
                p.sendMessage(colored);
            }
        }
    }

}
