package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPot;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Comando para gestionar las Macetas Mágicas
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MagicFlowerPotCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final MagicFlowerPot potFactory;
    private final MagicFlowerPotManager potManager;

    public MagicFlowerPotCommand(Main plugin) {
        this.plugin = plugin;
        this.potFactory = new MagicFlowerPot(plugin);
        this.potManager = plugin.getMagicFlowerPotManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "give":
                return handleGive(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "stats":
                return handleStats(sender);
            case "reload":
                return handleReload(sender);
            case "help":
                showHelp(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Cantidad inválida: " + args[3]);
                return true;
        }
    }

    // Crear y dar las macetas
        for (int i = 0; i < amount; i++) {
        ItemStack magicPot = potFactory.createMagicFlowerPot(level);
        target.getInventory().addItem(magicPot);
    }

    // Mensajes de confirmación
    String potText = amount == 1 ? "Maceta Mágica" : "Macetas Mágicas";
        target.sendMessage(ChatColor.GREEN + "Has recibido " + amount + " " + potText +
            " de nivel " + level + ".");
        sender.sendMessage(ChatColor.GREEN + "Has dado " + amount + " " + potText +
            " de nivel " + level + " a " + target.getName() + ".");

        return true;
}

/**
 * Maneja el subcomando 'info'
 */
private boolean handleInfo(CommandSender sender, String[] args) {
    if (!(sender instanceof Player)) {
        sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
        return true;
    }

    Player player = (Player) sender;
    ItemStack itemInHand = player.getInventory().getItemInMainHand();

    if (!potFactory.isMagicFlowerPot(itemInHand)) {
        player.sendMessage(ChatColor.RED + "Debes tener una Maceta Mágica en tu mano.");
        return true;
    }

    // Mostrar información detallada
    int level = potFactory.getPotLevel(itemInHand);
    int range = potFactory.getPotRange(itemInHand);
    String flowerId = potFactory.getContainedFlower(itemInHand);
    String potId = potFactory.getPotId(itemInHand);

    player.sendMessage("");
    player.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ══════ INFORMACIÓN DE MACETA ══════ ✿");
    player.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level);
    player.sendMessage(ChatColor.WHITE + "  📏 Rango de efectos: " + ChatColor.GREEN + range + " bloques");
    player.sendMessage(ChatColor.WHITE + "  🆔 ID único: " + ChatColor.GRAY + "#" + potId);

    if (!flowerId.equals("none")) {
        player.sendMessage(ChatColor.WHITE + "  🌸 Flor contenida: " + ChatColor.LIGHT_PURPLE +
                getFlowerDisplayName(flowerId));
        player.sendMessage(ChatColor.WHITE + "  ⚡ Estado: " + ChatColor.GREEN + "LISTA PARA COLOCAR");
    } else {
        player.sendMessage(ChatColor.WHITE + "  🌸 Flor contenida: " + ChatColor.GRAY + "Ninguna");
        player.sendMessage(ChatColor.WHITE + "  ⚡ Estado: " + ChatColor.YELLOW + "VACÍA");
    }

    // Información sobre mejoras
    if (potFactory.canUpgrade(itemInHand)) {
        int nextLevel = level + 1;
        int nextRange = 3 + (nextLevel - 1) * 2;
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  ⬆ Siguiente nivel (" + nextLevel + "):");
        player.sendMessage(ChatColor.GRAY + "     • Rango: " + nextRange + " bloques");
        player.sendMessage(ChatColor.GRAY + "     • Efectos más potentes");
    } else {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "  ⭐ ¡NIVEL MÁXIMO ALCANZADO!");
    }

    player.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ═══════════════════════════════════ ✿");

    return true;
}

/**
 * Maneja el subcomando 'stats'
 */
private boolean handleStats(CommandSender sender) {
    if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
        sender.sendMessage(ChatColor.RED + "No tienes permisos para ver las estadísticas.");
        return true;
    }

    String stats = potManager.getStatistics();

    sender.sendMessage("");
    sender.sendMessage(ChatColor.AQUA + "═══════ ESTADÍSTICAS DE MACETAS MÁGICAS ═══════");
    sender.sendMessage(ChatColor.WHITE + stats);

    // Mostrar distribución por nivel
    var activePots = potManager.getAllActivePots();
    int[] levelCounts = new int[6]; // Índice 0 no se usa, 1-5 para niveles

    for (var pot : activePots) {
        if (pot.getLevel() >= 1 && pot.getLevel() <= 5) {
            levelCounts[pot.getLevel()]++;
        }
    }

    sender.sendMessage("");
    sender.sendMessage(ChatColor.YELLOW + "Distribución por nivel:");
    for (int i = 1; i <= 5; i++) {
        sender.sendMessage(ChatColor.WHITE + "  Nivel " + i + ": " +
                ChatColor.AQUA + levelCounts[i] + " macetas");
    }

    sender.sendMessage(ChatColor.AQUA + "═════════════════════════════════════════════");

    return true;
}

/**
 * Maneja el subcomando 'reload'
 */
