package beansplusplus.deathswap;

import beansplusplus.gameconfig.GameConfiguration;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class Game implements Listener {
  private Plugin plugin;

  private int swapTime;
  private int timeTilSwap;
  private int immunityTime;
  private boolean isImmune;
  private int pointsToWin;
  private int swapPointsPeriod;
  private int gracePeriod;
  private boolean uniqueDeaths;
  private boolean hunger;
  private boolean showTimerAlways;

  private Random random = new Random();

  private Map<String, Integer> scores = new HashMap<>();

  private Map<String, String> teleportedBy = new HashMap<>();

  private Map<String, Set<String>> deaths = new HashMap<>();

  private float TELEPORT_BORDER = 1500;

  public Game(DeathSwapPlugin plugin) {
    this.plugin = plugin;

    pointsToWin = GameConfiguration.getConfig().getValue("points_to_win");
    swapTime = (int) ((double) GameConfiguration.getConfig().getValue("round_time_minutes") * 60.0);
    gracePeriod = (int) ((double) GameConfiguration.getConfig().getValue("grace_period_minutes") * 60.0);
    immunityTime = (int) ((double) GameConfiguration.getConfig().getValue("swap_immunity_seconds") * 20.0);
    uniqueDeaths = GameConfiguration.getConfig().getValue("unique_deaths");
    hunger = GameConfiguration.getConfig().getValue("hunger");
    swapPointsPeriod = (int) ((double) GameConfiguration.getConfig().getValue("swap_points_period_minutes") * 60.0);
    showTimerAlways = GameConfiguration.getConfig().getValue("always_show_timer");
  }

  public void start() {
    for (World world : Bukkit.getWorlds()) {
      world.setGameRule(GameRule.KEEP_INVENTORY, true);
      world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
      world.setPVP(false);
    }

    World world = Bukkit.getWorld("world");
    world.setTime(1000);

    timeTilSwap = swapTime + gracePeriod;
    isImmune = false;

    for (Player player : Bukkit.getOnlinePlayers()) {
      clearAllEffects(player);
      player.setGameMode(GameMode.SURVIVAL);

      scores.put(player.getName(), 0);
    }

    teleportToStartLocations();

    refreshScoreboard();

    Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::runTimer, 20, 20);
  }

  public void end() {
    HandlerList.unregisterAll(this);
    Bukkit.getScheduler().cancelTasks(plugin);

    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
      player.setGameMode(GameMode.SURVIVAL);
      clearAllEffects(player);
      safeTeleport(player, Bukkit.getWorld("world").getSpawnLocation());
    }
  }

  public void showCompletedDeaths(Player player) {
    if (!uniqueDeaths) {
      player.sendMessage(ChatColor.RED + "Unique deaths is off. Any death is allowed!");
      return;
    }

    Set<String> deathNames = deaths.get(player.getName());

    if (deathNames == null || deathNames.isEmpty()) {
      player.sendMessage(ChatColor.RED + "You have not killed anyone yet.");
      return;
    }

    player.sendMessage(ChatColor.YELLOW + "Kills you have already done:");

    for (String deathName : deathNames) {
      player.sendMessage("- " + simpleDeathName(deathName));
    }
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent e) {
    Player player = e.getEntity();

    if (!teleportedBy.containsKey(player.getName())) return;

    String killer = teleportedBy.get(player.getName());
    String causeStr = getDeathCause(player.getLastDamageCause());

    if (!deathAllowedNow(killer, causeStr)) return;

    scores.put(killer, scores.getOrDefault(killer, 0) + 1);

    notifyOfDeath(killer, player.getName(), causeStr);

    if (checkForWinner(killer)) return;

    addNewDeath(killer, causeStr);

    refreshScoreboard();
  }

  @EventHandler
  public void onEntityDamage(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player) {
      if (isImmune) e.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    if (!scores.containsKey(e.getPlayer().getName())) {
      scores.put(e.getPlayer().getName(), 0);
      safeTeleport(e.getPlayer(), randomLocation());
    }

    refreshScoreboard();
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent e) {
    refreshScoreboard();
  }


  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent e) {
    Player player = e.getPlayer();
    player.setGameMode(GameMode.SURVIVAL);
    e.setRespawnLocation(randomLocation());
    teleportedBy.remove(player.getName());
  }

  @EventHandler
  public void onFoodLevelChange(FoodLevelChangeEvent e) {
    if (!hunger) e.setCancelled(true);
  }

  private void runTimer() {
    timeTilSwap--;

    if (timeTilSwap == 0) {
      swapPlayers();
    } else {
      if (timeTilSwap + swapPointsPeriod == swapTime) {
        for (String pName : teleportedBy.values()) {
          Player player = Bukkit.getPlayer(pName);
          if (player == null) continue;

          player.sendMessage(ChatColor.YELLOW + "Swap period over. You will not get any points for deaths until the next swap.");
        }
      }

      if (timeTilSwap <= 5 || timeTilSwap == 30 || timeTilSwap == 60) {
        for (Player player : Bukkit.getOnlinePlayers()) {
          player.sendMessage(ChatColor.YELLOW + "Swapping in " + timeTilSwap + "s...");
        }
      }
    }

    refreshScoreboard();
  }

  private void teleportToStartLocations() {
    World world = Bukkit.getWorld("world");

    Location spawn = world.getSpawnLocation();

    int x = spawn.getBlockX();
    int z = spawn.getBlockZ();

    float angle = (float) (Math.random() * Math.PI * 2);

    float angleDif = ((float) Math.PI * 2.0f) / (float) Bukkit.getOnlinePlayers().size();

    for (Player player : Bukkit.getOnlinePlayers()) {
      safeTeleport(player, getFarLocation(x, z, 1000, angle));

      angle += angleDif;
    }
  }

  private void swapPlayers() {
    timeTilSwap = swapTime;

    List<LivingEntity> entities = despawnableEntities();

    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    List<Player> shuffledPlayers = new ArrayList<>(players);
    List<Location> shuffledPlayerLocationsPreTeleport = new ArrayList<>();

    while (!isShuffled(players, shuffledPlayers)) {
      Collections.shuffle(shuffledPlayers);
    }

    for (Player player : shuffledPlayers) {
      shuffledPlayerLocationsPreTeleport.add(player.getLocation());
    }

    for (int i = 0; i < players.size(); i++) {
      teleportedBy.put(players.get(i).getName(), shuffledPlayers.get(i).getName());

      Player player = players.get(i);
      Location location = shuffledPlayerLocationsPreTeleport.get(i);

      safeTeleport(player, location);
      player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

      player.sendMessage(ChatColor.BLUE + "You teleported to " + ChatColor.GREEN + shuffledPlayers.get(i).getName());
    }

    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
      for (LivingEntity e : entities) {
        e.setRemoveWhenFarAway(true);
      }
    }, 20);

    for (int i = 0; i < players.size(); i++) {
      shuffledPlayers.get(i).sendMessage(ChatColor.GREEN + players.get(i).getName() + ChatColor.BLUE + " teleported to you");
    }

    if (immunityTime <= 0) return;

    isImmune = true;
    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
      isImmune = false;
    }, immunityTime);
  }

  private List<LivingEntity> despawnableEntities() {
    List<LivingEntity> entities = new ArrayList<>();

    for (Player player : Bukkit.getOnlinePlayers()) {
      for (Entity entity : player.getLocation().getWorld().getNearbyEntities(player.getLocation(), 64, 64, 64)) {
        if (!(entity instanceof LivingEntity)) continue;

        LivingEntity livingEntity = (LivingEntity) entity;

        if (!livingEntity.getRemoveWhenFarAway()) continue;

        livingEntity.setRemoveWhenFarAway(false);
        entities.add(livingEntity);
      }
    }

    return entities;
  }

  private Location getFarLocation(int x, int z, int dist, float angle) {
    float px = (int) (x + Math.sin(angle) * dist) + 0.5f;
    float pz = (int) (z + Math.cos(angle) * dist) + 0.5f;

    Location l = topLocation(px, pz);

    if (l.getBlock().isLiquid()) return getFarLocation(x, z, dist - 5, angle);  // prevent landing in an ocean

    return l.add(0, 1, 0);
  }

  private Location randomLocation() {
    Location l;

    do {
      float x = (random.nextFloat() * TELEPORT_BORDER * 2) - TELEPORT_BORDER;
      float z = (random.nextFloat() * TELEPORT_BORDER * 2) - TELEPORT_BORDER;

      l = topLocation(x, z);
    } while (l.getBlock().isLiquid());

    return l.add(0, 1, 0);
  }

  private Location topLocation(float x, float z) {
    World w = Bukkit.getWorld("world");
    Location l = new Location(w, x, w.getMaxHeight(), z);
    while (l.getBlock().isEmpty()) {
      l = l.add(0, -1, 0);
    }

    return l;
  }

  private void safeTeleport(Player player, Location location) {
    player.teleport(location);
    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
  }

  private boolean isShuffled(List<?> l1, List<?> l2) {
    if (l1.size() != l2.size()) return false;
    if (l1.size() <= 1) return true;

    for (int i = 0; i < l1.size(); i++) {
      if (l1.get(i).equals(l2.get(i))) return false;
    }

    return true;
  }


  private void notifyOfDeath(String killer, String killed, String causeStr) {
    Player killerPlayer = Bukkit.getPlayer(killer);

    for (Player p : Bukkit.getOnlinePlayers()) {
      if (p.getName().equals(killer)) continue;

      p.sendMessage(ChatColor.GREEN + killer + ChatColor.BLUE + " killed " + ChatColor.GREEN + killed +
          ChatColor.BLUE + " with " + ChatColor.GREEN + simpleDeathName(causeStr));
    }

    if (killerPlayer != null) {
      killerPlayer.sendMessage(ChatColor.GOLD + "You have scored a point!");
      if (uniqueDeaths && scores.get(killer) < pointsToWin) {
        killerPlayer.sendMessage(ChatColor.WHITE + "Use " + ChatColor.RED + "/deaths" + ChatColor.WHITE + " to see what deaths you cannot repeat");
      }
    }
  }

  private void addNewDeath(String killer, String causeStr) {
    if (!deaths.containsKey(killer)) {
      deaths.put(killer, new HashSet<>());
    }

    deaths.get(killer).add(causeStr);
  }

  private boolean checkForWinner(String killer) {
    if (scores.get(killer) < pointsToWin) return false;

    for (Player p : Bukkit.getOnlinePlayers()) {
      p.sendMessage(ChatColor.GREEN + killer + ChatColor.BLUE + " has won the game!");
    }

    end();

    return true;
  }

  private boolean deathAllowedNow(String killer, String causeStr) {
    if (swapPointsPeriod > 0 && timeTilSwap + swapPointsPeriod < swapTime) return false;

    if (uniqueDeaths && deaths.containsKey(killer) && deaths.get(killer).contains(causeStr)) {
      Player killerPlayer = Bukkit.getPlayer(killer);
      if (killerPlayer != null) {
        killerPlayer.sendMessage(ChatColor.BLUE + "You have already killed someone with " + simpleDeathName(causeStr));
      }
      return false;
    }

    return true;
  }


  private void refreshScoreboard() {
    Scoreboard scoreboard = getScoreboard();

    for (Player p : Bukkit.getOnlinePlayers()) {
      p.setScoreboard(scoreboard);
    }
  }

  public Scoreboard getScoreboard() {
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

    Objective obj = scoreboard.registerNewObjective("scoreboard", "scoreboard",
        ChatColor.GOLD + "Death Swap - Points to win: " + ChatColor.RED + pointsToWin);
    obj.setDisplaySlot(DisplaySlot.SIDEBAR);

    for (String name : scores.keySet()) {
      obj.getScore(name).setScore(scores.get(name));
    }

    if (showTimerAlways) {
      int minutes = timeTilSwap / 60;
      int seconds = timeTilSwap % 60;

      obj.getScore(
          ChatColor.BLUE + "Time until swap: " +
              ChatColor.GREEN + minutes + ":" + String.format("%02d", seconds)
      ).setScore(-1);
    }

    return scoreboard;
  }

  private void clearAllEffects(Player player) {
    player.getInventory().clear();
    player.setHealth(20);
    player.setFoodLevel(20);
    for (PotionEffect effect : player.getActivePotionEffects()) {
      player.removePotionEffect(effect.getType());
    }
    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> player.setFireTicks(0), 2L);
  }

  private String getDeathCause(EntityDamageEvent evt) {
    if (evt instanceof EntityDamageByEntityEvent) {
      Entity damager = ((EntityDamageByEntityEvent) evt).getDamager();
      if (damager instanceof Projectile) {
        Projectile projectile = (Projectile) damager;

        ProjectileSource source = projectile.getShooter();

        if (source instanceof Entity) {
          return ((Entity) source).getType() + "_" + projectile.getType();
        } else if (source instanceof BlockProjectileSource) {
          return ((BlockProjectileSource) source).getBlock().getType() + "_" + projectile.getType();
        }
      } else {
        return damager.getType().toString();
      }
    } else if (evt instanceof EntityDamageByBlockEvent) {
      Block damager = ((EntityDamageByBlockEvent) evt).getDamager();

      if (damager != null) return damager.getType().toString();
    }

    return evt.getCause().toString();
  }

  private String simpleDeathName(String string) {
    return WordUtils.capitalize(string.toLowerCase().replace("_", " "));
  }
}
