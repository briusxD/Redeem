package com.example;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class RedeemPlugin extends JavaPlugin {

    private FileConfiguration config;
    private FileConfiguration messages;
    private HashMap<String, Boolean> usedCodes = new HashMap<>();
    private PlayerPointsAPI playerPointsAPI;

    @Override
    public void onEnable() {
        getLogger().info("RedeemPlugin has been enabled.");
        saveDefaultConfig();
        config = getConfig();

        // Load messages file based on locale
        loadMessagesFile();

        // Load used codes
        loadUsedCodes();

        // Initialize PlayerPoints API if available
        if (getServer().getPluginManager().getPlugin("PlayerPoints") != null) {
            playerPointsAPI = PlayerPoints.getInstance().getAPI();
        }

        // Register commands
        getCommand("redeem").setExecutor(new RedeemCommandExecutor());
        getCommand("redeemhelp").setExecutor(new RedeemCommandExecutor());

        // Check and add default codes
        checkAndAddDefaultCodes();
    }

    @Override
    public void onDisable() {
        getLogger().info("RedeemPlugin has been disabled.");
    }

    void loadMessagesFile() {
        try {
            String locale = config.getString("locale", "tr");
            File messagesFile = new File(getDataFolder(), "messages/messages_" + locale + ".yml");

            if (!messagesFile.exists()) {
                messagesFile.getParentFile().mkdirs();
                saveResource("messages/messages_" + locale + ".yml", false);
            }

            messages = YamlConfiguration.loadConfiguration(messagesFile);
        } catch (Exception e) {
            getLogger().severe("Error loading messages file: " + e.getMessage());
        }
    }

    private void loadUsedCodes() {
        try {
            List<String> usedCodesList = config.getStringList("used_codes");
            for (String code : usedCodesList) {
                usedCodes.put(code, true);
            }
        } catch (Exception e) {
            getLogger().severe("Error loading used codes: " + e.getMessage());
        }
    }

    private void saveUsedCode(String code) {
        try {
            usedCodes.put(code, true);
            List<String> usedCodesList = config.getStringList("used_codes");
            usedCodesList.add(code);
            config.set("used_codes", usedCodesList);
            saveConfig();
        } catch (Exception e) {
            getLogger().severe("Error saving used code: " + e.getMessage());
        }
    }

    private void checkAndAddDefaultCodes() {
        try {
            // Add default codes from config if they don't already exist
            FileConfiguration config = getConfig();
            Set<String> currentCodes = config.getConfigurationSection("codes").getKeys(false);

            // Define the default codes structure
            HashMap<String, List<String>> defaultCodes = new HashMap<>();
            defaultCodes.put("1250", List.of("ABCDEFGHI", "JKLMNOPQR"));
            defaultCodes.put("750", List.of("STUVWXYZA", "XYZ123456"));
            defaultCodes.put("500", List.of("ABC987654"));
            defaultCodes.put("250", List.of("CODE001"));
            defaultCodes.put("100", List.of("CODE003"));

            // Iterate over default codes and add them if they don't exist
            for (String points : defaultCodes.keySet()) {
                List<String> codes = defaultCodes.get(points);
                for (String code : codes) {
                    if (!currentCodes.contains(code)) {
                        config.set("codes." + code, Integer.parseInt(points));
                    }
                }
            }

            saveConfig();
        } catch (Exception e) {
            getLogger().severe("Error checking and adding default codes: " + e.getMessage());
        }
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    private class RedeemCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            try {
                if (command.getName().equalsIgnoreCase("redeem")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                        return true;
                    }

                    Player player = (Player) sender;
                    if (!player.hasPermission("redeem.use") && !player.isOp()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("errors.no_permission")));
                        return true;
                    }

                    if (args.length != 1) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("errors.incorrect_usage")));
                        return true;
                    }

                    String code = args[0];
                    redeemCode(player, code);
                    return true;
                }

                if (command.getName().equalsIgnoreCase("redeemhelp")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("help.header")));
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("help.usage")));
                    return true;
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "An error occurred while executing the command.");
                getLogger().severe("Error executing command: " + e.getMessage());
                e.printStackTrace();
            }

            return false;
        }

        private void redeemCode(Player player, String code) {
            try {
                if (!config.contains("codes." + code)) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("errors.invalid_code")));
                    return;
                }

                if (usedCodes.containsKey(code)) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("errors.code_used")));
                    return;
                }

                int points = config.getInt("codes." + code);

                // Grant PlayerPoints to the player
                if (playerPointsAPI != null) {
                    playerPointsAPI.give(player.getUniqueId(), points);
                } else {
                    player.sendMessage(ChatColor.RED + "PlayerPoints API is not available.");
                    return;
                }

                saveUsedCode(code); // Mark code as used

                player.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("success.redeemed").replace("%points%", String.valueOf(points))));
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "An error occurred while redeeming the code.");
                getLogger().severe("Error redeeming code: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
