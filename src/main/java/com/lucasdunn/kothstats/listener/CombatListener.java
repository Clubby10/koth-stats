package com.lucasdunn.kothstats.listener;

import com.lucasdunn.kothstats.model.PlayerStats;
import com.lucasdunn.kothstats.storage.StatsRepository;
import com.lucasdunn.kothstats.session.KothSession;
import com.lucasdunn.kothstats.session.SessionManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements Listener {
    private final StatsRepository repository;
    private final SessionManager sessionManager;

    public CombatListener(StatsRepository repository, SessionManager sessionManager) {
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageReceived(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        KothSession session = sessionManager.getSessionAt(victim.getLocation());
        if (session == null) {
            return;
        }
        double effectiveDamage = Math.min(event.getFinalDamage(), victim.getHealth());
        repository.getOrCreate(victim).addDamageReceived(effectiveDamage);
        sessionManager.recordDamageReceived(victim, victim.getLocation(), effectiveDamage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        KothSession session = sessionManager.getSessionAt(victim.getLocation());
        if (session == null) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || attacker.equals(event.getEntity())) {
            return;
        }
        double effectiveDamage = Math.min(event.getFinalDamage(), victim.getHealth());
        repository.getOrCreate(attacker).addDamageDealt(effectiveDamage);
        sessionManager.recordDamageDealt(attacker, victim.getLocation(), effectiveDamage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        KothSession session = sessionManager.getSessionAt(victim.getLocation());
        if (session == null) {
            return;
        }
        repository.getOrCreate(victim).incrementDeaths();
        sessionManager.recordDeath(victim, victim.getLocation());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            repository.getOrCreate(killer).incrementKills();
            sessionManager.recordKill(killer, victim, victim.getLocation());
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }
}
