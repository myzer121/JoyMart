package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quantity-selector GUI shown when a player clicks a shop item that has
 * {@code giveItem: true}. Lets the player pick how many units to buy with
 * +/- buttons (×1, ×10, ×64), shows the running total cost, and on confirm
 * deducts the tokens and stacks all purchased items into the delivery box.
 *
 * <p>Layout (27 slots, 3 rows):</p>
 * <pre>
 *  Row 0:  [       ] [item preview] [       ]
 *  Row 1:  [-10][-1][ qty: N ][+1][+10] [max]
 *  Row 2:  [back]  [total: T]  [confirm]
 * </pre>
 */
public class ShopQuantityGui extends AGui {

    private final Shop shop;
    private final String categoryKey;
    private final String itemKey;
    private final int maxStackSize;   // material max stack (usually 64)
    private final int perUnitCost;     // token cost per unit

    /** Per-player chosen quantity (1..maxStackSize). */
    private final Map<UUID, Integer> playerQty = new HashMap<>();

    // Slot layout
    private static final int SLOT_PREVIEW = 4;
    private static final int SLOT_M10 = 9;
    private static final int SLOT_M1 = 10;
    private static final int SLOT_QTY = 11;
    private static final int SLOT_P1 = 12;
    private static final int SLOT_P10 = 13;
    private static final int SLOT_MAX = 14;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_TOTAL = 22;
    private static final int SLOT_CONFIRM = 26;

    public ShopQuantityGui(GameBox plugin, Shop shop,
                           String categoryKey, String itemKey,
                           String itemDisplayName, Material material, int perUnitCost) {
        super(plugin, Utility.color(plugin.lang("gui.shopQtyTitle").replace("%item%", itemDisplayName)), 27);
        this.shop = shop;
        this.categoryKey = categoryKey;
        this.itemKey = itemKey;
        this.perUnitCost = perUnitCost;
        // Material max stack size caps how many the player can buy at once.
        this.maxStackSize = material.getMaxStackSize();
    }

    @Override
    public void build(Player player) {
        clear();

        ConfigurationSection item = plugin.getShopConfig()
                .getConfigurationSection("shop.categories." + categoryKey + ".items." + itemKey);
        if (item == null) {
            setButton(13, Button.display(Utility.createItem(Material.BARRIER, plugin.lang("gui.shopQtyItemNotFound"), null)));
            return;
        }

        int qtyRaw = playerQty.getOrDefault(player.getUniqueId(), 1);
        if (qtyRaw < 1) qtyRaw = 1;
        if (qtyRaw > maxStackSize) qtyRaw = maxStackSize;
        final int qty = qtyRaw;

        // --- Filler ---
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getSize(); i++) setItem(i, filler);

        // --- Item preview (slot 4) ---
        Material mat = Utility.matchMaterial(item.getString("material", "PAPER"), Material.PAPER);
        List<String> previewLore = new ArrayList<>(item.getStringList("lore"));
        previewLore.add("");
        previewLore.add(plugin.lang("gui.shopQtyUnitPrice").replace("%cost%", String.valueOf(perUnitCost)));
        previewLore.add(plugin.lang("gui.shopQtyCurrentQty").replace("%qty%", String.valueOf(qty)));
        previewLore.add(plugin.lang("gui.shopQtyTotalPrice").replace("%cost%", String.valueOf(perUnitCost * qty)));
        ItemStack preview = Utility.createItem(mat, item.getString("name", itemKey), previewLore, qty);
        setButton(SLOT_PREVIEW, Button.display(preview));

        // --- Quantity adjustment row ---
        // -10
        setButton(SLOT_M10, Button.action("m10",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-10", null),
                p -> adjust(p, -10)));
        // -1
        setButton(SLOT_M1, Button.action("m1",
                Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                p -> adjust(p, -1)));
        // Current qty display
        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        int balance = gb != null ? gb.getTokens() : 0;
        int totalCost = perUnitCost * qty;
        List<String> qtyLore = new ArrayList<>();
        qtyLore.add(plugin.lang("gui.shopQtyQtyLabel").replace("%qty%", String.valueOf(qty)).replace("%max%", String.valueOf(maxStackSize)));
        qtyLore.add(plugin.lang("gui.shopQtyTotalCost").replace("%cost%", String.valueOf(totalCost)));
        qtyLore.add(plugin.lang("gui.shopQtyBalance").replace("%balance%", String.valueOf(balance)));
        if (totalCost > balance) {
            qtyLore.add(plugin.lang("gui.shopQtyInsufficientTokens"));
        }
        setButton(SLOT_QTY, Button.display(
                Utility.createItem(Material.GOLD_BLOCK, plugin.lang("gui.shopQtyQtyDisplay").replace("%qty%", String.valueOf(qty)), qtyLore)));
        // +1
        setButton(SLOT_P1, Button.action("p1",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                p -> adjust(p, +1)));
        // +10
        setButton(SLOT_P10, Button.action("p10",
                Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10", null),
                p -> adjust(p, +10)));
        // Max (set to max stack, or max affordable, whichever is smaller)
        int maxAffordable = perUnitCost > 0 ? (balance / perUnitCost) : maxStackSize;
        int maxBuy = Math.min(maxStackSize, Math.max(1, maxAffordable));
        setButton(SLOT_MAX, Button.action("max",
                Utility.createItem(Material.DIAMOND_BLOCK, plugin.lang("gui.shopQtyMax"), Utility.list(plugin.lang("gui.shopQtyMaxLore").replace("%max%", String.valueOf(maxBuy)))),
                p -> { playerQty.put(p.getUniqueId(), maxBuy); build(p); }));

