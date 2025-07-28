package gc.grivyzom.survivalcore.flowers.integration;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowers.config.FlowerConfigManager;
import gc.grivyzom.survivalcore.flowers.config.FlowerDefinition;
import gc.grivyzom.survivalcore.flowers.config.TierDefinition;
import gc.grivyzom.survivalcore.flowers.config.ConfigurableFlowerFactory;
import gc.grivyzom.survivalcore.flowers.effects.FlowerEffectHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Clase de integración que conecta el sistema configurable con los comandos existentes
 * Extiende el comando MagicFlowerPotCommand para soportar flores configurables
 *
 * @author Brocolitx
 * @version 1.0
 */
public class ConfigurableFlowerIntegration {

    private final Main plugin;
    private final FlowerConfigManager configManager;
    private final ConfigurableFlowerFactory flowerFactory;
    private final FlowerEffectHandler effectHandler;

    public ConfigurableFlowerIntegration(Main plugin) {
        this.plugin = plugin;
        this.configManager = new FlowerConfigManager(plugin);
        this.flowerFactory = new ConfigurableFlowerFactory(plugin, configManager);
        this.effectHandler = new FlowerEffectHandler(plugin, configManager);

        plugin.getLogger().info("Sistema de flores configurables inicializado con " +
                configManager.getFlowers().size() + " flores y " +
                configManager.getTiers().size() + " tiers.");
    }

