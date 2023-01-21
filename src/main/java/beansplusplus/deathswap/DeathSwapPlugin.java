package beansplusplus.deathswap;

import beansplusplus.gameconfig.ConfigLoader;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class DeathSwapPlugin extends JavaPlugin implements CommandExecutor, Listener {
  private Game game;

  public void onEnable() {
    ConfigLoader.loadFromInput(getResource("config.yml"));
    getServer().getPluginManager().registerEvents(this, this);
    getCommand("start").setExecutor(this);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (game != null) {
      game.end();
    }
    game = new Game(this);
    game.start();

    return true;
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    Player player = e.getPlayer();

    player.sendMessage(ChatColor.BLUE + "Welcome to Deathswap");
    player.sendMessage(ChatColor.GREEN + "/config" + ChatColor.WHITE + " to configure game setup");
    player.sendMessage(ChatColor.GREEN + "/start" + ChatColor.WHITE + " to begin");
  }

}
