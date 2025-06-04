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

import java.util.*;

/**
 * Comando /repair que permite reparar completamente el ítem en mano
 * a cambio de experiencia, con costo escalado por encantamientos.
 */
public class RepairCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    // Configuración de costos base
    private static final int BASE_COST_LEVELS = 5;           // Costo base en niveles
    private static final int ENCHANTMENT_MULTIPLIER = 2;     // Multiplicador por encantamiento
    private static final int MAX_COST_LEVELS = 50;           // Costo máximo en niveles

    // Cooldown para evitar spam (en milisegundos)
    private static final long REPAIR_COOLDOWN = 3000; // 3 segundos
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RepairCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        // Verificar cooldown
        if (isOnCooldown(player)) {
            long remaining = getRemainingCooldown(player);
            player.sendMessage(ChatColor.RED + "Debes esperar " + (remaining / 1000) + " segundos antes de reparar otro ítem.");
            return true;
        }

        // Verificar argumentos
        if (args.length > 0) {
            String subcommand = args[0].toLowerCase();
            switch (subcommand) {
                case "info" -> {
                    showRepairInfo(player);
                    return true;
                }
                case "cost" -> {
                    showRepairCost(player);
                    return true;
                }
                case "help" -> {
                    showHelp(player);
                    return true;
                }
                default -> {
                    player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /repair help para ver la ayuda.");
                    return true;
                }
            }
        }

        // Ejecutar reparación
        executeRepair(player);
        return true;
    }

    /**
     * Ejecuta la reparación del ítem en mano
     */
    private void executeRepair(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        // Validar que el ítem sea reparable
        RepairResult validation = validateItem(item);
        if (!validation.success) {
            player.sendMessage(ChatColor.RED + validation.message);
            return;
        }

        // Calcular costo
        int repairCost = calculateRepairCost(item);

        // Verificar que el jugador tenga suficiente experiencia
        if (player.getLevel() < repairCost) {
            player.sendMessage(ChatColor.RED + "Necesitas " + repairCost + " niveles para reparar este ítem.");
            player.sendMessage(ChatColor.GRAY + "Tienes " + player.getLevel() + " niveles disponibles.");
            return;
        }

        // Confirmar reparación para ítems costosos
        if (repairCost >= 25) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Esta reparación cuesta " + repairCost +
                    " niveles. Usa el comando nuevamente para confirmar.");
            setCooldown(player, 10000); // 10 segundos de cooldown para confirmación
            return;
        }

        // Realizar reparación
        performRepair(player, item, repairCost);
    }

    /**
     * Realiza la reparación del ítem
     */
    private void performRepair(Player player, ItemStack item, int cost) {
        // Descontar experiencia
        player.setLevel(player.getLevel() - cost);

        // Reparar ítem completamente
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(meta);
        }

        // Establecer cooldown
        setCooldown(player, REPAIR_COOLDOWN);

        // Efectos visuales y sonoros
        playRepairEffects(player);

        // Mensaje de éxito
        String itemName = getItemDisplayName(item);
        player.sendMessage(ChatColor.GREEN + "✓ " + itemName + " ha sido reparado completamente.");
        player.sendMessage(ChatColor.GRAY + "Costo: " + cost + " niveles de experiencia.");

        // Log para administradores
        plugin.getLogger().info(String.format("Repair: %s reparó %s por %d niveles",
                player.getName(), item.getType(), cost));
    }

    /**
     * Valida si un ítem puede ser reparado
     */
    private RepairResult validateItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new RepairResult(false, "Debes tener un ítem en tu mano principal.");
        }

        // Verificar que el ítem tenga durabilidad
        if (item.getType().getMaxDurability() == 0) {
            return new RepairResult(false, "Este ítem no se puede reparar (no tiene durabilidad).");
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return new RepairResult(false, "Este ítem no se puede reparar.");
        }

        // Verificar que el ítem esté dañado
        if (damageable.getDamage() == 0) {
            return new RepairResult(false, "Este ítem ya está en perfectas condiciones.");
        }

        return new RepairResult(true, "El ítem puede ser reparado.");
    }

    /**
     * Calcula el costo de reparación basado en encantamientos
     */
    private int calculateRepairCost(ItemStack item) {
        // Costo base
        int cost = BASE_COST_LEVELS;

        // Obtener encantamientos
        Map<Enchantment, Integer> enchantments = item.getEnchantments();

        if (!enchantments.isEmpty()) {
            // Calcular costo adicional por encantamientos
            int enchantmentBonus = 0;

            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                Enchantment enchant = entry.getKey();
                int level = entry.getValue();

                // Costo base por encantamiento + nivel del encantamiento
                int enchantCost = ENCHANTMENT_MULTIPLIER + level;

                // Encantamientos raros cuestan más
                if (isRareEnchantment(enchant)) {
                    enchantCost *= 2;
                }

                enchantmentBonus += enchantCost;
            }

            cost += enchantmentBonus;
        }

        // Aplicar límite máximo
        return Math.min(cost, MAX_COST_LEVELS);
    }

    /**
     * Determina si un encantamiento es considerado raro/valioso
     */
    private boolean isRareEnchantment(Enchantment enchant) {
        // Lista de encantamientos considerados raros
        Set<Enchantment> rareEnchantments = Set.of(
                Enchantment.MENDING,
                Enchantment.SILK_TOUCH,
                Enchantment.LUCK,
                Enchantment.SWEEPING_EDGE,
                Enchantment.PROTECTION_ENVIRONMENTAL,
                Enchantment.DIG_SPEED,
                Enchantment.DURABILITY
        );

        return rareEnchantments.contains(enchant);
    }

    /**
     * Muestra información sobre el sistema de reparación
     */
    private void showRepairInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Sistema de Reparación" + ChatColor.GOLD + "            ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo base: " + ChatColor.YELLOW + BASE_COST_LEVELS + " niveles" + ChatColor.GOLD + "          ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Por encantamiento: +" + ChatColor.YELLOW + ENCHANTMENT_MULTIPLIER + " niveles" + ChatColor.GOLD + "     ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Encantamientos raros: " + ChatColor.YELLOW + "x2" + ChatColor.GOLD + "          ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo máximo: " + ChatColor.YELLOW + MAX_COST_LEVELS + " niveles" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Cooldown: " + ChatColor.YELLOW + (REPAIR_COOLDOWN/1000) + " segundos" + ChatColor.GOLD + "           ║");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        player.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/repair cost" +
                ChatColor.GRAY + " para ver el costo del ítem en tu mano.");
    }

    /**
     * Muestra el costo de reparar el ítem actual
     */
    private void showRepairCost(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        RepairResult validation = validateItem(item);
        if (!validation.success) {
            player.sendMessage(ChatColor.RED + validation.message);
            return;
        }

        int cost = calculateRepairCost(item);
        String itemName = getItemDisplayName(item);

        // Calcular durabilidad actual
        ItemMeta meta = item.getItemMeta();
        int currentDamage = ((Damageable) meta).getDamage();
        int maxDurability = item.getType().getMaxDurability();
        int currentDurability = maxDurability - currentDamage;
        double percentage = (double) currentDurability / maxDurability * 100;

        player.sendMessage(ChatColor.AQUA + "═══ Información de Reparación ═══");
        player.sendMessage(ChatColor.WHITE + "Ítem: " + itemName);
        player.sendMessage(ChatColor.WHITE + "Durabilidad: " + ChatColor.YELLOW + currentDurability +
                "/" + maxDurability + ChatColor.GRAY + " (" + String.format("%.1f", percentage) + "%)");

        // Mostrar encantamientos si los tiene
        Map<Enchantment, Integer> enchantments = item.getEnchantments();
        if (!enchantments.isEmpty()) {
            player.sendMessage(ChatColor.WHITE + "Encantamientos: " + ChatColor.LIGHT_PURPLE + enchantments.size());
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                String enchantName = getEnchantmentName(entry.getKey());
                String rarity = isRareEnchantment(entry.getKey()) ? ChatColor.GOLD + " (Raro)" : "";
                player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + enchantName +
                        " " + entry.getValue() + rarity);
            }
        }

        player.sendMessage(ChatColor.WHITE + "Costo de reparación: " +
                (cost <= player.getLevel() ? ChatColor.GREEN : ChatColor.RED) + cost + " niveles");

        if (cost <= player.getLevel()) {
            player.sendMessage(ChatColor.GREEN + "Usa " + ChatColor.WHITE + "/repair" +
                    ChatColor.GREEN + " para reparar este ítem.");
        } else {
            player.sendMessage(ChatColor.RED + "Te faltan " + (cost - player.getLevel()) + " niveles.");
        }
    }

    /**
     * Muestra la ayuda del comando
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══ Comando /repair ═══");
        player.sendMessage(ChatColor.YELLOW + "/repair" + ChatColor.GRAY + " - Repara el ítem en tu mano");
        player.sendMessage(ChatColor.YELLOW + "/repair cost" + ChatColor.GRAY + " - Muestra el costo de reparación");
        player.sendMessage(ChatColor.YELLOW + "/repair info" + ChatColor.GRAY + " - Información del sistema");
        player.sendMessage(ChatColor.YELLOW + "/repair help" + ChatColor.GRAY + " - Muestra esta ayuda");
    }

    /**
     * Reproduce efectos visuales y sonoros de reparación
     */
    private void playRepairEffects(Player player) {
        // Sonidos
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);

        // Partículas
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
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
            return Arrays.asList("cost", "info", "help").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Clase interna para resultados de validación
     */
    private static class RepairResult {
        final boolean success;
        final String message;

        RepairResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}