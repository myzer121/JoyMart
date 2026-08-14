package me.nikl.gamebox.music;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background music player for GameBox.
 *
 * <h3>NBS playback — native note-block sounds, no resource pack</h3>
 * Songs are loaded from {@code .nbs} (Note Block Studio) files in
 * {@code plugins/GameBox/music/}. The {@link NbsSong} parser extracts the
 * tempo and per-tick notes; this class then schedules a per-player task that
 * advances one NBS tick at a time (paced by the song's tempo) and plays every
 * note at the current tick through {@link Player#playSound} using Minecraft's
 * built-in note-block sounds ({@code BLOCK_NOTE_BLOCK_HARP},
 * {@code BLOCK_NOTE_BLOCK_BASS}, ...).
 *
 * <p>Because each player has their own scheduled task and playback state,
 * different players can listen to different songs simultaneously without
 * interfering with each other.</p>
 *
 * <h3>How the tick scheduler works</h3>
 * The NBS tempo is stored as ticks-per-second × 100 (so 1000 = 10 tps). The
 * server runs at 20 ticks/second, so one NBS tick takes
 * {@code 2000 / tempo} server ticks. That ratio is rounded to the nearest
 * whole tick and used as the {@code runTaskTimer} period. Tempos up to 2000
 * (20 tps) are exact; higher tempos play slightly slower (rounded up to 1
 * server tick per NBS tick).
 */
public class MusicPlayer {

    private final GameBox plugin;
    private final MusicPlayerGui gui;
    /** All loaded songs, in the order they were read from disk. */
    private final List<NbsSong> songs = new ArrayList<>();
    /** Per-player playback state (current song, current tick, playing flag). */
    private final Map<UUID, PlaybackState> states = new HashMap<>();
    /** Per-player active scheduler task (so we can cancel on stop / switch). */
    private final Map<UUID, BukkitTask> playbackTasks = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MusicPlayer(GameBox plugin) {
        this.plugin = plugin;
        this.gui = new MusicPlayerGui(plugin, this);
        loadSongs();
    }

    /** Scan the {@code music/} folder for {@code .nbs} files and parse them. */
    private void loadSongs() {
        songs.clear();
        File musicDir = new File(plugin.getDataFolder(), "music");
        if (!musicDir.exists()) musicDir.mkdirs();
        File[] nbsFiles = musicDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".nbs"));
        if (nbsFiles != null) {
            for (File f : nbsFiles) {
                try {
                    byte[] data = Files.readAllBytes(f.toPath());
                    NbsSong song = NbsSong.parse(data);
                    // Fall back to the file name (without extension) if the
                    // song has no in-file title set.
                    if (song.name == null || song.name.isEmpty()) {
                        song.name = f.getName().substring(0, f.getName().length() - 4);
                    }
                    songs.add(song);
                    plugin.getLogger().info("Loaded NBS song: " + song.name
                            + (song.author != null && !song.author.isEmpty()
                                    ? " by " + song.author : "")
                            + " (" + song.lengthTicks + " ticks, tempo "
                            + song.tempo + ", " + song.notesByTick.size()
                            + " active ticks)");
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to read NBS file " + f + ": " + e);
                }
            }
        }
        plugin.getLogger().info("MusicPlayer loaded " + songs.size() + " NBS songs.");
    }

    public MusicPlayerGui getGui() { return gui; }
    public List<NbsSong> getSongs() { return songs; }
    public int getSongCount() { return songs.size(); }

    private PlaybackState getState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), k -> new PlaybackState());
    }

    /** Play a random song for the player. Used when entering the main menu. */
    public void playRandom(Player player) {
        if (songs.isEmpty()) return;
        playSong(player, random.nextInt(songs.size()));
    }

    /** Play the song at the given index, stopping any current playback. */
    public void playSong(Player player, int index) {
        if (index < 0 || index >= songs.size()) return;
        NbsSong song = songs.get(index);
        PlaybackState st = getState(player);
        // Cancel previous task first so old notes don't bleed into the new song.
        cancelTask(player);
        st.currentIndex = index;
        st.currentTick = 0;
        st.playing = true;
        startPlaybackTask(player, song, true);
        player.sendMessage(Utility.color(plugin.lang(player, "prefix")
                + plugin.lang(player, "messages.musicNowPlayingMsg").replace("%name%", song.name)
                + (song.author != null && !song.author.isEmpty()
                        ? " &7by " + song.author : "")));
    }

    /** Play the next song (wraps around). */
    public void playNext(Player player) {
        PlaybackState st = getState(player);
        int next = st.currentIndex + 1;
        if (next >= songs.size()) next = 0;
        playSong(player, next);
    }

    /** Play the previous song (wraps around). */
    public void playPrev(Player player) {
        PlaybackState st = getState(player);
        int prev = st.currentIndex - 1;
        if (prev < 0) prev = songs.size() - 1;
        playSong(player, prev);
    }

    /** Toggle pause / play. If nothing is queued, starts a random song. */
    public void togglePause(Player player) {
        PlaybackState st = getState(player);
        if (st.currentIndex < 0) {
            playRandom(player);
            return;
        }
        if (st.playing) {
            st.playing = false;
            cancelTask(player);
            player.sendMessage(Utility.color(plugin.lang(player, "prefix") + plugin.lang(player, "messages.musicPausedMsg")));
        } else {
            st.playing = true;
            if (st.currentIndex >= 0 && st.currentIndex < songs.size()) {
                // Resume from the paused position — do NOT reset currentTick.
                startPlaybackTask(player, songs.get(st.currentIndex), false);
                player.sendMessage(Utility.color(plugin.lang(player, "prefix") + plugin.lang(player, "messages.musicResumedMsg")));
            }
        }
    }

    /** Stop all music for a player and reset playback position. */
    public void stop(Player player) {
        cancelTask(player);
        PlaybackState st = getState(player);
        st.playing = false;
        st.currentTick = 0;
    }

    public boolean isPlaying(Player player) {
        return getState(player).playing;
    }

    public int getCurrentIndex(Player player) {
        return getState(player).currentIndex;
    }

    public NbsSong getCurrentSong(Player player) {
        int idx = getCurrentIndex(player);
        if (idx < 0 || idx >= songs.size()) return null;
        return songs.get(idx);
    }

    /** Per-player snapshot of where they are in a song. */
    private static class PlaybackState {
        int currentIndex = -1;
        boolean playing = false;
        int currentTick = 0;
    }

    // ---- Tick scheduler ---------------------------------------------------

    /**
     * Schedule a per-player task that advances one NBS tick per invocation
     * and plays every note at that tick. The task self-cancels when the
     * player leaves, switches songs, pauses, or reaches the end of the song.
     *
     * @param resetPosition {@code true} to start from tick 0 (new song),
     *                      {@code false} to resume from the paused position.
     */
    private void startPlaybackTask(Player player, NbsSong song, boolean resetPosition) {
        cancelTask(player);
        if (song.lengthTicks <= 0 && song.notesByTick.isEmpty()) {
            plugin.getLogger().warning("Cannot play '" + song.name
                    + "': no notes parsed (lengthTicks=" + song.lengthTicks
                    + ", notesByTick empty). The .nbs file may be corrupt"
                    + " or use an unsupported format.");
            return;
        }

        long periodTicks = periodForTempo(song.tempo);
        PlaybackState st = getState(player);
        if (resetPosition) st.currentTick = 0;
        final UUID playerId = player.getUniqueId();
        // Snapshot the song index so a song switch invalidates this task.
        final int expectedIndex = st.currentIndex;
        final int startTick = st.currentTick;

        plugin.getLogger().info("[" + player.getName() + "] Starting NBS playback: "
                + song.name + " | tempo=" + song.tempo + " (tps=" + (song.tempo / 100.0) + ")"
                + " | period=" + periodTicks + " server ticks"
                + " | length=" + song.lengthTicks + " NBS ticks"
                + " | startTick=" + startTick
                + " | activeTicks=" + song.notesByTick.size());

        // Track whether we've logged the first note (for diagnostics).
        final boolean[] loggedFirst = {false};

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p == null || !p.isOnline()) {
                cancelTaskById(playerId);
                return;
            }
            PlaybackState s = states.get(playerId);
            if (s == null || !s.playing || s.currentIndex != expectedIndex) {
                cancelTaskById(playerId);
                return;
            }
            // Play all notes scheduled at the current tick.
            List<NbsSong.Note> notes = song.notesAt(s.currentTick);
            if (notes != null && !notes.isEmpty()) {
                for (NbsSong.Note note : notes) {
                    playNote(p, note);
                }
                if (!loggedFirst[0]) {
                    loggedFirst[0] = true;
                    NbsSong.Note first = notes.get(0);
                    plugin.getLogger().info("[" + player.getName()
                            + "] First note played at tick " + s.currentTick
                            + " | instrument=" + first.instrument
                            + " key=" + first.key
                            + " velocity=" + first.velocity
                            + " -> sound=" + instrumentToSound(first.instrument).name());
                }
            }
            s.currentTick++;
            // Periodic progress log every ~5 seconds (100 NBS ticks @ 10tps).
            if (s.currentTick % 100 == 0) {
                plugin.getLogger().info("[" + player.getName() + "] Progress: tick "
                        + s.currentTick + "/" + song.lengthTicks);
            }
            // End of song: stop and reset position (no auto-loop).
            if (s.currentTick > song.lengthTicks) {
                plugin.getLogger().info("[" + player.getName() + "] Song finished: "
                        + song.name + " (reached tick " + s.currentTick + ")");
                s.playing = false;
                s.currentTick = 0;
                cancelTaskById(playerId);
            }
        }, 0L, periodTicks);
        playbackTasks.put(playerId, task);
    }

    /** Convert NBS tempo (tps × 100) into a server-tick period (≥ 1). */
    private static long periodForTempo(int tempo) {
        if (tempo < 1) tempo = 1000;
        long period = Math.round(2000.0 / tempo);
        return Math.max(1L, period);
    }

    private void cancelTask(Player player) {
        cancelTaskById(player.getUniqueId());
    }

    private void cancelTaskById(UUID playerId) {
        BukkitTask task = playbackTasks.remove(playerId);
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    // ---- Note playback ----------------------------------------------------

    /**
     * Play a single NBS note as a native Minecraft note-block sound. The NBS
     * key range 33-57 maps to Minecraft's two-octave note range 0-24; keys
     * outside that range are clamped to the nearest playable note. Pitch is
     * {@code 2^((mcNote - 12) / 12)}, so note 12 (F#4) plays at pitch 1.0.
     *
     * <p>Uses {@link SoundCategory#PLAYERS} instead of {@code RECORDS} so the
     * audio is audible even if the player has turned down the "Jukebox/Note
     * Blocks" slider in their sound settings — the Players category defaults
     * to 100% and is rarely muted. The sound is emitted from the player
     * entity itself (Entity overload) so it follows the player as they move
     * and is always heard at full volume by that player only, keeping each
     * player's playback independent.</p>
     */
    private void playNote(Player player, NbsSong.Note note) {
        Sound sound = instrumentToSound(note.instrument);
        int mcNote = note.key - 33;
        if (mcNote < 0) mcNote = 0;
        if (mcNote > 24) mcNote = 24;
        // Minecraft note-block pitch formula: 2^((note-12)/12).
        // mcNote 0 -> 0.5, 12 -> 1.0, 24 -> 2.0. Clamped to Bukkit's [0.5, 2.0].
        float pitch = (float) Math.pow(2.0, (mcNote - 12) / 12.0);
        if (pitch < 0.5f) pitch = 0.5f;
        if (pitch > 2.0f) pitch = 2.0f;
        // Velocity 0-100 → volume 0-1. Guarantee a clearly audible floor so
        // quiet notes aren't lost; cap at 1.0 (values >1 act as range, not
        // louder volume, which would leak to nearby players).
        float volume = note.velocity / 100.0f;
        if (volume < 0.7f) volume = 0.7f;
        if (volume > 1f) volume = 1f;
        // Entity overload: sound follows the player, heard at full volume by
        // this player only. PLAYERS category avoids the common "jukebox muted"
        // problem and keeps playback independent per player.
        player.playSound(player, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    /** Map an NBS instrument id to the matching Minecraft note-block sound. */
    private static Sound instrumentToSound(int instrument) {
        switch (instrument) {
            case 0:  return Sound.BLOCK_NOTE_BLOCK_HARP;
            case 1:  return Sound.BLOCK_NOTE_BLOCK_BASS;
            case 2:  return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            case 3:  return Sound.BLOCK_NOTE_BLOCK_SNARE;
            case 4:  return Sound.BLOCK_NOTE_BLOCK_HAT;
            case 5:  return Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case 6:  return Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case 7:  return Sound.BLOCK_NOTE_BLOCK_BELL;
            case 8:  return Sound.BLOCK_NOTE_BLOCK_CHIME;
            case 9:  return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case 10: return Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case 11: return Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case 12: return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case 13: return Sound.BLOCK_NOTE_BLOCK_BIT;
            case 14: return Sound.BLOCK_NOTE_BLOCK_BANJO;
            case 15: return Sound.BLOCK_NOTE_BLOCK_PLING;
            default: return Sound.BLOCK_NOTE_BLOCK_HARP;
        }
    }
}
