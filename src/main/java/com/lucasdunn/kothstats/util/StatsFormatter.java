package com.lucasdunn.kothstats.util;

import com.lucasdunn.kothstats.model.PlayerStats;

import java.text.DecimalFormat;

public final class StatsFormatter {
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("0.0");

    private StatsFormatter() {
    }

    public static synchronized String damage(double value) {
        return DAMAGE_FORMAT.format(value);
    }

    public static String duration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public static String value(PlayerStats stats, String stat) {
        if ("kills".equals(stat)) {
            return String.valueOf(stats.getKills());
        }
        if ("deaths".equals(stat)) {
            return String.valueOf(stats.getDeaths());
        }
        if ("damage-dealt".equals(stat)) {
            return damage(stats.getDamageDealt());
        }
        if ("damage-received".equals(stat)) {
            return damage(stats.getDamageReceived());
        }
        return duration(stats.getCaptureSeconds());
    }
}
