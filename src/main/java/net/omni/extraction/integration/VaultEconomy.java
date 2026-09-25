package net.omni.extraction.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.managers.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class VaultEconomy implements Economy {

    private static final String CURRENCY_SINGULAR = "token";
    private static final String CURRENCY_PLURAL = "tokens";

    private final ExtractionPlugin plugin;
    private final TokenManager tokenManager;

    public VaultEconomy(ExtractionPlugin plugin, TokenManager tokenManager) {
        this.plugin = plugin;
        this.tokenManager = tokenManager;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "Extraction";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return String.valueOf((long) Math.floor(amount));
    }

    @Override
    public String currencyNamePlural() {
        return CURRENCY_PLURAL;
    }

    @Override
    public String currencyNameSingular() {
        return CURRENCY_SINGULAR;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return resolve(playerName) != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        UUID uuid = resolve(playerName);
        return uuid == null ? 0 : tokenManager.getTokens(uuid);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return player == null ? 0 : tokenManager.getTokens(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        if (amount <= 0)
            return true;

        UUID uuid = resolve(playerName);
        return uuid != null && tokenManager.hasTokens(uuid, toTokens(amount));
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (amount <= 0)
            return true;

        return player != null && tokenManager.hasTokens(player.getUniqueId(), toTokens(amount));
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        if (amount < 0)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative funds");

        UUID uuid = resolve(playerName);
        if (uuid == null)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");

        int tokens = tokenManager.getTokens(uuid);
        int charge = toTokens(amount);

        if (tokens < charge)
            return new EconomyResponse(amount, tokens, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");

        tokenManager.removeTokens(uuid, charge);
        int balance = tokenManager.getTokens(uuid);
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");

        return withdrawPlayer(player.getUniqueId().toString(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        if (amount < 0)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative funds");

        UUID uuid = resolve(playerName);
        if (uuid == null)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");

        int give = toTokens(amount);
        tokenManager.addTokens(uuid, give);
        int balance = tokenManager.getTokens(uuid);
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null)
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");

        return depositPlayer(player.getUniqueId().toString(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    private int toTokens(double amount) {
        return (int) Math.floor(amount);
    }

    private UUID resolve(String playerName) {
        if (playerName == null || playerName.isBlank())
            return null;

        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    @Override
    public String toString() {
        return "[Extraction VaultEconomy: " + CURRENCY_PLURAL + ", " + getName().toLowerCase(Locale.ROOT) + "]";
    }
}