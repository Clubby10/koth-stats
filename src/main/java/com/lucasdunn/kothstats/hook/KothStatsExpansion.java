package com.lucasdunn.kothstats.hook;

import com.lucasdunn.kothstats.KothStatsPlugin;
import com.lucasdunn.kothstats.model.PlayerStats;
import com.lucasdunn.kothstats.storage.StatsRepository;
import com.lucasdunn.kothstats.util.StatsFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class KothStatsExpansion extends PlaceholderExpansion {
    private final KothStatsPlugin plugin;
    private final StatsRepository repository;
    private final KoreWinsProvider winsProvider;

    public KothStatsExpansion(KothStatsPlugin plugin, StatsRepository repository,
                              KoreWinsProvider winsProvider) {
        this.plugin = plugin;
        this.repository = repository;
        this.winsProvider = winsProvider;
    }

    @Override
    public String getIdentifier() {
        return "kothstats";
    }

    @Override
    public String getAuthor() {
        return "LucasDunn";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        PlayerStats stats = repository.getOrCreate(player);
        if ("kills".equalsIgnoreCase(identifier)) {
            return String.valueOf(stats.getKills());
        }
        if ("deaths".equalsIgnoreCase(identifier)) {
            return String.valueOf(stats.getDeaths());
        }
        if ("damage_dealt".equalsIgnoreCase(identifier)) {
            return StatsFormatter.damage(stats.getDamageDealt());
        }
        if ("damage_received".equalsIgnoreCase(identifier)) {
            return StatsFormatter.damage(stats.getDamageReceived());
        }
        if ("capture_seconds".equalsIgnoreCase(identifier)) {
            return String.valueOf(stats.getCaptureSeconds());
        }
        if ("capture_time".equalsIgnoreCase(identifier)) {
            return StatsFormatter.duration(stats.getCaptureSeconds());
        }
        if ("koth_wins".equalsIgnoreCase(identifier)) {
            return winsProvider.getWins(player, player.getName());
        }
        return null;
    }
}
