package com.lucasdunn.kothstats.session;

import java.util.UUID;

public final class SessionPlayerStats {
    private final UUID uuid;
    private String name;
    private double damageDealt;
    private double damageReceived;
    private long kills;
    private long deaths;
    private long captureSeconds;

    public SessionPlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public void addDamageDealt(double amount) {
        if (isValidDamage(amount)) {
            damageDealt += amount;
        }
    }

    public double getDamageReceived() {
        return damageReceived;
    }

    public void addDamageReceived(double amount) {
        if (isValidDamage(amount)) {
            damageReceived += amount;
        }
    }

    public long getKills() {
        return kills;
    }

    public void incrementKills() {
        kills++;
    }

    public long getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        deaths++;
    }

    public long getCaptureSeconds() {
        return captureSeconds;
    }

    public void incrementCaptureSeconds() {
        captureSeconds++;
    }

    private boolean isValidDamage(double amount) {
        return amount >= 0.0D && !Double.isNaN(amount) && !Double.isInfinite(amount);
    }
}
