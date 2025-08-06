package gc.grivyzom.survivalcore.rankup;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sistema de mensajes personalizable para Rankup 2.0
 * Carga todos los mensajes desde rankups.yml
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MessageManager {

    private final Main plugin;
    private final FileConfiguration config;
    private final Map<String, String> messageCache = new HashMap<>();
    private boolean placeholderAPIEnabled;

    public MessageManager(Main plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.placeholderAPIEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        loadMessages();
    }

    /**
     * Carga todos los mensajes en caché para mejor rendimiento
     */
    private void loadMessages() {
        messageCache.clear();

        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            loadMessagesFromSection(messagesSection, "");
        }

        plugin.getLogger().info("✅ " + messageCache.size() + " mensajes cargados desde rankups.yml");
    }

    /**
     * Carga recursivamente mensajes de una sección
     */
    private void loadMessagesFromSection(ConfigurationSection section, String prefix) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (section.isConfigurationSection(key)) {
                loadMessagesFromSection(section.getConfigurationSection(key), fullKey);
            } else {
                String value = section.getString(key);
                if (value != null) {
                    messageCache.put(fullKey, value);
                }
            }
        }
    }

    /**
     * Obtiene un mensaje con reemplazos de variables
     */
    public String getMessage(String key, Map<String, String> replacements) {
        String message = messageCache.get(key);
        if (message == null) {
            plugin.getLogger().warning("⚠️ Mensaje no encontrado: " + key);
            return ChatColor.RED + "Mensaje no configurado: " + key;
        }

        // Aplicar reemplazos
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Obtiene un mensaje simple sin reemplazos
     */
    public String getMessage(String key) {
        return getMessage(key, null);
    }

    /**
     * Obtiene un mensaje con un solo reemplazo
     */
    public String getMessage(String key, String placeholder, String value) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put(placeholder, value);
        return getMessage(key, replacements);
    }

    /**
     * Envía un mensaje de rankup exitoso
     */
    public void sendSuccessMessage(Player player, String newRank, int xpReward) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("new_rank", newRank);
        replacements.put("xp_reward", String.valueOf(xpReward));
        replacements.put("player", player.getName());

        String message = getMessage("success.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);

        // Enviar título si está habilitado
        if (config.getBoolean("messages.success.show_title", true)) {
            sendTitle(player, "success", replacements);
        }
    }

    /**
     * Envía un mensaje de rankup fallido con requisitos faltantes
     */
    public void sendFailedMessage(Player player, List<String> missingRequirements) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("player", player.getName());

        // Construir lista de requisitos faltantes
        StringBuilder reqBuilder = new StringBuilder();
        String reqFormat = getMessage("failed.requirement_line");

        for (String requirement : missingRequirements) {
            String formattedReq = reqFormat.replace("{requirement_name}", requirement);
            reqBuilder.append(formattedReq).append("\n");
        }

        replacements.put("missing_requirements", reqBuilder.toString());

        String message = getMessage("failed.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Envía mensaje de cooldown
     */
    public void sendCooldownMessage(Player player, long seconds) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("seconds", String.valueOf(seconds));
        replacements.put("player", player.getName());

        String message = getMessage("cooldown.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Envía mensaje de rango máximo alcanzado
     */
    public void sendMaxRankMessage(Player player, String currentRank) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("current_rank", currentRank);
        replacements.put("player", player.getName());

        String message = getMessage("max_rank.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);

        // Enviar título si está habilitado
        if (config.getBoolean("messages.max_rank.show_title", false)) {
            sendTitle(player, "max_rank", replacements);
        }
    }

    /**
     * Envía mensaje de ayuda
     */
    public void sendHelpMessage(Player player) {
        String message = getMessage("command_help.text");
        message = processPlaceholders(player, message);
        player.sendMessage(message);
    }

    /**
     * Envía cabecera de progreso
     */
    public void sendProgressHeader(Player player, String currentRank, String nextRank, double progress) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("current_rank", currentRank != null ? currentRank : "Desconocido");
        replacements.put("next_rank", nextRank != null ? nextRank : "Rango máximo");
        replacements.put("progress", String.format("%.1f", progress));
        replacements.put("progress_bar", createProgressBar(progress));

        String message = getMessage("progress_header.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Envía pie de progreso
     */
    public void sendProgressFooter(Player player, String status, int completed, int missing) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("status", status);
        replacements.put("completed", String.valueOf(completed));
        replacements.put("missing", String.valueOf(missing));

        String message = getMessage("progress_footer.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Envía línea de requisito en progreso
     */
    public void sendProgressRequirement(Player player, String reqName, double current, double required, boolean completed) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("name", reqName);
        replacements.put("current", formatRequirementValue(reqName.toLowerCase(), current));
        replacements.put("required", formatRequirementValue(reqName.toLowerCase(), required));

        String messageKey = completed ? "progress_requirement_complete" : "progress_requirement_incomplete";
        String message = getMessage(messageKey, replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Envía cabecera de lista de rangos
     */
    public void sendRankListHeader(Player player) {
        String message = getMessage("rank_list_header.text");
        message = processPlaceholders(player, message);
        player.sendMessage(message);
    }

    /**
     * Envía pie de lista de rangos
     */
    public void sendRankListFooter(Player player, String currentRank, int position, int totalRanks) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("current_rank", currentRank);
        replacements.put("rank_position", String.valueOf(position));
        replacements.put("total_ranks", String.valueOf(totalRanks));

        String message = getMessage("rank_list_footer.text", replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Envía línea de rango en lista
     */
    public void sendRankLine(Player player, String rankName, int order, RankStatus status) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("rank_name", rankName);
        replacements.put("order", String.valueOf(order));

        String messageKey = switch (status) {
            case CURRENT -> "rank_list_current";
            case COMPLETED -> "rank_list_completed";
            case LOCKED -> "rank_list_locked";
        };

        String message = getMessage(messageKey, replacements);
        message = processPlaceholders(player, message);

        player.sendMessage(message);
    }

    /**
     * Crea una barra de progreso visual
     */
    private String createProgressBar(double percentage) {
        ConfigurationSection barConfig = config.getConfigurationSection("progress_bars");
        if (barConfig == null) return createDefaultProgressBar(percentage);

        String completeChar = barConfig.getString("complete_char", "█");
        String incompleteChar = barConfig.getString("incomplete_char", "▓");
        int length = barConfig.getInt("length", 20);

        int completed = (int) Math.round(percentage / 100.0 * length);
        completed = Math.max(0, Math.min(completed, length));

        StringBuilder bar = new StringBuilder();

        // Determinar color basado en porcentaje
        String color = getProgressColor(percentage);

        bar.append(color);
        for (int i = 0; i < completed; i++) {
            bar.append(completeChar);
        }

        bar.append("&7"); // Color gris para la parte incompleta
        for (int i = completed; i < length; i++) {
            bar.append(incompleteChar);
        }

        bar.append(" &f").append(String.format("%.1f%%", percentage));

        return bar.toString();
    }

    /**
     * Obtiene el color de la barra de progreso según el porcentaje
     */
    private String getProgressColor(double percentage) {
        ConfigurationSection colorsConfig = config.getConfigurationSection("progress_bars.colors");
        if (colorsConfig == null) {
            // Colores por defecto
            if (percentage >= 100) return "&a";
            if (percentage >= 75) return "&e";
            if (percentage >= 50) return "&6";
            if (percentage >= 25) return "&c";
            return "&4";
        }

        // Buscar color configurado
        for (String range : colorsConfig.getKeys(false)) {
            if (range.equals("100") && percentage >= 100) {
                return colorsConfig.getString(range, "&a");
            } else if (range.contains("-")) {
                String[] parts = range.split("-");
                if (parts.length == 2) {
                    try {
                        int min = Integer.parseInt(parts[0]);
                        int max = Integer.parseInt(parts[1]);
                        if (percentage >= min && percentage <= max) {
                            return colorsConfig.getString(range, "&7");
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return "&7"; // Color por defecto
    }

    /**
     * Crea una barra de progreso por defecto
     */
    private String createDefaultProgressBar(double percentage) {
        int length = 20;
        int completed = (int) Math.round(percentage / 100.0 * length);
        completed = Math.max(0, Math.min(completed, length));

        StringBuilder bar = new StringBuilder();

        if (percentage >= 100) bar.append("&a");
        else if (percentage >= 75) bar.append("&e");
        else if (percentage >= 50) bar.append("&6");
        else if (percentage >= 25) bar.append("&c");
        else bar.append("&4");

        for (int i = 0; i < completed; i++) {
            bar.append("█");
        }

        bar.append("&7");
        for (int i = completed; i < length; i++) {
            bar.append("▓");
        }

        bar.append(" &f").append(String.format("%.1f%%", percentage));

        return bar.toString();
    }

    /**
     * Formatea el valor de un requisito según su tipo
     */
    private String formatRequirementValue(String requirementType, double value) {
        ConfigurationSection reqConfig = config.getConfigurationSection("requirements." + requirementType);
        if (reqConfig != null) {
            String format = reqConfig.getString("format_short", "{value}");
            return format.replace("{value}", String.format("%.0f", value));
        }

        // Formatos por defecto
        return switch (requirementType) {
            case "money" -> String.format("$%.0f", value);
            case "level" -> String.format("Lv.%.0f", value);
            case "playtime_hours" -> String.format("%.1fh", value);
            case "farming_level", "mining_level" -> String.format("Lv.%.0f", value);
            default -> String.format("%.0f", value);
        };
    }

    /**
     * Obtiene el nombre formateado de un requisito
     */
    public String getRequirementName(String requirementType) {
        ConfigurationSection reqConfig = config.getConfigurationSection("requirements." + requirementType);
        if (reqConfig != null) {
            return reqConfig.getString("name", requirementType.replace("_", " "));
        }

        // Nombres por defecto
        return switch (requirementType) {
            case "money" -> "Dinero";
            case "level" -> "Nivel de experiencia";
            case "playtime_hours" -> "Tiempo jugado";
            case "mob_kills" -> "Mobs eliminados";
            case "blocks_mined" -> "Bloques minados";
            case "farming_level" -> "Nivel de farming";
            case "mining_level" -> "Nivel de minería";
            case "animals_bred" -> "Animales criados";
            case "fish_caught" -> "Peces pescados";
            case "ender_dragon_kills" -> "Ender Dragons eliminados";
            case "wither_kills" -> "Withers eliminados";
            default -> requirementType.replace("_", " ");
        };
    }

    /**
     * Envía un título personalizado
     */
    private void sendTitle(Player player, String messageKey, Map<String, String> replacements) {
        try {
            ConfigurationSection titleConfig = config.getConfigurationSection("messages." + messageKey + ".title");
            if (titleConfig == null) return;

            String title = titleConfig.getString("main", "");
            String subtitle = titleConfig.getString("subtitle", "");
            int fadeIn = titleConfig.getInt("fade_in", 10);
            int stay = titleConfig.getInt("stay", 60);
            int fadeOut = titleConfig.getInt("fade_out", 20);

            // Aplicar reemplazos
            if (replacements != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    title = title.replace("{" + entry.getKey() + "}", entry.getValue());
                    subtitle = subtitle.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }

            // Procesar placeholders
            title = processPlaceholders(player, title);
            subtitle = processPlaceholders(player, subtitle);

            // Aplicar colores
            title = ChatColor.translateAlternateColorCodes('&', title);
            subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

            // Enviar título
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        } catch (Exception e) {
            plugin.getLogger().warning("Error enviando título: " + e.getMessage());
        }
    }

    /**
     * Procesa placeholders de PlaceholderAPI si está disponible
     */
    private String processPlaceholders(Player player, String message) {
        if (placeholderAPIEnabled && player != null) {
            return PlaceholderAPI.setPlaceholders(player, message);
        }
        return message;
    }

    /**
     * Obtiene un mensaje de estado
     */
    public String getStatusMessage(String statusKey) {
        return getMessage("status_messages." + statusKey);
    }

    /**
     * Obtiene un mensaje de información
     */
    public String getInfoMessage(String infoKey, String value) {
        return getMessage(infoKey, "value", value);
    }

    /**
     * Obtiene un mensaje de error
     */
    public String getErrorMessage(String errorKey) {
        return getMessage("error_" + errorKey);
    }

    /**
     * Obtiene un mensaje administrativo
     */
    public String getAdminMessage(String adminKey, Map<String, String> replacements) {
        return getMessage("admin_" + adminKey, replacements);
    }

    /**
     * Recarga todos los mensajes desde el archivo
     */
    public void reloadMessages() {
        loadMessages();
        plugin.getLogger().info("🔄 Mensajes de rankup recargados desde rankups.yml");
    }

    /**
     * Verifica si un mensaje existe
     */
    public boolean hasMessage(String key) {
        return messageCache.containsKey(key);
    }

    /**
     * Obtiene estadísticas del sistema de mensajes
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_messages", messageCache.size());
        stats.put("placeholderapi_enabled", placeholderAPIEnabled);
        stats.put("custom_requirements", config.getConfigurationSection("requirements") != null ?
                config.getConfigurationSection("requirements").getKeys(false).size() : 0);
        return stats;
    }

    /**
     * Enum para el estado de los rangos
     */
    public enum RankStatus {
        CURRENT,    // Rango actual del jugador
        COMPLETED,  // Rango ya completado
        LOCKED      // Rango bloqueado/futuro
    }
}