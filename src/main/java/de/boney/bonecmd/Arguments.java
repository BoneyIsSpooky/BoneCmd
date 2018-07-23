package de.boney.bonecmd;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Arguments {
    private final Map<String, Command.Argument> arguments;
    private final DiscordApi api;
    private final User user;
    private final TextChannel channel;
    private final Server server;

    Arguments(Map<String, Command.Argument> arguments, DiscordApi api, User user, TextChannel channel, Server server) {
        this.arguments = arguments;
        this.api = api;
        this.user = user;
        this.channel = channel;
        this.server = server;
    }

    public DiscordApi getApi() {
        return api;
    }

    public User getInvokingUser() {
        return user;
    }

    public TextChannel getChannel() {
        return channel;
    }

    public Server getServer() {
        return server;
    }

    public CompletableFuture<Message> reply(String str) {
        return getChannel().sendMessage(str);
    }

    public CompletableFuture<Message> reply(String str, Object...format) {
        return reply(String.format(str, format));
    }

    public CompletableFuture<Message> reply(EmbedBuilder embedBuilder) {
        return getChannel().sendMessage(embedBuilder);
    }

    public Optional<Long> getLong(String name) {
        if (!arguments.containsKey(name)) return Optional.empty();
        if (arguments.get(name).type != Command.ArgType.LONG) return Optional.empty();
        return Optional.ofNullable((Long) arguments.get(name).value);
    }

    public Optional<Double> getDouble(String name) {
        if (!arguments.containsKey(name)) return Optional.empty();
        if (arguments.get(name).type != Command.ArgType.DOUBLE) return Optional.empty();
        return Optional.ofNullable((Double) arguments.get(name).value);
    }

    public Optional<Boolean> getBool(String name) {
        if (!arguments.containsKey(name)) return Optional.empty();
        if (arguments.get(name).type != Command.ArgType.BOOL) return Optional.empty();
        return Optional.ofNullable((Boolean) arguments.get(name).value);
    }

    public Optional<String> getString(String name) {
        if (!arguments.containsKey(name)) return Optional.empty();
        if (arguments.get(name).type != Command.ArgType.STRING) return Optional.empty();
        return Optional.ofNullable((String) arguments.get(name).value);
    }

    public Optional<User> getUser(String name) {
        if (!arguments.containsKey(name)) return Optional.empty();
        if (arguments.get(name).type != Command.ArgType.USER) return Optional.empty();
        return Optional.ofNullable((User) arguments.get(name).value);
    }
}
