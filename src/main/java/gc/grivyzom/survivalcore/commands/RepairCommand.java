package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sistema de Reparación Mejorado y Moderno v3.0
 *
 * Características:
 * - Cálculos precisos de costos basados en material, encantamientos y daño
 * - Sistema de confirmación para reparaciones masivas
 * - Descuentos por permisos
 * - Efectos visuales y sonoros
 * - Cooldowns y límites de seguridad
 * - Logging detallado para administradores
 * - Gestión segura de experiencia
 *
 * @author Brocolitx
 * @version 3.0 - Sistema Completamente Reescrito
 */
public class RepairCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    // Configuración dinámica cargada desde config.yml
    private double baseCostMultiplier;
    private double enchantmentMultiplier;
    private double mendingExtraMultiplier;
    private int minCost;
    private int maxCostPerItem;
    private long cooldownMs;

    // Multiplicadores de material
    private final Map<String, Double> materialMultipliers = new HashMap<>();

    // Multiplicadores de encantamientos específicos
    private final Map<Enchantment, Double> enchantmentSpecificMultipliers = new HashMap<>();

    // Gestión de cooldowns y sesiones
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, RepairSession> repairSessions = new HashMap<>();

    // Formateo de números
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");

    public RepairCommand(Main plugin) {
        this.plugin = plugin;
        loadConfiguration();
        initializeMultipliers();
    }

    /**
     * Carga la configuración desde config.yml con valores por defecto seguros
     */
    private void loadConfiguration() {
        plugin.reloadConfig();

        // Valores base
        baseCostMultiplier = plugin.getConfig().getDouble("repair.base_cost_multiplier", 0.05);
        enchantmentMultiplier = plugin.getConfig().getDouble("repair.enchantment_multiplier", 0.15);
        mendingExtraMultiplier = plugin.getConfig().getDouble("repair.mending_extra_multiplier", 0.20);
        minCost = plugin.getConfig().getInt("repair.min_cost", 1);
        maxCostPerItem = plugin.getConfig().getInt("repair.max_cost_per_item", 50);
        cooldownMs = plugin.getConfig().getLong("repair.cooldown", 3000);

        // Cargar multiplicadores de material
        materialMultipliers.clear();
        if (plugin.getConfig().contains("repair.material_multipliers")) {
            var section = plugin.getConfig().getConfigurationSection("repair.material_multipliers");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    materialMultipliers.put(key.toUpperCase(), section.getDouble(key));
                }
            }
        }

        // Valores por defecto si no hay configuración
        if (materialMultipliers.isEmpty()) {
            setDefaultMaterialMultipliers();
        }

        plugin.getLogger().info("Sistema de reparación configurado - Multiplicador base: " +
                baseCostMultiplier + ", Cooldown: " + cooldownMs + "ms");
    }

    /**
     * Establece multiplicadores por defecto para materiales
     */
    private void setDefaultMaterialMultipliers() {
        materialMultipliers.put("NETHERITE", 2.5);
        materialMultipliers.put("DIAMOND", 2.0);
        materialMultipliers.put("EMERALD", 1.8);
        materialMultipliers.put("IRON", 1.0);
        materialMultipliers.put("GOLD", 0.7);
        materialMultipliers.put("CHAINMAIL", 1.1);
        materialMultipliers.put("STONE", 0.5);
        materialMultipliers.put("WOOD", 0.3);
        materialMultipliers.put("LEATHER", 0.4);
        materialMultipliers.put("TURTLE", 1.3);
    }

    /**
     * Inicializa multiplicadores específicos para encantamientos
     */
    private void initializeMultipliers() {
        // Encantamientos premium (más costosos de reparar)
        enchantmentSpecificMultipliers.put(Enchantment.MENDING, 0.25);
        enchantmentSpecificMultipliers.put(Enchantment.SILK_TOUCH, 0.20);
        enchantmentSpecificMultipliers.put(Enchantment.LUCK, 0.18);
        enchantmentSpecificMultipliers.put(Enchantment.LOOT_BONUS_BLOCKS, 0.15);

        // Encantamientos de combate
        enchantmentSpecificMultipliers.put(Enchantment.DAMAGE_ALL, 0.12);
        enchantmentSpecificMultipliers.put(Enchantment.SWEEPING_EDGE, 0.10);
        enchantmentSpecificMultipliers.put(Enchantment.FIRE_ASPECT, 0.08);

        // Encantamientos de protección
        enchantmentSpecificMultipliers.put(Enchantment.PROTECTION_ENVIRONMENTAL, 0.10);
        enchantmentSpecificMultipliers.put(Enchantment.PROTECTION_FIRE, 0.08);
        enchantmentSpecificMultipliers.put(Enchantment.PROTECTION_EXPLOSIONS, 0.08);
        enchantmentSpecificMultipliers.put(Enchantment.PROTECTION_PROJECTILE, 0.08);

        // Encantamientos de utilidad
        enchantmentSpecificMultipliers.put(Enchantment.DIG_SPEED, 0.10);
        enchantmentSpecificMultipliers.put(Enchantment.DURABILITY, 0.05);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        // Verificar cooldown (con bypass para permisos)
        if (!player.hasPermission("survivalcore.repair.nocooldown") && isOnCooldown(player)) {
            long remaining = getRemainingCooldown(player);
            player.sendMessage(ChatColor.RED + "⏰ Debes esperar " +
                    decimalFormat.format(remaining / 1000.0) + " segundos antes de reparar nuevamente.");
            return true;
        }

        // Sin argumentos - reparar ítem en mano
        if (args.length == 0) {
            return repairItemInHand(player);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "all" -> {
                return repairAll(player);
            }
            case "armor", "armadura" -> {
                return repairArmor(player);
            }
            case "weapon", "arma", "sword" -> {
                return repairWeapon(player);
            }
            case "tool", "herramienta" -> {
                return repairTool(player);
            }
            case "cost", "costo", "precio" -> {
                return showRepairCost(player, args);
            }
            case "info", "information" -> {
                return showRepairInfo(player);
            }
            case "help", "ayuda" -> {
                return showHelp(player);
            }
            case "confirm", "confirmar" -> {
                return confirmRepair(player);
            }
            case "cancel", "cancelar" -> {
                return cancelRepair(player);
            }
            case "reload" -> {
                return reloadConfig(player);
            }
            case "debug" -> {
                return handleDebugCommand(player, args);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "❌ Subcomando desconocido. Usa " +
                        ChatColor.YELLOW + "/reparar help" + ChatColor.RED + " para ver la ayuda.");
                return true;
            }
        }
    }

