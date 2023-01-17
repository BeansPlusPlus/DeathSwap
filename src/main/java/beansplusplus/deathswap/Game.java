package beansplusplus.deathswap;

import beansplusplus.gameconfig.GameConfiguration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import org.bukkit.scoreboard.Team;

public class Game implements Listener {

  private Plugin plugin;

  public Game(DeathSwapPlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    double num = GameConfiguration.getConfig().getValue("some_number");
    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setHealth(20);
      player.setLevel(0);
      player.setSaturation(20);
      player.getInventory().clear();
      player.setGameMode(GameMode.SURVIVAL);
    }

    Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    refreshScoreboard();
  }

  public void end() {
    HandlerList.unregisterAll(this);

    for (Player player : Bukkit.getOnlinePlayers()) {
      player.setGameMode(GameMode.SPECTATOR);
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

    Objective obj = scoreboard.registerNewObjective("scoreboard", "scoreboard", "Scoreboard display name");
    obj.setDisplaySlot(DisplaySlot.PLAYER_LIST);

    Team team1 = scoreboard.registerNewTeam(ChatColor.RED + "Team 1");
    Team team2 = scoreboard.registerNewTeam(ChatColor.BLUE + "Team 2");

    team1.setPrefix(ChatColor.RED + "[Team 1] ");
    team2.setPrefix(ChatColor.BLUE + "[Team 2] ");

    return scoreboard;
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent e) {
    String pName = e.getEntity().getName();
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    refreshScoreboard();
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent e) {
    refreshScoreboard();
  }
}