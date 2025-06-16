package gc.grivyzom.survivalcore.util;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Gestor del sistema de cheques de experiencia.
 * Maneja la creación, validación y estadísticas de cheques.
 */
public class XpChequeManager {

    private final Main plugin;
    private final Map<String, Integer> rankLimits;
    private final Map<String, Double> rankCosts;
    private final NamespacedKey chequeKey;
    private final NamespacedKey amountKey;
    private final NamespacedKey creatorKey;
    private final NamespacedKey timestampKey;
    private final NamespacedKey idKey;

    // SQL para la tabla de cheques
    private static final String SQL_CREATE_CHEQUES =
            "CREATE TABLE IF NOT EXISTS xp_cheques (" +
                    "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "creator_uuid CHAR(36) NOT NULL, " +
                    "creator_name VARCHAR(16) NOT NULL, " +
                    "amount INT NOT NULL, " +
                    "cost DOUBLE NOT NULL, " +
                    "cheque_date DATE NOT NULL, " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "redeemed BOOLEAN DEFAULT FALSE, " +
                    "redeemer_uuid CHAR(36) NULL, " +
                    "redeemer_name VARCHAR(16) NULL, " +
                    "redeemed_at TIMESTAMP NULL, " +
                    "INDEX idx_creator_date (creator_uuid, cheque_date), " +
                    "INDEX idx_redeemed (redeemed)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    public XpChequeManager(Main plugin) {
        this.plugin = plugin;
        this.rankLimits = new HashMap<>();
        this.rankCosts = new HashMap<>();

        // Claves para NBT
        this.chequeKey = new NamespacedKey(plugin, "xp_cheque");
        this.amountKey = new NamespacedKey(plugin, "cheque_amount");
        this.creatorKey = new NamespacedKey(plugin, "cheque_creator");
        this.timestampKey = new NamespacedKey(plugin, "cheque_timestamp");
        this.idKey = new NamespacedKey(plugin, "cheque_id");

