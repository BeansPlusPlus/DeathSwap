package beansplusplus.deathswap;

import beansplusplus.gameconfig.GameConfiguration;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
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
  private int immunityTimer;
  private int immunityTime;
  private int pointsToWin;
  private boolean uniqueDeaths;
  private boolean hunger;

  private Map<String, Integer> scores = new HashMap<>();

  private Map<String, String> teleportedBy = new HashMap<>();

  private Map<String, Set<String>> deaths = new HashMap<>();

  public Game(DeathSwapPlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {

    for (World world : Bukkit.getWorlds()) {
      world.setGameRule(GameRule.KEEP_INVENTORY, true);
      world.setPVP(false);
    }

    World world = Bukkit.getWorld("world");
    world.setTime(1000);
    Location spawn = world.getSpawnLocation();

    int x = spawn.getBlockX();
    int z = spawn.getBlockZ();

    float angle = (float) (Math.random() * Math.PI * 2);

    float angleDif = ((float) Math.PI * 2.0f) / (float) Bukkit.getOnlinePlayers().size();

    pointsToWin = GameConfiguration.getConfig().getValue("points_to_win");
    swapTime = ((int) GameConfiguration.getConfig().getValue("round_time_minutes")) * 60;
    timeTilSwap = swapTime;
    immunityTimer = 0;
    immunityTime = GameConfiguration.getConfig().getValue("swap_immunity_seconds");
    uniqueDeaths = GameConfiguration.getConfig().getValue("unique_deaths");
    hunger = GameConfiguration.getConfig().getValue("unique_deaths");

    for (Player player : Bukkit.getOnlinePlayers()) {
      clearAllEffects(player);
      player.setGameMode(GameMode.SURVIVAL);

      scores.put(player.getName(), 0);

      safeTeleport(player, getFarLocation(x, z, 1000, angle));

      angle += angleDif;
    }

    Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::runTimer, 20, 20);
    refreshScoreboard();
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

    return scoreboard;
  }

  /**
   * Teleport the player based on an angle and distance from point x,z
   *
   * @param x
   * @param z
   * @param dist
   * @param angle
   */
  private Location getFarLocation(int x, int z, int dist, float angle) {
    float px = (int) (x + Math.sin(angle) * dist) + 0.5f;
    float pz = (int) (z + Math.cos(angle) * dist) + 0.5f;

    Location l = new Location(Bukkit.getWorld("world"), px, 255, pz);
    while (l.getBlock().isEmpty()) {
      l = l.add(0, -1, 0);
    }

    if (l.getBlock().isLiquid()) return getFarLocation(x, z, dist - 5, angle);  // prevent landing in an ocean

    return l.add(0, 1, 0);
  }

  /**
   * Teleport and cancel velocity
   *
   * @param player
   * @param location
   */
  private void safeTeleport(Player player, Location location) {
    player.teleport(location);
    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
  }

  private Location randomLocation() {
    Location spawn = Bukkit.getWorld("world").getSpawnLocation();
    int x = spawn.getBlockX();
    int z = spawn.getBlockZ();

    float angle = (float) (Math.random() * Math.PI * 2);

    return getFarLocation(x, z, 1000, angle);
  }

  /**
   * Swap players in the game
   */
  private void swapPlayers() {
    timeTilSwap = swapTime;
    immunityTimer = immunityTime;

    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    List<Player> shuffledPlayers = new ArrayList<>(players);

    while (!isShuffled(players, shuffledPlayers)) {
      Collections.shuffle(shuffledPlayers);
    }

    for (int i = 0; i < players.size(); i++) {
      teleportedBy.put(players.get(i).getName(), shuffledPlayers.get(i).getName());

      Player player = players.get(i);
      Location location = shuffledPlayers.get(i).getLocation();

      safeTeleport(player, location);
      player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

      player.sendMessage(ChatColor.BLUE + "You teleported to " + ChatColor.GREEN + shuffledPlayers.get(i).getName());
    }

    for (int i = 0; i < players.size(); i++) {
      shuffledPlayers.get(i).sendMessage(ChatColor.GREEN + players.get(i).getName() + ChatColor.BLUE + " teleported to you");
    }
  }

  private boolean isShuffled(List<?> l1, List<?> l2) {
    if (l1.size() != l2.size()) return false;
    if (l1.size() <= 1) return true;

    for (int i = 0; i < l1.size(); i++) {
      if (l1.get(i).equals(l2.get(i))) return false;
    }

    return true;
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

  private String simpleDeathName(String string) {
    return WordUtils.capitalize(string.toLowerCase().replace("_", " "));
  }

  /**
   * Run this method every second
   */
  public void runTimer() {
    timeTilSwap--;
    if (immunityTimer > 0) immunityTimer--;

    if (timeTilSwap == 0) {
      swapPlayers();
    } else {
      if (timeTilSwap <= 5 || timeTilSwap == 30) {
        for (Player player : Bukkit.getOnlinePlayers()) {
          player.sendMessage(ChatColor.YELLOW + "Swapping in " + timeTilSwap + "s...");
        }
      }
    }
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

      return damager.getType().toString();
    }

    return evt.getCause().toString();
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent e) {
    Player player = e.getEntity();

    if (!teleportedBy.containsKey(player.getName())) return;

    String killer = teleportedBy.get(player.getName());

    String causeStr = getDeathCause(player.getLastDamageCause());

    if (uniqueDeaths && deaths.containsKey(killer) && deaths.get(killer).contains(causeStr)) {
      Player killerPlayer;
      if ((killerPlayer = Bukkit.getPlayer(killer)) != null) {
        killerPlayer.sendMessage(ChatColor.BLUE + "You have already killed someone with " + simpleDeathName(causeStr));
      }
      return;
    }

    for (Player p : Bukkit.getOnlinePlayers()) {
      p.sendMessage(ChatColor.GREEN + killer + ChatColor.BLUE + " scored a point!");
    }

    scores.put(killer, scores.getOrDefault(killer, 0) + 1);

    if (scores.get(killer) >= pointsToWin) {
      for (Player p : Bukkit.getOnlinePlayers()) {
        p.sendMessage(ChatColor.GREEN + killer + ChatColor.BLUE + " has won the game!");
      }

      end();

      return;
    }

    if (!deaths.containsKey(killer)) {
      deaths.put(killer, new HashSet<>());
    }

    deaths.get(killer).add(causeStr);

    refreshScoreboard();
  }

  @EventHandler
  public void onEntityDamage(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player) {
      if (immunityTimer > 0) e.setCancelled(true);
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
}