    /**
     * Maneja el subcomando 'giveflower' con soporte para flores configurables
     */
    public boolean handleGiveConfigurableFlower(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.magicflower.give")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para dar Flores Mágicas configurables.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /flowerpot giveflower <jugador> <tipo> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: " + getAvailableFlowersList());
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[1]);
            return true;
        }

        String flowerId = args[2].toLowerCase();

        // Verificar que la flor existe en la configuración
        if (!configManager.hasFlower(flowerId)) {
            sender.sendMessage(ChatColor.RED + "Tipo de flor inválido: " + flowerId);
            sender.sendMessage(ChatColor.GRAY + "Tipos disponibles: " + getAvailableFlowersList());
            return true;
        }

        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        int level = 1;
        int amount = 1;

        // Procesar nivel si se proporciona
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
                if (!flowerDef.isValidLevel(level)) {
                    sender.sendMessage(ChatColor.RED + "El nivel debe estar entre 1 y " + flowerDef.getMaxLevel() + ".");
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
            ItemStack flower = flowerFactory.createConfigurableFlower(flowerId, level);
            if (flower != null) {
                target.getInventory().addItem(flower);
            }
        }

        // Mensajes de confirmación
        String flowerName = flowerDef.getDisplay().getName();
        String flowerText = amount == 1 ? "Flor Mágica" : "Flores Mágicas";
        String tierInfo = flowerDef.getTier().getFormattedName();

        target.sendMessage(ChatColor.GREEN + "Has recibido " + amount + " " +
                ChatColor.translateAlternateColorCodes('&', flowerName) +
                ChatColor.GREEN + " de nivel " + level + ".");
        target.sendMessage(ChatColor.GRAY + "Tier: " + tierInfo);

        sender.sendMessage(ChatColor.GREEN + "Has dado " + amount + " " + flowerText +
                " (" + flowerName + ") de nivel " + level + " a " + target.getName() + ".");

        return true;
    }

    /**
     * Maneja el subcomando 'listconfig' para mostrar flores configurables
     */
    public boolean handleListConfigurable(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════ FLORES CONFIGURABLES ═══════ 🌸");
        sender.sendMessage("");

        Map<String, TierDefinition> tiers = configManager.getTiers();
        Map<String, FlowerDefinition> flowers = configManager.getFlowers();

        // Mostrar información de tiers
        sender.sendMessage(ChatColor.AQUA + "Tiers disponibles:");
        for (TierDefinition tier : tiers.values()) {
            sender.sendMessage(ChatColor.WHITE + "  • " + tier.getFormattedName() +
                    ChatColor.GRAY + " (Nivel Max: " + tier.getMaxLevel() +
                    ", Multiplicador: " + tier.getEffectMultiplier() + "x)");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Flores disponibles:");

        // Agrupar flores por tier
        Map<String, List<FlowerDefinition>> flowersByTier = flowers.values().stream()
                .collect(Collectors.groupingBy(f -> f.getTier().getName()));

        for (TierDefinition tier : tiers.values()) {
            List<FlowerDefinition> tierFlowers = flowersByTier.get(tier.getName());
            if (tierFlowers == null || tierFlowers.isEmpty()) continue;

            sender.sendMessage("");
            sender.sendMessage(tier.getFormattedName() + ChatColor.WHITE + ":");

            for (FlowerDefinition flower : tierFlowers) {
                sender.sendMessage(ChatColor.WHITE + "  • " + ChatColor.GRAY + flower.getId() +
                        ChatColor.WHITE + " - " + ChatColor.translateAlternateColorCodes('&', flower.getDisplay().getName()));
                sender.sendMessage(ChatColor.GRAY + "    Material: " + ChatColor.YELLOW + flower.getMaterial().name() +
                        ChatColor.GRAY + " | Nivel Max: " + ChatColor.AQUA + flower.getMaxLevel());

                // Mostrar efectos principales
                if (!flower.getEffects().isEmpty()) {
                    String effectsText = flower.getEffects().stream()
                            .map(effect -> effect.getType().getName())
                            .collect(Collectors.joining(", "));
                    sender.sendMessage(ChatColor.GRAY + "    Efectos: " + ChatColor.GREEN + effectsText);
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "💡 Usa /flowerpot giveflower <jugador> <tipo> [nivel] para crear");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════════ 🌸");

        return true;
    }

    /**
     * Maneja el subcomando 'infoconfig' para mostrar información detallada
     */
    public boolean handleInfoConfigurable(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!flowerFactory.isConfigurableFlower(itemInHand)) {
            sender.sendMessage(ChatColor.RED + "Debes tener una Flor Mágica configurable en tu mano.");
            return true;
        }

        String info = flowerFactory.getFlowerInfo(itemInHand);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', info));

        return true;
    }

    /**
     * Maneja el subcomando 'reloadconfig' para recargar configuración
     */
    public boolean handleReloadConfig(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return true;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Recargar configuración
            configManager.reload();

            // Reiniciar handler de efectos
            effectHandler.reload();

            long endTime = System.currentTimeMillis();

            sender.sendMessage(ChatColor.GREEN + "✓ Configuración de flores recargada correctamente.");
            sender.sendMessage(ChatColor.GRAY + "Tiempo: " + (endTime - startTime) + "ms");
            sender.sendMessage(ChatColor.GRAY + "Flores cargadas: " + configManager.getFlowers().size());
            sender.sendMessage(ChatColor.GRAY + "Tiers cargados: " + configManager.getTiers().size());

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al recargar la configuración: " + e.getMessage());
            plugin.getLogger().severe("Error recargando configuración de flores: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Maneja el subcomando 'statsconfig' para mostrar estadísticas
     */
    public boolean handleStatsConfig(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para ver las estadísticas.");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "═══════ ESTADÍSTICAS CONFIGURABLES ═══════");

        // Estadísticas básicas
        sender.sendMessage(ChatColor.WHITE + "Flores definidas: " + ChatColor.AQUA + configManager.getFlowers().size());
        sender.sendMessage(ChatColor.WHITE + "Tiers definidos: " + ChatColor.AQUA + configManager.getTiers().size());

        // Estadísticas de efectos activos
        sender.sendMessage(ChatColor.WHITE + "Efectos activos: " + ChatColor.AQUA + effectHandler.getActiveEffectCount());
        sender.sendMessage(ChatColor.WHITE + "Mecánicas activas: " + ChatColor.AQUA + effectHandler.getActiveMechanicCount());

        // Configuración global
        FlowerConfigManager.GlobalSettings settings = configManager.getGlobalSettings();
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Configuración global:");
        sender.sendMessage(ChatColor.WHITE + "  Brillo de encantamiento: " +
                (settings.isEnchantGlintEnabled() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.WHITE + "  Partículas avanzadas: " +
                (settings.isAdvancedParticlesEnabled() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.WHITE + "  Sonidos personalizados: " +
                (settings.isCustomSoundsEnabled() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.WHITE + "  Intervalo de efectos: " + ChatColor.AQUA + settings.getEffectRefreshInterval() + "s");

        // Distribución por tiers
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Distribución por tier:");
        Map<String, Long> flowersByTier = configManager.getFlowers().values().stream()
                .collect(Collectors.groupingBy(f -> f.getTier().getName(), Collectors.counting()));

        for (Map.Entry<String, Long> entry : flowersByTier.entrySet()) {
            TierDefinition tier = configManager.getTier(entry.getKey());
            String tierName = tier != null ? tier.getFormattedName() : entry.getKey();
            sender.sendMessage(ChatColor.WHITE + "  " + tierName + ": " + ChatColor.AQUA + entry.getValue() + " flores");
        }

        sender.sendMessage(ChatColor.AQUA + "═════════════════════════════════════════");

        return true;
    }

    /**
     * Maneja el subcomando 'migrateflowers' para convertir flores tradicionales
     */
    public boolean handleMigrateFlowers(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para migrar flores.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        int convertedCount = 0;  // 🔧 CORREGIDO: Cambió nombre de variable
        int totalItems = 0;      // 🔧 CORREGIDO: Cambió nombre de variable

        // Revisar todo el inventario
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;

            totalItems++;

            // Intentar convertir si es una flor tradicional
            ItemStack convertedItem = flowerFactory.convertLegacyFlower(item);  // 🔧 CORREGIDO: Cambió nombre de variable
            if (convertedItem != item) { // Si cambió, fue convertida
                player.getInventory().setItem(i, convertedItem);
                convertedCount++;  // 🔧 CORREGIDO: Ahora incrementa el contador correcto
            }
        }

        player.sendMessage(ChatColor.GREEN + "✓ Migración completada.");
        player.sendMessage(ChatColor.GRAY + "Flores convertidas: " + convertedCount + "/" + totalItems);

        if (convertedCount > 0) {
            player.sendMessage(ChatColor.YELLOW
                    + "Las flores convertidas ahora usan el sistema configurable.");
        }

        return true;
    }
    /**
     * Obtiene la lista de flores disponibles como string
     */
    private String getAvailableFlowersList() {
        return String.join(", ", configManager.getFlowers().keySet());
    }

    /**
     * Obtiene sugerencias de autocompletado para flores configurables
     */
    public List<String> getFlowerTabCompletions(String partial) {
        return configManager.getFlowers().keySet().stream()
                .filter(flowerId -> flowerId.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene sugerencias de autocompletado para tiers
     */
    public List<String> getTierTabCompletions(String partial) {
        return configManager.getTiers().keySet().stream()
                .filter(tierName -> tierName.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene sugerencias de niveles para una flor específica
     */
    public List<String> getLevelTabCompletions(String flowerId, String partial) {
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) return new ArrayList<>();

        List<String> levels = new ArrayList<>();
        for (int i = 1; i <= flowerDef.getMaxLevel(); i++) {
            String levelStr = String.valueOf(i);
            if (levelStr.startsWith(partial)) {
                levels.add(levelStr);
            }
        }

        return levels;
    }

    // =================== GETTERS PARA INTEGRACIÓN ===================

    public FlowerConfigManager getConfigManager() {
        return configManager;
    }

    public ConfigurableFlowerFactory getFlowerFactory() {
        return flowerFactory;
    }

    public FlowerEffectHandler getEffectHandler() {
        return effectHandler;
    }

    /**
     * Shutdown del sistema
     */
    public void shutdown() {
        if (effectHandler != null) {
            effectHandler.shutdown();
        }

        plugin.getLogger().info("Sistema de flores configurables desactivado.");
    }
}