        loadConfig();
        createTable();
    }

    /**
     * Carga la configuración del sistema de cheques
     */
    private void loadConfig() {
        rankLimits.clear();
        rankCosts.clear();

        // Cargar límites por rango
        var limitsSection = plugin.getConfig().getConfigurationSection("cheque_system.daily_limits");
        if (limitsSection != null) {
            for (String rank : limitsSection.getKeys(false)) {
                rankLimits.put(rank, limitsSection.getInt(rank));
            }
        }

        // Cargar costos por rango
        var costsSection = plugin.getConfig().getConfigurationSection("cheque_system.creation_costs");
        if (costsSection != null) {
            for (String rank : costsSection.getKeys(false)) {
                rankCosts.put(rank, costsSection.getDouble(rank));
            }
        }

        // Valores por defecto
        rankLimits.putIfAbsent("default", 500);
        rankLimits.putIfAbsent("vip", 1000);
        rankLimits.putIfAbsent("elite", 2000);
        rankLimits.putIfAbsent("admin", -1);

        rankCosts.putIfAbsent("default", 0.1); // 10% de costo
        rankCosts.putIfAbsent("vip", 0.05);    // 5% de costo
        rankCosts.putIfAbsent("elite", 0.02);  // 2% de costo
        rankCosts.putIfAbsent("admin", 0.0);   // Sin costo
    }

    /**
     * Crea la tabla de cheques en la base de datos
     */
    private void createTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.execute(SQL_CREATE_CHEQUES);
                plugin.getLogger().info("Tabla xp_cheques creada/verificada correctamente.");

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error creando tabla xp_cheques", e);
            }
        });
    }

    /**
     * Crea un cheque de experiencia para un jugador
     */
    public void createCheque(Player player, int amount) {
        UUID uuid = player.getUniqueId();

        // Validaciones síncronas
        if (!hasPermissionToCreate(player)) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para crear cheques.");
            return;
        }

        // Verificar límites y costos de forma asíncrona
        CompletableFuture.supplyAsync(() -> {
            int dailyLimit = getDailyLimit(player);
            int usedToday = getChequesCreatedToday(uuid.toString());
            double costPercentage = getCreationCost(player);
            int totalCost = (int) Math.ceil(amount * (1 + costPercentage));

            return new Object[]{
                    dailyLimit == -1 || (usedToday + amount) <= dailyLimit, // Dentro del límite
                    player.getLevel() >= totalCost, // Tiene suficiente XP
                    dailyLimit,
                    usedToday,
                    totalCost,
                    costPercentage
            };
        }).thenAccept(results -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean withinLimit = (Boolean) results[0];
                boolean hasEnoughXp = (Boolean) results[1];
                int dailyLimit = (Integer) results[2];
                int usedToday = (Integer) results[3];
                int totalCost = (Integer) results[4];
                double costPercentage = (Double) results[5];

                if (!withinLimit) {
                    showLimitExceededMessage(player, dailyLimit, usedToday, amount);
                    return;
                }

                if (!hasEnoughXp) {
                    player.sendMessage(ChatColor.RED + "╔══════════════════════════════════╗");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "EXPERIENCIA INSUFICIENTE" + ChatColor.RED + "         ║");
                    player.sendMessage(ChatColor.RED + "╠══════════════════════════════════╣");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Necesitas: " +
                            ChatColor.YELLOW + totalCost + " niveles" + ChatColor.RED + "          ║");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Tienes: " +
                            ChatColor.YELLOW + player.getLevel() + " niveles" + ChatColor.RED + "             ║");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Te faltan: " +
                            ChatColor.GOLD + (totalCost - player.getLevel()) + " niveles" + ChatColor.RED + "         ║");
                    if (costPercentage > 0) {
                        player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "Costo: +" +
                                String.format("%.1f%%", costPercentage * 100) + ChatColor.RED + "                    ║");
                    }
                    player.sendMessage(ChatColor.RED + "╚══════════════════════════════════╝");
                    return;
                }

                // Crear el cheque
                executeCreateCheque(player, amount, totalCost, costPercentage);
            });
        });
    }

    /**
     * Crea un cheque sin restricciones (para administradores)
     */
    public boolean createChequeAdmin(Player target, int amount, String adminName) {
        try {
            long chequeId = System.currentTimeMillis() + target.hashCode();
            ItemStack cheque = createChequeItem(amount, target.getName(), chequeId);

            // Dar el cheque al jugador
            if (target.getInventory().addItem(cheque).isEmpty()) {
                // Registrar en base de datos
                recordChequeCreation(target.getUniqueId(), target.getName(), amount, 0.0, chequeId);

                plugin.getLogger().info(String.format("Admin %s dio cheque de %d niveles a %s",
                        adminName, amount, target.getName()));
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creando cheque de administrador", e);
            return false;
        }
    }

    /**
     * Ejecuta la creación del cheque después de las validaciones
     */
    private void executeCreateCheque(Player player, int amount, int totalCost, double costPercentage) {
        try {
            // Generar ID único para el cheque
            long chequeId = System.currentTimeMillis() + player.hashCode();

            // Crear el ítem cheque
            ItemStack cheque = createChequeItem(amount, player.getName(), chequeId);

            // Intentar añadir al inventario
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(cheque);

            if (!leftover.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No tienes espacio en el inventario para el cheque.");
                return;
            }

            // Cobrar el costo
            player.setLevel(player.getLevel() - totalCost);
            player.setExp(0); // Reset barra fraccionaria

            // Registrar en base de datos
            recordChequeCreation(player.getUniqueId(), player.getName(), amount, costPercentage, chequeId);

            // Mensajes y efectos
            player.sendMessage(ChatColor.GREEN + "✓ Cheque creado exitosamente:");
            player.sendMessage(ChatColor.WHITE + "  Valor: " + ChatColor.YELLOW + amount + " niveles");
            if (costPercentage > 0) {
                int fee = totalCost - amount;
                player.sendMessage(ChatColor.WHITE + "  Comisión: " + ChatColor.RED + fee + " niveles " +
                        ChatColor.GRAY + "(" + String.format("%.1f%%", costPercentage * 100) + ")");
            }
            player.sendMessage(ChatColor.WHITE + "  Total cobrado: " + ChatColor.GOLD + totalCost + " niveles");

            // Efectos
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);

            plugin.getLogger().info(String.format("Cheque creado: %s -> %d niveles (costo: %d)",
                    player.getName(), amount, totalCost));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creando cheque", e);
            player.sendMessage(ChatColor.RED + "Error al crear el cheque. Contacta a un administrador.");
        }
    }

    /**
     * Crea el ItemStack del cheque con todos los datos necesarios
     */
    private ItemStack createChequeItem(int amount, String creator, long chequeId) {
        Material chequeMaterial = Material.valueOf(
                plugin.getConfig().getString("cheque_system.item.material", "PAPER"));

        ItemStack cheque = new ItemStack(chequeMaterial);
        ItemMeta meta = cheque.getItemMeta();

        // Nombre del ítem
        String displayName = plugin.getConfig().getString("cheque_system.item.name",
                        "&6&l💰 Cheque de Experiencia &6&l💰")
                .replace("%amount%", String.valueOf(amount))
                .replace("%creator%", creator);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

        // Lore del ítem
        List<String> lore = new ArrayList<>();
        List<String> configLore = plugin.getConfig().getStringList("cheque_system.item.lore");

        for (String line : configLore) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%creator%", creator)
                    .replace("%id%", String.valueOf(chequeId))));
        }

        meta.setLore(lore);

        // Datos persistentes (NBT)
        meta.getPersistentDataContainer().set(chequeKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, amount);
        meta.getPersistentDataContainer().set(creatorKey, PersistentDataType.STRING, creator);
        meta.getPersistentDataContainer().set(timestampKey, PersistentDataType.LONG, System.currentTimeMillis());
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.LONG, chequeId);

        // Hacer que brille si está configurado
        if (plugin.getConfig().getBoolean("cheque_system.item.glow", true)) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        cheque.setItemMeta(meta);
        return cheque;
    }

    /**
     * Verifica si un ítem es un cheque válido
     */
    public boolean isCheque(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(chequeKey, PersistentDataType.BYTE);
    }

    /**
     * Canjea un cheque de experiencia
     */
    public boolean redeemCheque(Player player, ItemStack cheque) {
        if (!isCheque(cheque)) return false;

        ItemMeta meta = cheque.getItemMeta();
        int amount = meta.getPersistentDataContainer().getOrDefault(amountKey, PersistentDataType.INTEGER, 0);
        String creator = meta.getPersistentDataContainer().getOrDefault(creatorKey, PersistentDataType.STRING, "Desconocido");
        long chequeId = meta.getPersistentDataContainer().getOrDefault(idKey, PersistentDataType.LONG, 0L);

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Cheque inválido o corrupto.");
            return false;
        }

        // Verificar que el cheque no haya sido canjeado
        CompletableFuture<Boolean> isValidFuture = CompletableFuture.supplyAsync(() -> {
            return !isChequeRedeemed(chequeId);
        });

        isValidFuture.thenAccept(isValid -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!isValid) {
                    player.sendMessage(ChatColor.RED + "Este cheque ya ha sido canjeado.");
                    return;
                }

                // Canjear el cheque
                player.giveExpLevels(amount);

                // Marcar como canjeado en la base de datos
                markChequeAsRedeemed(chequeId, player.getUniqueId(), player.getName());

                // Remover el ítem del inventario
                cheque.setAmount(0);

                // Mensajes y efectos
                player.sendMessage(ChatColor.GREEN + "✓ Cheque canjeado exitosamente:");
                player.sendMessage(ChatColor.WHITE + "  Has recibido: " + ChatColor.YELLOW + amount + " niveles");
                player.sendMessage(ChatColor.WHITE + "  Creado por: " + ChatColor.AQUA + creator);

                // Efectos visuales y sonoros
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.0f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.8f);

                plugin.getLogger().info(String.format("Cheque canjeado: %s -> %d niveles (creado por %s, ID: %d)",
                        player.getName(), amount, creator, chequeId));
            });
        });

        return true;
    }

    /**
     * Obtiene el límite diario para un jugador según su rango
     */
    public int getDailyLimit(Player player) {
        int maxLimit = rankLimits.get("default");

        for (Map.Entry<String, Integer> entry : rankLimits.entrySet()) {
            String rank = entry.getKey();
            int limit = entry.getValue();

            if (player.hasPermission("survivalcore.cheque.rank." + rank)) {
                if (limit == -1) return -1; // Ilimitado
                maxLimit = Math.max(maxLimit, limit);
            }
        }

        return maxLimit;
    }

    /**
     * Obtiene el costo de creación para un jugador según su rango
     */
    public double getCreationCost(Player player) {
        double minCost = rankCosts.get("default");

        for (Map.Entry<String, Double> entry : rankCosts.entrySet()) {
            String rank = entry.getKey();
            double cost = entry.getValue();

            if (player.hasPermission("survivalcore.cheque.rank." + rank)) {
                minCost = Math.min(minCost, cost);
            }
        }

        return minCost;
    }

    /**
     * Obtiene cuántos cheques ha creado un jugador hoy
     */
    public int getChequesCreatedToday(String uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM xp_cheques " +
                             "WHERE creator_uuid = ? AND cheque_date = CURDATE()")) {

            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error consultando cheques diarios", e);
        }

        return 0;
    }

    /**
     * Verifica si un jugador tiene permisos para crear cheques
     */
    private boolean hasPermissionToCreate(Player player) {
        return player.hasPermission("survivalcore.cheque.create");
    }

    /**
     * Registra la creación de un cheque en la base de datos
     */
    private void recordChequeCreation(UUID creatorUuid, String creatorName, int amount, double costPercentage, long chequeId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO xp_cheques (id, creator_uuid, creator_name, amount, cost, cheque_date) " +
                                 "VALUES (?, ?, ?, ?, ?, CURDATE())")) {

                ps.setLong(1, chequeId);
                ps.setString(2, creatorUuid.toString());
                ps.setString(3, creatorName);
                ps.setInt(4, amount);
                ps.setDouble(5, costPercentage);

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error registrando cheque", e);
            }
        });
    }

    /**
     * Verifica si un cheque ya ha sido canjeado
     */
    private boolean isChequeRedeemed(long chequeId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT redeemed FROM xp_cheques WHERE id = ?")) {

            ps.setLong(1, chequeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("redeemed");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error verificando estado del cheque", e);
        }

        return false; // Si no se encuentra, asumir que no está canjeado
    }

    /**
     * Marca un cheque como canjeado
     */
    private void markChequeAsRedeemed(long chequeId, UUID redeemerUuid, String redeemerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE xp_cheques SET redeemed = TRUE, redeemer_uuid = ?, " +
                                 "redeemer_name = ?, redeemed_at = CURRENT_TIMESTAMP WHERE id = ?")) {

                ps.setString(1, redeemerUuid.toString());
                ps.setString(2, redeemerName);
                ps.setLong(3, chequeId);

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error marcando cheque como canjeado", e);
            }
        });
    }

    /**
     * Muestra información del jugador sobre cheques
     */
    public void showPlayerInfo(Player player) {
        CompletableFuture.supplyAsync(() -> {
            int dailyLimit = getDailyLimit(player);
            int usedToday = getChequesCreatedToday(player.getUniqueId().toString());
            double costPercentage = getCreationCost(player);

            return new Object[]{dailyLimit, usedToday, costPercentage};
        }).thenAccept(results -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int dailyLimit = (Integer) results[0];
                int usedToday = (Integer) results[1];
                double costPercentage = (Double) results[2];
                int remaining = dailyLimit == -1 ? -1 : Math.max(0, dailyLimit - usedToday);

                player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════╗");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "💰 Sistema de Cheques" + ChatColor.GOLD + "         ║");
                player.sendMessage(ChatColor.GOLD + "╠════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Límite diario: " +
                        ChatColor.WHITE + (dailyLimit == -1 ? "Ilimitado" : String.format("%,d", dailyLimit)) +
                        ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Usado hoy: " +
                        ChatColor.WHITE + String.format("%,d", usedToday) + ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Disponible: " +
                        ChatColor.WHITE + (remaining == -1 ? "Ilimitado" : String.format("%,d", remaining)) +
                        ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Comisión: " +
                        ChatColor.WHITE + String.format("%.1f%%", costPercentage * 100) + ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════╝");
                player.sendMessage(ChatColor.GRAY + "Los cheques se pueden canjear haciendo clic derecho.");
            });
        });
    }

    /**
     * Muestra los límites del sistema
     */
    public void showLimits(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Límites de Cheques por Rango" + ChatColor.GOLD + "     ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");

        for (Map.Entry<String, Integer> entry : rankLimits.entrySet()) {
            String rank = entry.getKey();
            int limit = entry.getValue();
            double cost = rankCosts.getOrDefault(rank, 0.0);

            String limitStr = limit == -1 ? "∞" : String.format("%,d", limit);
            String costStr = String.format("%.1f%%", cost * 100);

            ChatColor color = player.hasPermission("survivalcore.cheque.rank." + rank) ?
                    ChatColor.GREEN : ChatColor.GRAY;

            player.sendMessage(ChatColor.GOLD + "║ " + color + rank.toUpperCase() + ": " +
                    ChatColor.WHITE + limitStr + " niveles " + ChatColor.GRAY + "(" + costStr + ")" + ChatColor.GOLD + " ║");
        }

        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
    }

    /**
     * Muestra estadísticas de un jugador
     */
    public void showPlayerStats(Player requester, String targetName) {
        if (!requester.getName().equals(targetName) && !requester.hasPermission("survivalcore.cheque.stats.others")) {
            requester.sendMessage(ChatColor.RED + "No puedes ver las estadísticas de otros jugadores.");
            return;
        }

        UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();

        CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();

            try (Connection conn = getConnection()) {
                // Cheques creados
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as count, COALESCE(SUM(amount), 0) as total_amount " +
                                "FROM xp_cheques WHERE creator_uuid = ?")) {
                    ps.setString(1, targetUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            stats.put("created_count", rs.getInt("count"));
                            stats.put("created_amount", rs.getLong("total_amount"));
                        }
                    }
                }

                // Cheques canjeados
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as count, COALESCE(SUM(amount), 0) as total_amount " +
                                "FROM xp_cheques WHERE redeemer_uuid = ?")) {
                    ps.setString(1, targetUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            stats.put("redeemed_count", rs.getInt("count"));
                            stats.put("redeemed_amount", rs.getLong("total_amount"));
                        }
                    }
                }

                // Cheques pendientes
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as count, COALESCE(SUM(amount), 0) as total_amount " +
                                "FROM xp_cheques WHERE creator_uuid = ? AND redeemed = FALSE")) {
                    ps.setString(1, targetUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            stats.put("pending_count", rs.getInt("count"));
                            stats.put("pending_amount", rs.getLong("total_amount"));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error consultando estadísticas", e);
            }

            return stats;
        }).thenAccept(stats -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                requester.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Estadísticas de " + targetName + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "Creados: " +
                        ChatColor.WHITE + stats.getOrDefault("created_count", 0) + " cheques" + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "Total creado: " +
                        ChatColor.WHITE + String.format("%,d", (Long)stats.getOrDefault("created_amount", 0L)) + " niveles" + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.AQUA + "Canjeados: " +
                        ChatColor.WHITE + stats.getOrDefault("redeemed_count", 0) + " cheques" + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.AQUA + "Total canjeado: " +
                        ChatColor.WHITE + String.format("%,d", (Long)stats.getOrDefault("redeemed_amount", 0L)) + " niveles" + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Pendientes: " +
                        ChatColor.WHITE + stats.getOrDefault("pending_count", 0) + " cheques" + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Valor pendiente: " +
                        ChatColor.WHITE + String.format("%,d", (Long)stats.getOrDefault("pending_amount", 0L)) + " niveles" + ChatColor.GOLD + " ║");
                requester.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
            });
        });
    }

    /**
     * Muestra mensaje cuando se excede el límite diario
     */
    private void showLimitExceededMessage(Player player, int dailyLimit, int usedToday, int requestedAmount) {
        int remaining = dailyLimit == -1 ? Integer.MAX_VALUE : dailyLimit - usedToday;

        player.sendMessage(ChatColor.RED + "╔════════════════════════════════╗");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "¡LÍMITE DIARIO EXCEDIDO!" + ChatColor.RED + "       ║");
        player.sendMessage(ChatColor.RED + "╠════════════════════════════════╣");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Límite diario: " +
                (dailyLimit == -1 ? "∞" : dailyLimit) + " niveles" + ChatColor.RED + "    ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Usado hoy: " + usedToday + " niveles" + ChatColor.RED + "       ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Intentas crear: " + requestedAmount + " niveles" + ChatColor.RED + " ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Disponible: " +
                (remaining == Integer.MAX_VALUE ? "∞" : Math.max(0, remaining)) + " niveles" + ChatColor.RED + "    ║");
        player.sendMessage(ChatColor.RED + "║                                ║");
        player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "El límite se reinicia a las 00:00" + ChatColor.RED + " ║");
        player.sendMessage(ChatColor.RED + "╚════════════════════════════════╝");
    }

    /**
     * Recarga la configuración
     */
    public void reloadConfig() {
        loadConfig();
        plugin.getLogger().info("Configuración del sistema de cheques recargada.");
    }

    /**
     * Obtiene una conexión a la base de datos
     */
    private Connection getConnection() throws SQLException {
        String type = plugin.getConfig().getString("database.type", "mysql");
        if (type.equalsIgnoreCase("mysql")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.database", "survivalcore");
            String url = String.format("jdbc:mysql://%s:%d/%s?autoReconnect=true", host, port, database);
            return DriverManager.getConnection(url,
                    plugin.getConfig().getString("database.user"),
                    plugin.getConfig().getString("database.password"));
        } else {
            String url = String.format("jdbc:sqlite:%s/userdata.db", plugin.getDataFolder().getAbsolutePath());
            return DriverManager.getConnection(url);
        }
    }
}