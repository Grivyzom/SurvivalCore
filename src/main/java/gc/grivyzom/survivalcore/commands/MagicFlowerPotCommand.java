package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPot;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotManager;
import gc.grivyzom.survivalcore.flowers.MagicFlowerFactory;
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
import java.util.stream.Collectors;

/**
 * Comando para gestionar las Macetas Mágicas
 * ACTUALIZADO v1.1 - Información sobre nuevas restricciones
 *
 * @author Brocolitx
 * @version 1.1
 */
public class MagicFlowerPotCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final MagicFlowerPot potFactory;
    private final MagicFlowerPotManager potManager;
    private final MagicFlowerFactory flowerFactory;

    public MagicFlowerPotCommand(Main plugin) {
        this.plugin = plugin;
        this.potFactory = new MagicFlowerPot(plugin);
        this.potManager = plugin.getMagicFlowerPotManager();
        this.flowerFactory = new MagicFlowerFactory(plugin);
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
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender);
            case "help":
                showHelp(sender);
                return true;
            case "restrictions":  // 🆕 Nuevo subcomando
                return handleRestrictions(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /magicflower help para ver la ayuda.");
                return true;
        }
    }

    /**
     * Maneja el subcomando 'give'
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.magicflower.give")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para dar Flores Mágicas.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /magicflower give <jugador> <tipo> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: love, healing, speed, strength, night_vision");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[1]);
            return true;
        }

        // Obtener tipo de flor
        MagicFlowerFactory.FlowerType flowerType = getFlowerTypeFromString(args[2]);
        if (flowerType == null) {
            sender.sendMessage(ChatColor.RED + "Tipo de flor inválido: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: love, healing, speed, strength, night_vision");
            return true;
        }

        int level = 1;
        int amount = 1;

        // Procesar nivel si se proporciona
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
                if (level < 1 || level > 5) {
                    sender.sendMessage(ChatColor.RED + "El nivel debe estar entre 1 y 5.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Nivel inválido: " + args[3]);
                return true;
            }
        }

        // Procesar cantidad si se proporciona
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(ChatColor.RED + "La cantidad debe estar entre 1 y 64.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Cantidad inválida: " + args[4]);
                return true;
            }
        }

        // Crear y dar las flores
        for (int i = 0; i < amount; i++) {
            ItemStack magicFlower = flowerFactory.createMagicFlower(flowerType, level);
            target.getInventory().addItem(magicFlower);
        }

        // Mensajes de confirmación
        String flowerText = amount == 1 ? "Flor Mágica" : "Flores Mágicas";
        target.sendMessage(ChatColor.GREEN + "Has recibido " + amount + " " + flowerText +
                " de " + flowerType.getDisplayName() + " nivel " + level + ".");
        sender.sendMessage(ChatColor.GREEN + "Has dado " + amount + " " + flowerText +
                " de " + flowerType.getDisplayName() + " nivel " + level + " a " + target.getName() + ".");

        return true;
    }

    /**
     * Maneja el subcomando 'list'
     */
    private boolean handleList(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════ FLORES MÁGICAS DISPONIBLES ═══════ 🌸");
        sender.sendMessage("");

        for (MagicFlowerFactory.FlowerType type : MagicFlowerFactory.FlowerType.values()) {
            sender.sendMessage(ChatColor.AQUA + "• " + type.getDisplayName());
            sender.sendMessage(ChatColor.WHITE + "  ID: " + ChatColor.GRAY + type.getId());
            sender.sendMessage(ChatColor.WHITE + "  Efecto: " + ChatColor.GREEN + type.getEffectDescription());
            sender.sendMessage(ChatColor.WHITE + "  Material base: " + ChatColor.YELLOW + type.getMaterial().name());
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.YELLOW + "💡 Consejo: Usa estas flores en Macetas Mágicas para");
        sender.sendMessage(ChatColor.YELLOW + "    activar efectos de área continuos.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ══════════════════════════════════════════ 🌸");

        return true;
    }


    /**
     * Maneja el subcomando 'info'
     */
    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!flowerFactory.isMagicFlower(itemInHand)) {
            player.sendMessage(ChatColor.RED + "Debes tener una Flor Mágica en tu mano.");
            return true;
        }

        // Mostrar información detallada
        String flowerId = flowerFactory.getFlowerId(itemInHand);
        int level = flowerFactory.getFlowerLevel(itemInHand);
        MagicFlowerFactory.FlowerType type = MagicFlowerFactory.FlowerType.getById(flowerId);

        if (type == null) {
            player.sendMessage(ChatColor.RED + "Error: Flor mágica no reconocida.");
            return true;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ══════ INFORMACIÓN DE FLOR MÁGICA ══════ 🌸");
        player.sendMessage(ChatColor.WHITE + "  🌸 Nombre: " + ChatColor.LIGHT_PURPLE + type.getDisplayName());
        player.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level + "/5");
        player.sendMessage(ChatColor.WHITE + "  🆔 ID: " + ChatColor.GRAY + type.getId());
        player.sendMessage(ChatColor.WHITE + "  ⚡ Efecto: " + ChatColor.GREEN + type.getEffectDescription());
        player.sendMessage(ChatColor.WHITE + "  🧱 Material: " + ChatColor.YELLOW + type.getMaterial().name());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "🎯 Uso:");
        player.sendMessage(ChatColor.WHITE + "  • Coloca una Maceta Mágica en el suelo");
        player.sendMessage(ChatColor.WHITE + "  • Haz click derecho en la maceta con esta flor");
        player.sendMessage(ChatColor.WHITE + "  • ¡La maceta irradiará efectos mágicos!");
        player.sendMessage("");

        if (level < 5) {
            player.sendMessage(ChatColor.GRAY + "💡 Consejo: Las flores de mayor nivel son más potentes");
        } else {
            player.sendMessage(ChatColor.GOLD + "⭐ ¡Esta flor tiene el nivel máximo!");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════════ 🌸");

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

        // 🆕 Estadísticas adicionales de las nuevas funcionalidades
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Configuración activa:");

        int minDistance = plugin.getConfig().getInt("magic_flowerpot.settings.min_distance_between_pots", 2);
        boolean blockFlowers = plugin.getConfig().getBoolean("magic_flowerpot.settings.block_normal_flowers", true);
        boolean animations = plugin.getConfig().getBoolean("magic_flowerpot.settings.enable_enhanced_placement_animations", true);

        sender.sendMessage(ChatColor.WHITE + "  Distancia mínima: " + ChatColor.AQUA + minDistance + " bloques");
        sender.sendMessage(ChatColor.WHITE + "  Flores normales bloqueadas: " +
                (blockFlowers ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.WHITE + "  Animaciones mejoradas: " +
                (animations ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));

        sender.sendMessage(ChatColor.AQUA + "═════════════════════════════════════════════");

        return true;
    }

    /**
     * 🆕 NUEVO: Maneja el subcomando 'restrictions'
     */
    private boolean handleRestrictions(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ═════ RESTRICCIONES DE MACETAS MÁGICAS ═════ ✿");
        sender.sendMessage("");

        // Distancia mínima
        int minDistance = plugin.getConfig().getInt("magic_flowerpot.settings.min_distance_between_pots", 2);
        sender.sendMessage(ChatColor.YELLOW + "📏 Distancia entre macetas:");
        sender.sendMessage(ChatColor.WHITE + "  • Mínimo " + ChatColor.AQUA + minDistance + " bloques" +
                ChatColor.WHITE + " de separación");
        sender.sendMessage(ChatColor.GRAY + "  • Se mide en 3D (incluye altura)");
        sender.sendMessage(ChatColor.GRAY + "  • Solo se verifica en el mismo mundo");

        sender.sendMessage("");

        // Flores bloqueadas
        boolean blockFlowers = plugin.getConfig().getBoolean("magic_flowerpot.settings.block_normal_flowers", true);
        sender.sendMessage(ChatColor.YELLOW + "🌸 Restricciones de flores:");

        if (blockFlowers) {
            sender.sendMessage(ChatColor.RED + "  ❌ Flores normales BLOQUEADAS");
            sender.sendMessage(ChatColor.WHITE + "  • Solo se aceptan flores mágicas especiales");
            sender.sendMessage(ChatColor.GRAY + "  • Flores bloqueadas: Amapola, Diente de León,");
            sender.sendMessage(ChatColor.GRAY + "    Orquídea Azul, Tulipanes, Margaritas, etc.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "  ✓ Todas las flores permitidas");
        }

        sender.sendMessage("");

        // Ubicación
        sender.sendMessage(ChatColor.YELLOW + "📍 Restricciones de ubicación:");
        sender.sendMessage(ChatColor.WHITE + "  • Superficie sólida requerida debajo");
        sender.sendMessage(ChatColor.WHITE + "  • Espacio libre requerido arriba");

        // Límites
        int maxPots = plugin.getConfig().getInt("magic_flowerpot.settings.max_pots_per_player", 10);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "🔢 Límites por jugador:");
        if (maxPots > 0) {
            sender.sendMessage(ChatColor.WHITE + "  • Máximo " + ChatColor.AQUA + maxPots + " macetas" +
                    ChatColor.WHITE + " por jugador");
            sender.sendMessage(ChatColor.GRAY + "  • Los admins pueden tener ilimitadas");
        } else {
            sender.sendMessage(ChatColor.GREEN + "  • Sin límite configurado");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ══════════════════════════════════════════ ✿");

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

            // 🆕 Mostrar configuración recargada
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "Configuración actualizada:");

            int minDistance = plugin.getConfig().getInt("magic_flowerpot.settings.min_distance_between_pots", 2);
            boolean blockFlowers = plugin.getConfig().getBoolean("magic_flowerpot.settings.block_normal_flowers", true);
            boolean animations = plugin.getConfig().getBoolean("magic_flowerpot.settings.enable_enhanced_placement_animations", true);

            sender.sendMessage(ChatColor.WHITE + "  • Distancia mínima: " + ChatColor.AQUA + minDistance + " bloques");
            sender.sendMessage(ChatColor.WHITE + "  • Flores normales bloqueadas: " +
                    (blockFlowers ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
            sender.sendMessage(ChatColor.WHITE + "  • Animaciones mejoradas: " +
                    (animations ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));

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
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ════════ COMANDOS DE FLORES MÁGICAS ════════ 🌸");
        sender.sendMessage("");

        if (sender.hasPermission("survivalcore.magicflower.give")) {
            sender.sendMessage(ChatColor.AQUA + "/magicflower give <jugador> <tipo> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "  • Da flores mágicas a un jugador");
            sender.sendMessage(ChatColor.GRAY + "  • Tipos: love, healing, speed, strength, night_vision");
        }

        sender.sendMessage(ChatColor.AQUA + "/magicflower list");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra todas las flores mágicas disponibles");

        sender.sendMessage(ChatColor.AQUA + "/magicflower info");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra información de la flor en tu mano");

        sender.sendMessage(ChatColor.AQUA + "/magicflower help");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra esta ayuda");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "🎮 Cómo usar:");
        sender.sendMessage(ChatColor.WHITE + "1. Obtén flores mágicas con " + ChatColor.AQUA + "/magicflower give");
        sender.sendMessage(ChatColor.WHITE + "2. Coloca una Maceta Mágica en el suelo");
        sender.sendMessage(ChatColor.WHITE + "3. Click derecho en la maceta con la flor");
        sender.sendMessage(ChatColor.WHITE + "4. ¡Disfruta de los efectos mágicos continuos!");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "⚠ Importante:");
        sender.sendMessage(ChatColor.GRAY + "  • Solo funcionan en Macetas Mágicas especiales");
        sender.sendMessage(ChatColor.GRAY + "  • Las macetas normales no las aceptan");
        sender.sendMessage(ChatColor.GRAY + "  • Mayor nivel = efectos más potentes");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════════════ 🌸");
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

    /**
     * Convierte un string a tipo de flor
     */
    private MagicFlowerFactory.FlowerType getFlowerTypeFromString(String input) {
        switch (input.toLowerCase()) {
            case "love":
            case "amor":
                return MagicFlowerFactory.FlowerType.LOVE_FLOWER;
            case "healing":
            case "heal":
            case "sanacion":
                return MagicFlowerFactory.FlowerType.HEALING_FLOWER;
            case "speed":
            case "velocidad":
                return MagicFlowerFactory.FlowerType.SPEED_FLOWER;
            case "strength":
            case "fuerza":
                return MagicFlowerFactory.FlowerType.STRENGTH_FLOWER;
            case "night_vision":
            case "night":
            case "vision":
            case "nocturna":
                return MagicFlowerFactory.FlowerType.NIGHT_VISION_FLOWER;
            default:
                return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcomandos principales
            List<String> subcommands = Arrays.asList("list", "info", "help");

            if (sender.hasPermission("survivalcore.magicflower.give")) {
                subcommands = Arrays.asList("give", "list", "info", "help");
            }

            return subcommands.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());

        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Autocompletar jugadores
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());

        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Autocompletar tipos de flores
            List<String> flowerTypes = Arrays.asList("love", "healing", "speed", "strength", "night_vision");
            return flowerTypes.stream()
                    .filter(type -> type.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());

        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // Autocompletar niveles (1-5)
            List<String> levels = Arrays.asList("1", "2", "3", "4", "5");
            return levels.stream()
                    .filter(level -> level.startsWith(args[3]))
                    .collect(Collectors.toList());

        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            // Autocompletar cantidades comunes
            List<String> amounts = Arrays.asList("1", "2", "4", "8", "16", "32", "64");
            return amounts.stream()
                    .filter(amount -> amount.startsWith(args[4]))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}