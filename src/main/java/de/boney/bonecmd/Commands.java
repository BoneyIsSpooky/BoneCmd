package de.boney.bonecmd;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class Commands {
    private final static ConcurrentHashMap<String, Command> commands = new ConcurrentHashMap<>();
    private static boolean listenerRegistered = false;
    private static BiFunction<Server, String, String> macroTextSupplier;

    public static void setMacroTextSupplier(BiFunction<Server, String, String> myMacroTextSupplier) {
        macroTextSupplier = myMacroTextSupplier;
    }

    public static void registerCommand(final Command cmd) {
        if (!listenerRegistered) {
            throw new IllegalStateException();
        }
        commands.put(cmd.getName(), cmd);
    }

    public static String getTooltip(String command, Server server, User user) {
        return commands.containsKey(command) ? commands.get(command).getShortHelpMessage(server, user) : "Unknown command";
    }

    public static String getHelp(String command, Server server, User user) {
        return commands.containsKey(command) ? commands.get(command).getHelpMessage(server, user) : "Unknown command";
    }

    public static String getTooltipSummary(Server server, User user) {
        return commands.keySet().stream().map(x -> getTooltip(x, server, user)).collect(Collectors.joining("\n"));
    }

    private static void handleMessage(MessageCreateEvent event) {
        handleMessage(event.getMessage().getContent(), event.getApi(), event.getServer().get(), event.getMessage().getUserAuthor().get(), event.getChannel());
    }

    public static void handleMessage(String content, DiscordApi api, Server server, User user, TextChannel channel) {
        if (user.isBot()) return;
        if (!content.startsWith("!")) return;
        String commandName = content.substring(1);
        if (commandName.contains(" ")) commandName = commandName.substring(0, content.indexOf(" ") - 1);
        if (commands.containsKey(commandName)) {
            final Command command = commands.get(commandName);
            if (!command.checkPermissions(server, user)) {
                channel.sendMessage("You don't have permission.");
                return;
            }
            final String[] err = new String[]{""};
            final Arguments args = command.getArguments(api, server, channel, user, content, err);
            if (!err[0].equals("")) {
                channel.sendMessage("Error:\n" + err[0]);
                return;
            }
            final CommandTask task = command.getTask();
            api.getThreadPool().getExecutorService().submit(() -> task.execute(args));
        } else { // maybe a macro?
            if (macroTextSupplier == null) return;
            String macroText = macroTextSupplier.apply(server, commandName);
            if (macroText == null) return;

            List<String> args = Command.tokenize(content);
            String[] commands;
            if (macroText.contains(";")) commands = macroText.split(";");
            else commands = new String[]{macroText};
            for (String command : commands) {
                for (int i = 0; i < args.size(); i++) {
                    String r = "\\$" + (i + 1);
                    command = command.replaceAll(r, args.get(i));
                }
                command = command.replaceAll("\\$user", user.getIdAsString());
                handleMessage(command, api, server, user, channel);
            }
        }
    }

    public static void registerListener(DiscordApi api) {
        if (listenerRegistered) return;
        api.addMessageCreateListener(Commands::handleMessage);
        listenerRegistered = true;
    }
}
