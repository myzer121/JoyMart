package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.data.TopList;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated high-score display for a single game. Shows player heads ranked by
 * score with a back button.
 */
public class TopListPage extends AGui {

    /** Short-lived per-game cache (30s) so opening the same page twice doesn't re-query. */
    private static final java.util.Map<String, Cached> CACHE = new java.util.HashMap<>();
    private static final long CACHE_TTL_MS = 30_000L;

    private final String gameId;
    private final List<TopList.Entry> entries;
    private int page = 0;
    private final int perPage;

    public TopListPage(GameBox plugin, String gameId) {
        super(plugin, plugin.lang("gui.topListTitle").replace("%game%", gameId), 54);
        this.gameId = gameId;
        this.entries = getCached(plugin, gameId);
        this.perPage = 36;
    }

    @Override
    protected String getDynamicTitle() {
        return plugin.lang("gui.topListTitle").replace("%game%", gameId);
    }

    private static List<TopList.Entry> getCached(GameBox plugin, String gameId) {
        Cached c = CACHE.get(gameId);
        if (c != null && System.currentTimeMillis() - c.timestamp < CACHE_TTL_MS) {
            return c.entries;
        }
        List<TopList.Entry> fresh = plugin.getDataBase().getTopList(gameId, 45);
        CACHE.put(gameId, new Cached(fresh, System.currentTimeMillis()));
        return fresh;
    }

    /** Invalidate the cached top list for a game (call after a new high score). */
    public static void invalidate(String gameId) {
        CACHE.remove(gameId);
    }

    private static final class Cached {
        final List<TopList.Entry> entries;
        final long timestamp;
        Cached(List<TopList.Entry> e, long t) { this.entries = e; this.timestamp = t; }
    }

    @Override
    public void build(Player player) {
        clear();

        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            TopList.Entry e = entries.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Utility.replace(plugin.lang("gui.highScore"),
                        new String[]{"%rank%", String.valueOf(e.getRank())},
                        new String[]{"%player%", e.getName()},
                        new String[]{"%score%", String.valueOf(e.getScore())}));
                meta.setOwningPlayer(player.getServer().getOfflinePlayer(e.getOwner()));
                head.setItemMeta(meta);
            }
            setButton(slot, Button.display(head));
            slot++;
        }

        // Prev / Next
        if (page > 0) {
            setButton(45, Button.action("prev", Utility.createItem(Material.ARROW,
                    plugin.lang("gui.prevPage"), null), p -> { page--; open(p); }));
        }
        if (end < entries.size()) {
            setButton(53, Button.action("next", Utility.createItem(Material.ARROW,
                    plugin.lang("gui.nextPage"), null), p -> { page++; open(p); }));
        }

        // Back
        setButton(49, Button.action("back", Utility.createItem(Material.BARRIER,
                plugin.lang("gui.backButton"), null), p -> {
            me.nikl.gamebox.game.Game g = plugin.getGameRegistry().getGame(gameId);
            if (g != null) g.getGameGui().open(p);
            else plugin.getGuiManager().openMain(p);
        }));

        // Fill remaining with glass for aesthetics
        fillEmpty();
    }

    private void fillEmpty() {
        ItemStack glass = Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getSize(); i++) {
            if (getButton(i) == null) {
                setButton(i, Button.display(glass));
            }
        }
    }

    public List<TopList.Entry> getEntries() { return new ArrayList<>(entries); }
}
