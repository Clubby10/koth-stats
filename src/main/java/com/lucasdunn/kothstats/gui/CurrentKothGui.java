package com.lucasdunn.kothstats.gui;

import com.lucasdunn.kothstats.KothStatsPlugin;
import com.lucasdunn.kothstats.session.KothSession;
import com.lucasdunn.kothstats.session.SessionManager;
import com.lucasdunn.kothstats.session.SessionPlayerStats;
import com.lucasdunn.kothstats.util.StatsFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CurrentKothGui implements Listener {
    private static final String[] STAT_KEYS = {
        "kills", "deaths", "kd-ratio", "damage-dealt", "damage-received", "capture-time"
    };
    private static final int[] DEFAULT_STAT_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final String[] STAT_NAMES = {
        "Kills", "Deaths", "K/D Ratio", "Damage Dealt", "Damage Received", "Capture Time"
    };
    private static final Material[] STAT_MATERIALS = {
        Material.DIAMOND_SWORD,
        Material.SKULL_ITEM,
        Material.GOLDEN_APPLE,
        Material.BLAZE_POWDER,
        Material.IRON_CHESTPLATE,
        Material.WATCH
    };

    private final KothStatsPlugin plugin;
    private final SessionManager sessionManager;

    public CurrentKothGui(KothStatsPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    public boolean openCurrent(Player player, int ignoredPage) {
        KothSession session = sessionManager.getCurrent();
        if (session == null) {
            return false;
        }
        open(player, session, ignoredPage);
        return true;
    }

    public void open(Player player, KothSession session, int ignoredPage) {
        List<SessionPlayerStats> entries =
            new ArrayList<SessionPlayerStats>(session.getPlayers());
        LeaderboardHolder holder = new LeaderboardHolder(session.getName(), 1);
        FileConfiguration config = plugin.getConfig();
        String legacyTitle = config.getString("current-koth.gui-title");
        String title = color(legacyTitle == null
            ? text("current-koth.gui.title", "&1KOTH &3| &b{koth}")
            : legacyTitle).replace("{koth}", session.getName());
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }

        int rows = boundedInt("current-koth.gui.rows", 3, 1, 6);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inventory);
        if (config.getBoolean("current-koth.gui.filler.enabled", true)) {
            ItemStack filler = configuredItem(
                "current-koth.gui.filler", Material.STAINED_GLASS_PANE, (short) 11,
                " ", Collections.<String>emptyList(), session, entries.size());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        setItem(inventory, config.getInt("current-koth.gui.info.slot", 13),
            sessionItem(session, entries.size()));
        for (int index = 0; index < STAT_KEYS.length; index++) {
            String path = "current-koth.gui.stats." + STAT_KEYS[index];
            if (config.getBoolean(path + ".enabled", true)) {
                setItem(inventory, config.getInt(path + ".slot", DEFAULT_STAT_SLOTS[index]),
                    statItem(entries, STAT_KEYS[index], STAT_NAMES[index],
                        STAT_MATERIALS[index]));
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack statItem(List<SessionPlayerStats> sessionEntries, final String stat,
                               String displayName, Material material) {
        String path = "current-koth.gui.stats." + stat;
        List<SessionPlayerStats> ranked =
            new ArrayList<SessionPlayerStats>(sessionEntries);
        Collections.sort(ranked, new Comparator<SessionPlayerStats>() {
            @Override
            public int compare(SessionPlayerStats first, SessionPlayerStats second) {
                int value = Double.compare(score(second, stat), score(first, stat));
                if (value != 0) {
                    return value;
                }
                return safeName(first).compareToIgnoreCase(safeName(second));
            }
        });

        int amount = boundedInt(path + ".amount", 1, 1, 64);
        ItemStack item = new ItemStack(material(path + ".material", material),
            amount, (short) plugin.getConfig().getInt(path + ".data", 0));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(text(path + ".name", "&b&l" + displayName)));
        List<String> lore = new ArrayList<String>();
        int leaderboardSize = boundedInt(
            "current-koth.gui.leaderboard-size", 5, 1, 100);
        List<String> header = stringList(path + ".lore",
            Arrays.asList("&3Top {leaderboard_size} this event", ""));
        for (String line : header) {
            lore.add(color(line.replace(
                "{leaderboard_size}", String.valueOf(leaderboardSize))));
        }
        int size = Math.min(leaderboardSize, ranked.size());
        if (size == 0) {
            lore.add(color(text(
                "current-koth.gui.empty-format", "&3No stats recorded yet.")));
        } else {
            for (int index = 0; index < size; index++) {
                SessionPlayerStats stats = ranked.get(index);
                lore.add(color(text("current-koth.gui.entry-format",
                    "{rank_color}#{position} &b{player} &3- &f{value}")
                    .replace("{rank_color}", rankColor(index))
                    .replace("{position}", String.valueOf(index + 1))
                    .replace("{player}", safeName(stats))
                    .replace("{value}", formattedValue(stats, stat))));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack sessionItem(KothSession session, int participants) {
        return configuredItem("current-koth.gui.info", Material.NETHER_STAR, (short) 0,
            "&b&l{koth}", Arrays.asList(
                "&3Live KOTH leaderboard",
                "",
                "&9Participants: &b{participants}",
                "&9Running for: &b{elapsed}"
            ), session, participants);
    }

    private ItemStack configuredItem(String path, Material fallbackMaterial, short fallbackData,
                                     String fallbackName, List<String> fallbackLore,
                                     KothSession session, int participants) {
        int amount = boundedInt(path + ".amount", 1, 1, 64);
        short data = (short) plugin.getConfig().getInt(path + ".data", fallbackData);
        ItemStack item = new ItemStack(material(path + ".material", fallbackMaterial),
            amount, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(replaceGuiPlaceholders(
            text(path + ".name", fallbackName), session, participants)));
        List<String> lore = new ArrayList<String>();
        for (String line : stringList(path + ".lore", fallbackLore)) {
            lore.add(color(replaceGuiPlaceholders(line, session, participants)));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String rankColor(int index) {
        List<String> colors = stringList("current-koth.gui.rank-colors",
            Arrays.asList("&b", "&3", "&9", "&1"));
        if (colors.isEmpty()) {
            return "";
        }
        return colors.get(Math.min(index, colors.size() - 1));
    }

    private void setItem(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private Material material(String path, Material fallback) {
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        Material result = Material.getMaterial(
            configured.trim().toUpperCase(Locale.ENGLISH));
        if (result == null) {
            plugin.getLogger().warning("Invalid GUI material at " + path
                + ": " + configured + ". Using " + fallback.name() + ".");
            return fallback;
        }
        return result;
    }

    private int boundedInt(String path, int fallback, int minimum, int maximum) {
        int value = plugin.getConfig().getInt(path, fallback);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String text(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    private List<String> stringList(String path, List<String> fallback) {
        if (!plugin.getConfig().isList(path)) {
            return fallback;
        }
        return plugin.getConfig().getStringList(path);
    }

    private String replaceGuiPlaceholders(String value, KothSession session,
                                          int participants) {
        return value.replace("{koth}", session.getName())
            .replace("{participants}", String.valueOf(participants))
            .replace("{elapsed}", StatsFormatter.duration(session.getElapsedSeconds()));
    }

    private double score(SessionPlayerStats stats, String stat) {
        if ("kills".equals(stat)) {
            return stats.getKills();
        }
        if ("deaths".equals(stat)) {
            return stats.getDeaths();
        }
        if ("kd-ratio".equals(stat)) {
            return kdRatio(stats);
        }
        if ("damage-dealt".equals(stat)) {
            return stats.getDamageDealt();
        }
        if ("damage-received".equals(stat)) {
            return stats.getDamageReceived();
        }
        return stats.getCaptureSeconds();
    }

    private String formattedValue(SessionPlayerStats stats, String stat) {
        if ("kills".equals(stat)) {
            return String.valueOf(stats.getKills());
        }
        if ("deaths".equals(stat)) {
            return String.valueOf(stats.getDeaths());
        }
        if ("kd-ratio".equals(stat)) {
            return StatsFormatter.damage(kdRatio(stats));
        }
        if ("damage-dealt".equals(stat)) {
            return StatsFormatter.damage(stats.getDamageDealt());
        }
        if ("damage-received".equals(stat)) {
            return StatsFormatter.damage(stats.getDamageReceived());
        }
        return StatsFormatter.duration(stats.getCaptureSeconds());
    }

    private double kdRatio(SessionPlayerStats stats) {
        return (double) stats.getKills() / Math.max(1L, stats.getDeaths());
    }

    private String safeName(SessionPlayerStats stats) {
        return stats.getName() == null || stats.getName().isEmpty()
            ? "Unknown" : stats.getName();
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
