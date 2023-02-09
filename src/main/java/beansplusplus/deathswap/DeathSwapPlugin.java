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
  private GameState state;

  public void onEnable() {
    BeansGamePlugin beansGamePlugin = (BeansGamePlugin) getServer().getPluginManager().getPlugin("BeansGamePlugin");
    state = beansGamePlugin.registerGame(this);
    getCommand("deaths").setExecutor(this);
  }

  @Override
  public Game createGame(GameConfiguration config, GameState gameState) {
    return new DeathSwapGame(this, config, gameState);
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
    return List.of(
        "In death swap, you will swap locations with an opponent every 5 minutes. You need to put yourself into a dangerous position such that they will die when you swap.\n\nThe winner of the game is whoever causes 5 deaths first.",
        "Each of your points needs to be scored with a unique death cause. You can keep track of the types of deaths you have done with " + ChatColor.RED + "/deaths" + ChatColor.BLACK + ". This feature is optional and can be disabled.",
        "Points can only be scored within the first minute that you swap with another player. This period can be changed with the " + ChatColor.RED + "swap_points_period_minutes" + ChatColor.BLACK + " setting."
    );
  }

  @Override
  public String name() {
    return "Death Swap";
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!(sender instanceof Player)) sender.sendMessage("Need to be a player to run this command.");

    if (label.equalsIgnoreCase("deaths")) {
      if (state.gameStarted()) {
        ((DeathSwapGame) state.getCurrentGame()).showCompletedDeaths((Player) sender);
      } else {
        sender.sendMessage(ChatColor.DARK_RED + "Game not started.");
      }
    }

    return true;
  }
}
