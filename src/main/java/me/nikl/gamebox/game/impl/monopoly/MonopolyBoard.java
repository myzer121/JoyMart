package me.nikl.gamebox.game.impl.monopoly;

/**
 * Static layout of the 26-space Monopoly board. The board is rendered as the
 * perimeter of a 6×9 (54-slot) Bukkit inventory, traversed clockwise starting
 * from GO at the bottom-left corner.
 *
 * <h3>Space types</h3>
 * <ul>
 *   <li>{@link #TYPE_CORNER} — GO (0), Jail (8), Free Parking (13), Go To Jail (19)</li>
 *   <li>{@link #TYPE_CHANCE} — positions 4, 15, 21</li>
 *   <li>{@link #TYPE_CHEST} — positions 2, 11</li>
 *   <li>{@link #TYPE_PROPERTY} — the remaining 17 positions</li>
 * </ul>
 *
 * <p>The 17 property positions are mapped sequentially to indices 0–16 of the
 * {@code properties[]} array in {@link GameMonopoly}.</p>
 */
public final class MonopolyBoard {

    private MonopolyBoard() {}

    public static final int SIZE = 26;

    public static final int TYPE_GO = 0;
    public static final int TYPE_JAIL = 1;
    public static final int TYPE_FREE_PARKING = 2;
    public static final int TYPE_GO_TO_JAIL = 3;
    public static final int TYPE_CHANCE = 4;
    public static final int TYPE_CHEST = 5;
    public static final int TYPE_PROPERTY = 6;

    /** Inventory slot for each board position (0..25), clockwise from GO. */
    public static final int[] TRACK_SLOTS = {
            45, 46, 47, 48, 49, 50, 51, 52, 53, // bottom row L→R  (0-8)
            44, 35, 26, 17,                      // right col ↑      (9-12)
            8, 7, 6, 5, 4, 3, 2, 1,              // top row R→L      (13-20)
            0,                                    // top-left corner  (21)
            9, 18, 27, 36                         // left col ↓       (22-25)
    };

    /** Type of each board position (indexed 0..25). */
    public static final int[] SPACE_TYPES = {
            TYPE_GO,        // 0
            TYPE_PROPERTY,  // 1
            TYPE_CHEST,     // 2
            TYPE_PROPERTY,  // 3
            TYPE_CHANCE,    // 4
            TYPE_PROPERTY,  // 5
            TYPE_PROPERTY,  // 6
            TYPE_PROPERTY,  // 7
            TYPE_JAIL,      // 8
            TYPE_PROPERTY,  // 9
            TYPE_PROPERTY,  // 10
            TYPE_CHEST,     // 11
            TYPE_PROPERTY,  // 12
            TYPE_FREE_PARKING, // 13
            TYPE_PROPERTY,  // 14
            TYPE_CHANCE,    // 15
            TYPE_PROPERTY,  // 16
            TYPE_PROPERTY,  // 17
            TYPE_PROPERTY,  // 18
            TYPE_GO_TO_JAIL,// 19
            TYPE_PROPERTY,  // 20
            TYPE_CHANCE,    // 21
            TYPE_PROPERTY,  // 22
            TYPE_PROPERTY,  // 23
            TYPE_PROPERTY,  // 24
            TYPE_PROPERTY   // 25
    };

    /** Mapping from property board-position → sequential index (0..16). */
    private static final int[] PROPERTY_INDICES = buildPropertyIndices();

    private static int[] buildPropertyIndices() {
        int[] map = new int[SIZE];
        java.util.Arrays.fill(map, -1);
        int idx = 0;
        for (int pos = 0; pos < SIZE; pos++) {
            if (SPACE_TYPES[pos] == TYPE_PROPERTY) {
                map[pos] = idx++;
            }
        }
        return map;
    }

    /** Returns the sequential property index (0..16) for a board position, or -1. */
    public static int propertyIndex(int spaceIndex) {
        if (spaceIndex < 0 || spaceIndex >= SIZE) return -1;
        return PROPERTY_INDICES[spaceIndex];
    }

    /** Returns the inventory slot for a board position. */
    public static int slotFor(int spaceIndex) {
        return TRACK_SLOTS[spaceIndex % SIZE];
    }

    /** Returns the board position for an inventory slot, or -1 if not on the track. */
    public static int spaceForSlot(int slot) {
        for (int i = 0; i < TRACK_SLOTS.length; i++) {
            if (TRACK_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    public static int typeOf(int spaceIndex) {
        return SPACE_TYPES[spaceIndex % SIZE];
    }

    public static boolean isCorner(int spaceIndex) {
        int t = typeOf(spaceIndex);
        return t == TYPE_GO || t == TYPE_JAIL || t == TYPE_FREE_PARKING || t == TYPE_GO_TO_JAIL;
    }

    public static boolean isProperty(int spaceIndex) {
        return typeOf(spaceIndex) == TYPE_PROPERTY;
    }
}