private boolean handleReload(CommandSender sender) {
    if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
        sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
        return true;
    }

    try {
        // Recargar configuración del plugin
        plugin.reloadConfig();

        // Forzar actualización del manager
        potManager.forceUpdate();

        sender.sendMessage(ChatColor.GREEN + "✓ Configuración de Macetas Mágicas recargada correctamente.");
        sender.sendMessage(ChatColor.GRAY + "Macetas activas: " + potManager.getActivePotCount());

    } catch (Exception e) {
        sender.sendMessage(ChatColor.RED + "Error al recargar la configuración: " + e.getMessage());
        plugin.getLogger().severe("Error recargando configuración de macetas: " + e.getMessage());
    }

    return true;
}

/**
 * Muestra la ayuda del comando
 */
private void showHelp(CommandSender sender) {
    sender.sendMessage("");
    sender.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ══════ COMANDOS DE MACETAS MÁGICAS ══════ ✿");
    sender.sendMessage("");

    if (sender.hasPermission("survivalcore.flowerpot.give")) {
        sender.sendMessage(ChatColor.AQUA + "/flowerpot give <jugador> [nivel] [cantidad]");
        sender.sendMessage(ChatColor.GRAY + "  • Da macetas mágicas a un jugador");
    }

    sender.sendMessage(ChatColor.AQUA + "/flowerpot info");
    sender.sendMessage(ChatColor.GRAY + "  • Muestra información de la maceta en tu mano");

    if (sender.hasPermission("survivalcore.flowerpot.admin")) {
        sender.sendMessage(ChatColor.AQUA + "/flowerpot stats");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra estadísticas del sistema");

        sender.sendMessage(ChatColor.AQUA + "/flowerpot reload");
        sender.sendMessage(ChatColor.GRAY + "  • Recarga la configuración");
    }

    sender.sendMessage(ChatColor.AQUA + "/flowerpot help");
    sender.sendMessage(ChatColor.GRAY + "  • Muestra esta ayuda");

    sender.sendMessage("");
    sender.sendMessage(ChatColor.YELLOW + "Uso básico:");
    sender.sendMessage(ChatColor.WHITE + "1. Obtén una maceta con " + ChatColor.AQUA + "/flowerpot give");
    sender.sendMessage(ChatColor.WHITE + "2. Colócala en el suelo");
    sender.sendMessage(ChatColor.WHITE + "3. Usa flores mágicas para activar efectos");
    sender.sendMessage("");
    sender.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ═════════════════════════════════════════ ✿");
}

/**
 * Obtiene el nombre de display de una flor
 */
private String getFlowerDisplayName(String flowerId) {
    switch (flowerId.toLowerCase()) {
        case "love_flower":
            return "Flor del Amor";
        case "healing_flower":
            return "Flor Sanadora";
        case "speed_flower":
            return "Flor de Velocidad";
        case "strength_flower":
            return "Flor de Fuerza";
        case "night_vision_flower":
            return "Flor Nocturna";
        default:
            return "Flor Desconocida";
    }
}

@Override
public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
        // Subcomandos principales
        List<String> subcommands = Arrays.asList("give", "info", "help");

        if (sender.hasPermission("survivalcore.flowerpot.admin")) {
            subcommands = Arrays.asList("give", "info", "stats", "reload", "help");
        }

        for (String sub : subcommands) {
            if (sub.startsWith(args[0].toLowerCase())) {
                completions.add(sub);
            }
        }
    } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
        // Autocompletar jugadores para el comando give
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                completions.add(player.getName());
            }
        }
    } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
        // Autocompletar niveles (1-5)
        for (int i = 1; i <= 5; i++) {
            String level = String.valueOf(i);
            if (level.startsWith(args[2])) {
                completions.add(level);
            }
        }
    } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
        // Autocompletar cantidades comunes
        String[] amounts = {"1", "2", "4", "8", "16", "32", "64"};
        for (String amount : amounts) {
            if (amount.startsWith(args[3])) {
                completions.add(amount);
            }
        }
    }

    return completions;
}
} "Subcomando desconocido. Usa /flowerpot help para ver la ayuda.");
        return true;
        }
        }

/**
 * Maneja el subcomando 'give'
 */
private boolean handleGive(CommandSender sender, String[] args) {
    if (!sender.hasPermission("survivalcore.flowerpot.give")) {
        sender.sendMessage(ChatColor.RED + "No tienes permisos para dar Macetas Mágicas.");
        return true;
    }

    if (args.length < 2) {
        sender.sendMessage(ChatColor.RED + "Uso: /flowerpot give <jugador> [nivel] [cantidad]");
        return true;
    }

    Player target = plugin.getServer().getPlayer(args[1]);
    if (target == null) {
        sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[1]);
        return true;
    }

    int level = 1;
    int amount = 1;

    // Procesar nivel si se proporciona
    if (args.length >= 3) {
        try {
            level = Integer.parseInt(args[2]);
            if (level < 1 || level > 5) {
                sender.sendMessage(ChatColor.RED + "El nivel debe estar entre 1 y 5.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Nivel inválido: " + args[2]);
            return true;
        }
    }

    // Procesar cantidad si se proporciona
    if (args.length >= 4) {
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 1 || amount > 64) {
                sender.sendMessage(ChatColor.RED + "La cantidad debe estar entre 1 y 64.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED +