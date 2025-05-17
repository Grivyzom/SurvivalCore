package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.gui.ProfesionesMenuGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkillsCommand implements CommandExecutor {
    private final Main plugin;

    public SkillsCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }
        ProfesionesMenuGUI.open(player, plugin);
        return true;
    }
}
