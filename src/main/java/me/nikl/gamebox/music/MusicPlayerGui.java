package me.nikl.gamebox.music;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.inventory.AGui;
import me.nikl.gamebox.inventory.Button;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for the NBS music player. Lists all loaded songs as clickable items
 * with pagination (prev-page / next-page buttons), plus Previous / Pause /
 * Next control buttons. The currently playing song is highlighted.
 *
 * <p>Layout (54 slots):</p>
 * <ul>
 *   <li>Slot 4: title</li>
 *   <li>Slot 19: previous-song control</li>
 *   <li>Slot 22: pause/play control</li>
 *   <li>Slot 25: next-song control</li>
 *   <li>Slot 40: now-playing info</li>
 *   <li>Slots 28-34, 37-43: song list (7 per row × 2 rows = 14 per page)</li>
 *   <li>Slot 45: previous page</li>
 *   <li>Slot 49: page indicator</li>
 *   <li>Slot 53: next page</li>
 *   <li>Slot 48: back  ·  Slot 50: close</li>
 * </ul>
 */
public class MusicPlayerGui extends AGui {

    private final MusicPlayer player;
    /** Current page per viewer (so concurrent viewers browse independently). */
    private final Map<UUID, Integer> viewerPage = new HashMap<>();

    /** Songs displayed per page (2 rows of 7, skipping slot 40 reserved for now-playing). */
    private static final int SONGS_PER_PAGE = 13;

    public MusicPlayerGui(GameBox plugin, MusicPlayer player) {
        super(plugin, Utility.color("&8&l音乐播放器"), 54);
        this.player = player;
    }

    @Override
    public void build(Player viewer) {
        clear();

        // Filler
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getSize(); i++) {
            setItem(i, filler);
        }

        // Title
        setButton(4, Button.display(Utility.createItem(Material.NOTE_BLOCK,
                "&6&l音乐播放器",
                Utility.list("&7共 &f" + player.getSongCount() + " &7首歌曲",
                        "&7点击歌曲播放",
                        "&7NBS 乐谱 → 原生音符盒音效"))));

        // ---- Control buttons (row 2) ----
        // Previous song (slot 19)
        setButton(19, Button.action("prev",
                Utility.createItem(Material.ARROW, "&a&l上一首",
                        Utility.list("&7点击播放上一首歌曲")),
                p -> { player.playPrev(p); build(p); }));

        // Pause/Play (slot 22)
        NbsSong current = player.getCurrentSong(viewer);
        Material pauseMat = player.isPlaying(viewer) ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK;
        String pauseName = player.isPlaying(viewer) ? "&c&l暂停" : "&a&l播放";
        List<String> pauseLore = new ArrayList<>();
        if (current != null) {
            pauseLore.add("&7当前: &f" + current.name);
        } else {
            pauseLore.add("&7点击随机播放");
        }
        setButton(22, Button.action("pause",
                Utility.createItem(pauseMat, pauseName, pauseLore),
                p -> { player.togglePause(p); build(p); }));

        // Next song (slot 25)
        setButton(25, Button.action("next",
                Utility.createItem(Material.ARROW, "&a&l下一首",
                        Utility.list("&7点击播放下一首歌曲")),
                p -> { player.playNext(p); build(p); }));

        // ---- Now playing info (slot 40) ----
        if (current != null) {
            List<String> nowLore = new ArrayList<>();
            nowLore.add(player.isPlaying(viewer) ? "&a▶ 播放中" : "&c⏸ 已暂停");
            if (current.author != null && !current.author.isEmpty()) {
                nowLore.add("&7作者: &f" + current.author);
            }
            nowLore.add("&7长度: &f" + current.lengthTicks + " &7ticks");
            nowLore.add("&7节奏: &f" + (current.tempo / 100.0) + " &7tps");
            setButton(40, Button.display(Utility.createItem(Material.MUSIC_DISC_CAT,
                    "&e&l正在播放: &f" + current.name, nowLore)));
        } else {
            setButton(40, Button.display(Utility.createItem(Material.MUSIC_DISC_CAT,
                    "&e&l未播放", Utility.list("&7点击下方歌曲开始播放"))));
        }

        // ---- Song list with pagination (rows 4-5: slots 28-34, 37-43) ----
        List<NbsSong> songs = player.getSongs();
        int totalSongs = songs.size();
        int totalPages = Math.max(1, (totalSongs + SONGS_PER_PAGE - 1) / SONGS_PER_PAGE);
        int rawPage = viewerPage.getOrDefault(viewer.getUniqueId(), 0);
        int normalized = Math.min(rawPage, totalPages - 1);
        if (normalized < 0) normalized = 0;
        viewerPage.put(viewer.getUniqueId(), normalized);
        // Effectively-final copy for use in lambda callbacks below.
        final int page = normalized;

        int startIndex = page * SONGS_PER_PAGE;
        int endIndex = Math.min(startIndex + SONGS_PER_PAGE, totalSongs);
        int currentIndex = player.getCurrentIndex(viewer);

        // Slots for songs: row 4 (28-34) + row 5 (37-43), skipping slot 40
        // which is reserved for the now-playing info panel.
        int[] songSlots = {
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 41, 42, 43
        };
        int displayCount = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (displayCount >= songSlots.length) break;
            int slot = songSlots[displayCount];
            NbsSong song = songs.get(i);
            boolean isCurrent = (i == currentIndex);
            Material mat = isCurrent
                    ? (player.isPlaying(viewer) ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK)
                    : Material.MUSIC_DISC_CHIRP;
            List<String> lore = new ArrayList<>();
            if (song.author != null && !song.author.isEmpty()) {
                lore.add("&7作者: &f" + song.author);
            }
            lore.add("&7长度: &f" + song.lengthTicks + " &7ticks");
            lore.add("&7节奏: &f" + (song.tempo / 100.0) + " &7tps");
            if (isCurrent) {
                lore.add(player.isPlaying(viewer) ? "&a▶ 正在播放" : "&c⏸ 已暂停");
            } else {
                lore.add("&a点击播放");
            }
            final int songIdx = i;
            setButton(slot, Button.action("song_" + i,
                    Utility.createItem(mat, (isCurrent ? "&e" : "&f") + song.name, lore),
                    p -> { player.playSong(p, songIdx); build(p); }));
            displayCount++;
        }

        // ---- Pagination buttons (row 6: slots 45-53) ----
        // Previous page (slot 45)
        if (page > 0) {
            setButton(45, Button.action("prevpage",
                    Utility.createItem(Material.ARROW, "&a&l上一页",
                            Utility.list("&7查看之前的歌曲")),
                    p -> {
                        viewerPage.put(p.getUniqueId(), page - 1);
                        build(p);
                    }));
        }
        // Page indicator (slot 49)
        setButton(49, Button.display(Utility.createItem(Material.BOOK,
                "&e&l第 &f" + (page + 1) + " &e/ &f" + totalPages + " &e页",
                Utility.list("&7共 &f" + totalSongs + " &7首歌曲"))));
        // Next page (slot 53)
        if (page + 1 < totalPages) {
            setButton(53, Button.action("nextpage",
                    Utility.createItem(Material.ARROW, "&a&l下一页",
                            Utility.list("&7查看更多歌曲")),
                    p -> {
                        viewerPage.put(p.getUniqueId(), page + 1);
                        build(p);
                    }));
        }

        // Back button (slot 48) and Close button (slot 50)
        setButton(48, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> plugin.getGuiManager().openMain(p)));
        setButton(50, Button.action("close",
                Utility.createItem(Material.BARRIER, plugin.lang("gui.closeButton"), null),
                Player::closeInventory));
    }
}
