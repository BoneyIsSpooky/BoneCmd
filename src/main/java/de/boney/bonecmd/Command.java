package de.boney.bonecmd;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Command {
    private final String name;
    private final List<Parameter> formalParams;
    private CommandTask task;
    private boolean raw;

    private final static Pattern SPLIT = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private final static Pattern MENTION = Pattern.compile("^<@!?(\\d+)>$");
    private final static Pattern CHANNEL = Pattern.compile("^<#!?(\\d+)>$");

    private String toolTip = "No tooltip";
    private String longToolTip = "No help";
    private List<HelpWarning> warnings = new ArrayList<>();

    private long permissionBits = 0;
    private Set<SpecialRestriction> specialRestrictions = new HashSet<>();
    private Set<PermissionType> requiredTypes;

    private static BiFunction<Server, User, Long> permissionBitSupplier;

    public static void setInternalPermissionBitSupplier(BiFunction<Server, User, Long> myPermissionBitSupplier) {
        permissionBitSupplier = myPermissionBitSupplier;
    }

    public Command(String name) {
        this.name = name;
        this.formalParams = new ArrayList<>();
    }

    public Command arg(ArgType argType, String name, boolean optional) {
        formalParams.add(new Parameter(argType, name, optional));
        return this;
    }

    public Command arg(ArgType argType, String name) {
        formalParams.add(new Parameter(argType, name, false));
        return this;
    }

    public Command raw() {
        this.raw = true;
        return this;
    }

    public Command runs(CommandTask task) {
        this.task = task;
        return this;
    }

    public Command tip(String toolTip) {
        this.toolTip = toolTip;
        return this;
    }

    public Command help(String longToolTip) {
        this.longToolTip = longToolTip;
        return this;
    }

    public Command warn(HelpWarning...warns) {
        this.warnings.addAll(Arrays.asList(warns));
        return this;
    }

    public Command restrictInternal(long permissionBits) {
        this.permissionBits = permissionBits;
        return this;
    }

    public Command restrictExternal(final Collection<PermissionType> requiredTypes) {
        this.requiredTypes = Collections.unmodifiableSet(new HashSet<>(requiredTypes));
        return this;
    }

    public Command restrictExternal(PermissionType...requiredTypes) {
        return restrictExternal(Arrays.asList(requiredTypes));
    }

    public Command restrictSpecial(SpecialRestriction specialRestriction) {
        this.specialRestrictions.add(specialRestriction);
        return this;
    }

    public String getShortHelpMessage() {
        String base = name + ": " + toolTip;
        if (warnings.size() == 0) return base;
        return base + warnings.stream().map(w -> w.emote).collect(Collectors.joining(" "));
    }

    public String getHelpMessage() {
        String args = formalParams.stream().map(p -> p.type.name().toLowerCase() + " " + p.name + (p.optional ? "?" : "")).collect(Collectors.joining(", "));
        String base = String.format("%s (%s): %s%n%s", name, args, toolTip, longToolTip);
        if (warnings.size() == 0) return base;
        return base + "\n" + warnings.stream().map(w -> w.emote + " " + w.message).collect(Collectors.joining("\n"));
    }

    public String getShortHelpMessage(Server server, User user) {
        return (checkPermissions(server, user) ? "" : "\uD83D\uDEAB ") + getShortHelpMessage();
    }

    public String getHelpMessage(Server server, User user) {
        return (checkPermissions(server, user) ? "" : "\uD83D\uDEAB ") + getHelpMessage();
    }

    boolean checkPermissions(Server server, User user) {

        if (!specialRestrictions.isEmpty()) {
            if (specialRestrictions.contains(SpecialRestriction.BOT_OWNER) && user.isBotOwner())
                return true;
            if (specialRestrictions.contains(SpecialRestriction.SERVER_OWNER) && server.getOwner().equals(user))
                return true;

            return false;
        }

        if (requiredTypes != null && !requiredTypes.isEmpty()) {
            if (!server.getAllowedPermissions(user).containsAll(requiredTypes)) return false;
        }

        if (permissionBits == 0) return true;
        if (permissionBitSupplier == null) return true;
        long userBits = permissionBitSupplier.apply(server, user);
        if (permissionBits <= ADMIN_BITS) {
            return (userBits & (permissionBits | ADMIN_BITS)) > 0;
        } else {
            return (userBits & permissionBits) == permissionBits;
        }
    }

    String getName() {
        return name;
    }

    CommandTask getTask() {
        return task;
    }

    Arguments getArguments(DiscordApi api, Server server, TextChannel channel, User user, String fullCommand, String[] err) {
        return new Arguments(buildArgumentsList(server, fullCommand, err), api, user, channel, server);
    }

    private Map<String, Argument> buildArgumentsList(Server server, String fullCommand, String[] err) {

        if (raw) {
            HashMap<String, Argument> m = new HashMap<>();
            if (fullCommand.length() >= name.length() + 2) {
                m.put("raw", new Argument(ArgType.STRING, "raw", fullCommand.substring(name.length() + 2)));
            }
            return m;
        }

        List<String> tokens = tokenize(fullCommand);
        Map<String, Argument> arguments = new HashMap<>();

        for (int argsPos = 0, paramPos = 0; argsPos < tokens.size() && paramPos < formalParams.size(); paramPos++) {
            String token = tokens.get(argsPos);
            Parameter currentParameter = formalParams.get(paramPos);
            String currentParameterName = currentParameter.name;

            if (token.matches("^-?\\d+$")) {
                // try to get a long
                try {
                    Long l = Long.parseLong(token);
                    if (currentParameter.type == ArgType.LONG) {
                        arguments.put(currentParameterName, new Argument(ArgType.LONG, currentParameterName, l));
                        argsPos++;
                        continue;
                    }
                    if (currentParameter.type == ArgType.DOUBLE) {
                        // long ints are still valid doubles
                        arguments.put(currentParameterName, new Argument(ArgType.DOUBLE, currentParameterName, l.doubleValue()));
                        argsPos++;
                        continue;
                    }
                    if (currentParameter.type == ArgType.USER) {
                        arguments.put(currentParameterName, new Argument(ArgType.USER, currentParameterName, server.getMemberById(token).orElse(null)));
                        argsPos++;
                        continue;
                    }
                    if (currentParameter.type == ArgType.STRING) {
                        arguments.put(currentParameterName, new Argument(ArgType.STRING, currentParameterName, token));
                        argsPos++;
                        continue;
                    }
                    if (currentParameter.optional) { continue; }
                    err[0] = "Bad argument type for non-optional parameter " + currentParameterName + ", I expected " + currentParameter.type.name();
                    return null;
                } catch (NumberFormatException e) {
                    err[0] = "Bad argument format for long integer parameter " + currentParameterName + ", value is out of range";
                    return null; // long is too large or small, stop.
                }
            }

            if (token.matches("^(-?)(0|([1-9][0-9]*))(\\.[0-9]+)?$")) {
                try {
                    Double d = Double.parseDouble(token);
                    if (currentParameter.type == ArgType.DOUBLE) {
                        arguments.put(currentParameterName, new Argument(ArgType.DOUBLE, currentParameterName, d));
                        argsPos++;
                        continue;
                    }
                    if (currentParameter.type == ArgType.STRING) {
                        arguments.put(currentParameterName, new Argument(ArgType.STRING, currentParameterName, token));
                        argsPos++;
                        continue;
                    }
                    if (currentParameter.optional) { continue; }
                    err[0] = "Bad argument type for non-optional parameter " + currentParameterName + ", I expected " + currentParameter.type.name();
                    return null;
                } catch (NumberFormatException e) {
                    err[0] = "Bad argument format for floating point parameter " + currentParameterName + ", value is malformed";
                    return null;
                }
            }

            if (token.matches(MENTION.pattern())) {
                if (currentParameter.type != ArgType.USER) {
                    if (currentParameter.optional) { continue; }
                    err[0] = "Bad argument type for non-optional parameter " + currentParameterName + ", I expected " + currentParameter.type.name();
                    return null;
                }
                Matcher m = MENTION.matcher(token);
                if (m.find()) {
                    arguments.put(currentParameterName, new Argument(ArgType.USER, currentParameterName, server.getMemberById(m.group(1)).orElse(null)));
                    argsPos++;
                    continue;
                }
            }

            if (token.matches(CHANNEL.pattern())) {
                if (currentParameter.type != ArgType.CHANNEL) {
                    if (currentParameter.optional) { continue; }
                    err[0] = "Bad argument type for non-optional parameter " + currentParameterName + ", I expected " + currentParameter.type.name();
                    return null;
                }
                Matcher m = CHANNEL.matcher(token);
                if (m.find()) {
                    arguments.put(currentParameterName, new Argument(ArgType.CHANNEL, currentParameterName, server.getChannelById(m.group(1)).orElse(null)));
                    argsPos++;
                    continue;
                }
            }

            if (token.contains("\\s")) { // has whitespace, so it must be a quoted string
                if (currentParameter.type == ArgType.USER) {
                    // try to match this to a user. it's quoted, so don't bother with an id
                    // a search miss isn't a stop, just a null user
                    List<User> users = new ArrayList<>(server.getMembersByNameIgnoreCase(token));
                    if (users.size() == 0) {
                        arguments.put(currentParameterName, new Argument(ArgType.USER, currentParameterName, null));
                        argsPos++;
                        continue;
                    }
                    arguments.put(currentParameterName, new Argument(ArgType.USER, currentParameterName, users.get(0)));
                    argsPos++;
                    continue;
                }
                if (currentParameter.type != ArgType.STRING) {
                    //only String and User can be quoted. Check for optional command or stop
                    if (!currentParameter.optional) {
                        //bad argument, stop
                        err[0] = "Bad argument type for non-optional parameter " + currentParameterName + ", I expected " + currentParameter.type.name();
                        return null;
                    }
                    //don't increase argsPos because we still need to match it to something later
                    continue;
                }
                //it's a string so just add it
                arguments.put(currentParameterName, new Argument(ArgType.STRING, currentParameterName, token));
                argsPos++;
                continue;
            }

            //user?
            if (currentParameter.type ==  ArgType.USER) {
                List<User> users = new ArrayList<>(server.getMembersByNameIgnoreCase(token));
                if (users.size() == 0) {
                    arguments.put(currentParameterName, new Argument(ArgType.USER, currentParameterName, null));
                    argsPos++;
                    continue;
                }
                arguments.put(currentParameterName, new Argument(ArgType.USER, currentParameterName, users.get(0)));
                argsPos++;
                continue;
            }

            // i guess it is a string then

            if (currentParameter.type != ArgType.STRING) {
                if (currentParameter.optional) { continue; }
                err[0] = "Bad argument type for non-optional parameter " + currentParameterName + ", I expected " + currentParameter.type.name();
                return null;
            }
            arguments.put(currentParameterName, new Argument(ArgType.STRING, currentParameterName, token));
            argsPos++;
        }

        long min = formalParams.stream().filter(p -> !p.optional).count();
        int max = formalParams.size();

        String range;
        if (max == min) range = String.valueOf(max);
        else if (max - min == 1) range = min + " or " + max;
        else range = min + ".." + max;

        if (arguments.size() < min) {
            err[0] = "Not enough arguments. I expected " + range + " but you gave " + arguments.size() + ".";
            return null;
        }

        return arguments;
    }

    public static List<String> tokenize(String fullCommand) {
        List<String> tokens = new ArrayList<>();
        Matcher m = SPLIT.matcher(fullCommand);
        while (m.find()) {
            if (m.group(1) != null) {
                tokens.add(m.group(1));
            } else {
                tokens.add(m.group(2));
            }
        }
        tokens.remove(0);
        return tokens;
    }

    static class Parameter {
        final ArgType type;
        final String name;
        final boolean optional;

        Parameter(ArgType type, String name, boolean optional) {
            this.type = type;
            this.name = name;
            this.optional = optional;
        }
    }

    static class Argument {
        final ArgType type;
        final String name;
        final Object value;

        Argument(ArgType type, String name, Object value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }
    }

    public enum ArgType {
        LONG, DOUBLE, BOOL, STRING, USER, CHANNEL
    }

    public enum HelpWarning {
        MIGHT_MENTION("\uD83D\uDDEF", "Might mention other users."),
        TIME_CONSUMING("\uD83D\uDD57", "Might run for a long time."),
        DESTRUCTIVE("⚠", "Might perform irreversible changes.");

        String emote, message;

        HelpWarning(String emote, String message) {
            this.emote = emote;
            this.message = message;
        }
    }

    public static final long MODERATOR_BITS    = 0x1;
    public static final long ADMIN_BITS        = 0x2;
    public static final long MOD_OR_ADMIN_BITS = 0x3;

    public enum SpecialRestriction {
        BOT_OWNER, SERVER_OWNER, NOBODY
    }
}
