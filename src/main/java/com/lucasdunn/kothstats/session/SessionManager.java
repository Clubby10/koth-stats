package com.lucasdunn.kothstats.session;

import com.lucasdunn.kothstats.capture.CaptureRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SessionManager {
    private final JavaPlugin plugin;
    private final Map<String, KothSession> activeSessions =
        new HashMap<String, KothSession>();
    private double combatRadius;
    private long killCreditCooldownMillis;

    public SessionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        double configured = plugin.getConfig().getDouble(
            "current-koth.combat-radius", 100.0D);
        combatRadius = Double.isNaN(configured) || Double.isInfinite(configured)
            ? 100.0D : Math.max(0.0D, Math.min(configured, 10000.0D));
        long cooldownSeconds = Math.max(0L, Math.min(3600L,
            plugin.getConfig().getLong("anti-farm.kill-credit-cooldown-seconds", 30L)));
        killCreditCooldownMillis = cooldownSeconds * 1000L;
    }

    public void syncActive(Collection<CaptureRegion> regions) {
        Set<String> activeNames = new HashSet<String>();
        for (CaptureRegion region : regions) {
            activeNames.add(region.getName().toLowerCase());
            if (!activeSessions.containsKey(region.getName().toLowerCase())) {
                activeSessions.put(region.getName().toLowerCase(), new KothSession(region));
                plugin.getLogger().info("Started statistics session for KOTH " + region.getName());
            }
        }

        for (String name : new HashSet<String>(activeSessions.keySet())) {
            if (!activeNames.contains(name)) {
                activeSessions.remove(name);
                plugin.getLogger().info("Ended statistics session for KOTH " + name);
            }
        }
    }

    public KothSession getSessionAt(Location location) {
        KothSession closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (KothSession session : getActiveSessions()) {
            double distance = session.getRegion().distanceSquared(location);
            if (session.getRegion().containsKothArea(location, combatRadius)) {
                if (distance < closestDistance) {
                    closest = session;
                    closestDistance = distance;
                }
                continue;
            }
        }
        return closest;
    }

    public KothSession getCurrent() {
        List<KothSession> sessions = getActiveSessions();
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    public KothSession getActive(String name) {
        return name == null ? null : activeSessions.get(name.toLowerCase());
    }

    public List<KothSession> getActiveSessions() {
        List<KothSession> sessions = new ArrayList<KothSession>(activeSessions.values());
        Collections.sort(sessions, new Comparator<KothSession>() {
            @Override
            public int compare(KothSession first, KothSession second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });
        return sessions;
    }

    public void recordDamageDealt(Player player, Location location, double damage) {
        KothSession session = getSessionForPlayer(player, location);
        if (session != null) {
            session.getOrCreate(player).addDamageDealt(damage);
        }
    }

    public void recordDamageReceived(Player player, Location location, double damage) {
        KothSession session = getSessionForPlayer(player, location);
        if (session != null) {
            session.getOrCreate(player).addDamageReceived(damage);
        }
    }

    public void recordKill(Player killer, Player victim, Location location) {
        KothSession session = getSessionForPlayer(killer, location);
        if (session != null) {
            session.creditKill(killer, victim, killCreditCooldownMillis);
        }
    }

    public void recordDeath(Player player, Location location) {
        KothSession session = getSessionForPlayer(player, location);
        if (session != null) {
            session.getOrCreate(player).incrementDeaths();
        }
    }

    public void recordCaptureSecond(KothSession session, Player player) {
        if (session != null) {
            session.getOrCreate(player).incrementCaptureSeconds();
        }
    }

    public void registerParticipant(KothSession session, Player player) {
        if (session != null) {
            session.register(player);
        }
    }

    public double getFallbackAreaRadius() {
        return combatRadius;
    }

    public void handleTeleport(Player player, Location destination) {
        for (KothSession session : activeSessions.values()) {
            if (session.isTracking(player)
                && (destination == null || !session.getRegion().containsKothArea(
                destination, combatRadius))) {
                session.stopTracking(player);
            }
        }
    }

    public void stopTracking(Player player) {
        for (KothSession session : activeSessions.values()) {
            session.stopTracking(player);
        }
    }

    private KothSession getSessionForPlayer(Player player, Location location) {
        KothSession atLocation = getSessionAt(location);
        if (atLocation != null) {
            atLocation.register(player);
            return atLocation;
        }
        for (KothSession session : getActiveSessions()) {
            if (session.isTracking(player)) {
                return session;
            }
        }
        return null;
    }
}
