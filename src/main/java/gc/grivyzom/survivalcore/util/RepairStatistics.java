package gc.grivyzom.survivalcore.util;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de estadísticas para el sistema de reparación
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RepairStatistics {

    private final Main plugin;
    private final Map<UUID, PlayerRepairStats> playerStats = new ConcurrentHashMap<>();

    public RepairStatistics(Main plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    /**
     * Inicializa las tablas de la base de datos
     */
    private void initializeDatabase() {
        if (!plugin.getConfig().getBoolean("repair_advanced.statistics.save_to_database", false)) {
            return;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String createTable = """
                CREATE TABLE IF NOT EXISTS repair_statistics (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    total_repairs INTEGER DEFAULT 0,
                    total_xp_spent INTEGER DEFAULT 0,
                    items_repaired INTEGER DEFAULT 0,
                    favorite_material VARCHAR(50),
                    last_repair TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    free_repairs INTEGER DEFAULT 0,
                    discounted_repairs INTEGER DEFAULT 0
                )
            """;

            try (PreparedStatement stmt = conn.prepareStatement(createTable)) {
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error inicializando tabla de estadísticas de reparación: " + e.getMessage());
        }
    }

    /**
     * Registra una reparación realizada
     */
    public void recordRepair(Player player, int itemsRepaired, int xpCost, ItemStack[] repairedItems) {
        if (!plugin.getConfig().getBoolean("repair_advanced.statistics.track_player_stats", false)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerRepairStats stats = playerStats.computeIfAbsent(playerId, k -> new PlayerRepairStats());

        // Actualizar estadísticas en memoria
        stats.totalRepairs++;
        stats.totalXpSpent += xpCost;
        stats.itemsRepaired += itemsRepaired;
        stats.lastRepair = System.currentTimeMillis();

        if (xpCost == 0) {
            stats.freeRepairs++;
        } else if (player.hasPermission("survivalcore.repair.discount")) {
            stats.discountedRepairs++;
        }

        // Actualizar material favorito
        updateFavoriteMaterial(stats, repairedItems);

        // Guardar en base de datos si está habilitado
        if (plugin.getConfig().getBoolean("repair_advanced.statistics.save_to_database", false)) {
            saveStatsToDatabase(playerId, stats);
        }
    }

    /**
     * Actualiza el material favorito basado en los ítems reparados
     */
    private void updateFavoriteMaterial(PlayerRepairStats stats, ItemStack[] items) {
        Map<String, Integer> materialCount = new HashMap<>();

        for (ItemStack item : items) {
            if (item != null) {
                String material = item.getType().name();
                materialCount.merge(material, 1, Integer::sum);
            }
        }

        // Encontrar el material más común
        materialCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> stats.favoriteMaterial = entry.getKey());
    }

    /**
     * Guarda las estadísticas en la base de datos
     */
    private void saveStatsToDatabase(UUID playerId, PlayerRepairStats stats) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String upsert = """
                INSERT INTO repair_statistics 
                (player_uuid, total_repairs, total_xp_spent, items_repaired, favorite_material, 
                 last_repair, free_repairs, discounted_repairs)
                VALUES (?, ?, ?, ?, ?, FROM_UNIXTIME(?), ?, ?)
                ON DUPLICATE KEY UPDATE
                total_repairs = VALUES(total_repairs),
                total_xp_spent = VALUES(total_xp_spent),
                items_repaired = VALUES(items_repaired),
                favorite_material = VALUES(favorite_material),
                last_repair = VALUES(last_repair),
                free_repairs = VALUES(free_repairs),
                discounted_repairs = VALUES(discounted_repairs)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(upsert)) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, stats.totalRepairs);
                stmt.setInt(3, stats.totalXpSpent);
                stmt.setInt(4, stats.itemsRepaired);
                stmt.setString(5, stats.favoriteMaterial);
                stmt.setLong(6, stats.lastRepair / 1000); // Convertir a segundos
                stmt.setInt(7, stats.freeRepairs);
                stmt.setInt(8, stats.discountedRepairs);

                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error guardando estadísticas de reparación: " + e.getMessage());
        }
    }

    /**
     * Carga las estadísticas de un jugador desde la base de datos
     */
    public PlayerRepairStats getPlayerStats(UUID playerId) {
        PlayerRepairStats stats = playerStats.get(playerId);

        if (stats == null && plugin.getConfig().getBoolean("repair_advanced.statistics.save_to_database", false)) {
            stats = loadStatsFromDatabase(playerId);
            if (stats != null) {
                playerStats.put(playerId, stats);
            }
        }

        return stats != null ? stats : new PlayerRepairStats();
    }

    /**
     * Carga las estadísticas desde la base de datos
     */
    private PlayerRepairStats loadStatsFromDatabase(UUID playerId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT * FROM repair_statistics WHERE player_uuid = ?";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerRepairStats stats = new PlayerRepairStats();
                        stats.totalRepairs = rs.getInt("total_repairs");
                        stats.totalXpSpent = rs.getInt("total_xp_spent");
                        stats.itemsRepaired = rs.getInt("items_repaired");
                        stats.favoriteMaterial = rs.getString("favorite_material");
                        stats.lastRepair = rs.getTimestamp("last_repair").getTime();
                        stats.freeRepairs = rs.getInt("free_repairs");
                        stats.discountedRepairs = rs.getInt("discounted_repairs");
                        return stats;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error cargando estadísticas de reparación: " + e.getMessage());
        }

        return null;
    }

    /**
     * Obtiene estadísticas globales del servidor
     */
    public GlobalRepairStats getGlobalStats() {
        GlobalRepairStats global = new GlobalRepairStats();

        if (!plugin.getConfig().getBoolean("repair_advanced.statistics.save_to_database", false)) {
            // Calcular desde memoria si la base de datos no está habilitada
            for (PlayerRepairStats stats : playerStats.values()) {
                global.totalRepairs += stats.totalRepairs;
                global.totalXpSpent += stats.totalXpSpent;
                global.totalItemsRepaired += stats.itemsRepaired;
            }
            global.activePlayers = playerStats.size();
            return global;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = """
                SELECT 
                    COUNT(*) as active_players,
                    SUM(total_repairs) as total_repairs,
                    SUM(total_xp_spent) as total_xp_spent,
                    SUM(items_repaired) as total_items,
                    AVG(total_xp_spent) as avg_xp_per_player
                FROM repair_statistics 
                WHERE total_repairs > 0
            """;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        global.activePlayers = rs.getInt("active_players");
                        global.totalRepairs = rs.getInt("total_repairs");
                        global.totalXpSpent = rs.getInt("total_xp_spent");
                        global.totalItemsRepaired = rs.getInt("total_items");
                        global.averageXpPerPlayer = rs.getDouble("avg_xp_per_player");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error obteniendo estadísticas globales: " + e.getMessage());
        }

        return global;
    }

    /**
     * Resetea las estadísticas de un jugador
     */
    public void resetPlayerStats(UUID playerId) {
        playerStats.remove(playerId);

        if (plugin.getConfig().getBoolean("repair_advanced.statistics.save_to_database", false)) {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String delete = "DELETE FROM repair_statistics WHERE player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(delete)) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error reseteando estadísticas: " + e.getMessage());
            }
        }
    }

    /**
     * Clase para almacenar estadísticas de un jugador
     */
    public static class PlayerRepairStats {
        public int totalRepairs = 0;
        public int totalXpSpent = 0;
        public int itemsRepaired = 0;
        public String favoriteMaterial = "UNKNOWN";
        public long lastRepair = 0;
        public int freeRepairs = 0;
        public int discountedRepairs = 0;

        public double getAverageXpPerRepair() {
            return totalRepairs > 0 ? (double) totalXpSpent / totalRepairs : 0.0;
        }

        public double getAverageItemsPerRepair() {
            return totalRepairs > 0 ? (double) itemsRepaired / totalRepairs : 0.0;
        }

        public int getDiscountUsagePercentage() {
            return totalRepairs > 0 ? (int) (((double) discountedRepairs / totalRepairs) * 100) : 0;
        }

        public int getFreeRepairPercentage() {
            return totalRepairs > 0 ? (int) (((double) freeRepairs / totalRepairs) * 100) : 0;
        }
    }

    /**
     * Clase para almacenar estadísticas globales del servidor
     */
    public static class GlobalRepairStats {
        public int activePlayers = 0;
        public int totalRepairs = 0;
        public int totalXpSpent = 0;
        public int totalItemsRepaired = 0;
        public double averageXpPerPlayer = 0.0;

        public double getAverageRepairsPerPlayer() {
            return activePlayers > 0 ? (double) totalRepairs / activePlayers : 0.0;
        }

        public double getAverageXpPerRepair() {
            return totalRepairs > 0 ? (double) totalXpSpent / totalRepairs : 0.0;
        }

        public double getAverageItemsPerRepair() {
            return totalRepairs > 0 ? (double) totalItemsRepaired / totalRepairs : 0.0;
        }
    }
}