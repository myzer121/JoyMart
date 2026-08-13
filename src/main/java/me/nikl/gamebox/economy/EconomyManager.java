package me.nikl.gamebox.economy;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.data.GBPlayer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Central place for money (Vault) operations.
 * Token operations live on {@link GBPlayer}; this class handles Vault economy.
 */
public class EconomyManager {

    private final GameBox plugin;
    private Economy vaultEconomy;

    public EconomyManager(GameBox plugin) {
        this.plugin = plugin;
        hookVault();
    }

    public boolean hookVault() {
        if (!GameBoxSettings.vaultEnabled) return false;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found; economy rewards disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            vaultEconomy = rsp.getProvider();
            plugin.getLogger().info("Hooked into Vault economy: " + vaultEconomy.getName());
            return true;
        }
        plugin.getLogger().warning("Vault present but no economy provider found.");
        return false;
    }

    public boolean isVaultEnabled() {
        return vaultEconomy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (vaultEconomy == null) return 0;
        return vaultEconomy.getBalance(player);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (vaultEconomy == null || amount <= 0) return true;
        return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (vaultEconomy == null || amount <= 0) return true;
        return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (vaultEconomy == null) return true;
        return vaultEconomy.has(player, amount);
    }

    public String format(double amount) {
        if (vaultEconomy == null) return String.valueOf(amount);
        return vaultEconomy.format(amount);
    }
}
