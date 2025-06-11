package gc.grivyzom.survivalcore.sellwand;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comando principal para el sistema SellWand
 */
public class SellWandCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final SellWandManager manager;
    private final DecimalFormat priceFormat;
    private final DateTimeFormatter timeFormat;

    public SellWandCommand(Main plugin, SellWandManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.priceFormat = new DecimalFormat("#,##0.00");
        this.timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give":
                return handleGive(sender, args);
            case "reload":
                return handleReload(sender);
            case "limits":
                return handleLimits(sender, args);
            case "prices":
                return handlePrices(sender, args);
            case "info":
                return handleInfo(sender);
            case "help":
                sendHelp(sender, label);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /" + label + " help");
                return true;
        }
    }

    /**
     * Maneja el subcomando give
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.sellwand.give")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para dar SellWands.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sellwand give <jugador> [usos]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[1]);
            return true;
        }

        int uses = 100; // Valor por defecto
        if (args.length >= 3) {
            try {
                uses = Integer.parseInt(args[2]);
                if (uses <= 0) {
                    sender.sendMessage(ChatColor.RED + "Los usos deben ser un número positivo.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Número de usos inválido: " + args[2]);
                return true;
            }
        }

        // Crear y dar la SellWand
        ItemStack sellWand = manager.createSellWand(uses);

        // Verificar espacio en inventario
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItemNaturally(target.getLocation(), sellWand);
            target.sendMessage(ChatColor.YELLOW + "Tu inventario está lleno. La SellWand ha sido dropeada en el suelo.");
        } else {
            target.getInventory().addItem(sellWand);
        }

        // Mensajes
        target.sendMessage(ChatColor.GREEN + "¡Has recibido una SellWand con " + uses + " usos!");
        sender.sendMessage(ChatColor.GREEN + "SellWand entregada a " + target.getName() + " con " + uses + " usos.");

        // Log
        plugin.getLogger().info(String.format("SellWand: %s dio una SellWand (%d usos) a %s",
                sender.getName(), uses, target.getName()));

        return true;
    }

    /**
     * Maneja el subcomando reload
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.sellwand.reload")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return true;
        }

        try {
            manager.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "✓ Configuración de SellWand recargada correctamente.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al recargar la configuración: " + e.getMessage());
            plugin.getLogger().severe("Error recargando configuración SellWand: " + e.getMessage());
        }

        return true;
    }

    /**
     * Maneja el subcomando limits
     */
    private boolean handleLimits(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden ver sus límites.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("survivalcore.sellwand.use")) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para usar SellWands.");
            return true;
        }

        // Obtener información de límites
        Map<Material, SellWandManager.SellLimitInfo> limits = manager.getSellLimitInfo(player);

        if (limits.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No hay límites de venta configurados.");
            return true;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "    📊 LÍMITES DE VENTA 📊");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");

        for (Map.Entry<Material, SellWandManager.SellLimitInfo> entry : limits.entrySet()) {
            Material material = entry.getKey();
            SellWandManager.SellLimitInfo info = entry.getValue();

            String itemName = getItemDisplayName(material);
            int used = info.getUsedAmount();
            int max = info.getMaxAmount();
            int remaining = info.getRemainingAmount();

            // Determinar color basado en el uso
            String usageColor;
            double usagePercent = (double) used / max;
            if (usagePercent >= 0.9) {
                usageColor = ChatColor.RED.toString();
            } else if (usagePercent >= 0.7) {
                usageColor = ChatColor.YELLOW.toString();
            } else {
                usageColor = ChatColor.GREEN.toString();
            }

            player.sendMessage(ChatColor.WHITE + "• " + ChatColor.AQUA + itemName);
            player.sendMessage("  " + usageColor + used + ChatColor.GRAY + "/" +
                    ChatColor.WHITE + max + ChatColor.GRAY + " (restantes: " + usageColor + remaining + ChatColor.GRAY + ")");

            // Mostrar tiempo de reset si el límite no ha expirado
            if (!info.isExpired() && info.getResetTime() > System.currentTimeMillis()) {
                LocalDateTime resetTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(info.getResetTime()), ZoneId.systemDefault());
                player.sendMessage("  " + ChatColor.GRAY + "Reset: " + resetTime.format(timeFormat));
            }

            player.sendMessage("");
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");

        return true;
    }

    /**
     * Maneja el subcomando prices
     */
    private boolean handlePrices(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.sellwand.prices")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para ver los precios.");
            return true;
        }

        // Filtro opcional por material
        String materialFilter = null;
        if (args.length >= 2) {
            materialFilter = args[1].toUpperCase().replace(" ", "_");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "        💰 PRECIOS DE VENTA 💰");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");

        // Obtener todos los materiales con precios
        Set<Material> materialsWithPrices = Arrays.stream(Material.values())
                .filter(manager::canSellItem)
                .collect(Collectors.toSet());

        if (materialsWithPrices.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay items configurados para venta.");
            sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");
            return true;
        }

        // Filtrar si se especificó un material
        if (materialFilter != null) {
            String finalFilter = materialFilter;
            materialsWithPrices = materialsWithPrices.stream()
                    .filter(m -> m.name().contains(finalFilter) ||
                            getItemDisplayName(m).toUpperCase().contains(finalFilter))
                    .collect(Collectors.toSet());

            if (materialsWithPrices.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No se encontraron items que coincidan con: " + args[1]);
                sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");
                return true;
            }
        }

        // Ordenar por precio (descendente)
        List<Material> sortedMaterials = materialsWithPrices.stream()
                .sorted((a, b) -> Double.compare(manager.getItemPrice(b), manager.getItemPrice(a)))
                .collect(Collectors.toList());

        // Mostrar hasta 20 items por página
        int maxItems = Math.min(20, sortedMaterials.size());
        for (int i = 0; i < maxItems; i++) {
            Material material = sortedMaterials.get(i);
            double price = manager.getItemPrice(material);
            String itemName = getItemDisplayName(material);

            // Color basado en el precio
            String priceColor = price >= 10 ? ChatColor.GREEN.toString() :
                    price >= 1 ? ChatColor.YELLOW.toString() : ChatColor.GRAY.toString();

            sender.sendMessage(ChatColor.WHITE + "• " + ChatColor.AQUA + itemName +
                    ChatColor.GRAY + " → " + priceColor + priceFormat.format(price) + " pts");
        }

        if (sortedMaterials.size() > maxItems) {
            int remaining = sortedMaterials.size() - maxItems;
            sender.sendMessage(ChatColor.GRAY + "... y " + remaining + " items más");
            sender.sendMessage(ChatColor.GRAY + "Usa /sellwand prices <filtro> para buscar items específicos");
        }

        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");

        return true;
    }

    /**
     * Maneja el subcomando info
     */
    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "      🪄 SELLWAND SISTEMA 🪄");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        sender.sendMessage(ChatColor.WHITE + "La SellWand te permite vender items");
        sender.sendMessage(ChatColor.WHITE + "de contenedores de forma rápida.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Cómo usar:");
        sender.sendMessage(ChatColor.WHITE + "1. Obtén una SellWand");
        sender.sendMessage(ChatColor.WHITE + "2. Haz clic derecho en un cofre/contenedor");
        sender.sendMessage(ChatColor.WHITE + "3. Los items se venderán automáticamente");
        sender.sendMessage(ChatColor.WHITE + "4. Recibirás experiencia a cambio");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Características:");
        sender.sendMessage(ChatColor.WHITE + "• Usos limitados por SellWand");
        sender.sendMessage(ChatColor.WHITE + "• Cooldown anti-abuso");
        sender.sendMessage(ChatColor.WHITE + "• Límites de venta diarios");
        sender.sendMessage(ChatColor.WHITE + "• Efectos visuales y sonoros");
        sender.sendMessage(ChatColor.WHITE + "• Sistema de precios configurable");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");

        return true;
    }

    /**
     * Envía la ayuda del comando
     */
    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "════ SellWand Comandos ════");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info" + ChatColor.GRAY + " - Información del sistema");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " prices [filtro]" + ChatColor.GRAY + " - Ver precios de venta");

        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " limits" + ChatColor.GRAY + " - Ver tus límites de venta");
        }

        if (sender.hasPermission("survivalcore.sellwand.give")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " give <jugador> [usos]" + ChatColor.GRAY + " - Dar SellWand");
        }

        if (sender.hasPermission("survivalcore.sellwand.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - Recargar configuración");
        }

        sender.sendMessage(ChatColor.GOLD + "══════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcomandos principales
            List<String> subCommands = Arrays.asList("info", "prices", "help");

            if (sender instanceof Player) {
                subCommands.add("limits");
            }

            if (sender.hasPermission("survivalcore.sellwand.give")) {
                subCommands.add("give");
            }

            if (sender.hasPermission("survivalcore.sellwand.reload")) {
                subCommands.add("reload");
            }

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                // Autocompletar jugadores online
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("prices")) {
                // Autocompletar materiales comunes
                List<String> commonMaterials = Arrays.asList(
                        "DIAMOND", "EMERALD", "GOLD", "IRON", "COAL", "STONE", "WOOD", "WHEAT", "CARROT"
                );
                for (String material : commonMaterials) {
                    if (material.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(material);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Autocompletar cantidad de usos
            List<String> useSuggestions = Arrays.asList("10", "50", "100", "250", "500", "1000");
            for (String use : useSuggestions) {
                if (use.startsWith(args[2])) {
                    completions.add(use);
                }
            }
        }

        return completions;
    }

    /**
     * Obtiene el nombre de visualización de un material
     */
    private String getItemDisplayName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return result.toString();
    }
}