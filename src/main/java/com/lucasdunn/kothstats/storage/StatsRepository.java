package com.lucasdunn.kothstats.storage;

import com.lucasdunn.kothstats.model.PlayerStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StatsRepository {
    private static final long MAX_STATS_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_PLAYERS = 100000;
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerStats> stats = new HashMap<UUID, PlayerStats>();
    private boolean safeToSave = true;

    public StatsRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public synchronized void load() {
        stats.clear();
        safeToSave = true;
        if (file.exists() && file.length() > MAX_STATS_FILE_BYTES) {
            plugin.getLogger().severe(
                "stats.yml exceeds 16 MB; refusing to load or overwrite it.");
            safeToSave = false;
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        if (file.exists()) {
            try {
                data.load(file);
            } catch (IOException exception) {
                plugin.getLogger().severe(
                    "Could not read stats.yml; refusing to overwrite it.");
                safeToSave = false;
                return;
            } catch (InvalidConfigurationException exception) {
                plugin.getLogger().severe(
                    "stats.yml is malformed; refusing to overwrite it.");
                safeToSave = false;
                return;
            }
        }
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String key : players.getKeys(false)) {
            if (stats.size() >= MAX_PLAYERS) {
                plugin.getLogger().warning(
                    "stats.yml player limit reached; remaining entries were ignored.");
                break;
            }
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key + ".";
                PlayerStats value = new PlayerStats(uuid,
                    sanitizeName(data.getString(path + "name", "Unknown")));
                value.load(
                    data.getDouble(path + "damage-dealt"),
                    data.getDouble(path + "damage-received"),
                    data.getLong(path + "kills"),
                    data.getLong(path + "deaths"),
                    data.getLong(path + "capture-seconds")
                );
                stats.put(uuid, value);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid UUID in stats.yml: " + key);
            }
        }
    }

    public synchronized void save() {
        if (!safeToSave) {
            plugin.getLogger().severe(
                "Statistics were not saved because stats.yml did not load safely.");
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        for (PlayerStats value : stats.values()) {
            String path = "players." + value.getUuid().toString() + ".";
            data.set(path + "name", value.getLastName());
            data.set(path + "damage-dealt", value.getDamageDealt());
            data.set(path + "damage-received", value.getDamageReceived());
            data.set(path + "kills", value.getKills());
            data.set(path + "deaths", value.getDeaths());
            data.set(path + "capture-seconds", value.getCaptureSeconds());
        }

        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save stats.yml: " + exception.getMessage());
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException ignored) {
                // The next successful save will replace this temporary file.
            }
        }
    }

    public synchronized PlayerStats getOrCreate(Player player) {
        return getOrCreate(player.getUniqueId(), player.getName());
    }

    public synchronized PlayerStats getOrCreate(UUID uuid, String name) {
        String safeName = sanitizeName(name);
        PlayerStats value = stats.get(uuid);
        if (value == null) {
            if (stats.size() >= MAX_PLAYERS) {
                return new PlayerStats(uuid, safeName);
            }
            value = new PlayerStats(uuid, safeName);
            stats.put(uuid, value);
        } else {
            value.setLastName(safeName);
        }
        return value;
    }

    public synchronized PlayerStats findByName(String name) {
        for (PlayerStats value : stats.values()) {
            if (value.getLastName() != null && value.getLastName().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    public synchronized Collection<PlayerStats> all() {
        return new ArrayList<PlayerStats>(stats.values());
    }

    public synchronized int resetAll() {
        int reset = stats.size();
        for (PlayerStats value : stats.values()) {
            value.reset();
        }
        save();
        return reset;
    }

    private String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}]", "");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }
}
