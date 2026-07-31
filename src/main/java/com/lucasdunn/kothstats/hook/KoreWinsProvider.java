package com.lucasdunn.kothstats.hook;

import com.lucasdunn.kothstats.KothStatsPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KoreWinsProvider {
    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*");
    private static final String[] PLACEHOLDER_CANDIDATES = {
        "%kore_koth_captures%",
        "%kore_koth_player_captures%",
        "%kore_koth_playercaptures%",
        "%kore_koth_caps%",
        "%kore_koth_wins%"
    };

    private final KothStatsPlugin plugin;
    private final Map<String, CachedWins> cache =
        new LinkedHashMap<String, CachedWins>(16, 0.75F, true);
    private boolean warned;
    private boolean queryingNativeCommand;

    public KoreWinsProvider(KothStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public String getWins(Player viewer, String targetName) {
        String cacheKey = targetName.toLowerCase(Locale.ROOT);
        String cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        String placeholderValue = target == null ? null : fromPlaceholders(target);
        if (placeholderValue != null) {
            cache(cacheKey, placeholderValue);
            return placeholderValue;
        }
        String commandValue = fromNativeCommand(viewer, targetName);
        if (commandValue != null) {
            cache(cacheKey, commandValue);
            return commandValue;
        }
        if (!warned) {
            warned = true;
            plugin.getLogger().warning("Could not read FactionsKore KOTH wins. "
                + "Set koth-integration.wins-placeholder to a working numeric placeholder.");
        }
        String unavailable = plugin.getConfig().getString(
            "koth-integration.wins-unavailable-text", "Unavailable");
        cache(cacheKey, unavailable);
        return unavailable;
    }

    private synchronized String getCached(String cacheKey) {
        CachedWins cached = cache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtMillis <= System.currentTimeMillis()) {
            cache.remove(cacheKey);
            return null;
        }
        return cached.value;
    }

    private synchronized void cache(String cacheKey, String value) {
        long cacheSeconds = plugin.getConfig().getLong(
            "koth-integration.wins-cache-seconds", 5L);
        if (cacheSeconds <= 0L) {
            return;
        }
        long expiresAtMillis = System.currentTimeMillis()
            + Math.min(cacheSeconds, 86400L) * 1000L;
        cache.put(cacheKey, new CachedWins(value, expiresAtMillis));

        int maximumSize = Math.max(1, plugin.getConfig().getInt(
            "koth-integration.wins-cache-size", 256));
        Iterator<String> keys = cache.keySet().iterator();
        while (cache.size() > maximumSize && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private String fromPlaceholders(Player target) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return null;
        }
        String configured = plugin.getConfig().getString(
            "koth-integration.wins-placeholder", "");
        if (configured != null && !configured.trim().isEmpty()) {
            String value = resolvePlaceholder(target, configured.trim());
            if (value != null) {
                return value;
            }
        }
        for (String candidate : PLACEHOLDER_CANDIDATES) {
            String value = resolvePlaceholder(target, candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolvePlaceholder(Player target, String placeholder) {
        String resolved = PlaceholderAPI.setPlaceholders(target, placeholder);
        if (resolved == null || resolved.equals(placeholder)) {
            return null;
        }
        return numericValue(resolved);
    }

    private String fromNativeCommand(Player viewer, String targetName) {
        if (queryingNativeCommand) {
            return null;
        }
        PluginCommand kothCommand = Bukkit.getPluginCommand("koth");
        if (kothCommand == null
            || kothCommand.getPlugin().equals(plugin)) {
            return null;
        }
        final List<String> messages = new ArrayList<String>();
        Player silentViewer = (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            new SilentPlayerHandler(viewer, messages));
        String[] args = viewer.getName().equalsIgnoreCase(targetName)
            ? new String[] {"stats"}
            : new String[] {"stats", targetName};
        try {
            queryingNativeCommand = true;
            kothCommand.execute(silentViewer, "koth", args);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not query FactionsKore wins: "
                + exception.getMessage());
            return null;
        } finally {
            queryingNativeCommand = false;
        }
        for (String message : messages) {
            String plain = ChatColor.stripColor(message);
            String lower = plain == null ? "" : plain.toLowerCase();
            if (lower.contains("win") || lower.contains("captur")
                || lower.contains(" cap")) {
                String value = numericValue(plain);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String numericValue(String value) {
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        String result = null;
        while (matcher.find()) {
            result = matcher.group().replace(",", "");
        }
        return result;
    }

    private static final class CachedWins {
        private final String value;
        private final long expiresAtMillis;

        private CachedWins(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private static final class SilentPlayerHandler implements InvocationHandler {
        private final Player delegate;
        private final List<String> messages;

        private SilentPlayerHandler(Player delegate, List<String> messages) {
            this.delegate = delegate;
            this.messages = messages;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("sendMessage".equals(name) || "sendRawMessage".equals(name)) {
                capture(args);
                return null;
            }
            if ("openInventory".equals(name) || "closeInventory".equals(name)) {
                return null;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private void capture(Object[] args) {
            if (args == null || args.length == 0 || args[0] == null) {
                return;
            }
            if (args[0] instanceof String) {
                messages.add((String) args[0]);
            } else if (args[0] instanceof String[]) {
                for (String message : (String[]) args[0]) {
                    messages.add(message);
                }
            }
        }
    }
}
