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
import java.util.ArrayList;

/**
 * Sistema de mensajes modernizado para Rankup 2.0
 * Con paginación, mensajes compactos y navegación intuitiva
 * 🔧 CORREGIDO: rankupManager inicializado correctamente
 *
 * @author Brocolitx
 * @version 2.2 - Corregido con custom requirements
 */
public class MessageManager {

    private final Main plugin;
    private final FileConfiguration config;
    private final Map<String, String> messageCache = new HashMap<>();
    private boolean placeholderAPIEnabled;

    // 🔧 CORREGIDO: rankupManager puede ser null inicialmente
    private RankupManager rankupManager;

    // Configuración de paginación
    private int maxRequirementsPerPage = 4;
    private int maxRanksPerPage = 5;
    private boolean enableCompactMode = true;
    private boolean enableNavigation = true;

    public MessageManager(Main plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.placeholderAPIEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        // 🔧 NOTA: rankupManager se inicializará después con setRankupManager()
        loadPaginationConfig();
        loadMessages();
    }

    /**
     * 🆕 NUEVO: Método para establecer la referencia al RankupManager
     * Este método debe ser llamado desde RankupManager después de crear MessageManager
     */
    public void setRankupManager(RankupManager rankupManager) {
        this.rankupManager = rankupManager;
        plugin.getLogger().info("✅ RankupManager vinculado al MessageManager");
    }

    /**
     * 🔧 MÉTODO MEJORADO: getRequirementName con soporte para custom requirements
     * Ahora maneja correctamente el caso donde rankupManager puede ser null
     */
    public String getRequirementName(String requirementType) {
        try {
            // 🆕 NUEVO: Primero verificar si es un custom requirement
            if (rankupManager != null && rankupManager.isCustomRequirement(requirementType)) {
                String customName = rankupManager.getCustomRequirementDisplayName(requirementType);

                // 🔧 CRÍTICO: Asegurar que se procesen los códigos de color
                if (customName != null && !customName.equals(requirementType)) {
                    return ChatColor.translateAlternateColorCodes('&', customName);
                }
            }

            // Verificar en configuración de formatos de requirements
            String configName = config.getString("requirements." + requirementType + ".name");
            if (configName != null && !configName.isEmpty()) {
                return ChatColor.translateAlternateColorCodes('&', configName);
            }

            // 🔧 CORREGIDO: Procesar códigos de color en nombres estándar también
            String standardName = switch (requirementType.toLowerCase()) {
                case "money" -> "💰 Dinero";
                case "level" -> "📊 Nivel de experiencia";
                case "playtime_hours" -> "⏰ Tiempo jugado";
                case "mob_kills" -> "⚔️ Mobs eliminados";
                case "blocks_mined" -> "⛏️ Bloques minados";
                case "farming_level" -> "🌾 Nivel de farming";
                case "mining_level" -> "⛏️ Nivel de minería";
                case "animals_bred" -> "🐄 Animales criados";
                case "fish_caught" -> "🎣 Peces pescados";
                case "ender_dragon_kills" -> "🐲 Ender Dragons eliminados";
                case "wither_kills" -> "💀 Withers eliminados";

                // 🆕 CUSTOM REQUIREMENTS COMUNES CON FALLBACK
                case "vault_eco_balance", "dinero_vault" -> "&6💰 Dinero del Banco";
                case "mcmmo_mining", "mineria_mcmmo" -> "&8⛏️ McMMO Minería";
                case "mcmmo_power", "poder_mcmmo" -> "&c💪 Poder McMMO";
                case "jobs_total", "trabajos_total" -> "&9💼 Nivel de Trabajos";
                case "playtime", "tiempo_jugado" -> "&b⏰ Tiempo Jugado";
                case "combate_mcmmo" -> "&4⚔️ Combate McMMO";
                case "farming_mcmmo" -> "&2🌾 Agricultura McMMO";
                case "pesca_mcmmo" -> "&3🎣 Pesca McMMO";
                case "arco_mcmmo" -> "&e🏹 Tiro con Arco";
                case "reparacion_mcmmo" -> "&7🔧 Reparación";
                case "tokens_servidor" -> "&d💎 Tokens del Servidor";
                case "xp_total" -> "&a🏆 Experiencia Total";
                case "bloques_colocados" -> "&6📊 Bloques Colocados";
                case "votos_totales" -> "&e🌟 Votos Totales";

                default -> {
                    // Convertir snake_case a Title Case con emojis
                    String[] parts = requirementType.split("_");
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) result.append(" ");

                        String part = parts[i];
                        if (!part.isEmpty()) {
                            result.append(Character.toUpperCase(part.charAt(0)));
                            if (part.length() > 1) {
                                result.append(part.substring(1).toLowerCase());
                            }
                        }
                    }

                    // Añadir emojis contextuales
                    String finalName = result.toString();
                    if (finalName.toLowerCase().contains("money") || finalName.toLowerCase().contains("balance") || finalName.toLowerCase().contains("dinero")) {
                        finalName = "&6💰 " + finalName;
                    } else if (finalName.toLowerCase().contains("level") || finalName.toLowerCase().contains("nivel")) {
                        finalName = "&b📊 " + finalName;
                    } else if (finalName.toLowerCase().contains("time") || finalName.toLowerCase().contains("tiempo")) {
                        finalName = "&e⏰ " + finalName;
                    } else if (finalName.toLowerCase().contains("mcmmo")) {
                        finalName = "&6🎯 " + finalName;
                    }

                    yield finalName;
                }
            };

