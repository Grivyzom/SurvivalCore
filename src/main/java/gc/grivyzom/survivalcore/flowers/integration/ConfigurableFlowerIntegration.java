package gc.grivyzom.survivalcore.flowers.integration;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowers.config.ConfigurableFlowerFactory;
import gc.grivyzom.survivalcore.flowers.config.FlowerConfigManager;
import gc.grivyzom.survivalcore.flowers.effects.FlowerEffectHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;

/**
 * Clase de integración principal para el sistema de flores configurables
 * Conecta todos los componentes del sistema configurable
 *
 * @author Brocolitx
 * @version 1.0
 */
public class ConfigurableFlowerIntegration {

    private final Main plugin;
    private FlowerConfigManager configManager;
    private ConfigurableFlowerFactory flowerFactory;
    private FlowerEffectHandler effectHandler;

    public ConfigurableFlowerIntegration(Main plugin) {
        this.plugin = plugin;

        try {
            initializeConfigSystem();
            plugin.getLogger().info("ConfigurableFlowerIntegration inicializada correctamente");
        } catch (Exception e) {
            plugin.getLogger().severe("Error inicializando ConfigurableFlowerIntegration: " + e.getMessage());
            throw new RuntimeException("Fallo al inicializar sistema de flores configurables", e);
        }
    }

    /**
     * Inicializa todos los componentes del sistema configurable
     */
    private void initializeConfigSystem() {
        // 1. Asegurar que existe flowers.yml
        ensureFlowersConfigExists();

        // 2. Inicializar el manager de configuración
        configManager = new FlowerConfigManager(plugin);

        // 3. Inicializar la factory de flores
        flowerFactory = new ConfigurableFlowerFactory(plugin, configManager);

        // 4. Inicializar el handler de efectos
        effectHandler = new FlowerEffectHandler(plugin, configManager);

        plugin.getLogger().info("Todos los componentes del sistema configurable inicializados");
    }

    /**
     * Asegura que el archivo flowers.yml existe
     */
    private void ensureFlowersConfigExists() {
        File flowersFile = new File(plugin.getDataFolder(), "flowers.yml");

        if (!flowersFile.exists()) {
            plugin.getLogger().info("flowers.yml no encontrado, creando desde resources...");
            plugin.saveResource("flowers.yml", false);

            if (flowersFile.exists()) {
                plugin.getLogger().info("✓ flowers.yml creado correctamente");
            } else {
                throw new RuntimeException("No se pudo crear flowers.yml");
            }
        } else {
            plugin.getLogger().info("✓ flowers.yml encontrado");
        }
    }

    // =================== GETTERS PÚBLICOS ===================

    public FlowerConfigManager getConfigManager() {
        return configManager;
    }

    public ConfigurableFlowerFactory getFlowerFactory() {
        return flowerFactory;
    }

    public FlowerEffectHandler getEffectHandler() {
        return effectHandler;
    }

    // =================== MÉTODOS DE COMANDO ===================

    /**
     * Maneja el comando de dar flor configurable
     */
    public boolean handleGiveConfigurableFlower(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.magicflower.give")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para dar Flores Mágicas Configurables.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /flowerpot giveflower <jugador> <id_flor> [nivel] [cantidad]");
            sender.sendMessage(ChatColor.GRAY + "Usa '/flowerpot listconfig' para ver flores disponibles");
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

        // Verificar que la flor existe
        if (!configManager.hasFlower(flowerId)) {
            sender.sendMessage(ChatColor.RED + "Flor no encontrada: " + flowerId);
            sender.sendMessage(ChatColor.GRAY + "Usa '/flowerpot listconfig' para ver flores disponibles");
            return true;
        }

        // Procesar nivel
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
                int maxLevel = configManager.getFlower(flowerId).getConfig().getMaxLevel();
                if (level < 1 || level > maxLevel) {
                    sender.sendMessage(ChatColor.RED + "El nivel debe estar entre 1 y " + maxLevel + " para " + flowerId);
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Nivel inválido: " + args[3]);
                return true;
            }
        }

        // Procesar cantidad
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
            ItemStack configurableFlower = flowerFactory.createConfigurableFlower(flowerId, level);
            if (configurableFlower != null) {
                target.getInventory().addItem(configurableFlower);
            } else {
                sender.sendMessage(ChatColor.RED + "Error creando flor: " + flowerId);
                return true;
            }
        }

        // Mensajes de confirmación
        var flowerDef = configManager.getFlower(flowerId);
        String displayName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                flowerDef.getDisplay().getName()));
        String tierColor = configManager.getTierColor(flowerDef.getTier().getName());

        String flowerText = amount == 1 ? "Flor Configurable" : "Flores Configurables";
        target.sendMessage(ChatColor.GREEN + "Has recibido " + amount + " " + flowerText + ":");
        target.sendMessage(ChatColor.WHITE + "  🌸 " + ChatColor.translateAlternateColorCodes('&', tierColor + displayName));
        target.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + level);
        target.sendMessage(ChatColor.WHITE + "  🏆 Tier: " + ChatColor.translateAlternateColorCodes('&', tierColor + flowerDef.getTier().getName()));

        sender.sendMessage(ChatColor.GREEN + "Has dado " + amount + " " + flowerText + " de " +
                displayName + " (Lv." + level + ") a " + target.getName() + ".");

        return true;
    }

    /**
     * Lista todas las flores configurables
     */
    public boolean handleListConfigurable(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════ FLORES CONFIGURABLES ═══════ 🌸");
        sender.sendMessage("");

        for (String flowerId : configManager.getAllFlowerIds()) {
            var flowerDef = configManager.getFlower(flowerId);
            if (flowerDef == null) continue;

            String displayName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                    flowerDef.getDisplay().getName()));
            String tierName = flowerDef.getTier().getName();
            String tierColor = configManager.getTierColor(tierName);
            int maxLevel = flowerDef.getConfig().getMaxLevel();

            sender.sendMessage(ChatColor.AQUA + "• " + flowerId);
            sender.sendMessage(ChatColor.WHITE + "  Nombre: " + ChatColor.translateAlternateColorCodes('&', tierColor + displayName));
            sender.sendMessage(ChatColor.WHITE + "  Tier: " + ChatColor.translateAlternateColorCodes('&', tierColor + tierName));
            sender.sendMessage(ChatColor.WHITE + "  Nivel máximo: " + ChatColor.YELLOW + maxLevel);
            sender.sendMessage(ChatColor.WHITE + "  Material: " + ChatColor.GRAY + flowerDef.getConfig().getType().name());

            // Mostrar efectos principales
            if (!flowerDef.getEffects().isEmpty()) {
                sender.sendMessage(ChatColor.WHITE + "  Efectos: " + ChatColor.GREEN +
                        flowerDef.getEffects().size() + " efectos configurados");
            }

            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.YELLOW + "💡 Usa: /flowerpot giveflower <jugador> <id_flor> [nivel] [cantidad]");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════════ 🌸");

        return true;
    }

    /**
     * Muestra información detallada de una flor configurable
     */
    public boolean handleInfoConfigurable(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!flowerFactory.isConfigurableFlower(itemInHand)) {
            player.sendMessage(ChatColor.RED + "Debes tener una Flor Configurable en tu mano.");
            return true;
        }

        String flowerId = flowerFactory.getFlowerId(itemInHand);
        int level = flowerFactory.getFlowerLevel(itemInHand);
        var flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) {
            player.sendMessage(ChatColor.RED + "Error: Flor configurable no reconocida.");
            return true;
        }

        String displayName = ChatColor.translateAlternateColorCodes('&', flowerDef.getDisplay().getName());
        String tierName = flowerDef.getTier().getName();
        String tierColor = configManager.getTierColor(tierName);

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════ FLOR CONFIGURABLE ═══════ 🌸");
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

        // Mostrar mecánicas especiales si las hay
        if (!flowerDef.getSpecialMechanics().isEmpty()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "🔮 Mecánicas Especiales:");
            for (var mechanic : flowerDef.getSpecialMechanics().values()) {
                if (mechanic.isEnabled()) {
                    player.sendMessage(ChatColor.WHITE + "  • " + ChatColor.LIGHT_PURPLE +
                            mechanic.getName().replace("_", " "));
                }
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 ═══════════════════════════════════════ 🌸");

        return true;
    }

    /**
     * Recarga la configuración de flores
     */
    public boolean handleReloadConfig(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return true;
        }

        try {
            reloadFlowerConfig();

            sender.sendMessage(ChatColor.GREEN + "✓ Configuración de flores recargada correctamente.");

            int flowerCount = configManager.getAllFlowerIds().size();
            int tierCount = configManager.getAllTierNames().size();
            sender.sendMessage(ChatColor.GRAY + "Flores cargadas: " + flowerCount);
            sender.sendMessage(ChatColor.GRAY + "Tiers definidos: " + tierCount);

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al recargar configuración: " + e.getMessage());
            plugin.getLogger().severe("Error recargando flowers.yml: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Muestra estadísticas del sistema configurable
     */
    public boolean handleStatsConfig(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para ver estadísticas.");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "═══════ ESTADÍSTICAS SISTEMA CONFIGURABLE ═══════");

        int flowerCount = configManager.getAllFlowerIds().size();
        int tierCount = configManager.getAllTierNames().size();

        sender.sendMessage(ChatColor.WHITE + "Flores configurables: " + ChatColor.AQUA + flowerCount);
        sender.sendMessage(ChatColor.WHITE + "Tiers definidos: " + ChatColor.AQUA + tierCount);
        sender.sendMessage(ChatColor.WHITE + "Sistema de efectos: " + ChatColor.GREEN + "ACTIVO");

        if (effectHandler != null) {
            sender.sendMessage(ChatColor.WHITE + "Efectos activos: " + ChatColor.AQUA + effectHandler.getActiveEffectCount());
            sender.sendMessage(ChatColor.WHITE + "Mecánicas especiales: " + ChatColor.AQUA + effectHandler.getActiveMechanicCount());
        }

        // Distribución por tiers
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Distribución por tiers:");
        for (String tierName : configManager.getAllTierNames()) {
            long flowersByTier = configManager.getAllFlowerIds().stream()
                    .filter(id -> configManager.getFlower(id).getTier().getName().equals(tierName))
                    .count();
            String tierColor = configManager.getTierColor(tierName);
            sender.sendMessage(ChatColor.WHITE + "  " + ChatColor.translateAlternateColorCodes('&', tierColor + tierName) +
                    ": " + ChatColor.AQUA + flowersByTier + " flores");
        }

        sender.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════════════");

        return true;
    }

    /**
     * Migra flores del sistema tradicional al configurable (futuro)
     */
    public boolean handleMigrateFlowers(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para ejecutar migraciones.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "⚠ Función de migración no implementada aún.");
        sender.sendMessage(ChatColor.GRAY + "Esta función permitirá migrar flores tradicionales a configurables.");

        return true;
    }

    // =================== MÉTODOS DE GESTIÓN ===================

    /**
     * Recarga toda la configuración del sistema
     */
    public void reloadFlowerConfig() {
        try {
            // Detener handler de efectos
            if (effectHandler != null) {
                effectHandler.shutdown();
            }

            // Recargar configuración
            configManager.reloadConfig();

            // Reinicializar factory con nueva configuración
            flowerFactory = new ConfigurableFlowerFactory(plugin, configManager);

            // Reinicializar handler de efectos
            effectHandler = new FlowerEffectHandler(plugin, configManager);

            plugin.getLogger().info("Sistema de flores configurables recargado correctamente");

        } catch (Exception e) {
            plugin.getLogger().severe("Error recargando sistema configurable: " + e.getMessage());
            throw new RuntimeException("Fallo al recargar configuración de flores", e);
        }
    }

    /**
     * Desactiva todos los componentes del sistema
     */
    public void shutdown() {
        try {
            if (effectHandler != null) {
                effectHandler.shutdown();
                effectHandler = null;
            }

            configManager = null;
            flowerFactory = null;

            plugin.getLogger().info("Sistema de flores configurables desactivado correctamente");

        } catch (Exception e) {
            plugin.getLogger().warning("Error al desactivar sistema configurable: " + e.getMessage());
        }
    }

    // =================== MÉTODOS DE VALIDACIÓN ===================

    /**
     * Verifica si el sistema está funcionando correctamente
     */
    public boolean isSystemHealthy() {
        return configManager != null &&
                flowerFactory != null &&
                effectHandler != null &&
                !configManager.getAllFlowerIds().isEmpty();
    }

    /**
     * Obtiene información de diagnóstico del sistema
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();

        info.append("=== DIAGNÓSTICO SISTEMA CONFIGURABLE ===\n");
        info.append("ConfigManager: ").append(configManager != null ? "✓" : "❌").append("\n");
        info.append("FlowerFactory: ").append(flowerFactory != null ? "✓" : "❌").append("\n");
        info.append("EffectHandler: ").append(effectHandler != null ? "✓" : "❌").append("\n");

        if (configManager != null) {
            info.append("Flores cargadas: ").append(configManager.getAllFlowerIds().size()).append("\n");
            info.append("Tiers definidos: ").append(configManager.getAllTierNames().size()).append("\n");
        }

        if (effectHandler != null) {
            info.append("Efectos activos: ").append(effectHandler.getActiveEffectCount()).append("\n");
            info.append("Mecánicas activas: ").append(effectHandler.getActiveMechanicCount()).append("\n");
        }

        info.append("Estado general: ").append(isSystemHealthy() ? "SALUDABLE" : "CON PROBLEMAS");

        return info.toString();
    }

    // =================== MÉTODOS AUXILIARES ===================

    /**
     * Verifica si una flor es del sistema configurable
     */
    public boolean isConfigurableFlower(ItemStack item) {
        return flowerFactory != null && flowerFactory.isConfigurableFlower(item);
    }

    /**
     * Verifica si una flor es mágica (configurable o tradicional)
     */
    public boolean isMagicFlower(ItemStack item) {
        if (flowerFactory != null && flowerFactory.isMagicFlower(item)) {
            return true;
        }

        // También verificar flores tradicionales como fallback
        // (esto se podría expandir si necesitas compatibilidad)
        return false;
    }

    /**
     * Obtiene el ID de una flor configurable
     */
    public String getFlowerId(ItemStack item) {
        return flowerFactory != null ? flowerFactory.getFlowerId(item) : null;
    }

    /**
     * Obtiene el nivel de una flor configurable
     */
    public int getFlowerLevel(ItemStack item) {
        return flowerFactory != null ? flowerFactory.getFlowerLevel(item) : 1;
    }

    /**
     * Crea una flor configurable específica
     */
    public ItemStack createConfigurableFlower(String flowerId, int level) {
        return flowerFactory != null ? flowerFactory.createConfigurableFlower(flowerId, level) : null;
    }

    /**
     * Obtiene todas las flores configurables disponibles
     */
    public java.util.Set<String> getAllAvailableFlowers() {
        return configManager != null ? configManager.getAllFlowerIds() : java.util.Collections.emptySet();
    }

    /**
     * Verifica si existe una flor específica
     */
    public boolean hasFlower(String flowerId) {
        return configManager != null && configManager.hasFlower(flowerId);
    }

    /**
     * Obtiene el nombre de display de una flor
     */
    public String getFlowerDisplayName(String flowerId) {
        if (configManager == null || !configManager.hasFlower(flowerId)) {
            return "Flor Desconocida";
        }

        var flowerDef = configManager.getFlower(flowerId);
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                flowerDef.getDisplay().getName()));
    }

    /**
     * Obtiene el tier de una flor
     */
    public String getFlowerTier(String flowerId) {
        if (configManager == null || !configManager.hasFlower(flowerId)) {
            return "UNKNOWN";
        }

        return configManager.getFlower(flowerId).getTier().getName(); // 🔧 CORREGIDO: getName() en lugar de name()
    }

    /**
     * Obtiene el nivel máximo de una flor
     */
    public int getFlowerMaxLevel(String flowerId) {
        if (configManager == null || !configManager.hasFlower(flowerId)) {
            return 1;
        }

        return configManager.getFlower(flowerId).getConfig().getMaxLevel();
    }

    // =================== MÉTODOS DE COMPATIBILIDAD ===================

    /**
     * Convierte una flor tradicional a configurable (si es posible)
     */
    public ItemStack convertTraditionalToConfigurable(ItemStack traditionalFlower) {
        // Este método se podría implementar para migración automática
        // Por ahora retorna null indicando que no se puede convertir
        plugin.getLogger().info("Conversión de flor tradicional a configurable solicitada - no implementado");
        return null;
    }

    /**
     * Obtiene sugerencias de flores basadas en un texto
     */
    public java.util.List<String> getFlowerSuggestions(String input) {
        if (configManager == null) {
            return java.util.Collections.emptyList();
        }

        return configManager.getAllFlowerIds().stream()
                .filter(id -> id.toLowerCase().contains(input.toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
    }

    // =================== EVENTOS Y HOOKS ===================

    /**
     * Llamado cuando se coloca una flor en una maceta
     */
    public void onFlowerPlanted(String flowerId, int flowerLevel, org.bukkit.Location potLocation, Player player) {
        if (configManager == null) return;

        plugin.getLogger().info(String.format("Flor configurable plantada: %s (Lv.%d) por %s en %s",
                flowerId, flowerLevel, player.getName(),
                String.format("(%d,%d,%d)", potLocation.getBlockX(), potLocation.getBlockY(), potLocation.getBlockZ())));

        // Aquí se podrían disparar eventos personalizados del plugin
        // o ejecutar lógica especial cuando se planta una flor configurable
    }

    /**
     * Llamado cuando se remueve una flor de una maceta
     */
    public void onFlowerRemoved(String flowerId, int flowerLevel, org.bukkit.Location potLocation, Player player) {
        if (configManager == null) return;

        plugin.getLogger().info(String.format("Flor configurable removida: %s (Lv.%d) por %s",
                flowerId, flowerLevel, player.getName()));
    }

    /**
     * Llamado cuando se activa un efecto especial
     */
    public void onSpecialEffectTriggered(String flowerId, String mechanicName, Player player) {
        if (configManager == null) return;

        plugin.getLogger().info(String.format("Mecánica especial activada: %s.%s para %s",
                flowerId, mechanicName, player.getName()));
    }
}