        // --- Bottom row ---
        // Back
        setButton(SLOT_BACK, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> {
                    playerQty.remove(p.getUniqueId());
                    shop.openCategory(p, categoryKey);
                }));

        // Total cost display
        setButton(SLOT_TOTAL, Button.display(Utility.createItem(Material.EMERALD,
                plugin.lang("gui.shopQtyTotalLabel"), Utility.list(
                        plugin.lang("gui.shopQtyCurrentQty").replace("%qty%", String.valueOf(qty)),
                        plugin.lang("gui.shopQtyUnitPrice").replace("%cost%", String.valueOf(perUnitCost)),
                        plugin.lang("gui.shopQtyTotalCost").replace("%cost%", String.valueOf(totalCost)),
                        plugin.lang("gui.shopQtyBalance").replace("%balance%", String.valueOf(balance))))));

        // Confirm
        boolean canAfford = totalCost <= balance && qty > 0;
        Material confirmMat = canAfford ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
        String confirmName = canAfford ? plugin.lang("gui.shopQtyConfirmBuy") : plugin.lang("gui.shopQtyInsufficientBtn");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(plugin.lang("gui.shopQtyConfirmLore1").replace("%qty%", String.valueOf(qty)));
        confirmLore.add(plugin.lang("gui.shopQtyConfirmLore2").replace("%cost%", String.valueOf(totalCost)));
        if (canAfford) confirmLore.add(plugin.lang("gui.shopQtyConfirmLore3"));
        setButton(SLOT_CONFIRM, Button.action("confirm",
                Utility.createItem(confirmMat, confirmName, confirmLore),
                p -> confirm(p, qty)));
    }

    /** Adjust the quantity by delta, clamped to [1, maxStackSize]. */
    private void adjust(Player player, int delta) {
        int q = playerQty.getOrDefault(player.getUniqueId(), 1);
        q = Math.max(1, Math.min(maxStackSize, q + delta));
        playerQty.put(player.getUniqueId(), q);
        build(player);
    }

    /** Confirm the purchase: deduct tokens, stack items into delivery box. */
    private void confirm(Player player, int qty) {
        if (qty < 1) return;
        int totalCost = perUnitCost * qty;
        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        if (gb == null) {
            player.sendMessage(plugin.langPrefixed("messages.error"));
            return;
        }
        if (gb.getTokens() < totalCost) {
            player.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                    .replace("%tokens%", String.valueOf(totalCost))
                    .replace("%balance%", String.valueOf(gb.getTokens())));
            return;
        }

        ConfigurationSection item = plugin.getShopConfig()
                .getConfigurationSection("shop.categories." + categoryKey + ".items." + itemKey);
        if (item == null) return;

        // Check buy permission
        String buyPerm = item.getString("buyPermission", item.getString("permission", ""));
        if (!buyPerm.isEmpty() && !player.hasPermission(buyPerm)) {
            player.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }

        // Deduct tokens
        if (totalCost > 0 && !gb.removeTokens(totalCost)) {
            player.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                    .replace("%tokens%", String.valueOf(totalCost))
                    .replace("%balance%", String.valueOf(gb.getTokens())));
            return;
        }

        String displayName = item.getString("name", itemKey);

        // Give the items — stacked into the delivery box.
        if (item.getBoolean("giveItem", false)) {
            String matName = item.getString("material", "");
            if (matName == null || matName.isEmpty()) {
                plugin.getLogger().warning("Shop item " + categoryKey + ":" + itemKey
                        + " has giveItem=true but no material set; skipping item give.");
            } else {
                Material mat = Utility.matchMaterial(matName, null);
                if (mat == null || mat.isAir()) {
                    plugin.getLogger().warning("Shop item " + categoryKey + ":" + itemKey
                            + " has invalid material '" + matName + "'; skipping item give.");
                } else {
                    // Add items, splitting into max-stack-size chunks if the
                    // purchased quantity exceeds the material's stack limit
                    // (Bukkit's ItemStack constructor throws on amount > max).
                    int remaining = qty;
                    while (remaining > 0) {
                        int chunk = Math.min(remaining, mat.getMaxStackSize());
                        ItemStack stack = new ItemStack(mat, chunk);
                        plugin.getDeliveryBoxManager().addItem(player, stack);
                        remaining -= chunk;
                    }
                }
            }
        }

        // Run commands (once per purchase, not per unit, to avoid spam)
        for (String cmd : item.getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName())
                            .replace("%amount%", String.valueOf(qty)));
        }

        player.sendMessage(plugin.langPrefixed("messages.bought")
                .replace("%item%", displayName + " x" + qty)
                .replace("%cost%", String.valueOf(totalCost)));
        if (item.getBoolean("giveItem", false)) {
            player.sendMessage(plugin.langPrefixed("messages.deliveryStored")
                    .replace("%item%", displayName + " x" + qty));
        }

        playerQty.remove(player.getUniqueId());
        player.closeInventory();
    }

    /** Quantity is tracked per player so concurrent viewers don't clash. */
    public void setQuantity(UUID uuid, int qty) {
        playerQty.put(uuid, Math.max(1, Math.min(maxStackSize, qty)));
    }
}
