package com.lucasdunn.kothstats.capture;

import com.lucasdunn.kothstats.storage.StatsRepository;
import com.lucasdunn.kothstats.session.KothSession;
import com.lucasdunn.kothstats.session.SessionManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CaptureTracker {
    private static final String LEGACY_ACTIVE_PLACEHOLDER =
        "%factionskore_koth_{koth}_active%";
    private static final String KORE_CURRENT_KOTH_PLACEHOLDER =
        "%kore_koth_currentkoth%";
    private static final String KORE_CAPPER_PLACEHOLDER =
        "%kore_koth_{koth}_capper%";

    private final JavaPlugin plugin;
    private final StatsRepository repository;
    private final SessionManager sessionManager;
    private final List<CaptureRegion> regions = new ArrayList<CaptureRegion>();
    private final Set<String> activeValues = new HashSet<String>();
    private final Set<String> capturerValues = new HashSet<String>();
    private boolean requireActivePlaceholder;
    private String activePlaceholder;
    private String activeKothNamePlaceholder;
    private String capturerPlaceholder;
    private boolean inferCapturer;
    private boolean placeholderApiAvailable;
    private BukkitTask task;

    public CaptureTracker(JavaPlugin plugin, StatsRepository repository,
                          SessionManager sessionManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    public void reload() {
        regions.clear();
        activeValues.clear();
        capturerValues.clear();
        requireActivePlaceholder = plugin.getConfig().getBoolean(
            "capture-tracking.require-active-placeholder", true);
        if (!requireActivePlaceholder) {
            plugin.getLogger().warning("Active KOTH checking is disabled. All configured KOTHs "
                + "will be treated as permanently active and statistics can be farmed.");
        }
        activePlaceholder = plugin.getConfig().getString(
            "capture-tracking.active-placeholder", "");
        activeKothNamePlaceholder = plugin.getConfig().getString(
            "capture-tracking.active-koth-name-placeholder",
            KORE_CURRENT_KOTH_PLACEHOLDER);
        capturerPlaceholder = plugin.getConfig().getString(
            "capture-tracking.capturer-placeholder", KORE_CAPPER_PLACEHOLDER);
        migrateLegacyKorePlaceholders();
        inferCapturer = plugin.getConfig().getBoolean(
            "capture-tracking.infer-capturer-when-placeholder-unavailable", true);

        for (String value : plugin.getConfig().getStringList("capture-tracking.active-values")) {
            activeValues.add(value.toLowerCase(Locale.ENGLISH));
        }
        for (String value : plugin.getConfig().getStringList(
            "capture-tracking.capturer-values")) {
            capturerValues.add(value.toLowerCase(Locale.ENGLISH));
        }

        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        placeholderApiAvailable = papi != null && papi.isEnabled();
        if (requireActivePlaceholder && !placeholderApiAvailable) {
            plugin.getLogger().warning(
                "Capture tracking requires PlaceholderAPI, but PlaceholderAPI is unavailable.");
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(
            "capture-tracking.regions");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                String path = "capture-tracking.regions." + name + ".";
                if (!plugin.getConfig().getBoolean(path + "enabled", true)) {
                    continue;
                }
                addRegion(name,
                    plugin.getConfig().getString(path + "corner-one"),
                    plugin.getConfig().getString(path + "corner-two"),
                    plugin.getConfig().getString(path + "area-corner-one"),
                    plugin.getConfig().getString(path + "area-corner-two"),
                    false);
            }
        }

        if (plugin.getConfig().getBoolean("kore-config-import.enabled", true)) {
            for (KoreKothDefinition definition : new KoreConfigImporter(plugin).load()) {
                addRegion(definition.getName(), definition.getCornerOne(),
                    definition.getCornerTwo(), null, null, true);
            }
        }
        plugin.getLogger().info("Loaded " + regions.size() + " capture region(s).");
    }

    private void migrateLegacyKorePlaceholders() {
        if (!LEGACY_ACTIVE_PLACEHOLDER.equalsIgnoreCase(activePlaceholder)) {
            return;
        }

        activePlaceholder = "";
        activeKothNamePlaceholder = KORE_CURRENT_KOTH_PLACEHOLDER;
        capturerPlaceholder = KORE_CAPPER_PLACEHOLDER;
        plugin.getConfig().set("capture-tracking.active-placeholder", activePlaceholder);
        plugin.getConfig().set("capture-tracking.active-koth-name-placeholder",
            activeKothNamePlaceholder);
        plugin.getConfig().set("capture-tracking.capturer-placeholder",
            capturerPlaceholder);
        plugin.saveConfig();
        plugin.getLogger().info("Migrated capture tracking to KORE's current KOTH "
            + "and capturer placeholders.");
    }

    private void addRegion(String name, String firstValue, String secondValue,
                           String areaFirstValue, String areaSecondValue, boolean imported) {
        for (CaptureRegion existing : regions) {
            if (existing.getName().equalsIgnoreCase(name)) {
                if (!imported) {
                    plugin.getLogger().warning("Duplicate capture region ignored: " + name);
                }
                return;
            }
        }

        Location first = parseLocation(firstValue);
        Location second = parseLocation(secondValue);
        if (first == null || second == null || first.getWorld() != second.getWorld()) {
            plugin.getLogger().warning("Skipping invalid capture region: " + name);
            return;
        }
        Location areaFirst = parseLocation(areaFirstValue);
        Location areaSecond = parseLocation(areaSecondValue);
        if ((areaFirst == null) != (areaSecond == null)
            || (areaFirst != null && (areaFirst.getWorld() != areaSecond.getWorld()
            || areaFirst.getWorld() != first.getWorld()))) {
            plugin.getLogger().warning("Invalid general KOTH area for " + name
                + "; falling back to current-koth.combat-radius.");
            areaFirst = null;
            areaSecond = null;
        }
        regions.add(new CaptureRegion(name, first, second, areaFirst, areaSecond));
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        List<CaptureRegion> activeRegions = new ArrayList<CaptureRegion>();
        Player context = Bukkit.getOnlinePlayers().isEmpty()
            ? null : Bukkit.getOnlinePlayers().iterator().next();
        for (CaptureRegion region : regions) {
            if (isActive(context, region)) {
                activeRegions.add(region);
            }
        }
        sessionManager.syncActive(activeRegions);

        for (CaptureRegion region : activeRegions) {
            KothSession session = sessionManager.getActive(region.getName());
            List<Player> onCapturePoint = new ArrayList<Player>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (region.containsKothArea(
                    player.getLocation(), sessionManager.getFallbackAreaRadius())) {
                    sessionManager.registerParticipant(session, player);
                }
                if (region.contains(player.getLocation())) {
                    sessionManager.registerParticipant(session, player);
                    onCapturePoint.add(player);
                }
            }
            Player capturer = resolveCapturer(region, onCapturePoint);
            if (capturer != null) {
                repository.getOrCreate(capturer).incrementCaptureSeconds();
                sessionManager.recordCaptureSecond(session, capturer);
            }
        }
    }

    private Player resolveCapturer(CaptureRegion region, List<Player> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        boolean placeholderResolved = false;
        if (placeholderApiAvailable && capturerPlaceholder != null
            && !capturerPlaceholder.isEmpty()) {
            String placeholder = capturerPlaceholder.replace("{koth}", region.getName());
            for (Player candidate : candidates) {
                String result = PlaceholderAPI.setPlaceholders(candidate, placeholder);
                if (result == null || result.equals(placeholder) || result.contains("%")) {
                    continue;
                }
                placeholderResolved = true;
                String value = ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', result)).trim();
                if (candidate.getName().equalsIgnoreCase(value)
                    || capturerValues.contains(value.toLowerCase(Locale.ENGLISH))) {
                    return candidate;
                }
            }
            if (placeholderResolved) {
                return null;
            }
        }

        if (!inferCapturer || candidates.size() != 1) {
            return null;
        }
        return candidates.get(0);
    }

    private boolean isActive(Player player, CaptureRegion region) {
        if (!requireActivePlaceholder) {
            return true;
        }
        if (!placeholderApiAvailable || activePlaceholder == null || activePlaceholder.isEmpty()) {
            return matchesActiveKothName(player, region);
        }
        String placeholder = activePlaceholder.replace("{koth}", region.getName());
        String result = PlaceholderAPI.setPlaceholders(player, placeholder);
        if (result != null
            && activeValues.contains(result.trim().toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return matchesActiveKothName(player, region);
    }

    private boolean matchesActiveKothName(Player player, CaptureRegion region) {
        if (!placeholderApiAvailable || activeKothNamePlaceholder == null
            || activeKothNamePlaceholder.isEmpty()) {
            return false;
        }
        String result = PlaceholderAPI.setPlaceholders(player, activeKothNamePlaceholder);
        if (result == null) {
            return false;
        }
        String activeName = ChatColor.stripColor(
            ChatColor.translateAlternateColorCodes('&', result)).trim();
        return region.getName().equalsIgnoreCase(activeName);
    }

    private Location parseLocation(String serialized) {
        if (serialized == null) {
            return null;
        }
        String[] parts = serialized.split(":");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[3]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            if (!isFinite(x) || !isFinite(y) || !isFinite(z)) {
                return null;
            }
            return new Location(world, x, y, z);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
