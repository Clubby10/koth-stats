package com.lucasdunn.kothstats.capture;

import org.bukkit.Location;

public final class CaptureRegion {
    private final String name;
    private final String world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final Bounds kothArea;

    public CaptureRegion(String name, Location first, Location second) {
        this(name, first, second, null, null);
    }

    public CaptureRegion(String name, Location first, Location second,
                         Location areaFirst, Location areaSecond) {
        this.name = name;
        this.world = first.getWorld().getName();
        this.minX = Math.min(first.getX(), second.getX());
        this.minY = Math.min(first.getY(), second.getY());
        this.minZ = Math.min(first.getZ(), second.getZ());
        this.maxX = Math.max(first.getX(), second.getX());
        this.maxY = Math.max(first.getY(), second.getY());
        this.maxZ = Math.max(first.getZ(), second.getZ());
        this.kothArea = areaFirst == null || areaSecond == null
            ? null : new Bounds(areaFirst, areaSecond);
    }

    public String getName() {
        return name;
    }

    public boolean contains(Location location) {
        return location.getWorld() != null
            && world.equals(location.getWorld().getName())
            && location.getX() >= minX && location.getX() <= maxX
            && location.getY() >= minY && location.getY() <= maxY
            && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    public double distanceSquared(Location location) {
        return distanceSquared(location, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean containsKothArea(Location location, double fallbackRadius) {
        if (kothArea != null) {
            return kothArea.contains(location);
        }
        return distanceSquared(location) <= fallbackRadius * fallbackRadius;
    }

    private double distanceSquared(Location location, double lowX, double lowY, double lowZ,
                                   double highX, double highY, double highZ) {
        if (location.getWorld() == null || !world.equals(location.getWorld().getName())) {
            return Double.MAX_VALUE;
        }
        double dx = axisDistance(location.getX(), lowX, highX);
        double dy = axisDistance(location.getY(), lowY, highY);
        double dz = axisDistance(location.getZ(), lowZ, highZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0D;
    }

    private final class Bounds {
        private final double lowX;
        private final double lowY;
        private final double lowZ;
        private final double highX;
        private final double highY;
        private final double highZ;

        private Bounds(Location first, Location second) {
            this.lowX = Math.min(first.getX(), second.getX());
            this.lowY = Math.min(first.getY(), second.getY());
            this.lowZ = Math.min(first.getZ(), second.getZ());
            this.highX = Math.max(first.getX(), second.getX());
            this.highY = Math.max(first.getY(), second.getY());
            this.highZ = Math.max(first.getZ(), second.getZ());
        }

        private boolean contains(Location location) {
            return location.getWorld() != null
                && world.equals(location.getWorld().getName())
                && location.getX() >= lowX && location.getX() <= highX
                && location.getY() >= lowY && location.getY() <= highY
                && location.getZ() >= lowZ && location.getZ() <= highZ;
        }
    }
}
