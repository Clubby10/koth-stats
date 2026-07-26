package com.lucasdunn.kothstats.model;

import java.util.UUID;

public final class PlayerStats {
    private final UUID uuid;
    private volatile String lastName;
    private volatile double damageDealt;
    private volatile double damageReceived;
    private volatile long kills;
    private volatile long deaths;
    private volatile long captureSeconds;

    public PlayerStats(UUID uuid, String lastName) {
        this.uuid = uuid;
        this.lastName = lastName;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        if (lastName != null && !lastName.isEmpty()) {
            this.lastName = lastName;
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

    public void reset() {
        damageDealt = 0.0D;
        damageReceived = 0.0D;
        kills = 0L;
        deaths = 0L;
        captureSeconds = 0L;
    }

    public void load(double dealt, double received, long kills, long deaths, long captureSeconds) {
        this.damageDealt = isValidDamage(dealt) ? dealt : 0.0D;
        this.damageReceived = isValidDamage(received) ? received : 0.0D;
        this.kills = Math.max(0L, kills);
        this.deaths = Math.max(0L, deaths);
        this.captureSeconds = Math.max(0L, captureSeconds);
    }

    private boolean isValidDamage(double amount) {
        return amount >= 0.0D && !Double.isNaN(amount) && !Double.isInfinite(amount);
    }
}
