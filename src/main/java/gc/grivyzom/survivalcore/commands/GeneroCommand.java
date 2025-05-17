package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.gui.GeneroGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GeneroCommand implements CommandExecutor {

    private final Main plugin;
    public GeneroCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }
        GeneroGUI.open(p, plugin);
        return true;
    }
}