            // 🔧 CRÍTICO: Siempre procesar códigos de color antes de devolver
            return ChatColor.translateAlternateColorCodes('&', standardName);

        } catch (Exception e) {
            plugin.getLogger().warning("Error obteniendo nombre de requirement '" + requirementType + "': " + e.getMessage());
            return ChatColor.translateAlternateColorCodes('&', "📋 " + requirementType.replace("_", " "));
        }
    }

    /**
     * 🆕 NUEVO: Obtener formato de valor para custom requirements
     * Maneja el caso donde rankupManager puede ser null
     */
    public String formatCustomRequirementValue(String requirementType, double value) {
        try {
            // Verificar si hay formato personalizado en configuración (solo si rankupManager está disponible)
            if (rankupManager != null) {
                ConfigurationSection customReq = config.getConfigurationSection("custom_requirements." + requirementType);
                if (customReq != null) {
                    String format = customReq.getString("format");
                    if (format != null) {
                        String processedFormat = format.replace("{value}", String.format("%.0f", value));
                        // 🔧 CRÍTICO: Procesar códigos de color aquí
                        return ChatColor.translateAlternateColorCodes('&', processedFormat);
                    }
                }
            }

            // Verificar formatos estándar
            String configFormat = config.getString("requirements." + requirementType + ".format_short");
            if (configFormat != null) {
                String processedFormat = configFormat.replace("{value}", String.format("%.0f", value));
                // 🔧 CRÍTICO: Procesar códigos de color aquí también
                return ChatColor.translateAlternateColorCodes('&', processedFormat);
            }

            // Formatos por defecto según el tipo
            String defaultFormat = switch (requirementType.toLowerCase()) {
                case "money", "vault_eco_balance", "dinero_vault", "balance" -> String.format("$&a%,.0f", value);
                case "playtime_hours", "playtime", "tiempo_jugado" -> String.format("&e%.1fh", value);
                case "farming_level", "mining_level", "mcmmo_mining", "mcmmo_power", "mineria_mcmmo", "poder_mcmmo" -> String.format("&7Lv.&e%.0f", value);
                case "jobs_total", "trabajos_total" -> String.format("&9Nivel &e%.0f", value);
                case "combate_mcmmo", "farming_mcmmo", "pesca_mcmmo", "arco_mcmmo", "reparacion_mcmmo" -> String.format("&7Lv.&e%.0f", value);
                case "tokens_servidor" -> String.format("&d%.0f tokens", value);
                case "xp_total" -> String.format("&a%.0f XP", value);
                case "votos_totales" -> String.format("&e%.0f votos", value);
                default -> String.format("%,.0f", value);
            };

            // 🔧 CRÍTICO: Procesar códigos de color en formatos por defecto
            return ChatColor.translateAlternateColorCodes('&', defaultFormat);

        } catch (Exception e) {
            plugin.getLogger().warning("Error formateando valor para '" + requirementType + "': " + e.getMessage());
            return String.format("%.0f", value);
        }
    }
    /**
     * Formatea el valor de un requisito según su tipo (método original mejorado)
     */
    private String formatRequirementValue(double value, String type) {
        // Usar el método mejorado
        return formatCustomRequirementValue(type, value);
    }

    /**
     * Carga configuración de paginación
     */
    private void loadPaginationConfig() {
        ConfigurationSection paginationConfig = config.getConfigurationSection("pagination");
        if (paginationConfig != null) {
            maxRequirementsPerPage = paginationConfig.getInt("max_requirements_per_page", 4);
            maxRanksPerPage = paginationConfig.getInt("max_ranks_per_page", 5);
            enableCompactMode = paginationConfig.getBoolean("enable_compact_mode", true);
            enableNavigation = paginationConfig.getBoolean("enable_navigation", true);
        }
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
     * 🆕 NUEVO: Envía mensaje de rankup exitoso COMPACTO
     */
    public void sendSuccessMessage(Player player, String newRank, int xpReward) {
        if (enableCompactMode) {
            // Versión compacta y moderna
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "🎉 " + ChatColor.BOLD + "¡RANKUP EXITOSO!");
            player.sendMessage(ChatColor.WHITE + "   Nuevo rango: " + newRank);
            player.sendMessage(ChatColor.WHITE + "   Recompensa: " + ChatColor.YELLOW + "+" + xpReward + " XP");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "💡 Usa " + ChatColor.WHITE + "/rankup progress" +
                    ChatColor.GRAY + " para ver tu siguiente objetivo");
            player.sendMessage("");
        } else {
            // Versión extendida original
            Map<String, String> replacements = new HashMap<>();
            replacements.put("new_rank", newRank);
            replacements.put("xp_reward", String.valueOf(xpReward));
            replacements.put("player", player.getName());

            String message = getMessage("success.text", replacements);
            message = processPlaceholders(player, message);
            player.sendMessage(message);
        }

        // Enviar título si está habilitado
        if (config.getBoolean("messages.success.show_title", true)) {
            sendTitle(player, "success", Map.of("new_rank", newRank));
        }
    }

    /**
     * 🆕 NUEVO: Envía mensaje de rankup fallido COMPACTO
     */
    public void sendFailedMessage(Player player, List<String> missingRequirements) {
        if (enableCompactMode) {
            // Mensaje compacto con paginación
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "RANKUP NO DISPONIBLE");
            player.sendMessage("");

            if (missingRequirements.size() <= 3) {
                // Mostrar todos si son pocos
                player.sendMessage(ChatColor.WHITE + "Te faltan " + ChatColor.YELLOW + missingRequirements.size() +
                        ChatColor.WHITE + " requisitos:");
                for (String req : missingRequirements) {
                    player.sendMessage(ChatColor.GRAY + "  • " + req);
                }
            } else {
                // Mostrar solo los primeros 3 + indicador
                player.sendMessage(ChatColor.WHITE + "Te faltan " + ChatColor.YELLOW + missingRequirements.size() +
                        ChatColor.WHITE + " requisitos " + ChatColor.GRAY + "(mostrando 3):");
                for (int i = 0; i < Math.min(3, missingRequirements.size()); i++) {
                    player.sendMessage(ChatColor.GRAY + "  • " + missingRequirements.get(i));
                }

                if (missingRequirements.size() > 3) {
                    int remaining = missingRequirements.size() - 3;
                    player.sendMessage(ChatColor.GRAY + "  ... y " + ChatColor.YELLOW + remaining +
                            ChatColor.GRAY + " más");
                }
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "💡 Usa " + ChatColor.WHITE + "/rankup progress" +
                    ChatColor.GRAY + " para ver todos los detalles");
            player.sendMessage("");
        } else {
            // Versión extendida original
            Map<String, String> replacements = new HashMap<>();
            replacements.put("player", player.getName());

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
    }

    /**
     * 🆕 NUEVO: Envía progreso con PAGINACIÓN inteligente
     */
    public void sendProgressWithPagination(Player player, RankupManager.RankupProgress progress, int page) {
        if (progress.getNextRank() == null) {
            // Rango máximo alcanzado
            sendMaxRankMessage(player, progress.getCurrentRank());
            return;
        }

        List<RankupManager.RequirementProgress> requirements = new ArrayList<>(progress.getRequirements().values());
        int totalPages = (int) Math.ceil((double) requirements.size() / maxRequirementsPerPage);

        // Validar página
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // Cabecera compacta
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "📊 " + ChatColor.BOLD + "TU PROGRESO DE RANKUP");
        player.sendMessage("");

        // Información básica
        player.sendMessage(ChatColor.YELLOW + "🎯" + ChatColor.WHITE + "Tú Rango: " + ChatColor.YELLOW + progress.getCurrentRank());
        player.sendMessage(ChatColor.GREEN + "⬆" + ChatColor.WHITE + "Siguiente: " + ChatColor.GREEN + progress.getNextRank());

        // Barra de progreso compacta
        double overallProgress = progress.getOverallProgress();
        String progressBar = createCompactProgressBar(overallProgress, 20);
        player.sendMessage(ChatColor.WHITE + "📈 Progreso: " + progressBar +
                ChatColor.WHITE + " " + String.format("%.1f%%", overallProgress));

        player.sendMessage("");

        // Mostrar requisitos de la página actual
        int start = (page - 1) * maxRequirementsPerPage;
        int end = Math.min(start + maxRequirementsPerPage, requirements.size());

        int completed = 0;
        for (int i = start; i < end; i++) {
            RankupManager.RequirementProgress req = requirements.get(i);
            String reqLine = formatCompactRequirement(req);
            player.sendMessage(reqLine);
            if (req.isCompleted()) completed++;
        }

        // Información de paginación
        if (totalPages > 1) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "──────────────────────────────");

            StringBuilder pagination = new StringBuilder();
            pagination.append(ChatColor.GRAY).append("Página ").append(ChatColor.WHITE).append(page)
                    .append(ChatColor.GRAY).append("/").append(ChatColor.WHITE).append(totalPages);

            if (enableNavigation) {
                if (page > 1) {
                    pagination.append(ChatColor.GRAY).append(" │ ").append(ChatColor.YELLOW)
                            .append("/rankup progress ").append(page - 1).append(" ").append(ChatColor.GRAY).append("(anterior)");
                }
                if (page < totalPages) {
                    pagination.append(ChatColor.GRAY).append(" │ ").append(ChatColor.YELLOW)
                            .append("/rankup progress ").append(page + 1).append(" ").append(ChatColor.GRAY).append("(siguiente)");
                }
            }

            player.sendMessage(pagination.toString());
        }

        // Pie con resumen
        player.sendMessage("");
        int totalCompleted = (int) requirements.stream().mapToLong(req -> req.isCompleted() ? 1 : 0).sum();
        int totalMissing = requirements.size() - totalCompleted;

        if (totalMissing == 0) {
            player.sendMessage(ChatColor.GREEN + "✅ ¡Todos los requisitos completados! Usa " +
                    ChatColor.WHITE + "/rankup" + ChatColor.GREEN + " para ascender");
        } else {
            player.sendMessage(ChatColor.WHITE + "📋 Estado: " + ChatColor.GREEN + totalCompleted +
                    ChatColor.GRAY + "/" + ChatColor.WHITE + requirements.size() +
                    ChatColor.GRAY + " completados, " + ChatColor.RED + totalMissing +
                    ChatColor.GRAY + " pendientes");
        }

        player.sendMessage("");
    }

    /**
     * 🆕 NUEVO: Muestra lista de rangos con PAGINACIÓN
     */
    public void sendRanksListWithPagination(Player player, Map<String, RankupManager.SimpleRankData> allRanks,
                                            String currentRank, int page) {
        List<RankupManager.SimpleRankData> sortedRanks = allRanks.values().stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .toList();

        int totalPages = (int) Math.ceil((double) sortedRanks.size() / maxRanksPerPage);

        // Validar página
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // Cabecera
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "📋 " + ChatColor.BOLD + "RANGOS DEL SERVIDOR");
        player.sendMessage("");

        // Mostrar rangos de la página actual
        int start = (page - 1) * maxRanksPerPage;
        int end = Math.min(start + maxRanksPerPage, sortedRanks.size());

        for (int i = start; i < end; i++) {
            RankupManager.SimpleRankData rank = sortedRanks.get(i);
            String rankLine = formatCompactRank(rank, currentRank, allRanks);
            player.sendMessage(rankLine);
        }

        // Información de paginación
        if (totalPages > 1) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "──────────────────────────────");

            StringBuilder pagination = new StringBuilder();
            pagination.append(ChatColor.GRAY).append("Página ").append(ChatColor.WHITE).append(page)
                    .append(ChatColor.GRAY).append("/").append(ChatColor.WHITE).append(totalPages);

            if (enableNavigation) {
                if (page > 1) {
                    pagination.append(ChatColor.GRAY).append(" │ ").append(ChatColor.YELLOW)
                            .append("/rankup list ").append(page - 1).append(" ").append(ChatColor.GRAY).append("(anterior)");
                }
                if (page < totalPages) {
                    pagination.append(ChatColor.GRAY).append(" │ ").append(ChatColor.YELLOW)
                            .append("/rankup list ").append(page + 1).append(" ").append(ChatColor.GRAY).append("(siguiente)");
                }
            }

            player.sendMessage(pagination.toString());
        }

        // Pie con información del jugador
        player.sendMessage("");
        int currentPosition = currentRank != null && allRanks.containsKey(currentRank) ?
                allRanks.get(currentRank).getOrder() + 1 : 1;

        player.sendMessage(ChatColor.WHITE + "🎯 Tu rango: " +
                (currentRank != null ? getDisplayName(currentRank, allRanks) : "Desconocido"));
        player.sendMessage(ChatColor.WHITE + "📊 Tu posición: " + ChatColor.YELLOW + "#" + currentPosition +
                ChatColor.GRAY + "/" + ChatColor.WHITE + allRanks.size());
        player.sendMessage("");
    }

    /**
     * Formatea un requisito de forma compacta
     */
    private String formatCompactRequirement(RankupManager.RequirementProgress req) {
        String icon = req.isCompleted() ? ChatColor.GREEN + "✅" : ChatColor.RED + "❌";

        String name = getRequirementName(req.getType());

        String progress = formatRequirementValue(req.getCurrent(), req.getType()) + "/" +
                formatRequirementValue(req.getRequired(), req.getType());

        if (req.isCompleted()) {
            return icon + ChatColor.WHITE + " " + name + ": " + ChatColor.GREEN + progress;
        } else {
            double missing = req.getRequired() - req.getCurrent();
            String missingStr = formatRequirementValue(missing, req.getType());
            return icon + ChatColor.WHITE + " " + name + ": " + ChatColor.GRAY + progress +
                    ChatColor.RED + " (faltan " + missingStr + ")";
        }
    }

    /**
     * Formatea un rango de forma compacta
     */
    private String formatCompactRank(RankupManager.SimpleRankData rank, String currentRank,
                                     Map<String, RankupManager.SimpleRankData> allRanks) {
        String prefix;
        ChatColor nameColor;

        if (rank.getId().equals(currentRank)) {
            prefix = ChatColor.GREEN + "► ";
            nameColor = ChatColor.GREEN;
        } else if (currentRank != null) {
            RankupManager.SimpleRankData currentRankData = allRanks.get(currentRank);
            if (currentRankData != null && rank.getOrder() < currentRankData.getOrder()) {
                prefix = ChatColor.YELLOW + "✓ ";
                nameColor = ChatColor.YELLOW;
            } else {
                prefix = ChatColor.GRAY + "• ";
                nameColor = ChatColor.GRAY;
            }
        } else {
            prefix = ChatColor.GRAY + "• ";
            nameColor = ChatColor.GRAY;
        }

        String nextInfo = rank.hasNextRank() ?
                ChatColor.GRAY + " → " + rank.getNextRank() :
                ChatColor.LIGHT_PURPLE + " (máximo)";

        return prefix + nameColor + rank.getDisplayName() +
                ChatColor.GRAY + " (#" + (rank.getOrder() + 1) + ")" + nextInfo;
    }

    /**
     * Crea una barra de progreso compacta
     */
    private String createCompactProgressBar(double percentage, int length) {
        int filled = (int) Math.round(percentage / 100.0 * length);
        filled = Math.max(0, Math.min(filled, length));

        StringBuilder bar = new StringBuilder();
        String color = getProgressColor(percentage);

        bar.append(color);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("▓");
        }

        return bar.toString();
    }

    /**
     * Obtiene el color de la barra de progreso según el porcentaje
     */
    private String getProgressColor(double percentage) {
        if (percentage >= 100) return ChatColor.GREEN.toString();
        if (percentage >= 75) return ChatColor.YELLOW.toString();
        if (percentage >= 50) return ChatColor.GOLD.toString();
        if (percentage >= 25) return ChatColor.RED.toString();
        return ChatColor.DARK_RED.toString();
    }

    /**
     * Obtiene el nombre de display de un rango
     */
    private String getDisplayName(String rankId, Map<String, RankupManager.SimpleRankData> ranks) {
        RankupManager.SimpleRankData rank = ranks.get(rankId);
        return rank != null ? rank.getDisplayName() : rankId;
    }

    /**
     * Envía mensaje de cooldown COMPACTO
     */
    public void sendCooldownMessage(Player player, long seconds) {
        if (enableCompactMode) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "⏰ " + ChatColor.BOLD + "RANKUP EN COOLDOWN");
            player.sendMessage(ChatColor.WHITE + "   Espera " + ChatColor.YELLOW + seconds + "s" +
                    ChatColor.WHITE + " antes de intentar de nuevo");
            player.sendMessage("");
        } else {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("seconds", String.valueOf(seconds));
            replacements.put("player", player.getName());

            String message = getMessage("cooldown.text", replacements);
            message = processPlaceholders(player, message);
            player.sendMessage(message);
        }
    }

    /**
     * Envía mensaje de rango máximo COMPACTO
     */
    public void sendMaxRankMessage(Player player, String currentRank) {
        if (enableCompactMode) {
            player.sendMessage("");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "⭐ " + ChatColor.BOLD + "¡RANGO MÁXIMO ALCANZADO!");
            player.sendMessage(ChatColor.WHITE + "   Ya tienes el rango más alto: " + currentRank);
            player.sendMessage(ChatColor.WHITE + "   ¡Eres una leyenda en este servidor!");
            player.sendMessage("");
        } else {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("current_rank", currentRank);
            replacements.put("player", player.getName());

            String message = getMessage("max_rank.text", replacements);
            message = processPlaceholders(player, message);
            player.sendMessage(message);
        }

        // Enviar título si está habilitado
        if (config.getBoolean("messages.max_rank.show_title", false)) {
            sendTitle(player, "max_rank", Map.of("current_rank", currentRank));
        }
    }

    /**
     * Envía mensaje de ayuda MODERNIZADO
     */
    public void sendHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "📖 " + ChatColor.BOLD + "COMANDOS DE RANKUP");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "/rankup" + ChatColor.GRAY + " - Intentar hacer rankup");
        player.sendMessage(ChatColor.WHITE + "/rankup info" + ChatColor.GRAY + " - Ver tu rango actual");
        player.sendMessage(ChatColor.WHITE + "/rankup progress [página]" + ChatColor.GRAY + " - Ver progreso detallado");
        player.sendMessage(ChatColor.WHITE + "/rankup list [página]" + ChatColor.GRAY + " - Ver todos los rangos");
        player.sendMessage(ChatColor.WHITE + "/ranks" + ChatColor.GRAY + " - Abrir menú interactivo");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Los rangos se basan en dinero, nivel, tiempo y estadísticas");
        player.sendMessage("");
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
     * Recarga todos los mensajes desde el archivo
     */
    public void reloadMessages() {
        loadPaginationConfig();
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
        stats.put("compact_mode_enabled", enableCompactMode);
        stats.put("navigation_enabled", enableNavigation);
        stats.put("max_requirements_per_page", maxRequirementsPerPage);
        stats.put("max_ranks_per_page", maxRanksPerPage);
        return stats;
    }

    // =================== MÉTODOS HELPER PARA COMPATIBILIDAD ===================

    /**
     * Método de compatibilidad - delega a nuevo sistema de paginación
     */
    public void sendProgressHeader(Player player, String currentRank, String nextRank, double progress) {
        // Este método ahora es manejado por sendProgressWithPagination
        plugin.getLogger().info("Método sendProgressHeader deprecado - usar sendProgressWithPagination");
    }

    /**
     * Método de compatibilidad - delega a nuevo sistema de paginación
     */
    public void sendProgressFooter(Player player, String status, int completed, int missing) {
        // Este método ahora es manejado por sendProgressWithPagination
        plugin.getLogger().info("Método sendProgressFooter deprecado - usar sendProgressWithPagination");
    }

    /**
     * Método de compatibilidad - delega a nuevo sistema de paginación
     */
    public void sendRankListHeader(Player player) {
        // Este método ahora es manejado por sendRanksListWithPagination
        plugin.getLogger().info("Método sendRankListHeader deprecado - usar sendRanksListWithPagination");
    }

    /**
     * Método de compatibilidad - delega a nuevo sistema de paginación
     */
    public void sendRankListFooter(Player player, String currentRank, int position, int totalRanks) {
        // Este método ahora es manejado por sendRanksListWithPagination
        plugin.getLogger().info("Método sendRankListFooter deprecado - usar sendRanksListWithPagination");
    }

    // =================== CONFIGURACIÓN DINÁMICA ===================

    /**
     * Alterna entre modo compacto y extendido
     */
    public void toggleCompactMode() {
        this.enableCompactMode = !this.enableCompactMode;
        plugin.getLogger().info("Modo compacto " + (enableCompactMode ? "habilitado" : "deshabilitado"));
    }

    /**
     * Establece el número máximo de requisitos por página
     */
    public void setMaxRequirementsPerPage(int max) {
        this.maxRequirementsPerPage = Math.max(1, Math.min(max, 10));
    }

    /**
     * Establece el número máximo de rangos por página
     */
    public void setMaxRanksPerPage(int max) {
        this.maxRanksPerPage = Math.max(1, Math.min(max, 15));
    }

    // =================== MÉTODOS NUEVOS PARA REQUISITOS INDIVIDUALES ===================

    /**
     * 🆕 NUEVO: Envía línea de requisito individual con formato compacto
     */
    public void sendProgressRequirement(Player player, String reqName, double current, double required, boolean completed) {
        String formattedLine = formatCompactRequirementByValues(reqName, current, required, completed);
        player.sendMessage(formattedLine);
    }

    /**
     * Formatea un requisito individual por valores
     */
    private String formatCompactRequirementByValues(String reqName, double current, double required, boolean completed) {
        String icon = completed ? ChatColor.GREEN + "✅" : ChatColor.RED + "❌";
        String progress = String.format("%.0f/%.0f", current, required);

        if (completed) {
            return icon + ChatColor.WHITE + " " + reqName + ": " + ChatColor.GREEN + progress;
        } else {
            double missing = required - current;
            String missingStr = String.format("%.0f", missing);
            return icon + ChatColor.WHITE + " " + reqName + ": " + ChatColor.GRAY + progress +
                    ChatColor.RED + " (faltan " + missingStr + ")";
        }
    }

    /**
     * 🆕 NUEVO: Envía línea de rango individual con formato compacto
     */
    public void sendRankLine(Player player, String rankName, int order, RankStatus status) {
        String formattedLine = formatRankLineByStatus(rankName, order, status);
        player.sendMessage(formattedLine);
    }

    /**
     * Formatea una línea de rango según su estado
     */
    private String formatRankLineByStatus(String rankName, int order, RankStatus status) {
        String prefix;
        ChatColor nameColor;

        switch (status) {
            case CURRENT -> {
                prefix = ChatColor.GREEN + "► ";
                nameColor = ChatColor.GREEN;
            }
            case COMPLETED -> {
                prefix = ChatColor.YELLOW + "✓ ";
                nameColor = ChatColor.YELLOW;
            }
            case LOCKED -> {
                prefix = ChatColor.GRAY + "• ";
                nameColor = ChatColor.GRAY;
            }
            default -> {
                prefix = ChatColor.GRAY + "• ";
                nameColor = ChatColor.GRAY;
            }
        }

        return prefix + nameColor + rankName + ChatColor.GRAY + " (#" + (order + 1) + ")";
    }

    /**
     * Enum para el estado de los rangos
     */
    public enum RankStatus {
        CURRENT,    // Rango actual del jugador
        COMPLETED,  // Rango ya completado
        LOCKED      // Rango bloqueado/futuro
    }

    // =================== MÉTODOS DE MENSAJES DE ESTADO ===================

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
}