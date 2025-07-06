package gc.grivyzom.survivalcore.placeholders;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.rankup.RankupData;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Placeholders para SurvivalCore con soporte completo para el sistema de rankup.
 *
 * @author Brocolitx
 * @version 2.0
 */
public class ScorePlaceholder extends PlaceholderExpansion {

    private final Main plugin;

    public ScorePlaceholder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "score";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Brocolitx";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0";
    }

    @Override
    public boolean persist() {
        return true; // Mantener registrado incluso después de reloads
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "Jugador no encontrado";
        }

        // Convertir a Player si está online para funciones avanzadas
        Player onlinePlayer = player.getPlayer();

        // ===== PLACEHOLDERS DE RANKUP =====
        if (params.equals("rank")) {
            return getRankPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_next")) {
            return getNextRankPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_percentage")) {
            return getRankupPercentagePlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_progress_bar")) {
            return getRankupProgressBarPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_current_order")) {
            return getCurrentRankOrderPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_next_order")) {
            return getNextRankOrderPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_total_ranks")) {
            return getTotalRanksPlaceholder();
        }

        if (params.equals("rankup_is_max")) {
            return getIsMaxRankPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_requirements_count")) {
            return getRequirementsCountPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_requirements_completed")) {
            return getCompletedRequirementsPlaceholder(onlinePlayer);
        }

        // ===== PLACEHOLDERS DE DATOS EXISTENTES =====
        if (params.equals("farming_level")) {
            return getFarmingLevel(player);
        }

        if (params.equals("farming_xp")) {
            return getFarmingXp(player);
        }

        if (params.equals("mining_level")) {
            return getMiningLevel(player);
        }

        if (params.equals("mining_xp")) {
            return getMiningXp(player);
        }

        if (params.equals("total_score")) {
            return getTotalScore(player);
        }

        if (params.equals("banked_xp")) {
            return getBankedXp(player);
        }

        if (params.equals("bank_capacity")) {
            return getBankCapacity(player);
        }

        if (params.equals("bank_level")) {
            return getBankLevel(player);
        }

        if (params.equals("birthday")) {
            return getBirthday(player);
        }

        if (params.equals("gender")) {
            return getGender(player);
        }

        if (params.equals("country")) {
            return getCountry(player);
        }

        // ===== PLACEHOLDERS DE ESTADÍSTICAS ÚTILES =====
        if (params.equals("player_level")) {
            return onlinePlayer != null ? String.valueOf(onlinePlayer.getLevel()) : "0";
        }

        if (params.equals("player_exp")) {
            return onlinePlayer != null ? String.valueOf(Math.round(onlinePlayer.getExp() * 100)) + "%" : "0%";
        }

        if (params.equals("player_health")) {
            return onlinePlayer != null ? String.valueOf(Math.round(onlinePlayer.getHealth())) : "0";
        }

        if (params.equals("player_food")) {
            return onlinePlayer != null ? String.valueOf(onlinePlayer.getFoodLevel()) : "0";
        }

        return null; // Placeholder no encontrado
    }

    // =================== MÉTODOS DE RANKUP ===================

    /**
     * Obtiene el rango actual del jugador
     */
    private String getRankPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "Sin rango";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "Sin rango";
            }

            RankupData rankData = rankupManager.getRankups().get(currentRank);
            if (rankData != null) {
                return ChatColor.stripColor(rankData.getDisplayName());
            }

            return currentRank;
        } catch (Exception e) {
            plugin.getLogger().warning("Error obteniendo rango para placeholder: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * Obtiene el siguiente rango disponible
     */
    private String getNextRankPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "N/A";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "N/A";
            }

            RankupData currentRankData = rankupManager.getRankups().get(currentRank);
            if (currentRankData == null || !currentRankData.hasNextRank()) {
                return "Rango máximo";
            }

            String nextRank = currentRankData.getNextRank();
            RankupData nextRankData = rankupManager.getRankups().get(nextRank);

            if (nextRankData != null) {
                return ChatColor.stripColor(nextRankData.getDisplayName());
            }

            return nextRank;
        } catch (Exception e) {
            plugin.getLogger().warning("Error obteniendo siguiente rango para placeholder: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * Obtiene el porcentaje de progreso hacia el siguiente rango
     */
    private String getRankupPercentagePlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "0%";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            // Obtener progreso de forma asíncrona con timeout
            CompletableFuture<RankupManager.RankupProgress> progressFuture =
                    rankupManager.getPlayerProgress(player);

            RankupManager.RankupProgress progress = progressFuture.get(2, TimeUnit.SECONDS);

            if (progress.getNextRank() == null) {
                return "100%"; // Rango máximo
            }

            return String.format("%.1f%%", progress.getOverallProgress());

        } catch (TimeoutException e) {
            return "Cargando...";
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().warning("Error obteniendo progreso para placeholder: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * Obtiene una barra de progreso visual
     */
    private String getRankupProgressBarPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "▓▓▓▓▓▓▓▓▓▓";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            CompletableFuture<RankupManager.RankupProgress> progressFuture =
                    rankupManager.getPlayerProgress(player);

            RankupManager.RankupProgress progress = progressFuture.get(2, TimeUnit.SECONDS);

            if (progress.getNextRank() == null) {
                return "██████████"; // Barra completa para rango máximo
            }

            return createProgressBar(progress.getOverallProgress(), 10);

        } catch (TimeoutException e) {
            return "▓▓▓▓▓▓▓▓▓▓";
        } catch (InterruptedException | ExecutionException e) {
            return "▓▓▓▓▓▓▓▓▓▓";
        }
    }

    /**
     * Obtiene el orden del rango actual
     */
    private String getCurrentRankOrderPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "0";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "0";
            }

            RankupData rankData = rankupManager.getRankups().get(currentRank);
            return rankData != null ? String.valueOf(rankData.getOrder()) : "0";

        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * Obtiene el orden del siguiente rango
     */
    private String getNextRankOrderPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "N/A";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "N/A";
            }

            RankupData currentRankData = rankupManager.getRankups().get(currentRank);
            if (currentRankData == null || !currentRankData.hasNextRank()) {
                return "MAX";
            }

            RankupData nextRankData = rankupManager.getRankups().get(currentRankData.getNextRank());
            return nextRankData != null ? String.valueOf(nextRankData.getOrder()) : "N/A";

        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Obtiene el total de rangos en el sistema
     */
    private String getTotalRanksPlaceholder() {
        if (!isRankupSystemEnabled()) {
            return "0";
        }

        try {
            return String.valueOf(plugin.getRankupManager().getRankups().size());
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * Verifica si el jugador tiene el rango máximo
     */
    private String getIsMaxRankPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "false";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "false";
            }

            RankupData rankData = rankupManager.getRankups().get(currentRank);
            return String.valueOf(rankData == null || !rankData.hasNextRank());

        } catch (Exception e) {
            return "false";
        }
    }

    /**
     * Obtiene el número total de requisitos del siguiente rango
     */
    private String getRequirementsCountPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "0";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "0";
            }

            RankupData rankData = rankupManager.getRankups().get(currentRank);
            if (rankData == null) {
                return "0";
            }

            return String.valueOf(rankData.getRequirements().size());

        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * Obtiene el número de requisitos completados
     */
    private String getCompletedRequirementsPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "0";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            CompletableFuture<RankupManager.RankupProgress> progressFuture =
                    rankupManager.getPlayerProgress(player);

            RankupManager.RankupProgress progress = progressFuture.get(2, TimeUnit.SECONDS);

            long completed = progress.getRequirements().values().stream()
                    .mapToLong(req -> req.isCompleted() ? 1 : 0)
                    .sum();

            return String.valueOf(completed);

        } catch (TimeoutException e) {
            return "Cargando...";
        } catch (InterruptedException | ExecutionException e) {
            return "0";
        }
    }

    // =================== MÉTODOS DE DATOS EXISTENTES ===================

    private String getFarmingLevel(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getFarmingLevel()) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getFarmingXp(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getFarmingXP()) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getMiningLevel(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getMiningLevel()) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getMiningXp(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getMiningXP()) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getTotalScore(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            if (userData != null) {
                long total = userData.getFarmingXP() + userData.getMiningXP();
                return String.valueOf(total);
            }
            return "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getBankedXp(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getBankedXp()) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getBankCapacity(OfflinePlayer player) {
        // La capacidad del banco debe calcularse basándose en el nivel del banco
        // Ya que UserData no tiene método getBankCapacity(), lo calculamos
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            if (userData != null) {
                // Calcular capacidad basándose en configuración
                // Capacidad inicial + (nivel-1) * capacidad por nivel
                long initialCapacity = plugin.getConfig().getLong("xp_bank.initial_capacity", 68000);
                long capacityPerLevel = plugin.getConfig().getLong("xp_bank.capacity_per_level", 170000);
                int bankLevel = getBankLevelFromData(userData);

                long capacity = initialCapacity + ((bankLevel - 1) * capacityPerLevel);
                return String.valueOf(capacity);
            }
            return "68000"; // Capacidad por defecto
        } catch (Exception e) {
            return "68000";
        }
    }

    private String getBankLevel(OfflinePlayer player) {
        // El nivel del banco debe calcularse o estar en los datos
        // Si no existe el método, asumimos nivel 1 por defecto
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            if (userData != null) {
                return String.valueOf(getBankLevelFromData(userData));
            }
            return "1";
        } catch (Exception e) {
            return "1";
        }
    }

    /**
     * Obtiene el nivel del banco desde los datos del usuario
     * Implementación temporal hasta que se añada el campo al UserData
     */
    private int getBankLevelFromData(UserData userData) {
        // Si UserData tiene un método para obtener el nivel del banco, usarlo
        // Por ahora, calculamos basándose en la XP bankeada o devolvemos 1
        try {
            long bankedXp = userData.getBankedXp();
            long initialCapacity = plugin.getConfig().getLong("xp_bank.initial_capacity", 68000);

            if (bankedXp <= initialCapacity) {
                return 1;
            }

            // Calcular nivel aproximado basándose en la XP bankeada
            long capacityPerLevel = plugin.getConfig().getLong("xp_bank.capacity_per_level", 170000);
            int estimatedLevel = (int) ((bankedXp - initialCapacity) / capacityPerLevel) + 2;

            // Limitar al nivel máximo
            int maxLevel = plugin.getConfig().getInt("xp_bank.max_level", 20);
            return Math.min(estimatedLevel, maxLevel);

        } catch (Exception e) {
            return 1;
        }
    }

    private String getBirthday(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getCumpleaños() != null ? userData.getCumpleaños() : "No establecido";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getGender(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getGenero() != null ? userData.getGenero() : "No establecido";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getCountry(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getPais() != null ? userData.getPais() : "Desconocido";
        } catch (Exception e) {
            return "Error";
        }
    }

    // =================== MÉTODOS AUXILIARES ===================

    /**
     * Verifica si el sistema de rankup está habilitado
     */
    private boolean isRankupSystemEnabled() {
        return plugin.isRankupSystemEnabled();
    }

    /**
     * Crea una barra de progreso visual
     */
    private String createProgressBar(double percentage, int length) {
        int filled = (int) Math.ceil(percentage / 100.0 * length);
        StringBuilder bar = new StringBuilder();

        // Caracteres llenos
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        // Caracteres vacíos
        for (int i = filled; i < length; i++) {
            bar.append("▓");
        }

        return bar.toString();
    }
}