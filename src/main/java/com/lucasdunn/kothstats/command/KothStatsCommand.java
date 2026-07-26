package com.lucasdunn.kothstats.command;

import com.lucasdunn.kothstats.KothStatsPlugin;
import com.lucasdunn.kothstats.model.PlayerStats;
import com.lucasdunn.kothstats.gui.CurrentKothGui;
import com.lucasdunn.kothstats.hook.KoreWinsProvider;
import com.lucasdunn.kothstats.storage.StatsRepository;
import com.lucasdunn.kothstats.util.StatsFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class KothStatsCommand implements CommandExecutor, TabCompleter {
    private final KothStatsPlugin plugin;
    private final StatsRepository repository;
    private final CurrentKothGui currentKothGui;
    private final KoreWinsProvider winsProvider;
    private final Map<Player, Long> commandCooldowns = new WeakHashMap<Player, Long>();

    public KothStatsCommand(KothStatsPlugin plugin, StatsRepository repository,
                            CurrentKothGui currentKothGui,
                            KoreWinsProvider winsProvider) {
        this.plugin = plugin;
        this.repository = repository;
        this.currentKothGui = currentKothGui;
        this.winsProvider = winsProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return executeIntegrated(sender, args);
    }

    public boolean executeIntegrated(CommandSender sender, String[] args) {
        if (isRateLimited(sender)) {
            notice(sender, "command-cooldown");
            return true;
        }
        return execute(sender, args);
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 0 && ("help".equalsIgnoreCase(args[0])
            || "?".equals(args[0]))) {
            return help(sender);
        }
        if (args.length > 0 && "stats".equalsIgnoreCase(args[0])) {
            String[] statsArgs = new String[Math.max(0, args.length - 1)];
            if (statsArgs.length > 0) {
                System.arraycopy(args, 1, statsArgs, 0, statsArgs.length);
            }
            return show(sender, statsArgs);
        }
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!has(sender, "kothstats.admin.reload")) {
                return true;
            }
            plugin.reloadPlugin();
            notice(sender, "reloaded");
            return true;
        }
        if (args.length > 0 && "reset".equalsIgnoreCase(args[0])) {
            return reset(sender, args);
        }
        if (args.length > 0 && ("resetall".equalsIgnoreCase(args[0])
            || "globalreset".equalsIgnoreCase(args[0]))) {
            return resetAll(sender, args, 1);
        }
        if (args.length > 0 && ("top".equalsIgnoreCase(args[0])
            || "leaderboard".equalsIgnoreCase(args[0])
            || "lb".equalsIgnoreCase(args[0]))) {
            return top(sender, args);
        }
        if (args.length > 0 && ("current".equalsIgnoreCase(args[0])
            || "live".equalsIgnoreCase(args[0])
            || "gui".equalsIgnoreCase(args[0]))) {
            return current(sender, args);
        }
        if (args.length > 1) {
            return help(sender);
        }
        return show(sender, args);
    }

    private boolean help(CommandSender sender) {
        send(sender, "&8&m----------------------------------------");
        send(sender, "&6&lKOTH STATS &8| &7Commands");
        send(sender, "&e/koth stats [player] &8- &7View player stats");
        send(sender, "&e/koth stats current &8- &7View the live leaderboard");
        send(sender, "&e/koth stats top <stat> &8- &7View all-time leaders");
        if (sender.hasPermission("kothstats.admin.reset")) {
            send(sender, "&c/koth stats reset <player> &8- &7Reset one player");
        }
        if (sender.hasPermission("kothstats.admin.resetall")) {
            send(sender, "&c/koth stats reset all confirm &8- &7Reset all stats");
        }
        if (sender.hasPermission("kothstats.admin.reload")) {
            send(sender, "&c/koth stats reload &8- &7Reload configuration");
        }
        send(sender, "&8Aliases: &7/kothstats, /kstats");
        send(sender, "&8&m----------------------------------------");
        return true;
    }

    private boolean current(CommandSender sender, String[] args) {
        if (!has(sender, "kothstats.current")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the KOTH leaderboard.");
            return true;
        }
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        if (!currentKothGui.openCurrent((Player) sender, page)) {
            notice(sender, "no-active-koth");
        }
        return true;
    }

    private boolean show(CommandSender sender, String[] args) {
        if (!has(sender, "kothstats.view")) {
            return true;
        }
        PlayerStats stats;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /kothstats <player>");
                return true;
            }
            stats = repository.getOrCreate((Player) sender);
        } else {
            if (!has(sender, "kothstats.view.others")) {
                return true;
            }
            stats = find(args[0]);
        }
        if (stats == null) {
            notice(sender, "player-not-found");
            return true;
        }
        String wins = sender instanceof Player
            ? winsProvider.getWins((Player) sender, stats.getLastName())
            : plugin.getConfig().getString(
                "koth-integration.wins-unavailable-text", "Unavailable");
        boolean includesWins = false;
        for (String line : plugin.getConfig().getStringList("messages.stats")) {
            includesWins = includesWins || line.contains("{koth_wins}");
            send(sender, replaceStats(line, stats, wins));
        }
        if (!includesWins) {
            send(sender, "&7KOTH wins: &f" + wins);
        }
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!has(sender, "kothstats.admin.reset")) {
                return true;
            }
            send(sender, "&cUsage: /koth stats reset <player>");
            return true;
        }
        if ("all".equalsIgnoreCase(args[1]) || "*".equals(args[1])) {
            return resetAll(sender, args, 2);
        }
        if (!has(sender, "kothstats.admin.reset")) {
            return true;
        }
        PlayerStats stats = find(args[1]);
        if (stats == null) {
            notice(sender, "player-not-found");
            return true;
        }
        stats.reset();
        repository.save();
        noticeMessage(sender, plugin.message("reset").replace("{player}", stats.getLastName()));
        return true;
    }

    private boolean resetAll(CommandSender sender, String[] args, int confirmationIndex) {
        if (!has(sender, "kothstats.admin.resetall")) {
            return true;
        }
        if (args.length <= confirmationIndex
            || !"confirm".equalsIgnoreCase(args[confirmationIndex])) {
            send(sender, "&cThis resets every player tracked by KothStats.");
            send(sender, "&cConfirm with: &f/koth stats reset all confirm");
            return true;
        }
        int reset = repository.resetAll();
        noticeMessage(sender, plugin.message("reset-all")
            .replace("{count}", String.valueOf(reset)));
        plugin.getLogger().warning(sender.getName()
            + " globally reset KothStats data for " + reset + " players.");
        return true;
    }

    private boolean top(CommandSender sender, String[] args) {
        if (!has(sender, "kothstats.top")) {
            return true;
        }
        if (args.length < 2 || !isStat(args[1].toLowerCase())) {
            notice(sender, "invalid-stat");
            return true;
        }
        final String stat = args[1].toLowerCase();
        List<PlayerStats> values = new ArrayList<PlayerStats>(repository.all());
        Collections.sort(values, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats first, PlayerStats second) {
                return Double.compare(numericValue(second, stat), numericValue(first, stat));
            }
        });

        send(sender, "&8&m----------------------------------------");
        send(sender, plugin.message("top-header").replace("{stat}", stat));
        int size = Math.min(plugin.getConfig().getInt("leaderboard-size", 10), values.size());
        if (size == 0) {
            send(sender, "&7No statistics have been recorded yet.");
        }
        for (int index = 0; index < size; index++) {
            PlayerStats stats = values.get(index);
            String line = plugin.message("top-entry")
                .replace("{position}", String.valueOf(index + 1))
                .replace("{player}", stats.getLastName())
                .replace("{value}", StatsFormatter.value(stats, stat));
            send(sender, line);
        }
        send(sender, "&8&m----------------------------------------");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> options = new ArrayList<String>();
        if (args.length == 1) {
            addIfAllowed(options, sender, "help", null);
            addIfAllowed(options, sender, "stats", "kothstats.view");
            addIfAllowed(options, sender, "current", "kothstats.current");
            addIfAllowed(options, sender, "top", "kothstats.top");
            addIfAllowed(options, sender, "reset", "kothstats.admin.reset");
            addIfAllowed(options, sender, "resetall", "kothstats.admin.resetall");
            addIfAllowed(options, sender, "reload", "kothstats.admin.reload");
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        } else if (args.length == 2
            && ("top".equalsIgnoreCase(args[0]) || "leaderboard".equalsIgnoreCase(args[0])
                || "lb".equalsIgnoreCase(args[0]))) {
            options.add("kills");
            options.add("deaths");
            options.add("damage-dealt");
            options.add("damage-received");
            options.add("capture-time");
        } else if (args.length == 2 && "reset".equalsIgnoreCase(args[0])) {
            options.add("all");
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        } else if (args.length == 2
            && ("resetall".equalsIgnoreCase(args[0])
                || "globalreset".equalsIgnoreCase(args[0]))) {
            options.add("confirm");
        } else if (args.length == 3 && "reset".equalsIgnoreCase(args[0])
            && ("all".equalsIgnoreCase(args[1]) || "*".equals(args[1]))) {
            options.add("confirm");
        } else if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        }
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        List<String> matches = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input)) {
                matches.add(option);
            }
        }
        Collections.sort(matches, String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private void addIfAllowed(List<String> options, CommandSender sender,
                              String option, String permission) {
        if (permission == null || sender.hasPermission(permission)) {
            options.add(option);
        }
    }

    private PlayerStats find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return repository.getOrCreate(online);
        }
        PlayerStats existing = repository.findByName(name);
        if (existing != null) {
            return existing;
        }
        return null;
    }

    private boolean isRateLimited(CommandSender sender) {
        if (!(sender instanceof Player)
            || sender.hasPermission("kothstats.cooldown.bypass")) {
            return false;
        }
        Player player = (Player) sender;
        long now = System.currentTimeMillis();
        long delay = Math.max(250L,
            plugin.getConfig().getLong("command-cooldown-millis", 1000L));
        Long previous = commandCooldowns.put(player, now);
        return previous != null && now - previous < delay;
    }

    private boolean has(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        notice(sender, "no-permission");
        return false;
    }

    private boolean isStat(String stat) {
        return "kills".equals(stat) || "deaths".equals(stat) || "damage-dealt".equals(stat)
            || "damage-received".equals(stat) || "capture-time".equals(stat);
    }

    private double numericValue(PlayerStats stats, String stat) {
        if ("kills".equals(stat)) {
            return stats.getKills();
        }
        if ("deaths".equals(stat)) {
            return stats.getDeaths();
        }
        if ("damage-dealt".equals(stat)) {
            return stats.getDamageDealt();
        }
        if ("damage-received".equals(stat)) {
            return stats.getDamageReceived();
        }
        return stats.getCaptureSeconds();
    }

    private String replaceStats(String line, PlayerStats stats, String wins) {
        return line.replace("{player}", stats.getLastName())
            .replace("{koth_wins}", wins)
            .replace("{kills}", String.valueOf(stats.getKills()))
            .replace("{deaths}", String.valueOf(stats.getDeaths()))
            .replace("{damage_dealt}", StatsFormatter.damage(stats.getDamageDealt()))
            .replace("{damage_received}", StatsFormatter.damage(stats.getDamageReceived()))
            .replace("{capture_time}", StatsFormatter.duration(stats.getCaptureSeconds()))
            .replace("{capture_seconds}", String.valueOf(stats.getCaptureSeconds()));
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private void notice(CommandSender sender, String key) {
        noticeMessage(sender, plugin.message(key));
    }

    private void noticeMessage(CommandSender sender, String message) {
        send(sender, plugin.getConfig().getString(
            "messages.prefix", "&8[&6KothStats&8] ") + message);
    }
}
