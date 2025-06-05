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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comando /reparar mejorado con opciones avanzadas y sistema de costos dinámico.
 *
 * @author Brocolitx
 * @version 2.0
 */
public class RepairCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    // Configuración de costos
    private static final double BASE_COST_MULTIPLIER = 0.05; // 5% del daño como costo base
    private static final double ENCHANTMENT_MULTIPLIER = 0.10; // +10% por cada encantamiento
    private static final double MENDING_EXTRA_MULTIPLIER = 0.10; // +10% adicional si tiene Mending
    private static final int MIN_COST = 1; // Costo mínimo en niveles
    private static final int MAX_COST_PER_ITEM = 30; // Costo máximo por ítem individual

    // Configuración dinámica
    private double baseCostMultiplier;
    private double enchantmentMultiplier;
    private double mendingExtraMultiplier;
    private int minCost;
    private int maxCostPerItem;
    private Map<String, Double> materialMultipliers;

    // Cooldowns
    private static final long REPAIR_COOLDOWN = 3000; // 3 segundos
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // Cache de ítems reparables por tipo
    private final Map<UUID, RepairSession> repairSessions = new HashMap<>();

    public RepairCommand(Main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Carga la configuración desde config.yml
     */
    private void loadConfig() {
        plugin.reloadConfig();

        // Cargar valores de configuración con valores por defecto
        baseCostMultiplier = plugin.getConfig().getDouble("repair.base_cost_multiplier", BASE_COST_MULTIPLIER);
        enchantmentMultiplier = plugin.getConfig().getDouble("repair.enchantment_multiplier", ENCHANTMENT_MULTIPLIER);
        mendingExtraMultiplier = plugin.getConfig().getDouble("repair.mending_extra_multiplier", MENDING_EXTRA_MULTIPLIER);
        minCost = plugin.getConfig().getInt("repair.min_cost", MIN_COST);
        maxCostPerItem = plugin.getConfig().getInt("repair.max_cost_per_item", MAX_COST_PER_ITEM);

        // Cargar multiplicadores de material
        materialMultipliers = new HashMap<>();
        if (plugin.getConfig().contains("repair.material_multipliers")) {
            for (String material : plugin.getConfig().getConfigurationSection("repair.material_multipliers").getKeys(false)) {
                double multiplier = plugin.getConfig().getDouble("repair.material_multipliers." + material);
                materialMultipliers.put(material.toUpperCase(), multiplier);
            }
        } else {
            // Valores por defecto
            materialMultipliers.put("NETHERITE", 2.0);
            materialMultipliers.put("DIAMOND", 1.5);
            materialMultipliers.put("IRON", 1.0);
            materialMultipliers.put("GOLD", 0.8);
            materialMultipliers.put("STONE", 0.6);
            materialMultipliers.put("WOOD", 0.6);
        }
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
            player.sendMessage(ChatColor.RED + "Debes esperar " + (remaining / 1000) + " segundos antes de reparar nuevamente.");
            return true;
        }

        // Sin argumentos - reparar ítem en mano
        if (args.length == 0) {
            return repairItemInHand(player);
        }

        String subcommand = args[0].toLowerCase();

        // Usar switch statement tradicional en lugar de switch expression
        switch (subcommand) {
            case "all":
                return repairAll(player);
            case "armor":
                return repairArmor(player);
            case "sword":
                return repairWeapon(player, RepairableType.SWORD);
            case "tool":
                return repairTool(player);
            case "cost":
                return showRepairCost(player, args);
            case "info":
                return showRepairInfo(player);
            case "help":
                return showHelp(player);
            case "confirm":
                return confirmRepair(player);
            default:
                player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /reparar help para ver la ayuda.");
                return true;
        }
    }

    /**
     * Repara el ítem en la mano principal
     */
    private boolean repairItemInHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        RepairResult validation = validateItem(item);
        if (!validation.success()) {
            player.sendMessage(ChatColor.RED + validation.message());
            return true;
        }

        RepairInfo info = calculateRepairInfo(item, player); // Pasar player como parámetro

        if (player.getLevel() < info.cost()) {
            showInsufficientXP(player, info.cost());
            return true;
        }

        performRepair(player, Collections.singletonList(info));
        return true;
    }

    /**
     * Repara todos los ítems reparables del inventario
     */
    private boolean repairAll(Player player) {
        List<RepairInfo> repairableItems = new ArrayList<>();

        // Escanear inventario completo
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isRepairable(item)) continue;

            RepairInfo info = calculateRepairInfo(item, player); // Pasar player como parámetro
            if (info.damage() > 0) {
                repairableItems.add(info);
            }
        }

        if (repairableItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No tienes ítems dañados para reparar.");
            return true;
        }

        int totalCost = repairableItems.stream()
                .mapToInt(RepairInfo::cost)
                .sum();

        if (player.getLevel() < totalCost) {
            showInsufficientXP(player, totalCost);
            showRepairPreview(player, repairableItems, totalCost);
            return true;
        }

        // Solicitar confirmación para reparaciones masivas
        if (repairableItems.size() > 5 || totalCost > 50) {
            createRepairSession(player, repairableItems);
            showConfirmationMessage(player, repairableItems, totalCost);
            return true;
        }

        performRepair(player, repairableItems);
        return true;
    }

    /**
     * Repara toda la armadura equipada
     */
    private boolean repairArmor(Player player) {
        List<RepairInfo> armorPieces = new ArrayList<>();

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || !isRepairable(piece)) continue;

            RepairInfo info = calculateRepairInfo(piece, player); // Pasar player como parámetro
            if (info.damage() > 0) {
                armorPieces.add(info);
            }
        }

        if (armorPieces.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No tienes armadura dañada para reparar.");
            return true;
        }

        int totalCost = armorPieces.stream()
                .mapToInt(RepairInfo::cost)
                .sum();

        if (player.getLevel() < totalCost) {
            showInsufficientXP(player, totalCost);
            return true;
        }

        performRepair(player, armorPieces);
        return true;
    }

    /**
     * Repara armas (espadas, hachas, tridentes)
     */
    private boolean repairWeapon(Player player, RepairableType type) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isWeapon(item)) {
            player.sendMessage(ChatColor.RED + "No tienes un arma en tu mano principal.");
            return true;
        }

        RepairResult validation = validateItem(item);
        if (!validation.success()) {
            player.sendMessage(ChatColor.RED + validation.message());
            return true;
        }

        RepairInfo info = calculateRepairInfo(item, player); // Pasar player como parámetro

        if (player.getLevel() < info.cost()) {
            showInsufficientXP(player, info.cost());
            return true;
        }

        performRepair(player, Collections.singletonList(info));
        return true;
    }

    /**
     * Repara herramientas (picos, palas, hachas, azadas)
     */
    private boolean repairTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isTool(item)) {
            player.sendMessage(ChatColor.RED + "No tienes una herramienta en tu mano principal.");
            return true;
        }

        RepairResult validation = validateItem(item);
        if (!validation.success()) {
            player.sendMessage(ChatColor.RED + validation.message());
            return true;
        }

        RepairInfo info = calculateRepairInfo(item, player); // Pasar player como parámetro

        if (player.getLevel() < info.cost()) {
            showInsufficientXP(player, info.cost());
            return true;
        }

        performRepair(player, Collections.singletonList(info));
        return true;
    }

    /**
     * Muestra el costo de reparación
     */
    private boolean showRepairCost(Player player, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            showAllRepairCosts(player);
        } else {
            showItemRepairCost(player);
        }
        return true;
    }

    /**
     * Calcula la información de reparación para un ítem
     * @param item El ítem a reparar
     * @param player El jugador (para verificar permisos de descuento)
     */
    private RepairInfo calculateRepairInfo(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) {
            return new RepairInfo(item, 0, 0, 0);
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return new RepairInfo(item, 0, 0, 0);
        }

        int damage = damageable.getDamage();
        if (damage == 0) {
            return new RepairInfo(item, 0, 0, 0);
        }

        // Cálculo del costo base
        double baseCost = damage * baseCostMultiplier;

        // Multiplicador por encantamientos
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        double enchantmentMultiplierTotal = 1.0;

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            enchantmentMultiplierTotal += enchantmentMultiplier;

            // Costo adicional por nivel de encantamiento
            enchantmentMultiplierTotal += (entry.getValue() - 1) * 0.05;

            // Costo extra si tiene Mending
            if (entry.getKey().equals(Enchantment.MENDING)) {
                enchantmentMultiplierTotal += mendingExtraMultiplier;
            }
        }

        // Aplicar multiplicadores según el tipo de ítem
        double typeMultiplier = getTypeMultiplier(item.getType());

        int finalCost = (int) Math.ceil(baseCost * enchantmentMultiplierTotal * typeMultiplier);
        finalCost = Math.max(minCost, Math.min(finalCost, maxCostPerItem));

        // Aplicar descuentos por permisos
        if (player.hasPermission("survivalcore.repair.free")) {
            finalCost = 0;
        } else if (player.hasPermission("survivalcore.repair.discount.vip")) {
            finalCost = (int) Math.ceil(finalCost * 0.5); // 50% descuento
        } else if (player.hasPermission("survivalcore.repair.discount")) {
            finalCost = (int) Math.ceil(finalCost * 0.75); // 25% descuento
        }

        return new RepairInfo(item, damage, item.getType().getMaxDurability(), finalCost);
    }

    /**
     * Obtiene el multiplicador según el tipo de ítem
     */
    private double getTypeMultiplier(Material material) {
        String name = material.name();

        // Buscar en la configuración
        for (Map.Entry<String, Double> entry : materialMultipliers.entrySet()) {
            if (name.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Valor por defecto
        return 1.0;
    }

    /**
     * Realiza la reparación de los ítems
     */
    private void performRepair(Player player, List<RepairInfo> items) {
        int totalCost = items.stream().mapToInt(RepairInfo::cost).sum();

        // Descontar experiencia
        player.setLevel(player.getLevel() - totalCost);

        // Reparar cada ítem
        int repairedCount = 0;
        for (RepairInfo info : items) {
            ItemStack item = info.item();
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(0);
                item.setItemMeta(meta);
                repairedCount++;
            }
        }

        // Establecer cooldown
        setCooldown(player, REPAIR_COOLDOWN);

        // Efectos visuales y sonoros
        playRepairEffects(player, repairedCount > 1);

        // Mensaje de éxito
        if (repairedCount == 1) {
            RepairInfo info = items.get(0);
            String itemName = getItemDisplayName(info.item());
            player.sendMessage(ChatColor.GREEN + "✓ " + itemName + " ha sido reparado completamente.");
            player.sendMessage(ChatColor.GRAY + "Costo: " + info.cost() + " niveles de experiencia.");
        } else {
            player.sendMessage(ChatColor.GREEN + "✓ Has reparado " + repairedCount + " ítems exitosamente.");
            player.sendMessage(ChatColor.GRAY + "Costo total: " + totalCost + " niveles de experiencia.");
        }

        // Log para administradores
        plugin.getLogger().info(String.format("Repair: %s reparó %d ítems por %d niveles",
                player.getName(), repairedCount, totalCost));

        // Limpiar sesión si existe
        repairSessions.remove(player.getUniqueId());
    }

    /**
     * Crea una sesión de reparación para confirmación
     */
    private void createRepairSession(Player player, List<RepairInfo> items) {
        RepairSession session = new RepairSession(items, System.currentTimeMillis());
        repairSessions.put(player.getUniqueId(), session);

        // Expirar la sesión después de 30 segundos
        new BukkitRunnable() {
            @Override
            public void run() {
                repairSessions.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 600L); // 30 segundos
    }

    /**
     * Confirma una reparación pendiente
     */
    private boolean confirmRepair(Player player) {
        RepairSession session = repairSessions.get(player.getUniqueId());

        if (session == null) {
            player.sendMessage(ChatColor.RED + "No tienes reparaciones pendientes de confirmación.");
            return true;
        }

        if (System.currentTimeMillis() - session.timestamp() > 30000) {
            repairSessions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "La confirmación ha expirado. Ejecuta el comando nuevamente.");
            return true;
        }

        int totalCost = session.items().stream()
                .mapToInt(RepairInfo::cost)
                .sum();

        if (player.getLevel() < totalCost) {
            showInsufficientXP(player, totalCost);
            return true;
        }

        performRepair(player, session.items());
        return true;
    }

    /**
     * Muestra mensaje de confirmación
     */
    private void showConfirmationMessage(Player player, List<RepairInfo> items, int totalCost) {
        player.sendMessage(ChatColor.YELLOW + "╔══════════════════════════════════╗");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.GOLD + "CONFIRMACIÓN DE REPARACIÓN" + ChatColor.YELLOW + "      ║");
        player.sendMessage(ChatColor.YELLOW + "╠══════════════════════════════════╣");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Ítems a reparar: " +
                ChatColor.AQUA + items.size() + ChatColor.YELLOW + "              ║");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Costo total: " +
                ChatColor.GREEN + totalCost + " niveles" + ChatColor.YELLOW + "         ║");
        player.sendMessage(ChatColor.YELLOW + "║                                  ║");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Usa " + ChatColor.GREEN + "/reparar confirm" +
                ChatColor.WHITE + " para" + ChatColor.YELLOW + "     ║");
        player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "proceder con la reparación." + ChatColor.YELLOW + "      ║");
        player.sendMessage(ChatColor.YELLOW + "╚══════════════════════════════════╝");
    }

    /**
     * Muestra preview de reparación
     */
    private void showRepairPreview(Player player, List<RepairInfo> items, int totalCost) {
        player.sendMessage(ChatColor.AQUA + "═══ Vista Previa de Reparación ═══");

        // Agrupar por tipo
        Map<Material, List<RepairInfo>> byType = items.stream()
                .collect(Collectors.groupingBy(info -> info.item().getType()));

        for (Map.Entry<Material, List<RepairInfo>> entry : byType.entrySet()) {
            Material type = entry.getKey();
            List<RepairInfo> typeItems = entry.getValue();
            int typeCost = typeItems.stream().mapToInt(RepairInfo::cost).sum();

            player.sendMessage(ChatColor.WHITE + "• " + formatMaterialName(type.toString()) +
                    " x" + typeItems.size() + " - " + ChatColor.YELLOW + typeCost + " niveles");
        }

        player.sendMessage(ChatColor.WHITE + "Total: " + ChatColor.GOLD + totalCost + " niveles");
    }

    /**
     * Muestra mensaje de XP insuficiente
     */
    private void showInsufficientXP(Player player, int required) {
        int current = player.getLevel();
        int missing = required - current;

        player.sendMessage(ChatColor.RED + "╔══════════════════════════════════╗");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "EXPERIENCIA INSUFICIENTE" + ChatColor.RED + "        ║");
        player.sendMessage(ChatColor.RED + "╠══════════════════════════════════╣");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Necesitas: " +
                ChatColor.YELLOW + required + " niveles" + ChatColor.RED + "          ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Tienes: " +
                ChatColor.YELLOW + current + " niveles" + ChatColor.RED + "             ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Te faltan: " +
                ChatColor.GOLD + missing + " niveles" + ChatColor.RED + "          ║");
        player.sendMessage(ChatColor.RED + "╚══════════════════════════════════╝");
    }

    /**
     * Muestra costos de todos los ítems
     */
    private void showAllRepairCosts(Player player) {
        List<RepairInfo> allItems = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isRepairable(item)) continue;

            RepairInfo info = calculateRepairInfo(item, player); // Pasar player como parámetro
            if (info.damage() > 0) {
                allItems.add(info);
            }
        }

        if (allItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No tienes ítems dañados en tu inventario.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "═══ Costos de Reparación (Inventario) ═══");

        int totalCost = 0;
        for (RepairInfo info : allItems) {
            String itemName = getItemDisplayName(info.item());
            double percentage = (double)(info.maxDurability() - info.damage()) / info.maxDurability() * 100;

            player.sendMessage(String.format("%s%s %s- %s%d%% %s(%d niveles)",
                    ChatColor.WHITE, itemName,
                    ChatColor.GRAY, ChatColor.YELLOW, (int)percentage,
                    ChatColor.AQUA, info.cost()));

            totalCost += info.cost();
        }

        player.sendMessage(ChatColor.GOLD + "Costo total: " + totalCost + " niveles");

        if (totalCost <= player.getLevel()) {
            player.sendMessage(ChatColor.GREEN + "Usa " + ChatColor.WHITE + "/reparar all" +
                    ChatColor.GREEN + " para reparar todo.");
        } else {
            player.sendMessage(ChatColor.RED + "Te faltan " + (totalCost - player.getLevel()) + " niveles.");
        }
    }

    /**
     * Muestra el costo del ítem en mano
     */
    private void showItemRepairCost(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        RepairResult validation = validateItem(item);
        if (!validation.success()) {
            player.sendMessage(ChatColor.RED + validation.message());
            return;
        }

        RepairInfo info = calculateRepairInfo(item, player); // Pasar player como parámetro
        String itemName = getItemDisplayName(item);

        // Calcular durabilidad
        int currentDurability = info.maxDurability() - info.damage();
        double percentage = (double) currentDurability / info.maxDurability() * 100;

        player.sendMessage(ChatColor.AQUA + "═══ Información de Reparación ═══");
        player.sendMessage(ChatColor.WHITE + "Ítem: " + itemName);
        player.sendMessage(ChatColor.WHITE + "Durabilidad: " + ChatColor.YELLOW + currentDurability +
                "/" + info.maxDurability() + ChatColor.GRAY + " (" + String.format("%.1f", percentage) + "%)");

        // Mostrar encantamientos si los tiene
        Map<Enchantment, Integer> enchantments = item.getEnchantments();
        if (!enchantments.isEmpty()) {
            player.sendMessage(ChatColor.WHITE + "Encantamientos: " + ChatColor.LIGHT_PURPLE + enchantments.size());
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                String enchantName = getEnchantmentName(entry.getKey());
                String special = entry.getKey().equals(Enchantment.MENDING) ?
                        ChatColor.GOLD + " (+10% costo)" : "";
                player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + enchantName +
                        " " + entry.getValue() + special);
            }
        }

        // Desglose del costo
        player.sendMessage(ChatColor.WHITE + "═══ Desglose del Costo ═══");
        player.sendMessage(ChatColor.GRAY + "  • Base (daño): " +
                ChatColor.WHITE + String.format("%.1f", info.damage() * BASE_COST_MULTIPLIER));

        if (!enchantments.isEmpty()) {
            double enchantBonus = enchantments.size() * ENCHANTMENT_MULTIPLIER;
            if (enchantments.containsKey(Enchantment.MENDING)) {
                enchantBonus += MENDING_EXTRA_MULTIPLIER;
            }
            player.sendMessage(ChatColor.GRAY + "  • Encantamientos: +" +
                    ChatColor.WHITE + String.format("%.0f%%", enchantBonus * 100));
        }

        double typeMultiplier = getTypeMultiplier(item.getType());
        if (typeMultiplier != 1.0) {
            player.sendMessage(ChatColor.GRAY + "  • Tipo de material: x" +
                    ChatColor.WHITE + String.format("%.1f", typeMultiplier));
        }

        player.sendMessage(ChatColor.WHITE + "Costo de reparación: " +
                (info.cost() <= player.getLevel() ? ChatColor.GREEN : ChatColor.RED) +
                info.cost() + " niveles");

        if (info.cost() <= player.getLevel()) {
            player.sendMessage(ChatColor.GREEN + "Usa " + ChatColor.WHITE + "/reparar" +
                    ChatColor.GREEN + " para reparar este ítem.");
        } else {
            player.sendMessage(ChatColor.RED + "Te faltan " + (info.cost() - player.getLevel()) + " niveles.");
        }
    }

    /**
     * Muestra información del sistema
     */
    private boolean showRepairInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Sistema de Reparación v2.0" + ChatColor.GOLD + "     ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo base: " +
                ChatColor.YELLOW + "5% del daño" + ChatColor.GOLD + "          ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Por encantamiento: " +
                ChatColor.YELLOW + "+10%" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Con Mending: " +
                ChatColor.YELLOW + "+10% extra" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo máximo/ítem: " +
                ChatColor.YELLOW + MAX_COST_PER_ITEM + " niveles" + ChatColor.GOLD + "  ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Cooldown: " +
                ChatColor.YELLOW + (REPAIR_COOLDOWN/1000) + " segundos" + ChatColor.GOLD + "         ║");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Multiplicadores por material:");
        player.sendMessage(ChatColor.GRAY + "• Netherite: x2.0 | Diamante: x1.5");
        player.sendMessage(ChatColor.GRAY + "• Hierro: x1.0 | Oro: x0.8");
        player.sendMessage(ChatColor.GRAY + "• Piedra/Madera: x0.6");
        return true; // Agregar return true
    }

    /**
     * Muestra la ayuda del comando
     */
    private boolean showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══ Comando /reparar v2.0 ═══");
        player.sendMessage(ChatColor.YELLOW + "/reparar" +
                ChatColor.GRAY + " - Repara el ítem en tu mano");
        player.sendMessage(ChatColor.YELLOW + "/reparar all" +
                ChatColor.GRAY + " - Repara todos los ítems del inventario");
        player.sendMessage(ChatColor.YELLOW + "/reparar armor" +
                ChatColor.GRAY + " - Repara toda tu armadura");
        player.sendMessage(ChatColor.YELLOW + "/reparar sword" +
                ChatColor.GRAY + " - Repara el arma en tu mano");
        player.sendMessage(ChatColor.YELLOW + "/reparar tool" +
                ChatColor.GRAY + " - Repara la herramienta en tu mano");
        player.sendMessage(ChatColor.YELLOW + "/reparar cost [all]" +
                ChatColor.GRAY + " - Muestra el costo de reparación");
        player.sendMessage(ChatColor.YELLOW + "/reparar info" +
                ChatColor.GRAY + " - Información del sistema");
        player.sendMessage(ChatColor.YELLOW + "/reparar help" +
                ChatColor.GRAY + " - Muestra esta ayuda");
        return true;
    }

    /**
     * Valida si un ítem puede ser reparado
     */
    private RepairResult validateItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new RepairResult(false, "Debes tener un ítem en tu mano principal.");
        }

        if (!isRepairable(item)) {
            return new RepairResult(false, "Este ítem no se puede reparar.");
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return new RepairResult(false, "Este ítem no tiene durabilidad.");
        }

        if (damageable.getDamage() == 0) {
            return new RepairResult(false, "Este ítem ya está en perfectas condiciones.");
        }

        return new RepairResult(true, "El ítem puede ser reparado.");
    }

    /**
     * Verifica si un ítem es reparable
     */
    private boolean isRepairable(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.getMaxDurability() > 0;
    }

    /**
     * Verifica si es un arma
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("SWORD") || name.contains("AXE") ||
                name.equals("TRIDENT") || name.equals("BOW") ||
                name.equals("CROSSBOW") || name.equals("SHIELD");
    }

    /**
     * Verifica si es una herramienta
     */
    private boolean isTool(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("PICKAXE") || name.contains("SHOVEL") ||
                name.contains("HOE") || name.contains("SHEARS") ||
                name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL");
    }

    /**
     * Reproduce efectos visuales y sonoros de reparación
     */
    private void playRepairEffects(Player player, boolean isMultiple) {
        // Sonidos
        if (isMultiple) {
            // Efecto especial para reparaciones múltiples
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
        } else {
            // Efecto estándar
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        }

        // Partículas
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        if (isMultiple) {
            // Más partículas para reparaciones múltiples
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                    player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().spawnParticle(Particle.CRIT_MAGIC,
                    player.getLocation().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3, 0.05);
        } else {
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                    player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        }
    }

    /**
     * Sistema de cooldown
     */
    private boolean isOnCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUse = cooldowns.get(uuid);
        return lastUse != null && System.currentTimeMillis() - lastUse < REPAIR_COOLDOWN;
    }

    private long getRemainingCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUse = cooldowns.get(uuid);
        if (lastUse == null) return 0;
        return Math.max(0, REPAIR_COOLDOWN - (System.currentTimeMillis() - lastUse));
    }

    private void setCooldown(Player player, long cooldownMs) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Utilidades
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return formatMaterialName(item.getType().toString());
    }

    private String formatMaterialName(String material) {
        return Arrays.stream(material.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(material);
    }

    private String getEnchantmentName(Enchantment enchantment) {
        return formatMaterialName(enchantment.getKey().getKey().toUpperCase());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("all", "armor", "sword", "tool", "cost", "info", "help", "confirm")
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("cost")) {
            return Collections.singletonList("all");
        }
        return Collections.emptyList();
    }

    /**
     * Registros internos
     */
    private record RepairResult(boolean success, String message) {}

    private record RepairInfo(ItemStack item, int damage, int maxDurability, int cost) {}

    private record RepairSession(List<RepairInfo> items, long timestamp) {}

    private enum RepairableType {
        SWORD, TOOL, ARMOR, ALL
    }
}