package gc.grivyzom.survivalcore.placeholders;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sistema de placeholders simplificado para SurvivalCore 2.0
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
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "Jugador no encontrado";
        }

        Player onlinePlayer = player.getPlayer();

        // ===== PLACEHOLDERS DE REDES SOCIALES =====
        if (params.equals("discord")) {
            return getDiscord(player);
        }

        if (params.equals("instagram")) {
            return getInstagram(player);
        }

        if (params.equals("github")) {
            return getGithub(player);
        }

        if (params.equals("tiktok")) {
            return getTiktok(player);
        }

        if (params.equals("twitch")) {
            return getTwitch(player);
        }

        if (params.equals("kick")) {
            return getKick(player);
        }

        if (params.equals("youtube")) {
            return getYoutube(player);
        }

        if (params.equals("social_count")) {
            return getSocialCount(player);
        }

        if (params.equals("has_socials")) {
            return getHasSocials(player);
        }

        // ===== PLACEHOLDERS DE ESTADO DE REDES SOCIALES (para GUIs) =====
        if (params.equals("discord_status")) {
            return getSocialStatus(player, "discord");
        }

        if (params.equals("instagram_status")) {
            return getSocialStatus(player, "instagram");
        }

        if (params.equals("github_status")) {
            return getSocialStatus(player, "github");
        }

        if (params.equals("tiktok_status")) {
            return getSocialStatus(player, "tiktok");
        }

        if (params.equals("twitch_status")) {
            return getSocialStatus(player, "twitch");
        }

        if (params.equals("kick_status")) {
            return getSocialStatus(player, "kick");
        }

        if (params.equals("youtube_status")) {
            return getSocialStatus(player, "youtube");
        }

        // ===== PLACEHOLDERS DE INFORMACIÓN DE GÉNERO CON COOLDOWN =====
        if (params.equals("gender_cooldown")) {
            return getGenderCooldown(player);
        }

        if (params.equals("can_change_gender")) {
            return getCanChangeGender(player);
        }

        // ===== PLACEHOLDERS DE RANKUP SIMPLIFICADOS =====
        if (params.equals("rank")) {
            return getRankPlaceholder(onlinePlayer);
        }

        if (params.equals("rank_display")) {
            return getRankDisplayPlaceholder(onlinePlayer);
        }

        if (params.equals("next_rank")) {
            return getNextRankPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_progress")) {
            return getRankupProgressPlaceholder(onlinePlayer);
        }

        if (params.equals("rankup_progress_bar")) {
            return getRankupProgressBarPlaceholder(onlinePlayer);
        }

        if (params.equals("rank_order")) {
            return getRankOrderPlaceholder(onlinePlayer);
        }

        if (params.equals("is_max_rank")) {
            return getIsMaxRankPlaceholder(onlinePlayer);
        }

        if (params.equals("total_ranks")) {
            return getTotalRanksPlaceholder();
        }

        // ===== PLACEHOLDERS DE DATOS DEL JUGADOR =====
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

        if (params.equals("birthday")) {
            return getBirthday(player);
        }

        if (params.equals("gender")) {
            return getGender(player);
        }

        if (params.equals("country")) {
            return getCountry(player);
        }

        // ===== PLACEHOLDERS DE ESTADÍSTICAS BÁSICAS =====
        if (params.equals("player_level")) {
            return onlinePlayer != null ? String.valueOf(onlinePlayer.getLevel()) : "0";
        }

        if (params.equals("player_health")) {
            return onlinePlayer != null ? String.valueOf(Math.round(onlinePlayer.getHealth())) : "0";
        }

        if (params.equals("player_food")) {
            return onlinePlayer != null ? String.valueOf(onlinePlayer.getFoodLevel()) : "0";
        }

        return null; // Placeholder no encontrado
    }

    // =================== MÉTODOS DE RANKUP SIMPLIFICADOS ===================

    /**
     * Obtiene el ID del rango actual
     */
    private String getRankPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "default";
        }

        try {
            String currentRank = plugin.getRankupManager().getCurrentRank(player);
            return currentRank != null ? currentRank : "default";
        } catch (Exception e) {
            return "error";
        }
    }

    /**
     * Obtiene el nombre de display del rango actual
     */
    private String getRankDisplayPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "Sin rango";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "Sin rango";
            }

            var rankData = rankupManager.getRanks().get(currentRank);
            if (rankData != null) {
                return ChatColor.stripColor(rankData.getDisplayName());
            }

            return currentRank;
        } catch (Exception e) {
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

            var currentRankData = rankupManager.getRanks().get(currentRank);
            if (currentRankData == null || !currentRankData.hasNextRank()) {
                return "Rango máximo";
            }

            String nextRank = currentRankData.getNextRank();
            var nextRankData = rankupManager.getRanks().get(nextRank);

            if (nextRankData != null) {
                return ChatColor.stripColor(nextRankData.getDisplayName());
            }

            return nextRank;
        } catch (Exception e) {
            return "Error";
        }
    }

    /**
     * Obtiene el porcentaje de progreso hacia el siguiente rango
     */
    private String getRankupProgressPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "0%";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            var progressFuture = rankupManager.getPlayerProgress(player);
            var progress = progressFuture.get(2, TimeUnit.SECONDS);

            if (progress.getNextRank() == null) {
                return "100%"; // Rango máximo
            }

            return String.format("%.1f%%", progress.getOverallProgress());

        } catch (TimeoutException e) {
            return "Cargando...";
        } catch (InterruptedException | ExecutionException e) {
            return "Error";
        }
    }

    /**
     * Obtiene una barra de progreso visual simplificada
     */
    private String getRankupProgressBarPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "▓▓▓▓▓▓▓▓▓▓";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            var progressFuture = rankupManager.getPlayerProgress(player);
            var progress = progressFuture.get(2, TimeUnit.SECONDS);

            if (progress.getNextRank() == null) {
                return "██████████"; // Barra completa para rango máximo
            }

            return createSimpleProgressBar(progress.getOverallProgress(), 10);

        } catch (TimeoutException e) {
            return "▓▓▓▓▓▓▓▓▓▓";
        } catch (InterruptedException | ExecutionException e) {
            return "▓▓▓▓▓▓▓▓▓▓";
        }
    }

    /**
     * Obtiene el orden del rango actual
     */
    private String getRankOrderPlaceholder(Player player) {
        if (!isRankupSystemEnabled() || player == null) {
            return "0";
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                return "0";
            }

            var rankData = rankupManager.getRanks().get(currentRank);
            return rankData != null ? String.valueOf(rankData.getOrder()) : "0";

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

            var rankData = rankupManager.getRanks().get(currentRank);
            return String.valueOf(rankData == null || !rankData.hasNextRank());

        } catch (Exception e) {
            return "false";
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
            return String.valueOf(plugin.getRankupManager().getRanks().size());
        } catch (Exception e) {
            return "0";
        }
    }

    // =================== MÉTODOS DE DATOS EXISTENTES ===================

    private String getFarmingLevel(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getFarmingLevel()) : "1";
        } catch (Exception e) {
            return "1";
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
            return userData != null ? String.valueOf(userData.getMiningLevel()) : "1";
        } catch (Exception e) {
            return "1";
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
            long bankedXp = plugin.getDatabaseManager().getBankedXp(player.getUniqueId().toString());
            return String.valueOf(bankedXp);
        } catch (Exception e) {
            return "0";
        }
    }

    private String getBankCapacity(OfflinePlayer player) {
        try {
            long capacity = plugin.getDatabaseManager().getBankCapacity(player.getUniqueId().toString());
            return String.valueOf(capacity);
        } catch (Exception e) {
            return "68000";
        }
    }

    private String getBirthday(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getCumpleaños() != null ?
                    userData.getCumpleaños() : "No establecido";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getGender(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getGenero() != null ?
                    userData.getGenero() : "No establecido";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getCountry(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getPais() != null ?
                    userData.getPais() : "Desconocido";
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
     * Crea una barra de progreso simple y rápida
     */
    private String createSimpleProgressBar(double percentage, int length) {
        int filled = (int) Math.round(percentage / 100.0 * length);
        filled = Math.max(0, Math.min(filled, length));

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
    private String getDiscord(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getDiscord() != null ?
                    userData.getDiscord() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getInstagram(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getInstagram() != null ?
                    userData.getInstagram() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getGithub(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getGithub() != null ?
                    userData.getGithub() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getTiktok(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getTiktok() != null ?
                    userData.getTiktok() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getTwitch(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getTwitch() != null ?
                    userData.getTwitch() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getKick(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getKick() != null ?
                    userData.getKick() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getYoutube(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getYoutube() != null ?
                    userData.getYoutube() : "No configurado";
        } catch (Exception e) {
            return "Error";
        }
    }

    private String getSocialCount(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.getSocialMediaCount()) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getHasSocials(OfflinePlayer player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? String.valueOf(userData.hasAnySocialMedia()) : "false";
        } catch (Exception e) {
            return "false";
        }
    }

    /**
     * Obtiene el estado de una red social (para usar en GUIs)
     */
    private String getSocialStatus(OfflinePlayer player, String network) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            if (userData == null) return "✗ No configurado";

            String value = null;
            switch (network.toLowerCase()) {
                case "discord" -> value = userData.getDiscord();
                case "instagram" -> value = userData.getInstagram();
                case "github" -> value = userData.getGithub();
                case "tiktok" -> value = userData.getTiktok();
                case "twitch" -> value = userData.getTwitch();
                case "kick" -> value = userData.getKick();
                case "youtube" -> value = userData.getYoutube();
            }

            if (value != null && !value.isEmpty()) {
                // Truncar para mejor visualización en GUIs
                String displayValue = value.length() > 15 ? value.substring(0, 12) + "..." : value;
                return "✓ " + displayValue;
            } else {
                return "✗ No configurado";
            }
        } catch (Exception e) {
            return "✗ Error";
        }
    }

    /**
     * Obtiene información del cooldown de género
     */
    private String getGenderCooldown(OfflinePlayer player) {
        try {
            if (plugin.getDatabaseManager().canChangeGender(player.getUniqueId().toString())) {
                return "Disponible";
            } else {
                return plugin.getDatabaseManager().getGenderCooldownRemaining(player.getUniqueId().toString());
            }
        } catch (Exception e) {
            return "Error";
        }
    }

    /**
     * Verifica si puede cambiar de género
     */
    private String getCanChangeGender(OfflinePlayer player) {
        try {
            return String.valueOf(plugin.getDatabaseManager().canChangeGender(player.getUniqueId().toString()));
        } catch (Exception e) {
            return "false";
        }
    }

}

