package com.lucasdunn.kothstats.listener;

import com.lucasdunn.kothstats.command.KothStatsCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
public final class KothCommandIntegration implements Listener {
    private final KothStatsCommand command;

    public KothCommandIntegration(KothStatsCommand command) {
        this.command = command;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 2) {
            return;
        }
        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length < 2 || !"koth".equalsIgnoreCase(parts[0])
            || !"stats".equalsIgnoreCase(parts[1])) {
            return;
        }

        event.setCancelled(true);
        command.executeIntegrated(event.getPlayer(),
            Arrays.copyOfRange(parts, 2, parts.length));
    }
}
