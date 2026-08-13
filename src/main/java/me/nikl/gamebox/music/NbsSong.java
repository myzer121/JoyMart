package me.nikl.gamebox.music;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed representation of a Note Block Studio ({@code .nbs}) song file.
 *
 * <p>This is the low-level NBS reader for the GameBox music system. It supports
 * both the original (pre-OpenNBS) format (with or without the {@code "PIANO"}
 * magic header) and the OpenNBS extended format (version 1-5). Only fields
 * required for playback are retained: tempo, total length, and a per-tick note
 * map (notes at the same tick across different layers are merged into a single
 * list).</p>
 *
 * <h3>Format reference</h3>
 * <ul>
 *   <li>Old format: {@code "PIANO"} magic (5 bytes, optional) + short song
 *       length + short layer count + strings + short tempo + bytes/ints + tick
 *       data with notes (instrument, key only).</li>
 *   <li>New format: leading short 0 + byte version + byte vanilla instrument
 *       count + short song length + short layer count + strings + short tempo
 *       + bytes/ints + (version &gt;= 4) loop fields + custom instruments +
 *       tick data with notes (instrument, key, velocity, panning, pitch).</li>
 * </ul>
 *
 * <p>Tick / note data is delta-encoded: each tick entry starts with a short
 * giving how many ticks to jump forward from the previous one (0 = end of
 * song); within a tick, each layer entry starts with a short giving how many
 * layers to jump forward (0 = end of tick).</p>
 */
public class NbsSong {

    /** Song title (may be empty if the file has none set). */
    public String name = "";
    /** Original author (may be empty). */
    public String author = "";
    /** Transcription author (may be empty). */
    public String originalAuthor = "";
    /** Song description (may be empty). */
    public String description = "";
    /** Total song length in NBS ticks. */
    public int lengthTicks = 0;
    /** Tempo: NBS ticks per second × 100 (so 1000 = 10 tps, the most common value). */
    public int tempo = 1000;
    /** OpenNBS format version of the parsed file (0 for old format). */
    public int version = 0;

    /**
     * Notes grouped by NBS tick index. Multiple notes at the same tick
     * (i.e. across different layers) are stored in the order they appear in
     * the file.
     */
    public final Map<Integer, List<Note>> notesByTick = new HashMap<>();

    /** A single note inside the song. */
    public static class Note {
        /** NBS instrument id (0 = harp, 1 = bass, 2 = bass drum, ... see OpenNBS spec). */
        public final int instrument;
        /** NBS key (0-87). 33-57 maps to Minecraft's 2-octave note range. */
        public final int key;
        /** Note velocity (0-100). Old-format files default to 100. */
        public final int velocity;
        /** Stereo panning (0-200, 100 = center). Old-format files default to 100. */
        public final int panning;
        /** Fine pitch shift in semitones × 100 (OpenNBS v1+). Old format defaults to 0. */
        public final int pitch;

        public Note(int instrument, int key, int velocity, int panning, int pitch) {
            this.instrument = instrument;
            this.key = key;
            this.velocity = velocity;
            this.panning = panning;
            this.pitch = pitch;
        }
    }

    /** @return notes scheduled at the given tick, or {@code null} if none. */
    public List<Note> notesAt(int tick) {
        return notesByTick.get(tick);
    }

