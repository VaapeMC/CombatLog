package me.vaape.combatlog;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import de.ancash.actionbar.ActionBarAPI;
import me.vaape.events.Fishing;
import me.vaape.events.GuildWars;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class CombatLog extends JavaPlugin implements Listener {

    public static CombatLog instance;

    public HashMap<UUID, Integer> logoutCooldown = new HashMap<UUID, Integer>();
    private final HashMap<UUID, BukkitRunnable> logoutCooldownTask = new HashMap<UUID, BukkitRunnable>();

    public void onEnable() {
        instance = this;
        getLogger().info(ChatColor.GREEN + "CombatLog has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void onDisable() {
        instance = null;
    }

    //Enderpearl
    @EventHandler
    public void onPearl(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof EnderPearl) {
            Player player = event.getPlayer();

            runCooldown(player, 7);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        UUID UUID = event.getPlayer().getUniqueId();
        if (logoutCooldown.containsKey(UUID)) {

            ArrayList<String> blockedCommands = new ArrayList<String>();
            blockedCommands.add("/tpa");
            blockedCommands.add("/spawn");
            blockedCommands.add("/warp");
            blockedCommands.add("/g home");
            blockedCommands.add("/guild home");
            blockedCommands.add("/g chest");
            blockedCommands.add("/guild chest");
            blockedCommands.add("/home");

            for (String command : blockedCommands) {

                if (event.getMessage().startsWith(command)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot perform this command in combat.");
                    return;
                }
            }
        }
    }

    //Hit by player or arrow shot by player
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {

        Location location = event.getEntity().getLocation();

        //Do not apply in spawn or fishing area
        if (GuildWars.inSpawn(location) || Fishing.inFish(location)) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Player) {

                runCooldown(player, 30);
            } else if (event.getDamager() instanceof Arrow arrow) {
                if (arrow.getShooter() instanceof Player) {
                    runCooldown(player, 30);
                }
            }
        }
    }

    //Lightning
    @EventHandler
    public void onLightningDamage(EntityDamageEvent event) {
        if (event.getCause() == DamageCause.LIGHTNING) {

            Location location = event.getEntity().getLocation();

            //Do not apply in spawn or fishing area
            if (GuildWars.inSpawn(location) || Fishing.inFish(location)) {
                return;
            }

            if (event.getEntity() instanceof Player player) {
                runCooldown(player, 30);
            }
        }
    }

    @EventHandler
    public void onLogin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        Location location = player.getLocation();

        //Do not apply in spawn or fishing area
        if (GuildWars.inSpawn(location) || Fishing.inFish(location)) {
            return;
        }

        //runCooldown(player, 0);
    }

    @EventHandler
    public void onLogout(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        if (GuildWars.inSpawn(player.getLocation())) {
            logoutCooldown.remove(player.getUniqueId());
        }

        if (logoutCooldown.containsKey(player.getUniqueId())) {
            if (!GuildWars.inCastle(player.getLocation()) || !Fishing.inFish(player.getLocation())) {
                Inventory inventory = player.getInventory();

                for (int i = 0; i < inventory.getSize(); i++) { //Loop through each item slot in inventory
                    if (inventory.getItem(i) != null) {
                        ItemStack item = inventory.getItem(i);
                        if (i > 35 && i < 40) { //Armour drops 100% of the time due to god effects
                            player.getWorld().dropItemNaturally(player.getLocation(), item);
                            inventory.setItem(i, new ItemStack(Material.AIR));
                        } else {
                            double random = Math.random();
                            if (random > 0.5) { //50% chance;
                                player.getWorld().dropItemNaturally(player.getLocation(), item);
                                inventory.setItem(i, new ItemStack(Material.AIR));
                            }
                        }
                    }
                }
                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + player.getName() + " has combat logged");
            }
        }
    }

    //Remove combat timer on death
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID UUID = event.getEntity().getUniqueId();
        if (logoutCooldown.containsKey(UUID)) {
            logoutCooldown.remove(UUID);
            logoutCooldownTask.get(UUID).cancel(); //Cancel original task
            logoutCooldownTask.remove(UUID);
        }
    }

    private void runCooldown(Player player, int seconds) {

        if (player.hasPermission("combatlog.bypass")) {
            return;
        }

        UUID UUID = player.getUniqueId();
        ActionBarAPI.sendActionBar(player, ChatColor.RED + "Combat tag: " + seconds + " seconds");

        if (logoutCooldown.containsKey(UUID)) { //If already on cooldown
            logoutCooldown.remove(UUID);
            logoutCooldownTask.get(UUID).cancel(); //Cancel original task
            logoutCooldownTask.remove(UUID);
        }

        logoutCooldown.put(UUID, seconds);
        logoutCooldownTask.put(UUID, new BukkitRunnable() {

            @Override
            public void run() {
                logoutCooldown.put(UUID, logoutCooldown.get(UUID) - 1); //Lower cooldown by 1 second
                ActionBarAPI.sendActionBar(player, ChatColor.RED + "Combat tag: " + logoutCooldown.get(UUID) + " " +
                        "seconds");
                if (logoutCooldown.get(UUID) == 0) {
                    logoutCooldown.remove(UUID);
                    logoutCooldownTask.remove(UUID);
                    ActionBarAPI.sendActionBar(player, ChatColor.GREEN + "Combat tag expired");
                    cancel();
                }
            }
        });

        logoutCooldownTask.get(UUID).runTaskTimer(instance, 20, 20);
    }
}
