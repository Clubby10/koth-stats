package com.lucasdunn.kothstats;

import com.lucasdunn.kothstats.capture.CaptureTracker;
import com.lucasdunn.kothstats.command.KothStatsCommand;
import com.lucasdunn.kothstats.hook.KothStatsExpansion;
import com.lucasdunn.kothstats.hook.KoreWinsProvider;
import com.lucasdunn.kothstats.gui.CurrentKothGui;
import com.lucasdunn.kothstats.listener.CombatListener;
import com.lucasdunn.kothstats.listener.KothCommandIntegration;
import com.lucasdunn.kothstats.listener.ParticipationListener;
import com.lucasdunn.kothstats.session.SessionManager;
import com.lucasdunn.kothstats.storage.StatsRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class KothStatsPlugin extends JavaPlugin {
    private StatsRepository repository;
    private CaptureTracker captureTracker;
    private SessionManager sessionManager;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new StatsRepository(this);
        repository.load();

        sessionManager = new SessionManager(this);
        captureTracker = new CaptureTracker(this, repository, sessionManager);
        captureTracker.reload();
        captureTracker.start();

        CurrentKothGui currentKothGui = new CurrentKothGui(this, sessionManager);
        Bukkit.getPluginManager().registerEvents(
            new CombatListener(repository, sessionManager), this);
        Bukkit.getPluginManager().registerEvents(
            new ParticipationListener(sessionManager), this);
        Bukkit.getPluginManager().registerEvents(currentKothGui, this);
        KoreWinsProvider winsProvider = new KoreWinsProvider(this);
        KothStatsCommand statsCommand =
            new KothStatsCommand(this, repository, currentKothGui, winsProvider);
        getCommand("kothstats").setExecutor(statsCommand);
        getCommand("kothstats").setTabCompleter(statsCommand);
        Bukkit.getPluginManager().registerEvents(
            new KothCommandIntegration(statsCommand), this);
        getLogger().info("Registered KothStats as the /koth stats handler.");

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new KothStatsExpansion(this, repository, winsProvider).register();
            getLogger().info("Registered PlaceholderAPI placeholders.");
        }
        scheduleAutosave();
        getLogger().info("KothStats enabled.");
    }

    @Override
    public void onDisable() {
        if (captureTracker != null) {
            captureTracker.stop();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (repository != null) {
            repository.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        sessionManager.reload();
        captureTracker.reload();
        scheduleAutosave();
    }

    public String message(String key) {
        String value = getConfig().getString("messages." + key);
        return value == null ? "" : value;
    }

    private void scheduleAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long seconds = Math.max(30L, getConfig().getLong("autosave-seconds", 300L));
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                repository.save();
            }
        }, seconds * 20L, seconds * 20L);
    }
}