// Mover estos métodos fuera de onCommand, al nivel de la clase

    private boolean handleDebugCommand(Player player, String[] args) {
        if (!player.hasPermission("survivalcore.repair.admin")) {
            player.sendMessage(ChatColor.RED + "❌ No tienes permisos para usar comandos de debug.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Comandos de debug:");
            player.sendMessage(ChatColor.WHITE + "  /reparar debug permisos - Ver todos tus permisos de reparación");
            player.sendMessage(ChatColor.WHITE + "  /reparar debug costo - Calcular costo del ítem en mano (con debug)");
            return true;
        }

        String debugType = args[1].toLowerCase();

        switch (debugType) {
            case "permisos", "permissions" -> {
                return debugPermissions(player);
            }
            case "costo", "cost" -> {
                return debugCostCalculation(player);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "❌ Tipo de debug desconocido: " + debugType);
                return true;
            }
        }
    }

    private boolean debugPermissions(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══ DEBUG DE PERMISOS ═══");
        player.sendMessage(ChatColor.WHITE + "Jugador: " + ChatColor.YELLOW + player.getName());
        player.sendMessage(ChatColor.WHITE + "Es OP: " + (player.isOp() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        player.sendMessage("");

        // Verificar cada permiso de reparación
        String[] permisos = {
                "survivalcore.repair.free",
                "survivalcore.repair.discount.legend",
                "survivalcore.repair.discount.vip",
                "survivalcore.repair.discount.premium",
                "survivalcore.repair.discount",
                "survivalcore.repair.nocooldown",
                "survivalcore.repair.admin"
        };

        for (String permiso : permisos) {
            boolean tiene = player.hasPermission(permiso);
            ChatColor color = tiene ? ChatColor.GREEN : ChatColor.RED;
            String estado = tiene ? "✓" : "✗";
            player.sendMessage(color + estado + " " + ChatColor.WHITE + permiso);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Si tienes permisos inesperados, verifica tu plugin de permisos (LuckPerms, etc.)");

        return true;
    }

    private boolean debugCostCalculation(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().getMaxDurability() == 0) {
            player.sendMessage(ChatColor.RED + "❌ Debes tener un ítem reparable en tu mano.");
            return true;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══ DEBUG DE CÁLCULO DE COSTO ═══");
        player.sendMessage(ChatColor.WHITE + "Ítem: " + ChatColor.YELLOW + getItemDisplayName(item));
        player.sendMessage(ChatColor.WHITE + "Ejecutando cálculo con logs de debug...");
        player.sendMessage(ChatColor.GRAY + "Revisa la consola para información detallada.");

        // Forzar cálculo con debug
        RepairInfo info = calculateRepairInfo(item, player);

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Resultado:");
        player.sendMessage(ChatColor.WHITE + "  Daño: " + ChatColor.RED + info.getDamage());
        player.sendMessage(ChatColor.WHITE + "  Costo final: " + ChatColor.YELLOW + info.getCost() + " niveles");
        player.sendMessage(ChatColor.WHITE + "  Descripción: " + ChatColor.GRAY + info.getDescription());

        return true;
    }
    /**
     * Repara el ítem en la mano principal con validaciones completas
     */
    private boolean repairItemInHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        // Validación completa del ítem
        RepairValidation validation = validateItem(item);
        if (!validation.isValid()) {
            player.sendMessage(ChatColor.RED + "❌ " + validation.getMessage());
            return true;
        }

        // Calcular información de reparación
        RepairInfo info = calculateRepairInfo(item, player);

        // Verificar si necesita reparación
        if (info.getDamage() == 0) {
            player.sendMessage(ChatColor.GREEN + "✅ Este ítem ya está en perfectas condiciones.");
            return true;
        }

        // Verificar experiencia suficiente
        if (!hasEnoughExperience(player, info.getCost())) {
            showInsufficientXpMessage(player, info.getCost());
            return true;
        }

        // Realizar reparación individual
        performRepair(player, Collections.singletonList(info), "ítem individual");
        return true;
    }

    /**
     * Repara todos los ítems reparables del inventario
     */
    private boolean repairAll(Player player) {
        List<RepairInfo> repairableItems = scanInventoryForRepairableItems(player);

        if (repairableItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "📦 No tienes ítems dañados para reparar en tu inventario.");
            return true;
        }

        int totalCost = repairableItems.stream()
                .mapToInt(RepairInfo::getCost)
                .sum();

        // Verificar experiencia suficiente
        if (!hasEnoughExperience(player, totalCost)) {
            showInsufficientXpMessage(player, totalCost);
            showRepairPreview(player, repairableItems, totalCost);
            return true;
        }

        // Solicitar confirmación para reparaciones masivas costosas
        if (shouldRequestConfirmation(repairableItems, totalCost)) {
            createRepairSession(player, repairableItems, "inventario completo");
            showConfirmationMessage(player, repairableItems, totalCost);
            return true;
        }

        // Realizar reparación masiva
        performRepair(player, repairableItems, "inventario completo");
        return true;
    }

    /**
     * Repara toda la armadura equipada
     */
    private boolean repairArmor(Player player) {
        List<RepairInfo> armorPieces = new ArrayList<>();

        // Escanear armadura equipada
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (ItemStack piece : armorContents) {
            if (piece != null && isRepairable(piece) && getDamage(piece) > 0) {
                armorPieces.add(calculateRepairInfo(piece, player));
            }
        }

        if (armorPieces.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "🛡️ No tienes armadura dañada equipada.");
            return true;
        }

        int totalCost = armorPieces.stream()
                .mapToInt(RepairInfo::getCost)
                .sum();

        if (!hasEnoughExperience(player, totalCost)) {
            showInsufficientXpMessage(player, totalCost);
            return true;
        }

        performRepair(player, armorPieces, "armadura");
        return true;
    }

    /**
     * Repara armas (espadas, hachas, arcos, etc.)
     */
    private boolean repairWeapon(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isWeapon(item)) {
            player.sendMessage(ChatColor.RED + "⚔️ No tienes un arma en tu mano principal.");
            showWeaponTypes(player);
            return true;
        }

        return repairSpecificItem(player, item, "arma");
    }

    /**
     * Repara herramientas (picos, palas, azadas, etc.)
     */
    private boolean repairTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isTool(item)) {
            player.sendMessage(ChatColor.RED + "🔧 No tienes una herramienta en tu mano principal.");
            showToolTypes(player);
            return true;
        }

        return repairSpecificItem(player, item, "herramienta");
    }

    /**
     * Repara un ítem específico con validaciones
     */
    private boolean repairSpecificItem(Player player, ItemStack item, String itemType) {
        RepairValidation validation = validateItem(item);
        if (!validation.isValid()) {
            player.sendMessage(ChatColor.RED + "❌ " + validation.getMessage());
            return true;
        }

        RepairInfo info = calculateRepairInfo(item, player);

        if (info.getDamage() == 0) {
            player.sendMessage(ChatColor.GREEN + "✅ Tu " + itemType + " ya está en perfectas condiciones.");
            return true;
        }

        if (!hasEnoughExperience(player, info.getCost())) {
            showInsufficientXpMessage(player, info.getCost());
            return true;
        }

        performRepair(player, Collections.singletonList(info), itemType);
        return true;
    }

    /**
     * Calcula la información de reparación para un ítem con algoritmo mejorado
     */
    private RepairInfo calculateRepairInfo(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) {
            return new RepairInfo(item, 0, 0, 0, "Error: Ítem inválido");
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return new RepairInfo(item, 0, 0, 0, "Error: Ítem sin durabilidad");
        }

        int damage = damageable.getDamage();
        int maxDurability = item.getType().getMaxDurability();

        if (damage == 0) {
            return new RepairInfo(item, 0, maxDurability, 0, "Ítem en perfectas condiciones");
        }

        // === ALGORITMO DE CÁLCULO DE COSTO MEJORADO ===

        // 1. Costo base proporcional al daño
        double baseCost = damage * baseCostMultiplier;

        // 2. Multiplicador por material
        double materialMultiplier = getMaterialMultiplier(item.getType());
        double materialAdjustedCost = baseCost * materialMultiplier;

        // 3. Multiplicador por encantamientos
        double enchantmentMultiplier = calculateEnchantmentMultiplier(item);
        double enchantedCost = materialAdjustedCost * enchantmentMultiplier;

        // 4. Aplicar límites
        int finalCost = (int) Math.ceil(enchantedCost);
        finalCost = Math.max(minCost, Math.min(finalCost, maxCostPerItem));

        // 5. Aplicar descuentos por permisos
        int originalCost = finalCost;
        finalCost = applyPermissionDiscounts(player, finalCost);

        // Crear descripción detallada del cálculo
        String description = createCostDescription(damage, maxDurability, materialMultiplier,
                enchantmentMultiplier, originalCost, finalCost, player);

        return new RepairInfo(item, damage, maxDurability, finalCost, description);
    }

    /**
     * Calcula el multiplicador total de encantamientos
     */
    private double calculateEnchantmentMultiplier(ItemStack item) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) {
            return 1.0;
        }

        double multiplier = 1.0;

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            // Multiplicador específico del encantamiento o genérico
            double enchantValue = enchantmentSpecificMultipliers.getOrDefault(enchant, enchantmentMultiplier);

            // Aplicar multiplicador base
            multiplier += enchantValue;

            // Bonus por nivel (niveles superiores al 1)
            if (level > 1) {
                multiplier += (level - 1) * 0.03; // 3% por nivel adicional
            }

            // Bonus extra por Mending
            if (enchant.equals(Enchantment.MENDING)) {
                multiplier += mendingExtraMultiplier;
            }
        }

        return multiplier;
    }

    /**
     * Obtiene el multiplicador de material optimizado
     */
    private double getMaterialMultiplier(Material material) {
        String materialName = material.name();

        // Buscar por coincidencia exacta primero
        for (Map.Entry<String, Double> entry : materialMultipliers.entrySet()) {
            if (materialName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return 1.0; // Multiplicador por defecto
    }

    /**
     * Aplica descuentos basados en permisos del jugador
     */
    private int applyPermissionDiscounts(Player player, int baseCost) {
        // Debug log para verificar permisos
        plugin.getLogger().info("=== DEBUG PERMISOS DE REPARACIÓN ===");
        plugin.getLogger().info("Jugador: " + player.getName());
        plugin.getLogger().info("Costo base: " + baseCost);
        plugin.getLogger().info("Permiso FREE: " + player.hasPermission("survivalcore.repair.free"));
        plugin.getLogger().info("Permiso LEGEND: " + player.hasPermission("survivalcore.repair.discount.legend"));
        plugin.getLogger().info("Permiso VIP: " + player.hasPermission("survivalcore.repair.discount.vip"));
        plugin.getLogger().info("Permiso PREMIUM: " + player.hasPermission("survivalcore.repair.discount.premium"));
        plugin.getLogger().info("Permiso BÁSICO: " + player.hasPermission("survivalcore.repair.discount"));
        plugin.getLogger().info("Es OP: " + player.isOp());

        // Verificar permisos en orden de prioridad
        if (player.hasPermission("survivalcore.repair.free")) {
            plugin.getLogger().info("Aplicando reparación GRATUITA");
            return 0;
        } else if (player.hasPermission("survivalcore.repair.discount.legend")) {
            int discountedCost = (int) Math.ceil(baseCost * 0.3); // 70% descuento
            plugin.getLogger().info("Aplicando descuento LEGEND: " + baseCost + " -> " + discountedCost);
            return discountedCost;
        } else if (player.hasPermission("survivalcore.repair.discount.vip")) {
            int discountedCost = (int) Math.ceil(baseCost * 0.5); // 50% descuento
            plugin.getLogger().info("Aplicando descuento VIP: " + baseCost + " -> " + discountedCost);
            return discountedCost;
        } else if (player.hasPermission("survivalcore.repair.discount.premium")) {
            int discountedCost = (int) Math.ceil(baseCost * 0.75); // 25% descuento
            plugin.getLogger().info("Aplicando descuento PREMIUM: " + baseCost + " -> " + discountedCost);
            return discountedCost;
        } else if (player.hasPermission("survivalcore.repair.discount")) {
            int discountedCost = (int) Math.ceil(baseCost * 0.85); // 15% descuento
            plugin.getLogger().info("Aplicando descuento BÁSICO: " + baseCost + " -> " + discountedCost);
            return discountedCost;
        }

        plugin.getLogger().info("Sin descuentos aplicados. Costo final: " + baseCost);
        plugin.getLogger().info("=====================================");
        return baseCost;
    }

    /**
     * Crea una descripción detallada del cálculo de costo
     */
    private String createCostDescription(int damage, int maxDurability, double materialMultiplier,
                                         double enchantmentMultiplier, int originalCost, int finalCost, Player player) {
        StringBuilder desc = new StringBuilder();

        double durabilityPercent = ((double)(maxDurability - damage) / maxDurability) * 100;
        desc.append("Durabilidad: ").append(durabilityPercent).append("% | ");
        desc.append("Material: x").append(decimalFormat.format(materialMultiplier)).append(" | ");
        desc.append("Encantamientos: x").append(decimalFormat.format(enchantmentMultiplier));

        if (originalCost != finalCost) {
            desc.append(" | Descuento aplicado");
        }

        return desc.toString();
    }

    /**
     * Verifica si el jugador tiene suficiente experiencia
     */
    private boolean hasEnoughExperience(Player player, int requiredLevels) {
        // Debug log
        plugin.getLogger().info("=== DEBUG VERIFICACIÓN XP ===");
        plugin.getLogger().info("Jugador: " + player.getName());
        plugin.getLogger().info("XP requerida: " + requiredLevels + " niveles");
        plugin.getLogger().info("XP actual: " + player.getLevel() + " niveles");
        plugin.getLogger().info("Permiso FREE: " + player.hasPermission("survivalcore.repair.free"));

        if (player.hasPermission("survivalcore.repair.free")) {
            plugin.getLogger().info("Verificación XP: BYPASS (reparación gratuita)");
            plugin.getLogger().info("=============================");
            return true;
        }

        boolean hasEnough = player.getLevel() >= requiredLevels;
        plugin.getLogger().info("Tiene suficiente XP: " + hasEnough);
        plugin.getLogger().info("=============================");
        return hasEnough;
    }

    /**
     * Realiza la reparación de ítems con manejo seguro de experiencia
     */
    private void performRepair(Player player, List<RepairInfo> items, String repairType) {
        int totalCost = items.stream().mapToInt(RepairInfo::getCost).sum();

        plugin.getLogger().info("=== DEBUG REPARACIÓN ===");
        plugin.getLogger().info("Jugador: " + player.getName());
        plugin.getLogger().info("Tipo de reparación: " + repairType);
        plugin.getLogger().info("Cantidad de ítems: " + items.size());
        plugin.getLogger().info("Costo total calculado: " + totalCost + " niveles");
        plugin.getLogger().info("XP actual del jugador: " + player.getLevel() + " niveles");
        plugin.getLogger().info("Permiso FREE: " + player.hasPermission("survivalcore.repair.free"));

        // Verificación final de seguridad
        if (!hasEnoughExperience(player, totalCost)) {
            plugin.getLogger().warning("ERROR: No tiene suficiente experiencia para completar la reparación");
            player.sendMessage(ChatColor.RED + "❌ Error: No tienes suficiente experiencia para completar la reparación.");
            return;
        }

        // Descontar experiencia de manera segura SOLO si no es gratuito
        boolean isFreeRepair = player.hasPermission("survivalcore.repair.free") || totalCost == 0;

        if (!isFreeRepair && totalCost > 0) {
            int currentLevel = player.getLevel();
            int newLevel = Math.max(0, currentLevel - totalCost);

            plugin.getLogger().info("Descontando XP: " + currentLevel + " -> " + newLevel + " (diferencia: " + totalCost + ")");

            player.setLevel(newLevel);
            player.setExp(0.0f); // Resetear barra de XP

            plugin.getLogger().info("XP descontada correctamente");
        } else {
            plugin.getLogger().info("Reparación gratuita - no se descuenta XP");
        }

        // Reparar cada ítem
        int repairedCount = 0;
        for (RepairInfo info : items) {
            if (repairItem(info.getItem())) {
                repairedCount++;
            }
        }

        plugin.getLogger().info("Ítems reparados exitosamente: " + repairedCount);

        // Establecer cooldown
        setCooldown(player, cooldownMs);

        // Efectos visuales y sonoros
        playRepairEffects(player, repairedCount > 1);

        // Mensajes de éxito
        showSuccessMessage(player, repairedCount, totalCost, repairType);

        // Log para administradores
        logRepairAction(player, repairedCount, totalCost, repairType);

        // Limpiar sesión si existe
        repairSessions.remove(player.getUniqueId());

        plugin.getLogger().info("Reparación completada exitosamente");
        plugin.getLogger().info("=======================");
    }

    /**
     * Repara un ítem individual
     */
    private boolean repairItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(meta);
            return true;
        }

        return false;
    }

    /**
     * Muestra mensaje de éxito personalizado
     */
    private void showSuccessMessage(Player player, int repairedCount, int totalCost, String repairType) {
        if (repairedCount == 1) {
            player.sendMessage(ChatColor.GREEN + "✅ Tu " + repairType + " ha sido reparado completamente.");
        } else {
            player.sendMessage(ChatColor.GREEN + "✅ Has reparado " + repairedCount +
                    " ítems de tu " + repairType + " exitosamente.");
        }

        if (totalCost > 0) {
            player.sendMessage(ChatColor.GRAY + "💰 Costo total: " + ChatColor.YELLOW + totalCost +
                    " niveles de experiencia.");
        } else {
            player.sendMessage(ChatColor.GRAY + "🎁 Reparación gratuita aplicada.");
        }
    }

    /**
     * Escanea el inventario buscando ítems reparables
     */
    private List<RepairInfo> scanInventoryForRepairableItems(Player player) {
        List<RepairInfo> repairableItems = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isRepairable(item)) continue;

            RepairInfo info = calculateRepairInfo(item, player);
            if (info.getDamage() > 0) {
                repairableItems.add(info);
            }
        }

        return repairableItems;
    }

    /**
     * Determina si se debe solicitar confirmación
     */
    private boolean shouldRequestConfirmation(List<RepairInfo> items, int totalCost) {
        int minItems = plugin.getConfig().getInt("repair.confirmation.min_items", 5);
        int minCost = plugin.getConfig().getInt("repair.confirmation.min_total_cost", 50);

        return items.size() >= minItems || totalCost >= minCost;
    }

    /**
     * Crea una sesión de reparación para confirmación
     */
    private void createRepairSession(Player player, List<RepairInfo> items, String repairType) {
        RepairSession session = new RepairSession(items, repairType, System.currentTimeMillis());
        repairSessions.put(player.getUniqueId(), session);

        // Expirar la sesión después del tiempo configurado
        int timeoutSeconds = plugin.getConfig().getInt("repair.confirmation.timeout", 30);
        new BukkitRunnable() {
            @Override
            public void run() {
                repairSessions.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, timeoutSeconds * 20L);
    }

    /**
     * Confirma una reparación pendiente
     */
    private boolean confirmRepair(Player player) {
        RepairSession session = repairSessions.get(player.getUniqueId());

        if (session == null) {
            player.sendMessage(ChatColor.RED + "❌ No tienes reparaciones pendientes de confirmación.");
            player.sendMessage(ChatColor.GRAY + "💡 Tip: Usa " + ChatColor.YELLOW + "/reparar all" +
                    ChatColor.GRAY + " para reparar tu inventario.");
            return true;
        }

        int timeoutMs = plugin.getConfig().getInt("repair.confirmation.timeout", 30) * 1000;
        if (System.currentTimeMillis() - session.getTimestamp() > timeoutMs) {
            repairSessions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "⏰ La confirmación ha expirado. Ejecuta el comando nuevamente.");
            return true;
        }

        int totalCost = session.getItems().stream()
                .mapToInt(RepairInfo::getCost)
                .sum();

        if (!hasEnoughExperience(player, totalCost)) {
            showInsufficientXpMessage(player, totalCost);
            return true;
        }

        performRepair(player, session.getItems(), session.getRepairType());
        return true;
    }

    /**
     * Cancela una reparación pendiente
     */
    private boolean cancelRepair(Player player) {
        RepairSession session = repairSessions.remove(player.getUniqueId());

        if (session == null) {
            player.sendMessage(ChatColor.RED + "❌ No tienes reparaciones pendientes para cancelar.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "✅ Reparación de " + session.getRepairType() + " cancelada.");
        return true;
    }

    /**
     * Recarga la configuración del sistema
     */
    private boolean reloadConfig(Player player) {
        if (!player.hasPermission("survivalcore.repair.reload")) {
            player.sendMessage(ChatColor.RED + "❌ No tienes permisos para recargar la configuración.");
            return true;
        }

        try {
            loadConfiguration();
            player.sendMessage(ChatColor.GREEN + "✅ Configuración del sistema de reparación recargada.");
            player.sendMessage(ChatColor.GRAY + "• Multiplicador base: " + baseCostMultiplier);
            player.sendMessage(ChatColor.GRAY + "• Multiplicador encantamientos: " + enchantmentMultiplier);
            player.sendMessage(ChatColor.GRAY + "• Costo máximo: " + maxCostPerItem);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error al recargar la configuración: " + e.getMessage());
        }

        return true;
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Valida si un ítem puede ser reparado
     */
    private RepairValidation validateItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new RepairValidation(false, "Debes tener un ítem en tu mano principal.");
        }

        if (!isRepairable(item)) {
            return new RepairValidation(false, "Este ítem no se puede reparar.");
        }

        if (!item.hasItemMeta()) {
            return new RepairValidation(false, "Este ítem no tiene metadatos válidos.");
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return new RepairValidation(false, "Este ítem no tiene durabilidad.");
        }

        return new RepairValidation(true, "El ítem puede ser reparado.");
    }

    private boolean isRepairable(ItemStack item) {
        if (item == null) return false;
        return item.getType().getMaxDurability() > 0;
    }

    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("SWORD") || name.contains("AXE") ||
                name.equals("TRIDENT") || name.equals("BOW") ||
                name.equals("CROSSBOW") || name.equals("SHIELD");
    }

    private boolean isTool(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("PICKAXE") || name.contains("SHOVEL") ||
                name.contains("HOE") || name.equals("SHEARS") ||
                name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL");
    }

    private int getDamage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

    // =================== MÉTODOS DE VISUALIZACIÓN ===================

    /**
     * Muestra información de costos de reparación
     */
    private boolean showRepairCost(Player player, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            return showAllRepairCosts(player);
        } else {
            return showItemRepairCost(player);
        }
    }

    /**
     * Muestra el costo del ítem en mano con detalles completos
     */
    private boolean showItemRepairCost(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        RepairValidation validation = validateItem(item);
        if (!validation.isValid()) {
            player.sendMessage(ChatColor.RED + "❌ " + validation.getMessage());
            return true;
        }

        RepairInfo info = calculateRepairInfo(item, player);
        String itemName = getItemDisplayName(item);

        // Calcular estadísticas de durabilidad
        int currentDurability = info.getMaxDurability() - info.getDamage();
        double percentage = (double) currentDurability / info.getMaxDurability() * 100;

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "ANÁLISIS DE REPARACIÓN" + ChatColor.GOLD + "              ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Ítem: " + itemName);
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Estado: " + getConditionColor(percentage) +
                String.format("%.1f%%", percentage) + ChatColor.WHITE + " (" + currentDurability + "/" + info.getMaxDurability() + ")");

        // Mostrar barra de durabilidad visual
        String durabilityBar = createDurabilityBar(percentage, 20);
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Durabilidad: " + durabilityBar);

        // Mostrar encantamientos si los tiene
        Map<Enchantment, Integer> enchantments = item.getEnchantments();
        if (!enchantments.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Encantamientos: " +
                    ChatColor.LIGHT_PURPLE + enchantments.size());

            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                String enchantName = getEnchantmentDisplayName(entry.getKey());
                String special = entry.getKey().equals(Enchantment.MENDING) ?
                        ChatColor.GOLD + " (Mending)" : "";
                player.sendMessage(ChatColor.GOLD + "║   " + ChatColor.GRAY + "• " +
                        ChatColor.WHITE + enchantName + " " + entry.getValue() + special);
            }
        }

        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "DESGLOSE DEL COSTO" + ChatColor.GOLD + "              ║");
        player.sendMessage(ChatColor.GOLD + "║ " + info.getDescription());

        // Mostrar descuentos por permisos
        String discountInfo = getPlayerDiscountInfo(player);
        if (!discountInfo.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + discountInfo);
        }

        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════╣");

        ChatColor costColor = hasEnoughExperience(player, info.getCost()) ? ChatColor.GREEN : ChatColor.RED;
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo total: " +
                costColor + info.getCost() + " niveles");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Tu experiencia: " +
                ChatColor.YELLOW + player.getLevel() + " niveles");

        if (info.getCost() == 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "✅ Reparación GRATUITA");
        } else if (hasEnoughExperience(player, info.getCost())) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "✅ Puedes reparar este ítem");
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "Usa " + ChatColor.WHITE +
                    "/reparar" + ChatColor.GRAY + " para proceder");
        } else {
            int missing = info.getCost() - player.getLevel();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "❌ Te faltan " + missing + " niveles");
        }

        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        return true;
    }

    /**
     * Muestra los costos de todos los ítems reparables
     */
    private boolean showAllRepairCosts(Player player) {
        List<RepairInfo> allItems = scanInventoryForRepairableItems(player);

        if (allItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "📦 No tienes ítems dañados en tu inventario.");
            return true;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.AQUA + "     ANÁLISIS COMPLETO DE INVENTARIO");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");

        int totalCost = 0;
        Map<String, List<RepairInfo>> groupedItems = allItems.stream()
                .collect(Collectors.groupingBy(info -> getCategoryName(info.getItem())));

        for (Map.Entry<String, List<RepairInfo>> entry : groupedItems.entrySet()) {
            String category = entry.getKey();
            List<RepairInfo> categoryItems = entry.getValue();
            int categoryCost = categoryItems.stream().mapToInt(RepairInfo::getCost).sum();

            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "📂 " + category + " (" + categoryItems.size() + " ítems):");

            for (RepairInfo info : categoryItems) {
                String itemName = getItemDisplayName(info.getItem());
                double percentage = (double)(info.getMaxDurability() - info.getDamage()) /
                        info.getMaxDurability() * 100;

                String condition = getConditionColor(percentage) + String.format("%.0f%%", percentage);

                player.sendMessage(String.format("  %s%s %s%s %s(%d niveles)",
                        ChatColor.WHITE, itemName,
                        ChatColor.GRAY, condition,
                        ChatColor.AQUA, info.getCost()));
            }

            player.sendMessage(ChatColor.GRAY + "  Subtotal: " + ChatColor.GOLD + categoryCost + " niveles");
            totalCost += categoryCost;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "💰 COSTO TOTAL: " + totalCost + " niveles");
        player.sendMessage(ChatColor.WHITE + "Tu experiencia: " + ChatColor.YELLOW + player.getLevel() + " niveles");

        if (hasEnoughExperience(player, totalCost)) {
            player.sendMessage(ChatColor.GREEN + "✅ Puedes reparar todo tu inventario");
            player.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/reparar all" +
                    ChatColor.GRAY + " para proceder");
        } else {
            int missing = totalCost - player.getLevel();
            player.sendMessage(ChatColor.RED + "❌ Te faltan " + missing + " niveles para reparar todo");
            player.sendMessage(ChatColor.YELLOW + "💡 Tip: Puedes reparar ítems individuales");
        }

        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
        return true;
    }

    /**
     * Muestra información del sistema de reparación
     */
    private boolean showRepairInfo(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "SISTEMA DE REPARACIÓN v3.0" + ChatColor.GOLD + "           ║");
        player.sendMessage(ChatColor.GOLD + "╠════════════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "📊 CONFIGURACIÓN ACTUAL" + ChatColor.GOLD + "               ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• Costo base: " +
                ChatColor.YELLOW + (baseCostMultiplier * 100) + "% del daño");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• Por encantamiento: " +
                ChatColor.YELLOW + "+" + (enchantmentMultiplier * 100) + "%");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• Bonus Mending: " +
                ChatColor.YELLOW + "+" + (mendingExtraMultiplier * 100) + "%");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• Rango de costo: " +
                ChatColor.YELLOW + minCost + "-" + maxCostPerItem + " niveles");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• Cooldown: " +
                ChatColor.YELLOW + (cooldownMs/1000) + " segundos");

        player.sendMessage(ChatColor.GOLD + "║                                        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "🛠️ MULTIPLICADORES DE MATERIAL" + ChatColor.GOLD + "        ║");

        // Mostrar algunos multiplicadores importantes
        String[] importantMaterials = {"NETHERITE", "DIAMOND", "IRON", "GOLD"};
        for (String material : importantMaterials) {
            Double multiplier = materialMultipliers.get(material);
            if (multiplier != null) {
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• " +
                        formatMaterialName(material) + ": " + ChatColor.YELLOW + "x" + multiplier);
            }
        }

        player.sendMessage(ChatColor.GOLD + "║                                        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "🎯 TUS PERMISOS" + ChatColor.GOLD + "                   ║");

        String discountInfo = getPlayerDiscountInfo(player);
        if (discountInfo.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "• Sin descuentos especiales");
        } else {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "• " + discountInfo);
        }

        if (player.hasPermission("survivalcore.repair.nocooldown")) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "• Sin cooldown entre reparaciones");
        }

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
        return true;
    }

    /**
     * Muestra la ayuda del comando
     */
    private boolean showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "       SISTEMA DE REPARACIÓN v3.0");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "📋 COMANDOS BÁSICOS:");
        player.sendMessage(ChatColor.WHITE + "  /reparar" + ChatColor.GRAY + " - Repara el ítem en tu mano");
        player.sendMessage(ChatColor.WHITE + "  /reparar all" + ChatColor.GRAY + " - Repara todo tu inventario");
        player.sendMessage(ChatColor.WHITE + "  /reparar armor" + ChatColor.GRAY + " - Repara tu armadura equipada");
        player.sendMessage(ChatColor.WHITE + "  /reparar weapon" + ChatColor.GRAY + " - Repara el arma en tu mano");
        player.sendMessage(ChatColor.WHITE + "  /reparar tool" + ChatColor.GRAY + " - Repara la herramienta en tu mano");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "📊 INFORMACIÓN:");
        player.sendMessage(ChatColor.WHITE + "  /reparar cost" + ChatColor.GRAY + " - Costo del ítem en mano");
        player.sendMessage(ChatColor.WHITE + "  /reparar cost all" + ChatColor.GRAY + " - Costos de todo el inventario");
        player.sendMessage(ChatColor.WHITE + "  /reparar info" + ChatColor.GRAY + " - Información del sistema");
        player.sendMessage(ChatColor.WHITE + "  /reparar help" + ChatColor.GRAY + " - Muestra esta ayuda");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚙️ GESTIÓN:");
        player.sendMessage(ChatColor.WHITE + "  /reparar confirm" + ChatColor.GRAY + " - Confirma reparación pendiente");
        player.sendMessage(ChatColor.WHITE + "  /reparar cancel" + ChatColor.GRAY + " - Cancela reparación pendiente");

        if (player.hasPermission("survivalcore.repair.reload")) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "🔧 ADMINISTRACIÓN:");
            player.sendMessage(ChatColor.WHITE + "  /reparar reload" + ChatColor.GRAY + " - Recarga la configuración");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "💡 TIPS:");
        player.sendMessage(ChatColor.GRAY + "• Los ítems con más encantamientos cuestan más");
        player.sendMessage(ChatColor.GRAY + "• Mending añade un costo extra significativo");
        player.sendMessage(ChatColor.GRAY + "• Materiales superiores (Netherite) son más costosos");
        player.sendMessage(ChatColor.GRAY + "• Reparaciones masivas requieren confirmación");

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        return true;
    }

    /**
     * Muestra mensaje de confirmación
     */
    private void showConfirmationMessage(Player player, List<RepairInfo> items, int totalCost) {
        // Agrupar ítems por categoría para mejor visualización
        Map<String, List<RepairInfo>> groupedItems = items.stream()
                .collect(Collectors.groupingBy(info -> getCategoryName(info.getItem())));

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "╔════════════════════════════════════════╗");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.GOLD + "🔔 CONFIRMACIÓN DE REPARACIÓN" + ChatColor.YELLOW + "        ║");
        player.sendMessage(ChatColor.YELLOW + "╠════════════════════════════════════════╣");

        for (Map.Entry<String, List<RepairInfo>> entry : groupedItems.entrySet()) {
            String category = entry.getKey();
            int categoryCount = entry.getValue().size();
            int categoryCost = entry.getValue().stream().mapToInt(RepairInfo::getCost).sum();

            player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + category + ": " +
                    ChatColor.AQUA + categoryCount + " ítems" + ChatColor.GRAY + " (" +
                    categoryCost + " niveles)");
        }

        player.sendMessage(ChatColor.YELLOW + "╠════════════════════════════════════════╣");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Total de ítems: " +
                ChatColor.AQUA + items.size());
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Costo total: " +
                ChatColor.GREEN + totalCost + " niveles");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Tu experiencia: " +
                ChatColor.YELLOW + player.getLevel() + " niveles");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Después de reparar: " +
                ChatColor.GOLD + (player.getLevel() - totalCost) + " niveles");
        player.sendMessage(ChatColor.YELLOW + "║                                        ║");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.GREEN + "✅ /reparar confirm" +
                ChatColor.WHITE + " - Proceder");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.RED + "❌ /reparar cancel" +
                ChatColor.WHITE + " - Cancelar");
        player.sendMessage(ChatColor.YELLOW + "║                                        ║");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.GRAY + "Esta confirmación expira en 30s");
        player.sendMessage(ChatColor.YELLOW + "╚════════════════════════════════════════╝");
    }

    /**
     * Muestra vista previa de reparación cuando no hay suficiente XP
     */
    private void showRepairPreview(Player player, List<RepairInfo> items, int totalCost) {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "═══ VISTA PREVIA DE REPARACIÓN ═══");

        // Mostrar los ítems más prioritarios primero
        items.sort((a, b) -> {
            // Prioridad: menor % de durabilidad primero
            double percentA = (double)(a.getMaxDurability() - a.getDamage()) / a.getMaxDurability();
            double percentB = (double)(b.getMaxDurability() - b.getDamage()) / b.getMaxDurability();
            return Double.compare(percentA, percentB);
        });

        Map<String, List<RepairInfo>> byType = items.stream()
                .collect(Collectors.groupingBy(info -> getCategoryName(info.getItem())));

        for (Map.Entry<String, List<RepairInfo>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<RepairInfo> typeItems = entry.getValue();
            int typeCost = typeItems.stream().mapToInt(RepairInfo::getCost).sum();

            player.sendMessage(ChatColor.WHITE + "📦 " + type + " (" + typeItems.size() +
                    " ítems) - " + ChatColor.YELLOW + typeCost + " niveles");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "💰 Total requerido: " + ChatColor.GOLD + totalCost + " niveles");
        player.sendMessage(ChatColor.WHITE + "💎 Tu experiencia: " + ChatColor.YELLOW + player.getLevel() + " niveles");
        int missing = totalCost - player.getLevel();
        player.sendMessage(ChatColor.RED + "❌ Te faltan: " + missing + " niveles");

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Puedes reparar ítems individuales:");
        player.sendMessage(ChatColor.GRAY + "• /reparar weapon - Solo armas");
        player.sendMessage(ChatColor.GRAY + "• /reparar armor - Solo armadura");
        player.sendMessage(ChatColor.GRAY + "• /reparar tool - Solo herramientas");
    }

    /**
     * Muestra mensaje de XP insuficiente
     */
    private void showInsufficientXpMessage(Player player, int required) {
        int current = player.getLevel();
        int missing = required - current;

        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "╔════════════════════════════════════════╗");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "💎 EXPERIENCIA INSUFICIENTE" + ChatColor.RED + "           ║");
        player.sendMessage(ChatColor.RED + "╠════════════════════════════════════════╣");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Necesitas: " +
                ChatColor.YELLOW + required + " niveles");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Tienes: " +
                ChatColor.YELLOW + current + " niveles");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Te faltan: " +
                ChatColor.GOLD + missing + " niveles");
        player.sendMessage(ChatColor.RED + "║                                        ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "💡 Consigue más experiencia:");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "• Mata mobs y mina bloques");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "• Usa hornos y encantamientos");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "• Comercia con aldeanos");
        player.sendMessage(ChatColor.RED + "╚════════════════════════════════════════╝");
    }

    /**
     * Muestra tipos de armas disponibles
     */
    private void showWeaponTypes(Player player) {
        player.sendMessage(ChatColor.GRAY + "🗡️ Armas reparables: Espadas, Hachas, Arcos, Ballestas, Tridentes, Escudos");
    }

    /**
     * Muestra tipos de herramientas disponibles
     */
    private void showToolTypes(Player player) {
        player.sendMessage(ChatColor.GRAY + "🔧 Herramientas reparables: Picos, Palas, Azadas, Tijeras, Cañas de pescar, Mecheros");
    }

    // =================== EFECTOS Y SONIDOS ===================

    /**
     * Reproduce efectos visuales y sonoros
     */
    private void playRepairEffects(Player player, boolean isMultiple) {
        // Sonidos configurables desde config.yml
        if (isMultiple) {
            playSound(player, "BLOCK_ANVIL_USE", 1.0f, 1.0f);
            playSound(player, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
            playSound(player, "BLOCK_ENCHANTMENT_TABLE_USE", 0.8f, 1.5f);
        } else {
            playSound(player, "BLOCK_ANVIL_USE", 1.0f, 1.2f);
            playSound(player, "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 0.8f);
        }

        // Partículas
        if (plugin.getConfig().getBoolean("repair.effects.particles.enabled", true)) {
            player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                    player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

            if (isMultiple) {
                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                        player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                player.getWorld().spawnParticle(Particle.CRIT_MAGIC,
                        player.getLocation().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3, 0.05);
            } else {
                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                        player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    /**
     * Reproduce un sonido de manera segura
     */
    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            // Sonido no válido, ignorar silenciosamente
        }
    }

    // =================== UTILIDADES DE FORMATO ===================

    /**
     * Obtiene el nombre de categoría para un ítem
     */
    private String getCategoryName(ItemStack item) {
        if (isWeapon(item)) return "Armas";
        if (isTool(item)) return "Herramientas";
        if (isArmor(item)) return "Armadura";
        return "Otros";
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS") ||
                name.equals("ELYTRA");
    }

    /**
     * Obtiene el nombre de display de un ítem
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return formatMaterialName(item.getType().toString());
    }

    /**
     * Formatea el nombre de un material
     */
    private String formatMaterialName(String material) {
        return Arrays.stream(material.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Obtiene el nombre de display de un encantamiento
     */
    private String getEnchantmentDisplayName(Enchantment enchantment) {
        return formatMaterialName(enchantment.getKey().getKey().toUpperCase());
    }

    /**
     * Obtiene información de descuentos del jugador
     */
    private String getPlayerDiscountInfo(Player player) {
        if (player.hasPermission("survivalcore.repair.free")) {
            return "Reparación GRATUITA";
        } else if (player.hasPermission("survivalcore.repair.discount.legend")) {
            return "Descuento LEGEND (70%)";
        } else if (player.hasPermission("survivalcore.repair.discount.vip")) {
            return "Descuento VIP (50%)";
        } else if (player.hasPermission("survivalcore.repair.discount.premium")) {
            return "Descuento Premium (25%)";
        } else if (player.hasPermission("survivalcore.repair.discount")) {
            return "Descuento básico (15%)";
        }
        return "";
    }

    /**
     * Obtiene el color según el porcentaje de condición
     */
    private ChatColor getConditionColor(double percentage) {
        if (percentage > 75) return ChatColor.GREEN;
        if (percentage > 50) return ChatColor.YELLOW;
        if (percentage > 25) return ChatColor.GOLD;
        return ChatColor.RED;
    }

    /**
     * Crea una barra de durabilidad visual
     */
    private String createDurabilityBar(double percentage, int length) {
        int filled = (int) Math.round(percentage / 100.0 * length);
        filled = Math.max(0, Math.min(filled, length));

        StringBuilder bar = new StringBuilder();
        ChatColor color = getConditionColor(percentage);

        bar.append(color);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }

        return bar.toString();
    }

    // =================== GESTIÓN DE COOLDOWNS ===================

    /**
     * Verifica si un jugador está en cooldown
     */
    private boolean isOnCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUse = cooldowns.get(uuid);
        return lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs;
    }

    /**
     * Obtiene el tiempo restante de cooldown
     */
    private long getRemainingCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUse = cooldowns.get(uuid);
        if (lastUse == null) return 0;
        return Math.max(0, cooldownMs - (System.currentTimeMillis() - lastUse));
    }

    /**
     * Establece el cooldown para un jugador
     */
    private void setCooldown(Player player, long cooldownMs) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // =================== LOGGING Y ADMINISTRACIÓN ===================

    /**
     * Registra una acción de reparación para administradores
     */
    private void logRepairAction(Player player, int repairedCount, int totalCost, String repairType) {
        String logMessage = String.format("REPAIR: %s reparó %d ítems (%s) por %d niveles",
                player.getName(), repairedCount, repairType, totalCost);

        plugin.getLogger().info(logMessage);

        // Notificar a administradores online si la reparación es significativa
        if (totalCost >= 100 || repairedCount >= 10) {
            String adminMessage = ChatColor.GRAY + "[REPAIR] " + ChatColor.YELLOW + player.getName() +
                    ChatColor.GRAY + " reparó " + ChatColor.WHITE + repairedCount +
                    ChatColor.GRAY + " ítems por " + ChatColor.GOLD + totalCost +
                    ChatColor.GRAY + " niveles";

            for (Player admin : plugin.getServer().getOnlinePlayers()) {
                if (admin.hasPermission("survivalcore.repair.admin.notify")) {
                    admin.sendMessage(adminMessage);
                }
            }
        }
    }

    // =================== TAB COMPLETION ===================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList(
                    "all", "armor", "weapon", "tool", "cost", "info", "help", "confirm", "cancel"
            );

            if (sender.hasPermission("survivalcore.repair.reload")) {
                completions = new ArrayList<>(completions);
                completions.add("reload");
            }

            if (sender.hasPermission("survivalcore.repair.admin")) {
                completions = new ArrayList<>(completions);
                completions.add("debug");
            }

            return completions.stream()
                    .filter(completion -> completion.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("cost")) {
                return Stream.of("all")
                        .filter(completion -> completion.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("debug") && sender.hasPermission("survivalcore.repair.admin")) {
                return Arrays.asList("permisos", "costo")
                        .stream()
                        .filter(completion -> completion.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }

    // =================== CLASES DE DATOS ===================

    /**
     * Clase para almacenar información de reparación
     */
    private static class RepairInfo {
        private final ItemStack item;
        private final int damage;
        private final int maxDurability;
        private final int cost;
        private final String description;

        public RepairInfo(ItemStack item, int damage, int maxDurability, int cost, String description) {
            this.item = item;
            this.damage = damage;
            this.maxDurability = maxDurability;
            this.cost = cost;
            this.description = description;
        }

        public ItemStack getItem() { return item; }
        public int getDamage() { return damage; }
        public int getMaxDurability() { return maxDurability; }
        public int getCost() { return cost; }
        public String getDescription() { return description; }
    }

    /**
     * Clase para validación de ítems
     */
    private static class RepairValidation {
        private final boolean valid;
        private final String message;

        public RepairValidation(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    /**
     * Clase para sesiones de reparación
     */
    private static class RepairSession {
        private final List<RepairInfo> items;
        private final String repairType;
        private final long timestamp;

        public RepairSession(List<RepairInfo> items, String repairType, long timestamp) {
            this.items = items;
            this.repairType = repairType;
            this.timestamp = timestamp;
        }

        public List<RepairInfo> getItems() { return items; }
        public String getRepairType() { return repairType; }
        public long getTimestamp() { return timestamp; }
    }
}