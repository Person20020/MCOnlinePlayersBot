package com.github.person20020.mconlineplayersbot;

import net.milkbowl.vault.chat.Chat;
import com.earth2me.essentials.Essentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;


import java.util.ArrayList;
import java.util.List;

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

    private DiscordBot discordBot;

    private FileConfiguration config;
    private boolean vaultEnabled = false;
    private Chat chat = null;
    private boolean debug = false;
    private Essentials ess = null;

    long DISCORD_APP_ID = 0;
    String DISCORD_TOKEN = "";
    String DISCORD_PUBLIC_KEY = "";
    long DISCORD_CHANNEL_ID = 0;
    long DISCORD_GUILD_ID = 0;
    int PLAYER_CHECK_INTERVAL;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = getConfig();

        // Discord bot info
        DISCORD_APP_ID = config.getLong("discord_app_id");
        DISCORD_TOKEN = config.getString("discord_token");
        DISCORD_PUBLIC_KEY = config.getString("discord_public_key");
        DISCORD_CHANNEL_ID = config.getLong("discord_channel_id");
        DISCORD_GUILD_ID = config.getLong("discord_guild_id");

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
        if (ess != null) {
            getServer().getPluginManager().registerEvents(new AFKListener(this), this);
        }

        // Connect Discord bot
        discordBot = new DiscordBot(DISCORD_TOKEN, this);
        discordBot.start();

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
        discordBot.updateMessage("Bot offline.", DISCORD_CHANNEL_ID);
        discordBot.stop();

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

    public JsonObject getPlayersJson() {
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

        JsonArray players = new JsonArray();

        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject player = new JsonObject();
            player.addProperty("username", p.getName());

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
                player.addProperty("prefix", prefix == null ? "" : prefix);

                // Groups
                JsonArray groups = new JsonArray();
                try {
                    for (String group : chat.getPlayerGroups(p)) {
                        groups.add(group);
                    }
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("Could not get player groups: " + e);
                    }
                }
                player.add("groups", groups);
                // Primary group
                String primaryGroup = "";
                try {
                    primaryGroup = chat.getPrimaryGroup(p);
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("Could not get player primary group: " + e);
                    }
                }
                player.addProperty("primary_group", primaryGroup);

                // Suffix
                String suffix = "";
                try {
                    suffix = chat.getPlayerSuffix(p);
                } catch (Exception e) {
                    if (debug) {
                        getLogger().warning("Could not get player suffix:" + e);
                    }
                }
                player.addProperty("suffix", suffix);
            }

            // AFK if essentials is enabled
            boolean afk = false;
            if (ess != null) {
                afk = ess.getUser(p).isAfk();
            }
            player.addProperty("is_afk", afk);

            players.add(player);
        }

        JsonObject root = new JsonObject();
        root.add("players", players);
        root.addProperty("player_count", players.size());

        return root;
    }

    public String generateMessageContent() {
        // Update player list
        JsonObject playersJSON = getPlayersJson();

        /*
        Format:
        Current Players (#):
        'No players online.' if none
        group name:
            prefix (or [AFK] if is_afk) username
         */

        JsonArray players = playersJSON.get("players").getAsJsonArray();
        int playerCount = playersJSON.get("player_count").getAsInt();


        List<String> sections = new ArrayList<>();
        sections.add(Styles.BOLD + "Current Players (" + playerCount + "):" + Styles.RESET);
        if (playerCount == 0) {
            sections.add("No players online.");
        } else {
            // Get unique primary groups
            List<String> uniquePrimaryGroups = new ArrayList<>();
            for (JsonElement el : players) {
                JsonObject player = el.getAsJsonObject();
                String primaryGroup = player.get("primary_group").getAsString();
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
                lines.add(Styles.UNDERLINE + group + ":" + Styles.RESET);
                for (JsonElement el : players) {
                    JsonObject player = el.getAsJsonObject();
                    if (player.get("primary_group").getAsString().equals(group)) {
                        String line = "- ";
                        if (player.get("is_afk").getAsBoolean()) {
                            if (config.isSet("prefix_colors")) {
                                Styles colorCode = getPrefixColor("[AFK]");
                                line += colorCode + "[AFK]" + Styles.RESET;
                            } else {
                                line += Styles.GRAY + "[AFK]" + Styles.RESET;
                            }
                        } else {
                            String prefix = player.get("prefix").getAsString();
                            if (config.isSet("prefix_colors")) {
                                line += getPrefixColor(prefix) + prefix + Styles.RESET;
                            } else {
                                line += prefix;
                            }
                        }
                        line += player.get("username").getAsString();
                        lines.add(line);
                    }
                }

                sections.add(String.join("\n", lines));
            }
        }
        return "```ansi\n" + String.join("\n\n", sections) + "\n```";
    }

    public void discordUpdatePlayerList() {
        // TODO: Finish discord bot
        String message = generateMessageContent();

        // Update message
        discordBot.updateMessage(message, DISCORD_CHANNEL_ID);

    }
}

