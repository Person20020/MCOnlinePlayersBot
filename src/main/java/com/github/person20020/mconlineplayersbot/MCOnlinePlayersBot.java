package com.github.person20020.mconlineplayersbot;

import net.milkbowl.vault.chat.Chat;
import com.earth2me.essentials.Essentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MCOnlinePlayersBot extends JavaPlugin {

    private enum Styles {
        GRAY("\u001B[2;30m"),
        RED("\u001B[2;31m"),
        GREEN("\u001B[2;32m"),
        YELLOW("\u001B[2;33m"),
        BLUE("\u001B[2;34m"),
        PINK("\u001B[2;35m"),
        CYAN("\u001B[2;36m"),
        WHITE("\u001B[2;37m"),
        BOLD("\u001B[1;2m"),
        UNDERLINE("\u001B[4;2m"),
        RESET("\u001B[0m");

        public final String code;

        @Override
        public String toString() {
            return code;
        }

        Styles(String code) {
            this.code = code;
        }
    }


    private FileConfiguration config;
    private boolean vaultEnabled = false;
    private Chat chat = null;
    private boolean debug = false;
    private Essentials ess = null;

    int DISCORD_APP_ID = 0;
    String DISCORD_TOKEN = "";
    String DISCORD_PUBLIC_KEY = "";
    int DISCORD_CHANNEL_ID = 0;
    int DISCORD_GUILD_ID = 0;
    int PLAYER_CHECK_INTERVAL;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = getConfig();

        // Discord bot info
        DISCORD_APP_ID = config.getInt("discord_app_id");
        DISCORD_TOKEN = config.getString("discord_token");
        DISCORD_PUBLIC_KEY = config.getString("discord_public_key");
        DISCORD_CHANNEL_ID = config.getInt("discord_channel_id");
        DISCORD_GUILD_ID = config.getInt("discord_guild_id");

        // Check that Discord bot info is set
        if (
                DISCORD_APP_ID == 0 ||
                DISCORD_TOKEN.equals("replace_me") ||
                DISCORD_PUBLIC_KEY.equals("replace_me") ||
                DISCORD_CHANNEL_ID == 0 ||
                DISCORD_GUILD_ID == 0
        ) {
            getLogger().severe("Some Discord configuration is still set to default. " +
                    "This plugin will not run until they are updated.");
            getServer().getPluginManager().disablePlugin(this);
        }

        // Config
        debug = config.getBoolean("debug");
        PLAYER_CHECK_INTERVAL = config.getInt("player_check_interval");

        // Check installed plugins
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (plugin instanceof Essentials) {
            ess = (Essentials) plugin;
            getLogger().info("Essentials detected, AFK enabled.");
        } else {
            getLogger().info("Essentials not detected, AFK check disabled.");
        }


        if (!setupChat()) {
            getLogger().warning("Vault chat not found. Prefixes and groups disabled.");
        } else {
            vaultEnabled = true;
        }
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new AFKListener(this), this);

        // TODO: Create service to run discordUpdatePlayerList
        // Schedule manual check
        Bukkit.getScheduler().runTaskTimer(
                this,
                this::discordUpdatePlayerList,
                0L,
                PLAYER_CHECK_INTERVAL * 20L
        );
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        // TODO: Kill discord bot ???

        getLogger().info("MCOnlinePlayersBot plugin disabled.");
    }

    private boolean setupChat() {
        // TODO: Make sure this is correct
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp == null) return false;
        chat = rsp.getProvider();
        return true;
    }

    private Styles getPrefixColor(String prefix) {
        if (!config.isSet("prefix_colors." + prefix)) {
            getLogger().warning("No color found for prefix: " + prefix);
            return null;
        }

        // Retrieve the color name associated with the given prefix
        String colorName = config.getString("prefix_colors." + prefix, "");
        if (colorName.isEmpty()) {
            getLogger().warning("Color is empty for prefix: " + prefix);
            return null;
        }


        // Convert the color name to its corresponding enum value
        for (Styles color : Styles.values()) {
            if (color.name().equalsIgnoreCase(colorName)) {
                return color;
            }
        }
        getLogger().severe("Invalid color specified in config for prefix '" + prefix + ": " + colorName);
        return null;
    }

    public JSONObject getPlayersJson() {
        // Get online players and info
        /*
            If Vault is not present, prefix, groups, primary_group, and suffix will all be empty.
            If Essentials is not present, is_afk will always be false.
            JSON example:
            {
                "players": [
                    {
                        "username": "Steve",
                        "prefix": "Admin",
                        "groups": [
                            "Admin",
                            "Mod",
                            "Default"
                        ],
                        "primary_group": "Admin",
                        "suffix": "",
                        "is_afk": false
                    },
                    {
                        "username": "Alex",
                        "prefix": "Mod",
                        "groups": [
                            "Mod",
                            "Default"
                        ],
                        "primary_group": "Mod",
                        "suffix": "",
                        "is_afk": true
                    }
                ]
            }
         */

        JSONArray players = new JSONArray();

        for (Player p : Bukkit.getOnlinePlayers()) {
            JSONObject player = new JSONObject();
            player.put("username", p.getName());

            // Vault info
            if (vaultEnabled) {
                // Prefix
                String prefix = "";
                try {
                    prefix = chat.getPlayerPrefix(p);
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("could not get player prefix: " + e);
                    }
                }
                player.put("prefix", prefix == null ? "" : prefix);

                // Groups
                JSONArray groups = new JSONArray();
                try {
                    for (String group : chat.getPlayerGroups(p)) {
                        groups.put(group);
                    }
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("Could not get player groups: " + e);
                    }
                }
                player.put("groups", groups);
                // Primary group
                String primaryGroup = "";
                try {
                    primaryGroup = chat.getPrimaryGroup(p);
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("Could not get player primary group: " + e);
                    }
                }
                player.put("primary_group", primaryGroup);

                // Suffix
                String suffix = "";
                try {
                    suffix = chat.getPlayerSuffix(p);
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("Could not get player suffix:" + e);
                    }
                }
                player.put("suffix", suffix);
            }

            // AFK if essentials is enabled
            boolean afk = false;
            if (ess != null) {
                afk = ess.getUser(p).isAfk();
            }
            player.put("is_afk", afk);

            players.put(player);
        }

        JSONObject root = new JSONObject();
        int playerCount = players.length();
        root.put("players", players);
        root.put("player_count", playerCount);

        return root;
    }

    public String generateMessageContent() {
        // Update player list
        JSONObject playersJSON = new JSONObject(getPlayersJson());

        /*
        Format:
        Current Players (#):
        'No players online.' if none
        group name:
            prefix (or [AFK] if is_afk) username
         */

        JSONArray players = new JSONArray(playersJSON.getJSONArray("players"));
        int playerCount = playersJSON.getInt("player_count");


        List<String> sections = new ArrayList<>();
        sections.add("{BOLD}Current Players ({playerCount):{RESET}" + Styles.BOLD + "Current Players (" + playerCount + "):" + Styles.RESET);
        if (playerCount == 0) {
            sections.add("No players online.");
        } else {
            // Get unique primary groups
            List<String> uniquePrimaryGroups = new ArrayList<>();
            for (int i = 0; i < players.length(); i++) {
                JSONObject player = new JSONObject(players.get(i));
                String primaryGroup = player.getString("primary_group");
                if (!uniquePrimaryGroups.contains(primaryGroup)) {
                    uniquePrimaryGroups.add(primaryGroup);
                }
            }
            int uniquePrimaryGroupsCount = uniquePrimaryGroups.toArray().length;

            // Generate group sections
            for (int i = 0; i < uniquePrimaryGroupsCount; i++) {
                String group = uniquePrimaryGroups.get(i);

                // Generate lines
                List<String> lines = new ArrayList<>();
                lines.add(Styles.UNDERLINE + group + Styles.RESET);
                for (int j = 0; j < playerCount; j++) {
                    JSONObject player = new JSONObject(players.get(j));
                    if (player.getString("primary_group").equals(group)) {
                        String line = "";
                        if (player.getBoolean("is_afk")) {
                            if (config.isSet("prefix_colors")) {
                                Styles colorCode = getPrefixColor("[AFK]");
                                line = colorCode + "[AFK]" + Styles.RESET;
                            } else {
                                line = Styles.GRAY + "[AFK]" + Styles.RESET;
                            }
                        } else {
                            String prefix = player.getString("prefix");
                            if (config.isSet("prefix_colors")) {
                                line = getPrefixColor(prefix) + prefix + Styles.RESET;
                            } else {
                                line = prefix;
                            }
                        }
                        line += player.getString("username");
                        lines.add(line);
                    }
                }

                sections.add(String.join("\n", lines));
            }
        }
        return "```ansi\n" + String.join("\n\n", sections) + "\n````";
    }

    public void discordUpdatePlayerList() {
        // TODO: Finish discord bot
        String message = generateMessageContent();
//        try {
//            DiscordApi api = new DiscordApiBuilder()
//                    .setToken(DISCORD_TOKEN)
//                    .login().join();
//            getLogger().info("Discord bot connected as: " + api.getYourself().getDiscriminatedName());
//        } catch (Exception e) {
//            getLogger().severe("Could not connect discord bot!");
//        }
        getServer().broadcastMessage(message);
    }
}

