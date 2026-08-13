package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The delivery box GUI: shows purchased items waiting for pickup (top rows)
 * above the player's real inventory (bottom rows, shown natively by Bukkit).
 *
 * <p>Layout (54 slots):</p>
 * <ul>
 *   <li>Slots 0–35 (4 rows): delivery box items — click to take one</li>
 *   <li>Slots 36–44 (1 row): gray separator</li>
 *   <li>Slot 45: "Take All" button (moves everything to inventory)</li>
 *   <li>Slot 49: info button (shows count)</li>
 *   <li>Slot 53: close button</li>
 *   <li>Below slot 53: the player's real inventory (visible, read-only)</li>
 * </ul>
 *
 * <p>The player cannot move items from their inventory back into the delivery
 * box. If the inventory is full, items cannot be taken (a message is shown).</p>
 */
public class DeliveryBoxGui extends AGui {

    /** Maximum items shown at once (4 rows × 9). */
    public static final int MAX_DISPLAY = 36;
    public static final int SEPARATOR_START = 36;
    public static final int TAKE_ALL_SLOT = 45;
    public static final int INFO_SLOT = 49;
    public static final int CLOSE_SLOT = 53;

    public DeliveryBoxGui(GameBox plugin) {
        super(plugin, Utility.color(plugin.lang("gui.deliveryBoxTitle")), 54);
    }

    @Override
    protected String getDynamicTitle() {
        return Utility.color(plugin.lang("gui.deliveryBoxTitle"));
    }

    @Override
    public boolean allowPlayerInventoryInteraction() {
        // Block all interaction with the player's own inventory — items can
        // only flow one way (delivery box → inventory), never back.
        return false;
    }

    @Override
    public void build(Player player) {
        clear();
        List<ItemStack> items = plugin.getDeliveryBoxManager().getItems(player);

        // --- Delivery items (slots 0-35) ---
        int shown = 0;
        for (int i = 0; i < Math.min(items.size(), MAX_DISPLAY); i++) {
            ItemStack item = items.get(i);
            if (item == null || item.getType().isAir()) continue;
            final int index = i;
            // Clone for display; add a "click to take" lore hint
            ItemStack display = item.clone();
            List<String> lore = display.getItemMeta() != null && display.getItemMeta().hasLore()
                    ? new ArrayList<>(display.getItemMeta().getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(plugin.lang("gui.deliveryClickToTake"));
            display = Utility.createItem(display.getType(),
                    display.getItemMeta() != null && display.getItemMeta().hasDisplayName()
                            ? display.getItemMeta().getDisplayName()
                            : "&f" + display.getType().name(),
                    lore, display.getAmount());
            setButton(shown, Button.action("item_" + i, display, p -> takeItem(p, index)));
            shown++;
        }

        // --- Separator row (slots 36-44) ---
        for (int i = SEPARATOR_START; i < SEPARATOR_START + 9; i++) {
            setButton(i, Button.display(
                    Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null)));
        }

        // --- Take All button (slot 45) ---
        ItemStack takeAllBtn = Utility.createItem(Material.HOPPER,
                plugin.lang("gui.deliveryTakeAll"),
                Utility.list(plugin.lang("gui.deliveryTakeAllLore")));
        setButton(TAKE_ALL_SLOT, Button.action("take_all", takeAllBtn, this::takeAll));

        // --- Info button (slot 49) ---
        int total = items.size();
        int hidden = Math.max(0, total - MAX_DISPLAY);
        List<String> infoLore = new ArrayList<>();
        infoLore.add(plugin.lang("gui.deliveryInfoLore").replace("%count%", String.valueOf(total)));
        if (hidden > 0) {
            infoLore.add(plugin.lang("gui.deliveryMoreHidden").replace("%count%", String.valueOf(hidden)));
        }
        ItemStack infoBtn = Utility.createItem(Material.BOOK,
                plugin.lang("gui.deliveryInfo"), infoLore);
        setButton(INFO_SLOT, Button.display(infoBtn));

        // --- Close button (slot 53) ---
        setButton(CLOSE_SLOT, Button.action("close",
                Utility.createItem(Material.BARRIER, plugin.lang("gui.closeButton"), null),
                Player::closeInventory));

        // Fill remaining control-row slots with separator
        for (int i = SEPARATOR_START + 9; i < 54; i++) {
            if (getButton(i) == null) {
                setButton(i, Button.display(
                        Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null)));
            }
        }
    }

    /**
     * Take a single item from the delivery box and add it to the player's
     * inventory. If the inventory is full, the item stays in the box and a
     * message is sent.
     */
    private void takeItem(Player player, int index) {
        List<ItemStack> items = plugin.getDeliveryBoxManager().getItems(player);
        if (index < 0 || index >= items.size()) {
            build(player);
            return;
        }
        ItemStack item = items.get(index);
        if (item == null || item.getType().isAir()) {
            plugin.getDeliveryBoxManager().removeItem(player, index);
            build(player);
            return;
        }

        // Try to add to the player's real inventory
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        if (overflow.isEmpty()) {
            // Success — remove from delivery box
            plugin.getDeliveryBoxManager().removeItem(player, index);
            String name = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();
            player.sendMessage(plugin.langPrefixed("messages.deliveryItemTaken")
                    .replace("%item%", name));
            plugin.getLogger().info("DeliveryBox: " + player.getName() + " took "
                    + item.getType() + " x" + item.getAmount());
        } else {
            // Inventory full
            player.sendMessage(plugin.langPrefixed("messages.deliveryInventoryFull"));
        }
        build(player);
    }

    /**
     * Take all items from the delivery box. Items that don't fit in the
     * inventory remain in the box for later retrieval.
     */
    private void takeAll(Player player) {
        List<ItemStack> items = plugin.getDeliveryBoxManager().getItems(player);
        if (items.isEmpty()) {
            player.sendMessage(plugin.langPrefixed("messages.deliveryEmpty"));
            return;
        }

        int delivered = 0;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            if (overflow.isEmpty()) {
                delivered++;
            } else {
                remaining.addAll(overflow.values());
            }
        }

        // Update the delivery box with whatever didn't fit
        plugin.getDeliveryBoxManager().setItems(player, remaining);

        if (delivered > 0) {
            player.sendMessage(plugin.langPrefixed("messages.deliveryTakenAll")
                    .replace("%count%", String.valueOf(delivered)));
            plugin.getLogger().info("DeliveryBox: " + player.getName() + " took " + delivered
                    + " item(s) via Take All; " + remaining.size() + " remaining.");
        }
        if (!remaining.isEmpty()) {
            player.sendMessage(plugin.langPrefixed("messages.deliveryInventoryFull"));
        }
        build(player);
    }
}
