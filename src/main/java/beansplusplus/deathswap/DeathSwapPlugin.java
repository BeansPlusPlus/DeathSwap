package beansplusplus.deathswap;

import beansplusplus.beansgameplugin.*;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.List;

public class DeathSwapPlugin extends JavaPlugin implements GameCreator, CommandExecutor {
  private DeathSwapGame game;

  public void onEnable() {
    BeansGamePlugin beansGamePlugin = (BeansGamePlugin) getServer().getPluginManager().getPlugin("BeansGamePlugin");
    beansGamePlugin.registerGame(this);
    getCommand("deaths").setExecutor(this);
  }

  @Override
  public Game createGame(GameConfiguration config, GameState gameState) {
    game = new DeathSwapGame(this, config, gameState);
    return game;
  }

  @Override
  public boolean isValidSetup(CommandSender commandSender, GameConfiguration gameConfiguration) {
    return true;
  }

  @Override
  public InputStream config() {
    return getResource("config.yml");
  }

  @Override
  public List<String> rulePages() {
    return List.of("TODO");
  }

  @Override
  public String name() {
    return "Death Swap";
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!(sender instanceof Player)) sender.sendMessage("Need to be a player to run this command.");

    if (label.equalsIgnoreCase("deaths")) {
      if (game == null) {
        sender.sendMessage(ChatColor.DARK_RED + "Game not started.");
      } else {
        game.showCompletedDeaths((Player) sender);
      }
    }

    return true;
  }
}
