package com.nevvsiita.backpack;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final BackPackPlugin plugin;
    private Economy econ = null;

    public VaultHook(BackPackPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicializa la economía de Vault si está presente.
     * @return true si se inicializó correctamente, false en caso contrario.
     */
    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * Verifica si la economía de Vault está activa.
     */
    public boolean hasEconomy() {
        return econ != null;
    }

    /**
     * Obtiene el balance de dinero de un jugador.
     */
    public double getBalance(OfflinePlayer player) {
        if (!hasEconomy()) {
            return 0.0;
        }
        return econ.getBalance(player);
    }

    /**
     * Comprueba si el jugador tiene suficiente dinero.
     */
    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (!hasEconomy()) {
            return true; // Si no hay economía, permitimos todo por defecto
        }
        return econ.has(player, amount);
    }

    /**
     * Retira dinero de la cuenta del jugador.
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!hasEconomy()) {
            return true;
        }
        return econ.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Deposita dinero en la cuenta del jugador.
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!hasEconomy()) {
            return true;
        }
        return econ.depositPlayer(player, amount).transactionSuccess();
    }
}