    /**
     * Parse an NBS file from its raw bytes. The buffer is fully consumed; no
     * I/O is performed beyond the initial read. Returns a populated
     * {@link NbsSong} even if the file is truncated — already-parsed ticks
     * remain available.
     *
     * @param data the raw file contents
     * @return parsed song (never {@code null})
     */
    public static NbsSong parse(byte[] data) {
        NbsSong song = new NbsSong();
        if (data == null || data.length < 2) return song;
        NbsReader r = new NbsReader(data);

        // Detect old "PIANO" magic.
        boolean oldMagic = data.length >= 5
                && data[0] == 'P' && data[1] == 'I' && data[2] == 'A'
                && data[3] == 'N' && data[4] == 'O';
        if (oldMagic) r.pos = 5;

        // Peek the first short. New format starts with 0 (placeholder song
        // length); old format starts with the real song length.
        int firstShort = r.peekShort();
        int version = 0;
        // Declared outside the try-block so it's visible when finalizing
        // lengthTicks after a (possibly partial) parse.
        int tick = -1;

        try {
            if (firstShort == 0 && !oldMagic) {
                // ---- New OpenNBS format ----
                r.readShort();                  // 0 placeholder
                version = r.readByte();          // format version
                song.version = version;
                r.readByte();                    // vanilla instrument count
                song.lengthTicks = r.readShort();
                r.readShort();                   // layer count
                song.name = r.readString();
                song.author = r.readString();
                song.originalAuthor = r.readString();
                song.description = r.readString();
                song.tempo = r.readShort();
                if (song.tempo < 1) song.tempo = 1000;
                r.readByte();                    // auto-saving
                r.readByte();                    // auto-saving duration
                r.readByte();                    // time signature
                r.readInt();                     // minutes spent
                r.readInt();                     // left clicks
                r.readInt();                     // right clicks
                r.readInt();                     // note blocks added
                r.readInt();                     // note blocks removed
                r.readString();                  // MIDI / schematic file name
                if (version >= 4) {
                    r.readByte();                // loop mode
                    r.readByte();                // max loop count
                    r.readShort();               // min start delay
                }
                // Custom instruments (we don't use them — harp fallback).
                int customCount = r.readShort();
                for (int i = 0; i < customCount && r.has(1); i++) {
                    r.readString();              // name
                    r.readString();              // file
                    r.readByte();                // pitch
                }
            } else {
                // ---- Old format (no header byte) ----
                song.lengthTicks = r.readShort();
                r.readShort();                   // layer count
                song.name = r.readString();
                song.author = r.readString();
                song.originalAuthor = r.readString();
                song.description = r.readString();
                song.tempo = r.readShort();
                if (song.tempo < 1) song.tempo = 1000;
                r.readByte();                    // auto-saving
                r.readByte();                    // auto-saving duration
                r.readByte();                    // time signature
                r.readInt();                     // minutes spent
                r.readInt();                     // left clicks
                r.readInt();                     // right clicks
                r.readInt();                     // note blocks added
                r.readInt();                     // note blocks removed
                r.readString();                  // MIDI / schematic file name
            }

            // ---- Tick / jump data (identical for both formats) ----
            while (r.has(2)) {
                int jumpTicks = r.readShort();
                if (jumpTicks == 0) break;       // end of song
                tick += jumpTicks;
                int layer = -1;
                while (r.has(2)) {
                    int jumpLayers = r.readShort();
                    if (jumpLayers == 0) break;  // end of this tick
                    layer += jumpLayers;
                    if (!r.has(2)) break;
                    int instrument = r.readByte();
                    int key = r.readByte();
                    int velocity = 100;
                    int panning = 100;
                    int pitch = 0;
                    if (version >= 1 && r.has(4)) {
                        velocity = r.readByte();
                        panning = r.readByte();
                        pitch = r.readShort();
                    }
                    song.notesByTick
                            .computeIfAbsent(tick, k -> new ArrayList<>())
                            .add(new Note(instrument, key, velocity, panning, pitch));
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
            // Truncated file — keep what we have so far.
        }

        if (tick > song.lengthTicks) song.lengthTicks = tick;
        return song;
    }

    /** Tiny little-endian reader with bounds checking. */
    private static class NbsReader {
        private final byte[] data;
        private int pos = 0;

        NbsReader(byte[] data) { this.data = data; }

        boolean has(int bytes) { return pos + bytes <= data.length; }

        int readByte() {
            if (!has(1)) throw new IndexOutOfBoundsException("nbs byte");
            return data[pos++] & 0xff;
        }

        int peekShort() {
            if (!has(2)) return 0;
            return (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8);
        }

        int readShort() {
            if (!has(2)) throw new IndexOutOfBoundsException("nbs short");
            int v = (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8);
            pos += 2;
            // Returned as unsigned (0..65535). The tick/layer jump sentinels
            // use 0 to signal "end of song" / "end of tick".
            return v;
        }

        int readInt() {
            if (!has(4)) throw new IndexOutOfBoundsException("nbs int");
            int v = (data[pos] & 0xff)
                    | ((data[pos + 1] & 0xff) << 8)
                    | ((data[pos + 2] & 0xff) << 16)
                    | ((data[pos + 3] & 0xff) << 24);
            pos += 4;
            return v;
        }

        String readString() {
            int len = readInt();
            if (len <= 0 || !has(len)) return "";
            String s = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }
    }
}
