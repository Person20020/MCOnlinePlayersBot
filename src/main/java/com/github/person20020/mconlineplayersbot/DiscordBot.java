package com.github.person20020.mconlineplayersbot;

import org.bukkit.plugin.java.JavaPlugin;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;

import java.util.Optional;

public class DiscordBot {

    private final JavaPlugin plugin;

    private DiscordApi api;
    private final String token;

    public DiscordBot(String token, JavaPlugin plugin) {
        this.token = token;

        this.plugin = plugin;
    }

    public void start() {
        api = new DiscordApiBuilder()
                .setToken(token)
                .login()
                .join();

        api.addReconnectListener(event ->
                plugin.getLogger().info("Discord bot reconnected.")
        );
        api.addLostConnectionListener(event ->
                plugin.getLogger().info("Discord connection lost.")
        );

        plugin.getLogger().info("Discord bot logged in as " + api.getYourself().getDiscriminatedName());
    }

    public void stop() {
        if (api != null) {
            try {
                api.disconnect().join();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to disconnect from Discord cleanly: " + t.getMessage());
            }
        }
    }

    public boolean sendMessage(String message, long channelId) {
        if (api == null) return false;

        Optional<TextChannel> channel = api.getTextChannelById(channelId);
        channel.ifPresent(c -> c.sendMessage(message));

        if (! channel.isPresent()) {
            plugin.getLogger().warning("Specified channel does not exist.");
            return false;
        }
        return true;
    }

    public void updateMessage(String newMessage, long channelId) {
        if (api == null) return;

        api.getTextChannelById(channelId).ifPresent(channel -> {
            channel.getMessages(10).thenAccept(messages -> {
                messages.stream()
                        .filter(m -> m.getAuthor().isBotUser())
                        .findFirst()
                        .ifPresentOrElse(
                                // Edit existing message
                                message -> message.edit(newMessage),
                                // Send a new message if none exists
                                () -> channel.sendMessage(newMessage)
                        );
            });
        });
    }
}
