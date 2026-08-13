package me.nikl.gamebox.game.impl.monopoly;

/**
 * A single buyable property on the Monopoly board. Holds the display name,
 * purchase price, and rent amount. Ownership (which player index owns it, or
 * -1 for unowned) is tracked here so the session can look it up in O(1).
 */
public class MonopolyProperty {

    private final int id;
    private final String name;
    private final int price;
    private final int rent;
    private int owner = -1; // -1 = unowned, otherwise player index (0-based)

    public MonopolyProperty(int id, String name, int price, int rent) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.rent = rent;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public int getRent() { return rent; }
    public int getOwner() { return owner; }
    public void setOwner(int owner) { this.owner = owner; }
    public boolean isOwned() { return owner >= 0; }
}
