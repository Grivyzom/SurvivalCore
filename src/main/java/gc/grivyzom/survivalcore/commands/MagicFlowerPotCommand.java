package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPot;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotManager;
import gc.grivyzom.survivalcore.flowers.MagicFlowerFactory;
import gc.grivyzom.survivalcore.flowers.config.FlowerConfigManager;
import gc.grivyzom.survivalcore.flowers.config.ConfigurableFlowerFactory;
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
 * Comando para gestionar las Macetas Mágicas - CORREGIDO v2.1
 * Errores de compilación solucionados
 *
 * @author Brocolitx
 * @version 2.1
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
            case "restrictions":
                return handleRestrictions(sender);
            case "stats":
                return handleStats(sender);
            case "reload":
                return handleReload(sender);
            case "giveflower":
                return handleGiveFlower(sender, args); // 🔧 CORREGIDO: Tipo correcto
            case "listflowers":
                return handleListFlowers(sender);
            case "reloadflowers":
                return handleReloadFlowers(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /flowerpot help para ver la ayuda.");
                return true;
        }
    }

    /**
     * Maneja el subcomando 'give' para MACETAS MÁGICAS
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.flowerpot.give")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para dar Macetas Mágicas.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /flowerpot give <jugador> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "Niveles disponibles: 1, 2, 3, 4, 5");
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
                sender.sendMessage(ChatColor.RED + "Cantidad inválida: " + args[3]);
                return true;
            }
        }

        // Crear macetas mágicas
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
     * 🔧 CORREGIDO: Maneja el subcomando 'giveflower' para FLORES CONFIGURABLES
     */
    private boolean handleGiveFlower(CommandSender sender, String[] args) { // 🔧 CORREGIDO: CommandSender, no SendSender
        if (!sender.hasPermission("survivalcore.magicflower.give")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para dar Flores Mágicas.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /flowerpot giveflower <jugador> <tipo> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "Usa '/flowerpot listflowers' para ver tipos disponibles");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[1]);
            return true;
        }

        String flowerId = args[2].toLowerCase();
        int level = 1;
        int amount = 1;

        // Procesar nivel si se proporciona
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
                if (level < 1 || level > 15) { // Máximo 15 para flores míticas
                    sender.sendMessage(ChatColor.RED + "El nivel debe estar entre 1 y 15.");
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

        // 🔧 CORRECCIÓN PRINCIPAL: Usar el sistema configurable
        if (plugin.getFlowerIntegration() != null) {
            ConfigurableFlowerFactory configurableFactory = plugin.getFlowerIntegration().getFlowerFactory();
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();

            // Verificar si la flor existe en la configuración
            if (!configManager.hasFlower(flowerId)) {
                sender.sendMessage(ChatColor.RED + "Tipo de flor no encontrado: " + flowerId);
                sender.sendMessage(ChatColor.GRAY + "Usa '/flowerpot listflowers' para ver flores disponibles");
                return true;
            }

            // 🔧 CORREGIDO: Verificar nivel máximo correctamente
            var flowerDef = configManager.getFlower(flowerId);
            int maxLevel = flowerDef.getConfig().getMaxLevel();
            if (level > maxLevel) {
                sender.sendMessage(ChatColor.RED + "El nivel máximo para " + flowerId + " es " + maxLevel);
                return true;
            }

            // Crear las flores configurables
            for (int i = 0; i < amount; i++) {
                ItemStack configurableFlower = configurableFactory.createConfigurableFlower(flowerId, level);
                if (configurableFlower != null) {
                    target.getInventory().addItem(configurableFlower);
                } else {
                    sender.sendMessage(ChatColor.RED + "Error al crear la flor: " + flowerId);
                    return true;
                }
            }

            // Obtener información de la flor para el mensaje
            String flowerDisplayName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                    flowerDef.getDisplay().getName()));
            String tierName = flowerDef.getTier().getName(); // 🔧 CORREGIDO: getName() en lugar de name()
            String tierColor = configManager.getTierColor(tierName);

            // Mensajes de confirmación
            String flowerText = amount == 1 ? "Flor Mágica" : "Flores Mágicas";
            target.sendMessage(ChatColor.GREEN + "Has recibido " + amount + " " + flowerText + ":");
            target.sendMessage(ChatColor.WHITE + "  🌸 " + ChatColor.translateAlternateColorCodes('&', tierColor + flowerDisplayName));
            target.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level);
            target.sendMessage(ChatColor.WHITE + "  🏆 Tier: " + ChatColor.translateAlternateColorCodes('&', tierColor + tierName));

            sender.sendMessage(ChatColor.GREEN + "Has dado " + amount + " " + flowerText + " de " +
                    flowerDisplayName + " (Lv." + level + ") a " + target.getName() + ".");

        } else {
            // Fallback al sistema tradicional si el configurable no está disponible
            sender.sendMessage(ChatColor.RED + "Sistema de flores configurables no disponible.");
            sender.sendMessage(ChatColor.GRAY + "Contacta a un administrador.");
            plugin.getLogger().warning("FlowerIntegration es null - Sistema configurable no inicializado");
            return true;
        }

        return true;
    }

    /**
     * 🆕 NUEVO: Lista todas las flores disponibles del sistema configurable
     */
    private boolean handleListFlowers(CommandSender sender) {
        if (plugin.getFlowerIntegration() == null) {
            sender.sendMessage(ChatColor.RED + "Sistema de flores configurables no disponible.");
            return true;
        }

        FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();

        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════ FLORES CONFIGURABLES ═══════ 🌸");
        sender.sendMessage("");

        for (String flowerId : configManager.getAllFlowerIds()) {
            var flowerDef = configManager.getFlower(flowerId);
            if (flowerDef == null) continue;

            String displayName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                    flowerDef.getDisplay().getName()));
            String tierName = flowerDef.getTier().getName(); // 🔧 CORREGIDO: getName() en lugar de name()
            String tierColor = configManager.getTierColor(tierName);
            int maxLevel = flowerDef.getConfig().getMaxLevel();

            sender.sendMessage(ChatColor.AQUA + "• " + flowerId);
            sender.sendMessage(ChatColor.WHITE + "  Nombre: " + ChatColor.translateAlternateColorCodes('&', tierColor + displayName));
            sender.sendMessage(ChatColor.WHITE + "  Tier: " + ChatColor.translateAlternateColorCodes('&', tierColor + tierName));
            sender.sendMessage(ChatColor.WHITE + "  Nivel máximo: " + ChatColor.YELLOW + maxLevel);
            sender.sendMessage(ChatColor.WHITE + "  Material: " + ChatColor.GRAY + flowerDef.getConfig().getType().name());
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.YELLOW + "💡 Usa: /flowerpot giveflower <jugador> <id_flor> [nivel] [cantidad]");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════ 🌸");

        return true;
    }

    /**
     * 🆕 NUEVO: Recarga el sistema de flores configurables
     */
    private boolean handleReloadFlowers(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return true;
        }

        if (plugin.getFlowerIntegration() == null) {
            sender.sendMessage(ChatColor.RED + "Sistema de flores configurables no disponible.");
            return true;
        }

        try {
            // Recargar configuración de flores
            plugin.getFlowerIntegration().reloadFlowerConfig();

            sender.sendMessage(ChatColor.GREEN + "✓ Configuración de flores recargada correctamente.");

            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
            int flowerCount = configManager.getAllFlowerIds().size();
            sender.sendMessage(ChatColor.GRAY + "Flores cargadas: " + flowerCount);

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al recargar flores: " + e.getMessage());
            plugin.getLogger().severe("Error recargando configuración de flores: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Maneja el subcomando 'list' - Solo para flores tradicionales
     */
    private boolean handleList(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════ FLORES TRADICIONALES ═══════ 🌸");
        sender.sendMessage(ChatColor.YELLOW + "⚠ Estas son las flores del sistema anterior");
        sender.sendMessage("");

        for (MagicFlowerFactory.FlowerType type : MagicFlowerFactory.FlowerType.values()) {
            sender.sendMessage(ChatColor.AQUA + "• " + type.getDisplayName());
            sender.sendMessage(ChatColor.WHITE + "  ID: " + ChatColor.GRAY + type.getId());
            sender.sendMessage(ChatColor.WHITE + "  Efecto: " + ChatColor.GREEN + type.getEffectDescription());
            sender.sendMessage(ChatColor.WHITE + "  Material: " + ChatColor.YELLOW + type.getMaterial().name());
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.YELLOW + "💡 Usa '/flowerpot listflowers' para ver las flores configurables");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════ 🌸");

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

        // Verificar si es una maceta mágica
        if (potFactory.isMagicFlowerPot(itemInHand)) {
            showPotInfo(player, itemInHand);
            return true;
        }

        // Verificar si es una flor mágica (configurable o tradicional)
        if (plugin.getFlowerIntegration() != null &&
                plugin.getFlowerIntegration().getFlowerFactory().isMagicFlower(itemInHand)) {
            showConfigurableFlowerInfo(player, itemInHand);
            return true;
        }

        if (flowerFactory.isMagicFlower(itemInHand)) {
            showTraditionalFlowerInfo(player, itemInHand);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Debes tener una Maceta Mágica o Flor Mágica en tu mano.");
        return true;
    }

    /**
     * 🆕 NUEVO: Muestra información de flores configurables
     */
    private void showConfigurableFlowerInfo(Player player, ItemStack flower) {
        ConfigurableFlowerFactory factory = plugin.getFlowerIntegration().getFlowerFactory();
        FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();

        String flowerId = factory.getFlowerId(flower);
        int level = factory.getFlowerLevel(flower);
        var flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) {
            player.sendMessage(ChatColor.RED + "Error: Flor configurable no reconocida.");
            return;
        }

        String displayName = ChatColor.translateAlternateColorCodes('&', flowerDef.getDisplay().getName());
        String tierName = flowerDef.getTier().getName(); // 🔧 CORREGIDO: getName() en lugar de name()
        String tierColor = configManager.getTierColor(tierName);

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ══════ FLOR CONFIGURABLE ══════ 🌸");
        player.sendMessage(ChatColor.WHITE + "  🌸 Nombre: " + displayName);
        player.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level + "/" + flowerDef.getConfig().getMaxLevel());
        player.sendMessage(ChatColor.WHITE + "  🆔 ID: " + ChatColor.GRAY + flowerId);
        player.sendMessage(ChatColor.WHITE + "  🏆 Tier: " + ChatColor.translateAlternateColorCodes('&', tierColor + tierName));
        player.sendMessage(ChatColor.WHITE + "  🧱 Material: " + ChatColor.YELLOW + flowerDef.getConfig().getType().name());
        player.sendMessage("");

        // Mostrar efectos
        player.sendMessage(ChatColor.YELLOW + "⚡ Efectos:");
        for (var effect : flowerDef.getEffects()) {
            int effectLevel = effect.calculateLevel(level);
            int duration = effect.calculateDuration(level);
            player.sendMessage(ChatColor.WHITE + "  • " + ChatColor.GREEN + effect.getType().getName() +
                    " " + (effectLevel + 1) + " (" + duration + "s)");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "🎯 Uso:");
        player.sendMessage(ChatColor.WHITE + "  • Coloca una Maceta Mágica en el suelo");
        player.sendMessage(ChatColor.WHITE + "  • Haz click derecho en la maceta con esta flor");
        player.sendMessage(ChatColor.WHITE + "  • ¡La maceta irradiará efectos configurables!");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════ 🌸");
    }

    /**
     * Muestra información de flores tradicionales
     */
    private void showTraditionalFlowerInfo(Player player, ItemStack flower) {
        String flowerId = flowerFactory.getFlowerId(flower);
        int level = flowerFactory.getFlowerLevel(flower);
        MagicFlowerFactory.FlowerType type = MagicFlowerFactory.FlowerType.getById(flowerId);

        if (type == null) {
            player.sendMessage(ChatColor.RED + "Error: Flor tradicional no reconocida.");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ══════ FLOR TRADICIONAL ══════ 🌸");
        player.sendMessage(ChatColor.WHITE + "  🌸 Nombre: " + ChatColor.LIGHT_PURPLE + type.getDisplayName());
        player.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level + "/5");
        player.sendMessage(ChatColor.WHITE + "  🆔 ID: " + ChatColor.GRAY + type.getId());
        player.sendMessage(ChatColor.WHITE + "  ⚡ Efecto: " + ChatColor.GREEN + type.getEffectDescription());
        player.sendMessage(ChatColor.WHITE + "  🧱 Material: " + ChatColor.YELLOW + type.getMaterial().name());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚠ Esta es una flor del sistema anterior");
        player.sendMessage(ChatColor.GRAY + "Se recomienda usar flores configurables");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════ 🌸");
    }

    /**
     * Muestra información de macetas
     */
    private void showPotInfo(Player player, ItemStack pot) {
        int level = potFactory.getPotLevel(pot);
        int range = potFactory.getPotRange(pot);
        String containedFlower = potFactory.getContainedFlower(pot);
        String potId = potFactory.getPotId(pot);

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🏺 ══════ MACETA MÁGICA ══════ 🏺");
        player.sendMessage(ChatColor.WHITE + "  🏺 Tipo: " + ChatColor.LIGHT_PURPLE + "Maceta Mágica");
        player.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level + "/5");
        player.sendMessage(ChatColor.WHITE + "  📏 Rango: " + ChatColor.GREEN + range + " bloques");
        player.sendMessage(ChatColor.WHITE + "  🆔 ID: " + ChatColor.GRAY + "#" + potId);

        if (containedFlower != null && !containedFlower.equals("none")) {
            String flowerDisplayName = getFlowerDisplayName(containedFlower);
            player.sendMessage(ChatColor.WHITE + "  🌸 Flor: " + ChatColor.LIGHT_PURPLE + flowerDisplayName);
            player.sendMessage(ChatColor.GREEN + "  ✓ Maceta activa irradiando efectos");
        } else {
            player.sendMessage(ChatColor.WHITE + "  🌸 Flor: " + ChatColor.YELLOW + "Vacía");
            player.sendMessage(ChatColor.GRAY + "  • Esperando flor mágica");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🏺 ═══════════════════════════════════ 🏺");
    }

    /**
     * Obtiene el nombre de display de una flor
     */
    private String getFlowerDisplayName(String flowerId) {
        // Intentar obtener de configuración primero
        if (plugin.getFlowerIntegration() != null) {
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
            if (configManager.hasFlower(flowerId)) {
                var flowerDef = configManager.getFlower(flowerId);
                if (flowerDef != null) {
                    return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                            flowerDef.getDisplay().getName()));
                }
            }
        }

        // Fallback a nombres tradicionales
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
     * 🔧 ACTUALIZADA: Muestra la ayuda con los nuevos comandos
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🏺 ═══════ COMANDOS DE MACETAS MÁGICAS ═══════ 🏺");
        sender.sendMessage("");

        if (sender.hasPermission("survivalcore.flowerpot.give")) {
            sender.sendMessage(ChatColor.AQUA + "/flowerpot give <jugador> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "  • Da macetas mágicas a un jugador (niveles 1-5)");
        }

        if (sender.hasPermission("survivalcore.magicflower.give")) {
            sender.sendMessage(ChatColor.AQUA + "/flowerpot giveflower <jugador> <id_flor> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "  • Da flores configurables a un jugador");
        }

        sender.sendMessage(ChatColor.AQUA + "/flowerpot listflowers");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra todas las flores configurables disponibles");

        sender.sendMessage(ChatColor.AQUA + "/flowerpot list");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra las flores tradicionales (sistema anterior)");

        sender.sendMessage(ChatColor.AQUA + "/flowerpot info");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra información del ítem en tu mano");

        sender.sendMessage(ChatColor.AQUA + "/flowerpot restrictions");
        sender.sendMessage(ChatColor.GRAY + "  • Muestra las restricciones del sistema");

        if (sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.AQUA + "/flowerpot stats");
            sender.sendMessage(ChatColor.GRAY + "  • Muestra estadísticas del sistema (admin)");

            sender.sendMessage(ChatColor.AQUA + "/flowerpot reload");
            sender.sendMessage(ChatColor.GRAY + "  • Recarga configuración de macetas (admin)");

            sender.sendMessage(ChatColor.AQUA + "/flowerpot reloadflowers");
            sender.sendMessage(ChatColor.GRAY + "  • Recarga configuración de flores (admin)");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "🎮 Cómo usar macetas mágicas:");
        sender.sendMessage(ChatColor.WHITE + "1. Obtén macetas con " + ChatColor.AQUA + "/flowerpot give");
        sender.sendMessage(ChatColor.WHITE + "2. Obtén flores con " + ChatColor.AQUA + "/flowerpot giveflower");
        sender.sendMessage(ChatColor.WHITE + "3. Coloca la maceta en el suelo");
        sender.sendMessage(ChatColor.WHITE + "4. Click derecho en la maceta con la flor");
        sender.sendMessage(ChatColor.WHITE + "5. ¡Disfruta de los efectos mágicos configurables!");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "⚠ Notas importantes:");
        sender.sendMessage(ChatColor.GRAY + "  • Las flores configurables están en flowers.yml");
        sender.sendMessage(ChatColor.GRAY + "  • Las macetas deben estar separadas al menos 2 bloques");
        sender.sendMessage(ChatColor.GRAY + "  • Mayor nivel = efectos más potentes");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🏺 ═══════════════════════════════════════ 🏺");
    }

    // =================== RESTO DE MÉTODOS ===================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcomandos principales
            List<String> subcommands = Arrays.asList("list", "listflowers", "info", "help", "restrictions");

            if (sender.hasPermission("survivalcore.flowerpot.give")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.add("give");
            }

            if (sender.hasPermission("survivalcore.magicflower.give")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.add("giveflower");
            }

            if (sender.hasPermission("survivalcore.flowerpot.admin")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.addAll(Arrays.asList("stats", "reload", "reloadflowers"));
            }

            return subcommands.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());

        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveflower"))) {
            // Autocompletar jugadores
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());

        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                // Autocompletar niveles de macetas (1-5)
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5");
                return levels.stream()
                        .filter(level -> level.startsWith(args[2]))
                        .collect(Collectors.toList());

            } else if (args[0].equalsIgnoreCase("giveflower")) {
                // 🔧 CORREGIDO: Autocompletar IDs de flores configurables
                if (plugin.getFlowerIntegration() != null) {
                    FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
                    return configManager.getAllFlowerIds().stream()
                            .filter(flowerId -> flowerId.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                } else {
                    // Fallback a flores tradicionales
                    List<String> traditionalFlowers = Arrays.asList("love_flower", "healing_flower", "speed_flower", "strength_flower", "night_vision_flower");
                    return traditionalFlowers.stream()
                            .filter(type -> type.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }

        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("give")) {
                // Autocompletar cantidades para macetas
                List<String> amounts = Arrays.asList("1", "2", "4", "8", "16", "32", "64");
                return amounts.stream()
                        .filter(amount -> amount.startsWith(args[3]))
                        .collect(Collectors.toList());

            } else if (args[0].equalsIgnoreCase("giveflower")) {
                // 🔧 CORREGIDO: Autocompletar niveles basado en la flor específica
                if (plugin.getFlowerIntegration() != null) {
                    FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
                    String flowerId = args[2].toLowerCase();

                    if (configManager.hasFlower(flowerId)) {
                        int maxLevel = configManager.getFlower(flowerId).getConfig().getMaxLevel();
                        List<String> levels = new ArrayList<>();
                        for (int i = 1; i <= maxLevel; i++) {
                            levels.add(String.valueOf(i));
                        }
                        return levels.stream()
                                .filter(level -> level.startsWith(args[3]))
                                .collect(Collectors.toList());
                    }
                }

                // Fallback genérico
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
                return levels.stream()
                        .filter(level -> level.startsWith(args[3]))
                        .collect(Collectors.toList());
            }

        } else if (args.length == 5 && args[0].equalsIgnoreCase("giveflower")) {
            // Autocompletar cantidades para flores
            List<String> amounts = Arrays.asList("1", "2", "4", "8", "16", "32", "64");
            return amounts.stream()
                    .filter(amount -> amount.startsWith(args[4]))
                    .collect(Collectors.toList());
        }

        return completions;
    }

    /**
     * Maneja el subcomando 'restrictions'
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
            sender.sendMessage(ChatColor.GRAY + "  • Sistema configurable en flowers.yml");
        } else {
            sender.sendMessage(ChatColor.GREEN + "  ✓ Todas las flores permitidas");
        }

        sender.sendMessage("");

        // Límites
        int maxPots = plugin.getConfig().getInt("magic_flowerpot.settings.max_pots_per_player", 10);
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

        // Estadísticas del sistema configurable
        if (plugin.getFlowerIntegration() != null) {
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "Sistema de flores configurables:");
            sender.sendMessage(ChatColor.WHITE + "  Flores disponibles: " + ChatColor.AQUA + configManager.getAllFlowerIds().size());
            sender.sendMessage(ChatColor.WHITE + "  Tiers definidos: " + ChatColor.AQUA + configManager.getAllTierNames().size());
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
}