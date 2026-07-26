package com.lucasdunn.kothstats.session;

import com.lucasdunn.kothstats.capture.CaptureRegion;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class KothSession {
    private final CaptureRegion region;
    private final long startedAt;
    private final Map<UUID, SessionPlayerStats> players =
        new HashMap<UUID, SessionPlayerStats>();
    private final Set<UUID> trackedPlayers = new HashSet<UUID>();
    private final Map<String, Long> lastKillCredits = new HashMap<String, Long>();

    public KothSession(CaptureRegion region) {
        this.region = region;
        this.startedAt = System.currentTimeMillis();
    }

    public String getName() {
        return region.getName();
    }

    public CaptureRegion getRegion() {
        return region;
    }

    public long getElapsedSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
    }

    public SessionPlayerStats getOrCreate(Player player) {
        SessionPlayerStats stats = players.get(player.getUniqueId());
        if (stats == null) {
            stats = new SessionPlayerStats(player.getUniqueId(), player.getName());
            players.put(player.getUniqueId(), stats);
        } else {
            stats.setName(player.getName());
        }
        return stats;
    }

    public void register(Player player) {
        getOrCreate(player);
        trackedPlayers.add(player.getUniqueId());
    }

    public Collection<SessionPlayerStats> getPlayers() {
        return new ArrayList<SessionPlayerStats>(players.values());
    }

    public boolean isTracking(Player player) {
        return trackedPlayers.contains(player.getUniqueId());
    }

    public void stopTracking(Player player) {
        trackedPlayers.remove(player.getUniqueId());
    }

    public boolean creditKill(Player killer, Player victim, long cooldownMillis) {
        String key = killer.getUniqueId().toString() + ":" + victim.getUniqueId().toString();
        long now = System.currentTimeMillis();
        Long previous = lastKillCredits.get(key);
        if (previous != null && now - previous < cooldownMillis) {
            return false;
        }
        lastKillCredits.put(key, now);
        getOrCreate(killer).incrementKills();
        return true;
    }
}
