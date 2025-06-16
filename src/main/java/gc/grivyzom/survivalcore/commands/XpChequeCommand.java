package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.util.XpChequeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando para el sistema de cheques de experiencia.
 * Permite crear cheques con experiencia que pueden ser canjeados posteriormente.
 */
public class XpChequeCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final XpChequeManager chequeManager;

    public XpChequeCommand(Main plugin) {
        this.plugin = plugin;
        this.chequeManager = new XpChequeManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "crear", "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /cheque crear <cantidad>");
                    player.sendMessage(ChatColor.GRAY + "Ejemplo: /cheque crear 100");
                    return true;
                }
                handleCreateCheque(player, args[1]);
            }
            case "info" -> chequeManager.showPlayerInfo(player);
            case "limits", "limites" -> chequeManager.showLimits(player);
            case "reload" -> {
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para recargar el sistema.");
                    return true;
                }
                chequeManager.reloadConfig();
                player.sendMessage(ChatColor.GREEN + "Sistema de cheques recargado.");
            }
            case "give" -> {
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para dar cheques.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Uso: /cheque give <jugador> <cantidad>");
                    return true;
                }
                handleGiveCheque(player, args[1], args[2]);
            }
            case "stats" -> {
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para ver estadísticas.");
                    return true;
                }
                if (args.length >= 2) {
                    chequeManager.showPlayerStats(player, args[1]);
                } else {
                    chequeManager.showPlayerStats(player, player.getName());
                }
            }
            case "help", "ayuda" -> showHelp(player);
            default -> {
                // Si no es un subcomando, intentar crear cheque directamente
                try {
                    int amount = Integer.parseInt(args[0]);
                    handleCreateCheque(player, String.valueOf(amount));
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Comando desconocido. Usa /cheque help para ver la ayuda.");
                }
            }
        }

        return true;
    }

    /**
     * Maneja la creación de un cheque
     */
    private void handleCreateCheque(Player player, String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);

            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
                return;
            }

            // Validar cantidad mínima y máxima
            int minAmount = plugin.getConfig().getInt("cheque_system.min_amount", 1);
            int maxAmount = plugin.getConfig().getInt("cheque_system.max_amount", 1000);

            if (amount < minAmount) {
                player.sendMessage(ChatColor.RED + "La cantidad mínima para un cheque es " + minAmount + " niveles.");
                return;
            }

            if (amount > maxAmount) {
                player.sendMessage(ChatColor.RED + "La cantidad máxima para un cheque es " + maxAmount + " niveles.");
                return;
            }

            // Intentar crear el cheque
            chequeManager.createCheque(player, amount);

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "'" + amountStr + "' no es un número válido.");
            player.sendMessage(ChatColor.GRAY + "Ejemplo: /cheque crear 50");
        }
    }

    /**
     * Maneja el comando de dar cheques (solo para administradores)
     */
    private void handleGiveCheque(Player sender, String targetName, String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);

            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
                return;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "El jugador " + targetName + " no está online.");
                return;
            }

            // Crear cheque sin restricciones para administradores
            boolean success = chequeManager.createChequeAdmin(target, amount, sender.getName());

            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Has dado un cheque de " + amount +
                        " niveles a " + target.getName() + ".");
                target.sendMessage(ChatColor.GREEN + "Has recibido un cheque de " + amount +
                        " niveles de " + sender.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Error al crear el cheque.");
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "'" + amountStr + "' no es un número válido.");
        }
    }

    /**
     * Muestra la ayuda del comando
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══ Sistema de Cheques de Experiencia ═══");
        player.sendMessage(ChatColor.YELLOW + "/cheque <cantidad>" + ChatColor.GRAY + " - Crear un cheque");
        player.sendMessage(ChatColor.YELLOW + "/cheque crear <cantidad>" + ChatColor.GRAY + " - Crear un cheque");
        player.sendMessage(ChatColor.YELLOW + "/cheque info" + ChatColor.GRAY + " - Ver tu información");
        player.sendMessage(ChatColor.YELLOW + "/cheque limites" + ChatColor.GRAY + " - Ver límites diarios");
        player.sendMessage(ChatColor.YELLOW + "/cheque help" + ChatColor.GRAY + " - Mostrar esta ayuda");

        if (player.isOp()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "Comandos de administrador:");
            player.sendMessage(ChatColor.YELLOW + "/cheque give <jugador> <cantidad>" + ChatColor.GRAY + " - Dar cheque");
            player.sendMessage(ChatColor.YELLOW + "/cheque stats [jugador]" + ChatColor.GRAY + " - Ver estadísticas");
            player.sendMessage(ChatColor.YELLOW + "/cheque reload" + ChatColor.GRAY + " - Recargar configuración");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Los cheques se pueden canjear haciendo clic derecho sobre ellos.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "crear", "info", "limites", "help"
            ));

            // Añadir comandos de admin si es OP
            if (player.isOp()) {
                completions.addAll(Arrays.asList("give", "stats", "reload"));
            }

            // Añadir números comunes
            completions.addAll(Arrays.asList("1", "5", "10", "25", "50", "100", "250", "500"));

            return completions.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            if ("give".equals(subcommand) && player.hasPermission("survivalcore.cheque.give")) {
                // Autocompletar jugadores online
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if ("stats".equals(subcommand) && player.hasPermission("survivalcore.cheque.stats")) {
                // Autocompletar jugadores online
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if ("crear".equals(subcommand) || "create".equals(subcommand)) {
                // Sugerir cantidades comunes
                return Arrays.asList("1", "5", "10", "25", "50", "100", "250", "500").stream()
                        .filter(amount -> amount.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if ("give".equals(args[0].toLowerCase()) && player.hasPermission("survivalcore.cheque.give")) {
                // Sugerir cantidades para el comando give
                return Arrays.asList("1", "5", "10", "25", "50", "100", "250", "500", "1000").stream()
                        .filter(amount -> amount.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Getter para el manager de cheques
     */
    public XpChequeManager getChequeManager() {
        return chequeManager;
    }
}