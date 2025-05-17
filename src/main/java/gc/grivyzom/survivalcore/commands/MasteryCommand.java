package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MasteryCommand implements CommandExecutor {
    private final Main plugin;

    public MasteryCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("upgrade")) {
            String masteryName = args[1];
            // Ejecutar upgrade de forma asíncrona
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = plugin.getMasteryManager().upgradeMastery(player, masteryName);
                // Notificar en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage(ChatColor.GREEN + "¡Has mejorado la maestría " + masteryName + "!");
                    } else {
                        player.sendMessage(ChatColor.RED + "No puedes mejorar esa maestría en este momento.");
                    }
                });
            });
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usa /mastery upgrade <nombre_maestria>");
        return true;
    }
}
