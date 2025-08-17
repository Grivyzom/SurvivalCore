package gc.grivyzom.survivalcore.rankup;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sistema de Rankup mejorado - Versión 2.0
 * Simplificado, más eficiente y fácil de configurar
 *
 * @author Brocolitx
 * @version 2.0
 */
public class RankupManager {

    private final Main plugin;
    private final Map<String, SimpleRankData> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private File configFile;
    private FileConfiguration config;
    private LuckPerms luckPerms;
    private boolean placeholderAPIEnabled = false;
    private boolean debugMode = false;
    private MessageManager messageManager;

    // Configuración simplificada
    private long cooldownTime;
    private boolean enableEffects;
    private boolean enableBroadcast;
    private String detectionMethod;
    private String groupPrefix;
    private String defaultRank;


    public RankupManager(Main plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "rankups.yml");

        if (!initLuckPerms()) {
            throw new RuntimeException("LuckPerms no disponible");
        }

        checkPlaceholderAPI();
        loadConfiguration();

        plugin.getLogger().info("✅ Sistema de Rankup 2.0 inicializado correctamente.");
    }

    /**
     * Carga la configuración simplificada
     */
    private void loadConfiguration() {
        try {
            if (!configFile.exists()) {
                createDefaultConfig();
            }

            config = YamlConfiguration.loadConfiguration(configFile);

            // Configuración general
            cooldownTime = config.getLong("settings.cooldown", 5) * 1000L;
            enableEffects = config.getBoolean("settings.enable_effects", true);
            enableBroadcast = config.getBoolean("settings.enable_broadcast", true);
            debugMode = config.getBoolean("settings.debug_mode", false);

            // Configuración de LuckPerms
            detectionMethod = config.getString("luckperms.detection_method", "primary_group");
            groupPrefix = config.getString("luckperms.group_prefix", "");
            defaultRank = config.getString("luckperms.default_rank", "default");

            // Limpiar y cargar rangos
            ranks.clear();
            loadSimpleRanks();

            // 🔧 INICIALIZAR MessageManager AQUÍ
            this.messageManager = new MessageManager(plugin, config);

            // 🆕 CRÍTICO: Establecer la referencia bidireccional
            messageManager.setRankupManager(this);

            if (config.getBoolean("advanced.validate_config", true)) {
                validateConfiguration();
            }

            plugin.getLogger().info("📊 Cargados " + ranks.size() + " rangos.");

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error cargando configuración: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private double handleCustomRequirement(Player player, String type) {
        if (!placeholderAPIEnabled) {
            if (debugMode) {
                plugin.getLogger().warning("PlaceholderAPI no disponible para requisito personalizado: " + type);
            }
            return 0;
        }

        try {
            String placeholder = null;

            // Mapear tipos a placeholders específicos
            switch (type.toLowerCase()) {
                case "total_blocks_mined", "all_blocks_mined" -> {
                    placeholder = "%statistic_mine_block%";
                }
                case "vault_balance", "money_vault" -> {
                    placeholder = "%vault_eco_balance%";
                }
                case "mcmmo_mining" -> {
                    placeholder = "%mcmmo_level_mining%";
                }
                case "mcmmo_power" -> {
                    placeholder = "%mcmmo_power_level%";
                }
                case "jobs_total" -> {
                    placeholder = "%jobs_total_level%";
                }
                case "cmi_playtime" -> {
                    placeholder = "%cmi_user_playtime_hoursf%";
                }
                default -> {
                    // Buscar en configuración personalizada
                    placeholder = config.getString("custom_requirements." + type);
                }
            }

            if (placeholder == null) {
                if (debugMode) {
                    plugin.getLogger().warning("No se encontró placeholder para requisito: " + type);
                }
                return 0;
            }

            // Procesar placeholder con PlaceholderAPI
            String result = PlaceholderAPI.setPlaceholders(player, placeholder);

            if (debugMode) {
                plugin.getLogger().info("PlaceholderAPI - " + type + ": " + placeholder + " = " + result);
            }

            // Limpiar y convertir resultado
            if (result != null && !result.equals(placeholder)) {
                // Remover caracteres no numéricos excepto punto y coma
                String cleanResult = result.replaceAll("[^0-9.-]", "");

                if (!cleanResult.isEmpty()) {
                    try {
                        return Double.parseDouble(cleanResult);
                    } catch (NumberFormatException e) {
                        if (debugMode) {
                            plugin.getLogger().warning("No se pudo convertir resultado a número: " + cleanResult);
                        }
                    }
                }
            } else {
                if (debugMode) {
                    plugin.getLogger().warning("Placeholder no procesado o no disponible: " + placeholder);
                }
            }

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("Error procesando requisito personalizado " + type + ": " + e.getMessage());
            }
        }

        return 0;
    }

    /**
     * Carga rangos con formato simplificado
     */
    private void loadSimpleRanks() {
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks");
        if (ranksSection == null) {
            plugin.getLogger().warning("No se encontró sección 'ranks'");
            return;
        }

        for (String rankId : ranksSection.getKeys(false)) {
            try {
                ConfigurationSection rankConfig = ranksSection.getConfigurationSection(rankId);
                if (rankConfig == null) continue;

                SimpleRankData rank = new SimpleRankData();
                rank.setId(rankId);

                // 🔧 CRÍTICO: Cambiar '§' por '&' para procesar códigos correctamente
                rank.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        rankConfig.getString("name", rankId)));

                rank.setNextRank(rankConfig.getString("next"));
                rank.setOrder(rankConfig.getInt("order", 0));

                // Cargar requisitos simplificados
                loadSimpleRequirements(rank, rankConfig.getConfigurationSection("requirements"));

                // Cargar recompensas
                loadSimpleRewards(rank, rankConfig.getConfigurationSection("rewards"));

                ranks.put(rankId, rank);

                if (debugMode) {
                    plugin.getLogger().info("✅ Rango cargado: " + rankId + " (orden: " + rank.getOrder() + ")");
                    plugin.getLogger().info("  • Display name procesado: " + rank.getDisplayName());
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando rango '" + rankId + "': " + e.getMessage());
            }
        }
    }
    /**
     * Carga requisitos de forma simplificada
     */
    private void loadSimpleRequirements(SimpleRankData rank, ConfigurationSection reqSection) {
        if (reqSection == null) return;

        Map<String, Object> requirements = new HashMap<>();

        for (String key : reqSection.getKeys(false)) {
            Object value = reqSection.get(key);

            // Convertir nombres amigables a tipos estándar
            String standardKey = convertRequirementKey(key);
            requirements.put(standardKey, value);

            if (debugMode) {
                plugin.getLogger().info("  Requisito: " + key + " -> " + standardKey + " = " + value);
            }
        }

        rank.setRequirements(requirements);
    }

    public void debugPlayerStatistics(Player player, Player admin) {
        admin.sendMessage(ChatColor.AQUA + "═══ DEBUG ESTADÍSTICAS - " + player.getName() + " ═══");

        try {
            // Estadísticas básicas de Minecraft
            admin.sendMessage(ChatColor.YELLOW + "📊 Estadísticas de Minecraft:");
            admin.sendMessage(ChatColor.WHITE + "  • Nivel: " + ChatColor.GREEN + player.getLevel());
            admin.sendMessage(ChatColor.WHITE + "  • Mobs matados: " + ChatColor.GREEN +
                    player.getStatistic(org.bukkit.Statistic.MOB_KILLS));
            admin.sendMessage(ChatColor.WHITE + "  • Animales criados: " + ChatColor.GREEN +
                    player.getStatistic(org.bukkit.Statistic.ANIMALS_BRED));
            admin.sendMessage(ChatColor.WHITE + "  • Peces pescados: " + ChatColor.GREEN +
                    player.getStatistic(org.bukkit.Statistic.FISH_CAUGHT));

            // Intentar estadísticas de bloques
            admin.sendMessage(ChatColor.YELLOW + "⛏️ Estadísticas de minería:");

            // Método personalizado
            double totalMined = getTotalBlocksMined(player);
            admin.sendMessage(ChatColor.WHITE + "  • Total minado (calculado): " + ChatColor.GREEN + totalMined);

            // Algunos materiales específicos
            try {
                admin.sendMessage(ChatColor.WHITE + "  • Piedra minada: " + ChatColor.GREEN +
                        player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE));
                admin.sendMessage(ChatColor.WHITE + "  • Carbón minado: " + ChatColor.GREEN +
                        player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.COAL_ORE));
                admin.sendMessage(ChatColor.WHITE + "  • Hierro minado: " + ChatColor.GREEN +
                        player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.IRON_ORE));
            } catch (Exception e) {
                admin.sendMessage(ChatColor.RED + "  • Error obteniendo estadísticas específicas");
            }

            // Placeholders si está disponible
            if (placeholderAPIEnabled) {
                admin.sendMessage(ChatColor.YELLOW + "🔌 Placeholders de PlaceholderAPI:");

                String[] testPlaceholders = {
                        "%statistic_mine_block%",
                        "%statistic_mob_kills%",
                        "%player_level%",
                        "%vault_eco_balance%"
                };

                for (String placeholder : testPlaceholders) {
                    try {
                        String result = PlaceholderAPI.setPlaceholders(player, placeholder);
                        admin.sendMessage(ChatColor.WHITE + "  • " + placeholder + " = " +
                                ChatColor.GREEN + result);
                    } catch (Exception e) {
                        admin.sendMessage(ChatColor.RED + "  • " + placeholder + " = ERROR");
                    }
                }
            } else {
                admin.sendMessage(ChatColor.RED + "❌ PlaceholderAPI no disponible");
            }

            // Datos de SurvivalCore
            admin.sendMessage(ChatColor.YELLOW + "🎯 Datos de SurvivalCore:");
            admin.sendMessage(ChatColor.WHITE + "  • Nivel farming: " + ChatColor.GREEN + getFarmingLevel(player));
            admin.sendMessage(ChatColor.WHITE + "  • Nivel minería: " + ChatColor.GREEN + getMiningLevel(player));

        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "❌ Error en debug de estadísticas: " + e.getMessage());
            e.printStackTrace();
        }

        admin.sendMessage(ChatColor.AQUA + "═══════════════════════════════════");
    }


    /**
     * Convierte nombres de requisitos amigables a estándar
     */
    private String convertRequirementKey(String key) {
        return switch (key.toLowerCase()) {
            case "money", "dinero" -> "money";
            case "level", "nivel" -> "level";
            case "playtime_hours", "tiempo_jugado" -> "playtime_hours";
            case "mob_kills", "mobs_matados" -> "mob_kills";
            case "blocks_mined", "bloques_minados" -> "blocks_mined";
            case "farming_level", "nivel_farming" -> "farming_level";
            case "mining_level", "nivel_mineria" -> "mining_level";
            case "animals_bred", "animales_criados" -> "animals_bred";
            case "fish_caught", "peces_pescados" -> "fish_caught";
            case "ender_dragon_kills", "dragones_matados" -> "ender_dragon_kills";
            case "wither_kills", "withers_matados" -> "wither_kills";
            default -> key;
        };
    }

    /**
     * Carga recompensas simplificadas
     */
    private void loadSimpleRewards(SimpleRankData rank, ConfigurationSection rewardsSection) {
        if (rewardsSection == null) return;

        Map<String, Object> rewards = new HashMap<>();

        for (String key : rewardsSection.getKeys(false)) {
            rewards.put(key, rewardsSection.get(key));
        }

        rank.setRewards(rewards);
    }

    /**
     * Valida la configuración cargada
     */
    private void validateConfiguration() {
        List<String> errors = new ArrayList<>();

        // Verificar que existe rango por defecto
        if (!ranks.containsKey(defaultRank)) {
            errors.add("Rango por defecto '" + defaultRank + "' no existe");
        }

        // Verificar cadena de rangos
        for (SimpleRankData rank : ranks.values()) {
            String nextRank = rank.getNextRank();
            if (nextRank != null && !ranks.containsKey(nextRank)) {
                errors.add("Rango '" + rank.getId() + "' apunta a rango inexistente: " + nextRank);
            }
        }

        // Verificar PlaceholderAPI si es necesario
        List<String> requiresPAPI = config.getStringList("advanced.requires_placeholderapi");
        if (!requiresPAPI.isEmpty() && !placeholderAPIEnabled) {
            plugin.getLogger().warning("⚠️ Algunos requisitos requieren PlaceholderAPI pero no está disponible");
        }

        if (!errors.isEmpty()) {
            plugin.getLogger().severe("❌ Errores de configuración:");
            errors.forEach(error -> plugin.getLogger().severe("  • " + error));
            throw new RuntimeException("Configuración inválida");
        }

        if (debugMode) {
            plugin.getLogger().info("✅ Configuración validada correctamente");
        }
    }

    /**
     * Intenta hacer rankup con mensajes personalizables
     */
    public CompletableFuture<RankupResult> attemptRankup(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();

            // Verificar cooldown
            if (isOnCooldown(uuid)) {
                long remaining = getRemainingCooldown(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (messageManager != null) {
                        messageManager.sendCooldownMessage(player, remaining / 1000);
                    } else {
                        player.sendMessage(ChatColor.RED + "⏰ Espera " + (remaining / 1000) + " segundos");
                    }
                });

                return new RankupResult(false, "En cooldown");
            }

            // Obtener rango actual
            String currentRank = getCurrentRank(player);
            if (currentRank == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = (messageManager != null) ?
                            messageManager.getErrorMessage("no_rank_detected") :
                            ChatColor.RED + "❌ No se pudo detectar tu rango actual";
                    player.sendMessage(errorMsg);
                });
                return new RankupResult(false, "No se pudo detectar rango");
            }

            SimpleRankData rankData = ranks.get(currentRank);
            if (rankData == null || rankData.getNextRank() == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (messageManager != null) {
                        messageManager.sendMaxRankMessage(player, getDisplayName(currentRank));
                    } else {
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "⭐ ¡Ya has alcanzado el rango máximo!");
                    }
                });
                return new RankupResult(false, "Rango máximo alcanzado");
            }

            // Verificar requisitos
            RequirementCheckResult check = checkAllRequirements(player, rankData);
            if (!check.isSuccess()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (messageManager != null) {
                        messageManager.sendFailedMessage(player, check.getFailedRequirements());
                    } else {
                        player.sendMessage(ChatColor.RED + "❌ No cumples los requisitos:");
                        for (String req : check.getFailedRequirements()) {
                            player.sendMessage(ChatColor.RED + "• " + req);
                        }
                    }
                });
                return new RankupResult(false, "Requisitos no cumplidos");
            }

            // Realizar rankup
            if (performRankupProcess(player, currentRank, rankData.getNextRank(), rankData)) {
                setCooldown(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String newRankDisplay = getDisplayName(rankData.getNextRank());
                    int xpReward = (Integer) rankData.getRewards().getOrDefault("xp", 0);

                    if (messageManager != null) {
                        messageManager.sendSuccessMessage(player, newRankDisplay, xpReward);
                    } else {
                        player.sendMessage(ChatColor.GREEN + "🎉 ¡Has ascendido a " + newRankDisplay + "!");
                    }
                });

                return new RankupResult(true, "Rankup exitoso");
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = (messageManager != null) ?
                            messageManager.getErrorMessage("database") :
                            ChatColor.RED + "❌ Error interno. Contacta a un administrador";
                    player.sendMessage(errorMsg);
                });
                return new RankupResult(false, "Error interno");
            }
        });
    }

    /**
     * Verifica todos los requisitos de forma eficiente
     */
    private RequirementCheckResult checkAllRequirements(Player player, SimpleRankData rankData) {
        Map<String, Object> requirements = rankData.getRequirements();
        List<String> failedRequirements = new ArrayList<>();

        if (debugMode) {
            plugin.getLogger().info("🔍 Verificando " + requirements.size() + " requisitos para " + player.getName());
        }

        for (Map.Entry<String, Object> req : requirements.entrySet()) {
            String type = req.getKey();
            Object required = req.getValue();

            if (!checkSingleRequirement(player, type, required)) {
                // 🔧 CORREGIDO: Verificar que messageManager no sea null
                String reqName = (messageManager != null) ?
                        messageManager.getRequirementName(type) :
                        convertRequirementKeyToDisplayName(type);

                double current = getCurrentRequirementValue(player, type);
                double requiredValue = ((Number) required).doubleValue();

                String formattedReq = String.format("%s: %s/%s",
                        reqName,
                        formatRequirementValue(type, current),
                        formatRequirementValue(type, requiredValue)
                );

                failedRequirements.add(formattedReq);

                if (debugMode) {
                    plugin.getLogger().info("  ❌ " + type + ": " + formattedReq);
                }
            } else if (debugMode) {
                plugin.getLogger().info("  ✅ " + type + ": cumplido");
            }
        }

        return new RequirementCheckResult(failedRequirements.isEmpty(), failedRequirements);
    }

    private String formatRequirementValue(String type, double value) {
        return switch (type) {
            case "money" -> String.format("$%,.0f", value);
            case "playtime_hours" -> String.format("%.1fh", value);
            case "farming_level", "mining_level" -> String.format("Lv.%.0f", value);
            default -> String.format("%,.0f", value);
        };
    }

    private String convertRequirementKeyToDisplayName(String key) {
        // Primero verificar si es un custom requirement
        if (isCustomRequirement(key)) {
            String customName = getCustomRequirementDisplayName(key);
            if (!customName.equals(key)) { // Si encontró un nombre personalizado
                return customName;
            }
        }

        // Nombres estándar existentes
        return switch (key.toLowerCase()) {
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

            // 🆕 NUEVOS: Custom requirements comunes con fallback
            case "vault_eco_balance", "dinero_vault" -> "💰 Dinero del Banco";
            case "mcmmo_mining", "mineria_mcmmo" -> "⛏️ McMMO Minería";
            case "mcmmo_power", "poder_mcmmo" -> "💪 Poder McMMO";
            case "jobs_total", "trabajos_total" -> "💼 Nivel total de trabajos";
            case "playtime", "tiempo_jugado" -> "⏰ Tiempo jugado";
            case "combate_mcmmo" -> "⚔️ Combate McMMO";
            case "farming_mcmmo" -> "🌾 Agricultura McMMO";
            case "pesca_mcmmo" -> "🎣 Pesca McMMO";
            case "arco_mcmmo" -> "🏹 Tiro con Arco";
            case "reparacion_mcmmo" -> "🔧 Reparación";
            case "tokens_servidor" -> "💎 Tokens del Servidor";
            case "xp_total" -> "🏆 Experiencia Total";
            case "bloques_colocados" -> "📊 Bloques Colocados";
            case "votos_totales" -> "🌟 Votos Totales";

            default -> {
                // Convertir nombres como vault_eco_balance -> Vault Eco Balance
                String[] parts = key.split("_");
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

                // Añadir emoji contextual según el contenido
                String finalName = result.toString();
                if (finalName.toLowerCase().contains("money") || finalName.toLowerCase().contains("balance") || finalName.toLowerCase().contains("dinero")) {
                    finalName = "💰 " + finalName;
                } else if (finalName.toLowerCase().contains("mcmmo")) {
                    finalName = "🎯 " + finalName;
                } else if (finalName.toLowerCase().contains("time") || finalName.toLowerCase().contains("tiempo")) {
                    finalName = "⏰ " + finalName;
                } else if (finalName.toLowerCase().contains("level") || finalName.toLowerCase().contains("nivel")) {
                    finalName = "📊 " + finalName;
                } else if (finalName.toLowerCase().contains("token")) {
                    finalName = "💎 " + finalName;
                } else if (finalName.toLowerCase().contains("vot")) {
                    finalName = "🌟 " + finalName;
                }

                yield finalName;
            }
        };
    }

    /**
     * Verifica un requisito individual
     */
    private boolean checkSingleRequirement(Player player, String type, Object required) {
        try {
            double requiredValue = ((Number) required).doubleValue();
            double currentValue = getCurrentRequirementValue(player, type);
            boolean satisfied = currentValue >= requiredValue;

            if (debugMode) {
                plugin.getLogger().info("🔍 Verificando requirement '" + type + "':");
                plugin.getLogger().info("  • Requerido: " + requiredValue);
                plugin.getLogger().info("  • Actual: " + currentValue);
                plugin.getLogger().info("  • Satisfecho: " + (satisfied ? "✅ SÍ" : "❌ NO"));

                if (!satisfied) {
                    double missing = requiredValue - currentValue;
                    plugin.getLogger().info("  • Faltante: " + missing);
                }
            }

            return satisfied;

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Error verificando requisito " + type + ": " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }

    public String getCustomRequirementFormat(String type, double value) {
        try {
            // Buscar formato en configuración extendida
            ConfigurationSection customReq = config.getConfigurationSection("custom_requirements." + type);
            if (customReq != null) {
                String format = customReq.getString("format");
                if (format != null && !format.isEmpty()) {
                    String processedFormat = format.replace("{value}", String.format("%.0f", value));
                    // 🔧 CRÍTICO: Procesar códigos de color en el formato
                    return ChatColor.translateAlternateColorCodes('&', processedFormat);
                }
            }

            // Buscar en requirements estándar
            String configFormat = config.getString("requirements." + type + ".format_short");
            if (configFormat != null && !configFormat.isEmpty()) {
                String processedFormat = configFormat.replace("{value}", String.format("%.0f", value));
                // 🔧 CRÍTICO: Procesar códigos de color aquí también
                return ChatColor.translateAlternateColorCodes('&', processedFormat);
            }

            // Formatos por defecto para custom requirements comunes
            String defaultFormat = switch (type.toLowerCase()) {
                case "dinero_vault", "vault_eco_balance" -> String.format("$&a%,.0f", value);
                case "tiempo_jugado", "playtime" -> String.format("&e%.1fh", value);
                case "mineria_mcmmo", "poder_mcmmo", "combate_mcmmo", "farming_mcmmo", "pesca_mcmmo", "arco_mcmmo", "reparacion_mcmmo" -> String.format("&7Nivel &e%.0f", value);
                case "trabajos_total" -> String.format("&9Nivel &e%.0f", value);
                case "tokens_servidor" -> String.format("&d%.0f tokens", value);
                case "xp_total" -> String.format("&a%.0f XP", value);
                case "votos_totales" -> String.format("&e%.0f votos", value);
                case "bloques_colocados" -> String.format("&6%.0f bloques", value);
                default -> String.format("%,.0f", value);
            };

            // 🔧 CRÍTICO: Procesar códigos de color en formatos por defecto también
            return ChatColor.translateAlternateColorCodes('&', defaultFormat);

        } catch (Exception e) {
            plugin.getLogger().warning("Error obteniendo formato para '" + type + "': " + e.getMessage());
            return String.format("%.0f", value);
        }
    }

    public void debugPlayerCustomRequirements(Player player) {
        if (!debugMode) return;

        plugin.getLogger().info("🔍 DEBUG CUSTOM REQUIREMENTS PARA " + player.getName());
        plugin.getLogger().info("════════════════════════════════════════");

        ConfigurationSection customReqSection = config.getConfigurationSection("custom_requirements");
        if (customReqSection == null) {
            plugin.getLogger().info("❌ No hay custom requirements configurados");
            return;
        }

        for (String reqType : customReqSection.getKeys(false)) {
            plugin.getLogger().info("🔧 Custom Requirement: " + reqType);

            try {
                // Verificar si es configuración extendida
                ConfigurationSection extendedConfig = customReqSection.getConfigurationSection(reqType);
                if (extendedConfig != null) {
                    plugin.getLogger().info("  📋 Configuración extendida:");
                    plugin.getLogger().info("    • Display name: " + extendedConfig.getString("display_name", "N/A"));
                    plugin.getLogger().info("    • Placeholder: " + extendedConfig.getString("placeholder", "N/A"));
                    plugin.getLogger().info("    • Format: " + extendedConfig.getString("format", "N/A"));
                    plugin.getLogger().info("    • Description: " + extendedConfig.getString("description", "N/A"));
                } else {
                    // Configuración simple
                    String placeholder = customReqSection.getString(reqType);
                    plugin.getLogger().info("  📋 Configuración simple: " + placeholder);
                }

                // Obtener valor actual
                double currentValue = getCurrentRequirementValue(player, reqType);
                plugin.getLogger().info("  💰 Valor actual: " + currentValue);

                // Obtener nombre de display
                String displayName = getCustomRequirementDisplayName(reqType);
                plugin.getLogger().info("  🏷️ Display name: " + displayName);

                // Obtener formato
                String formattedValue = getCustomRequirementFormat(reqType, currentValue);
                plugin.getLogger().info("  🎨 Valor formateado: " + formattedValue);

                plugin.getLogger().info("  ────────────────");

            } catch (Exception e) {
                plugin.getLogger().info("  ❌ Error procesando: " + e.getMessage());
            }
        }

        plugin.getLogger().info("════════════════════════════════════════");
    }

    /**
     * Obtiene el valor actual de un requisito
     */
    private double getCurrentRequirementValue(Player player, String type) {
        try {
            if (debugMode) {
                plugin.getLogger().info("🔍 Obteniendo valor para requirement '" + type + "' del jugador " + player.getName());
            }

            double value = switch (type) {
                case "money" -> getPlayerMoney(player);
                case "level" -> player.getLevel();
                case "playtime_hours" -> getPlaytimeHours(player);
                case "mob_kills" -> player.getStatistic(org.bukkit.Statistic.MOB_KILLS);

                // 🔧 CORRECCIÓN: Usar estadística correcta para bloques minados
                case "blocks_mined" -> getTotalBlocksMined(player);

                case "farming_level" -> getFarmingLevel(player);
                case "mining_level" -> getMiningLevel(player);
                case "animals_bred" -> player.getStatistic(org.bukkit.Statistic.ANIMALS_BRED);
                case "fish_caught" -> player.getStatistic(org.bukkit.Statistic.FISH_CAUGHT);
                case "ender_dragon_kills" -> getEntityKills(player, "ENDER_DRAGON");
                case "wither_kills" -> getEntityKills(player, "WITHER");
                default -> {
                    // Si no es un tipo estándar, intentar como custom requirement
                    if (isCustomRequirement(type)) {
                        yield handleCustomRequirement(player, type);
                    } else {
                        plugin.getLogger().warning("⚠️ Tipo de requisito desconocido: " + type);
                        yield 0.0;
                    }
                }
            };

            if (debugMode) {
                plugin.getLogger().info("  • Valor obtenido: " + value);
            }

            return value;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error obteniendo valor para requirement '" + type + "': " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            return 0.0;
        }
    }
    /**
     * Obtiene el rango actual de forma más robusta
     */
    public String getCurrentRank(Player player) {
        if (luckPerms == null) {
            plugin.getLogger().warning("⚠️ LuckPerms no disponible para " + player.getName());
            return defaultRank;
        }

        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                plugin.getLogger().warning("⚠️ Usuario LuckPerms null para " + player.getName());
                return defaultRank;
            }

            if (debugMode) {
                plugin.getLogger().info("🔍 Detectando rango para " + player.getName());
                plugin.getLogger().info("  • Método de detección: " + detectionMethod);
                plugin.getLogger().info("  • Prefijo de grupo: '" + groupPrefix + "'");
                plugin.getLogger().info("  • Grupo primario: " + user.getPrimaryGroup());
            }

            // MÉTODO 1: Grupo primario (principal)
            if ("primary_group".equals(detectionMethod)) {
                String primaryGroup = user.getPrimaryGroup();

                if (debugMode) {
                    plugin.getLogger().info("  • Grupo primario detectado: " + primaryGroup);
                }

                // Remover prefijo si existe
                String rankId = primaryGroup;
                if (!groupPrefix.isEmpty() && primaryGroup.startsWith(groupPrefix)) {
                    rankId = primaryGroup.substring(groupPrefix.length());
                }

                if (debugMode) {
                    plugin.getLogger().info("  • RankID procesado: " + rankId);
                    plugin.getLogger().info("  • ¿Existe en configuración? " + ranks.containsKey(rankId));
                }

                // Verificar que el rango existe en la configuración
                if (ranks.containsKey(rankId)) {
                    plugin.getLogger().info("✅ Rango detectado para " + player.getName() + ": " + rankId);
                    return rankId;
                } else {
                    plugin.getLogger().warning("⚠️ Rango '" + rankId + "' no encontrado en configuración para " + player.getName());
                }
            }

            // MÉTODO 2: Buscar en todos los grupos heredados (FALLBACK)
            plugin.getLogger().info("🔄 Usando método fallback para " + player.getName());

            String highestRank = user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .map(group -> {
                        String name = group.getName();
                        String processedName = name;

                        // Remover prefijo si existe
                        if (!groupPrefix.isEmpty() && name.startsWith(groupPrefix)) {
                            processedName = name.substring(groupPrefix.length());
                        }

                        if (debugMode) {
                            plugin.getLogger().info("  • Grupo heredado: " + name + " → " + processedName);
                        }

                        return processedName;
                    })
                    .filter(rankId -> {
                        boolean exists = ranks.containsKey(rankId);
                        if (debugMode) {
                            plugin.getLogger().info("  • ¿Rango '" + rankId + "' existe? " + exists);
                        }
                        return exists;
                    })
                    .max(Comparator.comparingInt(rankId -> {
                        int order = ranks.get(rankId).getOrder();
                        if (debugMode) {
                            plugin.getLogger().info("  • Orden de '" + rankId + "': " + order);
                        }
                        return order;
                    }))
                    .orElse(defaultRank);

            if (debugMode) {
                plugin.getLogger().info("🎯 Rango final detectado para " + player.getName() + ": " + highestRank);
            }

            return highestRank;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error crítico detectando rango de " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return defaultRank;
        }
    }

    /**
     * Realiza el proceso completo de rankup
     */
    private boolean performRankupProcess(Player player, String fromRank, String toRank, SimpleRankData rankData) {
        try {
            if (debugMode) {
                plugin.getLogger().info("🚀 Iniciando rankup: " + player.getName() + " de " + fromRank + " a " + toRank);
            }

            // Cambiar grupo en LuckPerms
            if (!updatePlayerGroup(player, fromRank, toRank)) {
                plugin.getLogger().severe("❌ FALLÓ actualización de grupo LuckPerms");
                return false;
            }

            // 🆕 VERIFICACIÓN CRÍTICA: Confirmar que el cambio se aplicó
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String newDetectedRank = getCurrentRank(player);

                if (!toRank.equals(newDetectedRank)) {
                    plugin.getLogger().severe("🚨 CRÍTICO: Rankup falló la verificación!");
                    plugin.getLogger().severe("   Esperado: " + toRank);
                    plugin.getLogger().severe("   Detectado: " + newDetectedRank);
                    plugin.getLogger().severe("   Jugador: " + player.getName());

                    // Intentar corregir inmediatamente
                    plugin.getLogger().info("🔧 Intentando corrección automática...");
                    if (forceSetPlayerRank(player, toRank)) {
                        plugin.getLogger().info("✅ Corrección exitosa");
                        player.sendMessage(ChatColor.GREEN + "✅ Rankup completado y verificado correctamente");
                    } else {
                        plugin.getLogger().severe("❌ Corrección falló - intervención manual requerida");
                        player.sendMessage(ChatColor.RED + "⚠️ Rankup completado pero detectamos un problema. Contacta a un administrador.");
                    }
                } else {
                    plugin.getLogger().info("✅ Rankup verificado correctamente: " + player.getName() + " → " + toRank);
                }
            }, 10L); // Esperar 0.5 segundos

            // Aplicar recompensas
            applyRankupRewards(player, rankData);

            // Efectos
            if (enableEffects) {
                playRankupEffects(player, toRank);
            }

            // Broadcast
            if (enableBroadcast) {
                broadcastRankup(player, toRank);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error en proceso de rankup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Actualiza el grupo del jugador en LuckPerms
     */
    private boolean updatePlayerGroup(Player player, String fromRank, String toRank) {
        try {
            if (debugMode) {
                plugin.getLogger().info("🔄 Actualizando grupo LuckPerms:");
                plugin.getLogger().info("  • Jugador: " + player.getName());
                plugin.getLogger().info("  • De: " + fromRank + " → A: " + toRank);
                plugin.getLogger().info("  • Prefijo: '" + groupPrefix + "'");
            }

            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                plugin.getLogger().severe("❌ Usuario LuckPerms es null para " + player.getName());
                return false;
            }

            // PASO 1: Mostrar estado actual
            if (debugMode) {
                plugin.getLogger().info("📋 Estado actual del usuario:");
                plugin.getLogger().info("  • Grupo primario: " + user.getPrimaryGroup());
                plugin.getLogger().info("  • Grupos heredados:");
                user.getInheritedGroups(user.getQueryOptions()).forEach(group ->
                        plugin.getLogger().info("    - " + group.getName()));
            }

            // PASO 2: Construcción de nombres de grupos
            String oldGroup = groupPrefix.isEmpty() ? fromRank : groupPrefix + fromRank;
            String newGroup = groupPrefix.isEmpty() ? toRank : groupPrefix + toRank;

            if (debugMode) {
                plugin.getLogger().info("🏷️ Nombres de grupos:");
                plugin.getLogger().info("  • Grupo anterior: '" + oldGroup + "'");
                plugin.getLogger().info("  • Grupo nuevo: '" + newGroup + "'");
            }

            // PASO 3: Verificar que el grupo anterior existe en el usuario
            boolean hadOldGroup = user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .anyMatch(group -> group.getName().equals(oldGroup));

            if (debugMode) {
                plugin.getLogger().info("🔍 ¿Usuario tenía grupo anterior? " + hadOldGroup);
            }

            // PASO 4: Remover grupo anterior (solo si lo tenía)
            if (hadOldGroup) {
                InheritanceNode oldNode = InheritanceNode.builder(oldGroup).build();
                user.data().remove(oldNode);

                if (debugMode) {
                    plugin.getLogger().info("🗑️ Grupo anterior removido: " + oldGroup);
                }
            } else {
                plugin.getLogger().warning("⚠️ Jugador " + player.getName() + " no tenía el grupo " + oldGroup);

                // 🆕 LIMPIEZA ADICIONAL: Remover TODOS los rangos conocidos para evitar conflictos
                if (debugMode) {
                    plugin.getLogger().info("🧹 Limpiando todos los rangos conocidos...");
                }

                for (String rankId : ranks.keySet()) {
                    String rankGroup = groupPrefix.isEmpty() ? rankId : groupPrefix + rankId;
                    InheritanceNode rankNode = InheritanceNode.builder(rankGroup).build();
                    user.data().remove(rankNode);

                    if (debugMode) {
                        plugin.getLogger().info("  🗑️ Removido: " + rankGroup);
                    }
                }
            }

            // PASO 5: Añadir nuevo grupo
            InheritanceNode newNode = InheritanceNode.builder(newGroup).build();
            user.data().add(newNode);

            if (debugMode) {
                plugin.getLogger().info("➕ Grupo nuevo añadido: " + newGroup);
            }

            // PASO 6: Guardar cambios con timeout
            try {
                luckPerms.getUserManager().saveUser(user).get(5, java.util.concurrent.TimeUnit.SECONDS);

                if (debugMode) {
                    plugin.getLogger().info("💾 Cambios guardados en LuckPerms");
                }
            } catch (java.util.concurrent.TimeoutException e) {
                plugin.getLogger().severe("❌ Timeout guardando cambios en LuckPerms para " + player.getName());
                return false;
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Error guardando cambios en LuckPerms: " + e.getMessage());
                return false;
            }

            // PASO 7: Verificación inmediata (esperar un tick)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Recargar usuario para obtener datos frescos
                    User refreshedUser = luckPerms.getUserManager().loadUser(user.getUniqueId()).get(3, java.util.concurrent.TimeUnit.SECONDS);

                    if (refreshedUser != null) {
                        boolean hasNewGroup = refreshedUser.getInheritedGroups(refreshedUser.getQueryOptions())
                                .stream()
                                .anyMatch(group -> group.getName().equals(newGroup));

                        String currentPrimary = refreshedUser.getPrimaryGroup();

                        if (debugMode) {
                            plugin.getLogger().info("🔍 Verificación post-rankup:");
                            plugin.getLogger().info("  • ¿Tiene nuevo grupo? " + hasNewGroup);
                            plugin.getLogger().info("  • Grupo primario actual: " + currentPrimary);
                        }

                        if (!hasNewGroup) {
                            plugin.getLogger().severe("🚨 VERIFICACIÓN FALLÓ: Jugador " + player.getName() +
                                    " no tiene el grupo " + newGroup + " después del rankup!");

                            // Intentar corrección
                            plugin.getLogger().info("🔧 Intentando corrección automática...");
                            refreshedUser.data().add(InheritanceNode.builder(newGroup).build());
                            luckPerms.getUserManager().saveUser(refreshedUser);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error en verificación post-rankup: " + e.getMessage());
                }
            }, 1L);

            if (debugMode) {
                plugin.getLogger().info("✅ Actualización de grupo completada para " + player.getName());
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error crítico actualizando grupo para " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    // =================== MÉTODOS DE UTILIDAD ===================

    private boolean initLuckPerms() {
        try {
            this.luckPerms = LuckPermsProvider.get();
            return luckPerms != null;
        } catch (Exception e) {
            plugin.getLogger().severe("LuckPerms no disponible: " + e.getMessage());
            return false;
        }
    }

    private void checkPlaceholderAPI() {
        placeholderAPIEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        plugin.getLogger().info(placeholderAPIEnabled ?
                "✅ PlaceholderAPI detectado" : "⚠️ PlaceholderAPI no disponible");
    }

    private void createDefaultConfig() {
        try {
            plugin.saveResource("rankups.yml", false);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo crear configuración por defecto");
        }
    }

    private double getPlayerMoney(Player player) {
        // TODO: Integrar con Vault
        return 0.0;
    }

    private double getPlaytimeHours(Player player) {
        long ticks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        return (ticks * 50L) / (1000.0 * 60 * 60); // Convertir ticks a horas
    }

    private double getTotalBlocksMined(Player player) {
        try {
            // Método 1: Intentar usar la estadística general si existe
            try {
                // En versiones más recientes de Minecraft/Bukkit puede existir
                return player.getStatistic(org.bukkit.Statistic.valueOf("MINE_BLOCK"));
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                // Si no existe, usar método alternativo
            }

            // Método 2: Sumar estadísticas específicas de materiales comunes
            double totalMined = 0;

            // Lista de materiales comunes que se minan
            Material[] commonMaterials = {
                    Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
                    Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                    Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                    Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                    Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                    Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                    Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                    Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                    Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
                    Material.DIRT, Material.GRAVEL, Material.SAND,
                    Material.NETHERRACK, Material.NETHER_QUARTZ_ORE,
                    Material.ANCIENT_DEBRIS, Material.NETHER_GOLD_ORE,
                    Material.END_STONE
            };

            for (Material material : commonMaterials) {
                try {
                    totalMined += player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, material);
                } catch (Exception e) {
                    // Ignorar si el material no es válido o no se puede obtener
                    if (debugMode) {
                        plugin.getLogger().warning("No se pudo obtener estadística para material: " + material.name());
                    }
                }
            }

            return totalMined;

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("Error obteniendo bloques minados para " + player.getName() + ": " + e.getMessage());
            }
            return 0;
        }
    }

    private double getFarmingLevel(Player player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? userData.getFarmingLevel() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private double getMiningLevel(Player player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null ? userData.getMiningLevel() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private double getEntityKills(Player player, String entityType) {
        try {
            return player.getStatistic(org.bukkit.Statistic.KILL_ENTITY,
                    org.bukkit.entity.EntityType.valueOf(entityType));
        } catch (Exception e) {
            return 0;
        }
    }

    public String getCustomRequirementDisplayName(String type) {
        try {
            if (debugMode) {
                plugin.getLogger().info("🔍 Obteniendo display name para: " + type);
            }

            // Buscar en configuración extendida primero
            ConfigurationSection customReq = config.getConfigurationSection("custom_requirements." + type);

            if (customReq != null) {
                String displayName = customReq.getString("display_name");
                if (displayName != null && !displayName.isEmpty()) {
                    // 🔧 CRÍTICO: Procesar códigos de color aquí
                    String formattedName = ChatColor.translateAlternateColorCodes('&', displayName);
                    if (debugMode) {
                        plugin.getLogger().info("  • Display name encontrado en configuración extendida: " + formattedName);
                    }
                    return formattedName;
                }
            }

            // Buscar en la sección de formatos de requirements
            String formatName = config.getString("requirements." + type + ".name");
            if (formatName != null && !formatName.isEmpty()) {
                // 🔧 CRÍTICO: Procesar códigos de color aquí también
                String formattedName = ChatColor.translateAlternateColorCodes('&', formatName);
                if (debugMode) {
                    plugin.getLogger().info("  • Display name encontrado en sección requirements: " + formattedName);
                }
                return formattedName;
            }

            // Fallback: convertir el tipo a nombre legible
            String fallbackName = convertRequirementKeyToDisplayName(type);
            if (debugMode) {
                plugin.getLogger().info("  • Usando nombre fallback: " + fallbackName);
            }
            return fallbackName;

        } catch (Exception e) {
            plugin.getLogger().warning("Error obteniendo display name para '" + type + "': " + e.getMessage());
            return convertRequirementKeyToDisplayName(type);
        }
    }

    public boolean isCustomRequirement(String type) {
        try {
            // Verificar si existe como configuración extendida
            ConfigurationSection customReq = config.getConfigurationSection("custom_requirements." + type);
            if (customReq != null) {
                if (debugMode) {
                    plugin.getLogger().info("✅ '" + type + "' es un custom requirement (configuración extendida)");
                }
                return true;
            }

            // Verificar si existe como string simple
            String placeholder = config.getString("custom_requirements." + type);
            boolean isCustom = placeholder != null && !placeholder.isEmpty();

            if (debugMode) {
                plugin.getLogger().info((isCustom ? "✅" : "❌") + " '" + type + "' " +
                        (isCustom ? "es" : "NO es") + " un custom requirement (formato simple)");
            }

            return isCustom;

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("Error verificando si '" + type + "' es custom requirement: " + e.getMessage());
            }
            return false;
        }
    }


    private String formatRequirementFailure(Player player, String type, Object required) {
        double requiredValue = ((Number) required).doubleValue();
        double currentValue = getCurrentRequirementValue(player, type);

        String messageKey = "messages.requirements." + type;
        String template = config.getString(messageKey, "&7• " + type + ": &c{current}&7/&a{required}");

        // 🔧 CRÍTICO: Cambiar '§' por '&' aquí también
        return ChatColor.translateAlternateColorCodes('&', template
                .replace("{current}", formatValue(type, currentValue))
                .replace("{required}", formatValue(type, requiredValue)));
    }
    private String formatValue(String type, double value) {
        return switch (type) {
            case "money" -> String.format("$%,.0f", value);
            case "playtime_hours" -> String.format("%.1fh", value);
            default -> String.format("%,.0f", value);
        };
    }

    private void applyRankupRewards(Player player, SimpleRankData rankData) {
        Map<String, Object> rewards = rankData.getRewards();

        for (Map.Entry<String, Object> reward : rewards.entrySet()) {
            String type = reward.getKey();
            Object value = reward.getValue();

            switch (type.toLowerCase()) {
                case "xp", "experience" -> {
                    int amount = ((Number) value).intValue();
                    player.giveExp(amount);
                }
                case "levels" -> {
                    int amount = ((Number) value).intValue();
                    player.giveExpLevels(amount);
                }
                case "commands" -> {
                    if (value instanceof List<?> commands) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (Object cmdObj : commands) {
                                String command = cmdObj.toString().replace("%player%", player.getName());
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                            }
                        });
                    }
                }
                case "money" -> {
                    // TODO: Integrar con Vault Economy
                }
            }
        }
    }

    private void playRankupEffects(Player player, String newRank) {
        ConfigurationSection effectsConfig = config.getConfigurationSection("effects.rankup");
        if (effectsConfig == null) return;

        try {
            // Sonidos
            List<String> sounds = effectsConfig.getStringList("sounds");
            for (String soundStr : sounds) {
                String[] parts = soundStr.split(":");
                Sound sound = Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                player.playSound(player.getLocation(), sound, volume, pitch);
            }

            // Título
            if (effectsConfig.getBoolean("title.enabled", true)) {
                String title = ChatColor.translateAlternateColorCodes('§',
                        effectsConfig.getString("title.title", "§6§l¡RANKUP!"));
                String subtitle = ChatColor.translateAlternateColorCodes('§',
                        effectsConfig.getString("title.subtitle", "§fAhora eres {new_rank}")
                                .replace("{new_rank}", getDisplayName(newRank)));
                int duration = effectsConfig.getInt("title.duration", 60);

                player.sendTitle(title, subtitle, 10, duration, 20);
            }

            // Partículas
            String particleType = effectsConfig.getString("particles.type", "FIREWORK");
            int count = effectsConfig.getInt("particles.count", 50);

            try {
                org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleType);
                player.getWorld().spawnParticle(particle,
                        player.getLocation().add(0, 1, 0), count, 0.5, 0.5, 0.5, 0.1);
            } catch (Exception e) {
                if (debugMode) {
                    plugin.getLogger().warning("Error en partículas: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error aplicando efectos de rankup: " + e.getMessage());
        }
    }

    private void broadcastRankup(Player player, String newRank) {
        String message = ChatColor.YELLOW + "🌟 " + ChatColor.BOLD + player.getName() +
                ChatColor.RESET + ChatColor.YELLOW + " ascendió a " +
                getDisplayName(newRank) + ChatColor.YELLOW + "! 🎉";

        Bukkit.broadcastMessage(message);
    }

    private String getDisplayName(String rankId) {
        SimpleRankData rank = ranks.get(rankId);
        return rank != null ? rank.getDisplayName() : rankId;
    }

    private String formatMessage(String key, Map<String, String> replacements) {
        String message = config.getString("messages." + key, "Mensaje no configurado: " + key);

        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }

        // 🔧 CRÍTICO: Cambiar '§' por '&' aquí también
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // =================== GESTIÓN DE COOLDOWNS ===================

    public boolean isOnCooldown(UUID uuid) {
        Long lastUse = cooldowns.get(uuid);
        return lastUse != null && System.currentTimeMillis() - lastUse < cooldownTime;
    }

    public long getRemainingCooldown(UUID uuid) {
        Long lastUse = cooldowns.get(uuid);
        if (lastUse == null) return 0;
        return Math.max(0, cooldownTime - (System.currentTimeMillis() - lastUse));
    }

    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    // =================== INFORMACIÓN Y PROGRESO ===================

    public CompletableFuture<RankupProgress> getPlayerProgress(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String currentRank = getCurrentRank(player);
            if (currentRank == null) {
                return new RankupProgress(null, null, new HashMap<>(), 0.0);
            }

            SimpleRankData rankData = ranks.get(currentRank);
            if (rankData == null || rankData.getNextRank() == null) {
                return new RankupProgress(currentRank, null, new HashMap<>(), 100.0);
            }

            Map<String, Object> requirements = rankData.getRequirements();
            Map<String, RequirementProgress> progress = new HashMap<>();
            double totalProgress = 0.0;

            for (Map.Entry<String, Object> requirement : requirements.entrySet()) {
                String type = requirement.getKey();
                double required = ((Number) requirement.getValue()).doubleValue();
                double current = getCurrentRequirementValue(player, type);
                double percentage = Math.min((current / required) * 100.0, 100.0);

                RequirementProgress reqProgress = new RequirementProgress(
                        type, current, required, percentage, current >= required
                );
                progress.put(type, reqProgress);
                totalProgress += percentage;
            }

            if (!requirements.isEmpty()) {
                totalProgress /= requirements.size();
            }

            return new RankupProgress(currentRank, rankData.getNextRank(), progress, totalProgress);
        });
    }

    /**
     * Debug de información del jugador (para admins)
     */
    public void debugPlayerRankup(Player player, Player admin) {
        admin.sendMessage(ChatColor.GOLD + "═══ Debug Rankup - " + player.getName() + " ═══");

        String currentRank = getCurrentRank(player);
        admin.sendMessage(ChatColor.WHITE + "Rango actual: " + ChatColor.YELLOW +
                (currentRank != null ? currentRank : "NULL"));

        if (currentRank == null) {
            String errorMsg = (messageManager != null) ?
                    messageManager.getErrorMessage("no_rank_detected") :
                    ChatColor.RED + "❌ No se pudo detectar tu rango actual";
            admin.sendMessage(errorMsg);
            return;
        }

        SimpleRankData rankData = ranks.get(currentRank);
        if (rankData == null) {
            String errorMsg = (messageManager != null) ?
                    messageManager.getErrorMessage("invalid_rank") :
                    ChatColor.RED + "❌ Configuración de rango inválida";
            admin.sendMessage(errorMsg);
            return;
        }

        admin.sendMessage(ChatColor.WHITE + "Display: " + rankData.getDisplayName());
        admin.sendMessage(ChatColor.WHITE + "Orden: " + ChatColor.YELLOW + rankData.getOrder());
        admin.sendMessage(ChatColor.WHITE + "Siguiente: " + ChatColor.YELLOW +
                (rankData.getNextRank() != null ? rankData.getNextRank() : "RANGO MÁXIMO"));

        if (rankData.getNextRank() == null) {
            admin.sendMessage(ChatColor.GREEN + "✅ El jugador ya tiene el rango máximo");
            return;
        }

        admin.sendMessage(ChatColor.WHITE + "Verificando requisitos:");
        RequirementCheckResult check = checkAllRequirements(player, rankData);

        if (check.isSuccess()) {
            admin.sendMessage(ChatColor.GREEN + "✅ Todos los requisitos cumplidos");
        } else {
            admin.sendMessage(ChatColor.RED + "❌ Requisitos faltantes:");
            for (String failedReq : check.getFailedRequirements()) {
                admin.sendMessage(ChatColor.RED + "  • " + failedReq);
            }
        }

        if (isOnCooldown(player.getUniqueId())) {
            long remaining = getRemainingCooldown(player.getUniqueId());
            admin.sendMessage(ChatColor.YELLOW + "⏰ Cooldown: " + (remaining / 1000) + "s restantes");
        } else {
            admin.sendMessage(ChatColor.GREEN + "✅ Sin cooldown activo");
        }
    }    // =================== GETTERS Y CONFIGURACIÓN ===================

    public void reloadConfig() {
        try {
            long startTime = System.currentTimeMillis();

            if (debugMode) {
                plugin.getLogger().info("🔄 Iniciando recarga de configuración de Rankup 2.0...");
            }

            // 1. Verificar que LuckPerms sigue disponible
            if (!initLuckPerms()) {
                throw new RuntimeException("LuckPerms ya no está disponible");
            }

            // 2. Verificar PlaceholderAPI
            checkPlaceholderAPI();

            // 3. Crear respaldo de configuración actual (por si falla)
            Map<String, SimpleRankData> backupRanks = new HashMap<>(ranks);
            long backupCooldownTime = this.cooldownTime;
            boolean backupEffects = this.enableEffects;
            boolean backupBroadcast = this.enableBroadcast;

            try {
                // 4. Intentar cargar nueva configuración
                loadConfiguration();

                // 5. Recargar mensajes
                if (messageManager != null) {
                    messageManager.reloadMessages();
                } else {
                    messageManager = new MessageManager(plugin, config);
                }

                long duration = System.currentTimeMillis() - startTime;

                plugin.getLogger().info("✅ Configuración de Rankup 2.0 recargada exitosamente en " + duration + "ms");

                // Log de estadísticas actualizadas
                plugin.getLogger().info("📊 Estadísticas actualizadas:");
                plugin.getLogger().info("  • Rangos: " + ranks.size());
                plugin.getLogger().info("  • Cooldown: " + (cooldownTime / 1000) + "s");
                plugin.getLogger().info("  • Efectos: " + (enableEffects ? "Habilitados" : "Deshabilitados"));
                plugin.getLogger().info("  • Broadcast: " + (enableBroadcast ? "Habilitado" : "Deshabilitado"));
                plugin.getLogger().info("  • PlaceholderAPI: " + (placeholderAPIEnabled ? "Disponible" : "No disponible"));

                // Verificar cambios importantes
                if (ranks.size() != backupRanks.size()) {
                    plugin.getLogger().info("🔄 Cambio en número de rangos: " + backupRanks.size() + " → " + ranks.size());
                }

                if (cooldownTime != backupCooldownTime) {
                    plugin.getLogger().info("🔄 Cambio en cooldown: " + (backupCooldownTime / 1000) + "s → " + (cooldownTime / 1000) + "s");
                }

                // Limpiar cooldowns si se cambió la configuración de cooldown
                if (cooldownTime != backupCooldownTime) {
                    cooldowns.clear();
                    plugin.getLogger().info("🧹 Cooldowns limpiados debido a cambio de configuración");
                }

                if (debugMode) {
                    plugin.getLogger().info("🔍 Recarga completa - Listando rangos cargados:");
                    ranks.values().stream()
                            .sorted(Comparator.comparingInt(SimpleRankData::getOrder))
                            .forEach(rank -> plugin.getLogger().info("  • " + rank.getId() +
                                    " (orden: " + rank.getOrder() + ", siguiente: " + rank.getNextRank() + ")"));
                }

            } catch (Exception configError) {
                // Restaurar configuración de respaldo si la nueva falla
                plugin.getLogger().severe("❌ Error cargando nueva configuración, restaurando respaldo...");

                this.ranks.clear();
                this.ranks.putAll(backupRanks);
                this.cooldownTime = backupCooldownTime;
                this.enableEffects = backupEffects;
                this.enableBroadcast = backupBroadcast;

                plugin.getLogger().warning("⚠️ Configuración restaurada al estado anterior");
                throw new RuntimeException("Error en nueva configuración: " + configError.getMessage(), configError);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error crítico recargando configuración de Rankup:");
            plugin.getLogger().severe("Tipo: " + e.getClass().getSimpleName());
            plugin.getLogger().severe("Mensaje: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            throw new RuntimeException("Error crítico en recarga de configuración", e);
        }
    }

    /**
     * Método auxiliar para verificar si un archivo de configuración fue modificado recientemente
     */
    private boolean isConfigFileModifiedRecently(long thresholdMinutes) {
        try {
            if (!configFile.exists()) {
                return false;
            }

            long lastModified = configFile.lastModified();
            long now = System.currentTimeMillis();
            long thresholdMs = thresholdMinutes * 60 * 1000;

            return (now - lastModified) < thresholdMs;

        } catch (Exception e) {
            return false;
        }
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * Método auxiliar para obtener información detallada del archivo de configuración
     */
    public Map<String, Object> getConfigFileInfo() {
        Map<String, Object> info = new HashMap<>();

        try {
            if (configFile.exists()) {
                info.put("exists", true);
                info.put("size", configFile.length());
                info.put("lastModified", configFile.lastModified());
                info.put("canRead", configFile.canRead());
                info.put("path", configFile.getAbsolutePath());
            } else {
                info.put("exists", false);
            }
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }

        return info;
    }

    /**
     * Método para forzar la creación de un archivo de configuración por defecto
     */
    public void createDefaultConfigFile() {
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            if (!configFile.exists()) {
                plugin.saveResource("rankups.yml", false);
                plugin.getLogger().info("✅ Archivo rankups.yml creado con configuración por defecto");
            } else {
                plugin.getLogger().warning("⚠️ El archivo rankups.yml ya existe, no se sobrescribirá");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error creando archivo de configuración por defecto: " + e.getMessage());
        }
    }

    /**
     * Método de debug para validar la configuración actual
     */
    public void validateCurrentConfig() {
        if (!debugMode) return;

        plugin.getLogger().info("🔍 VALIDACIÓN DE CONFIGURACIÓN ACTUAL:");
        plugin.getLogger().info("════════════════════════════════════");

        // Validar rangos
        plugin.getLogger().info("Rangos (" + ranks.size() + " total):");
        for (SimpleRankData rank : ranks.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append("  • ").append(rank.getId());
            sb.append(" (orden: ").append(rank.getOrder()).append(")");
            sb.append(" → ").append(rank.getNextRank() != null ? rank.getNextRank() : "FIN");
            sb.append(" [").append(rank.getRequirements().size()).append(" req]");
            plugin.getLogger().info(sb.toString());
        }

        // Validar configuración general
        plugin.getLogger().info("Configuración general:");
        plugin.getLogger().info("  • Cooldown: " + (cooldownTime / 1000) + " segundos");
        plugin.getLogger().info("  • Efectos: " + enableEffects);
        plugin.getLogger().info("  • Broadcast: " + enableBroadcast);
        plugin.getLogger().info("  • Método detección: " + detectionMethod);
        plugin.getLogger().info("  • Prefijo grupos: " + groupPrefix);
        plugin.getLogger().info("  • Rango por defecto: " + defaultRank);

        // Validar integraciones
        plugin.getLogger().info("Integraciones:");
        plugin.getLogger().info("  • LuckPerms: " + (luckPerms != null ? "✓" : "✗"));
        plugin.getLogger().info("  • PlaceholderAPI: " + (placeholderAPIEnabled ? "✓" : "✗"));

        plugin.getLogger().info("════════════════════════════════════");
    }

    public Map<String, SimpleRankData> getRanks() {
        return new HashMap<>(ranks);
    }

    public Map<String, PrestigeData> getPrestiges() {
        return new HashMap<>(); // TODO: Implementar prestiges simplificados
    }

    public boolean isPrestigeEnabled() {
        return config.getBoolean("settings.enable_prestige", false);
    }

    public boolean areEffectsEnabled() {
        return enableEffects;
    }

    public boolean isBroadcastEnabled() {
        return enableBroadcast;
    }

    public long getCooldownTime() {
        return cooldownTime;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    // =================== CLASES DE DATOS SIMPLIFICADAS ===================

    public static class SimpleRankData {
        private String id;
        private String displayName;
        private String nextRank;
        private int order;
        private Map<String, Object> requirements = new HashMap<>();
        private Map<String, Object> rewards = new HashMap<>();

        // Getters y Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getNextRank() {
            return nextRank;
        }

        public void setNextRank(String nextRank) {
            this.nextRank = nextRank;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public Map<String, Object> getRequirements() {
            return requirements;
        }

        public void setRequirements(Map<String, Object> requirements) {
            this.requirements = requirements;
        }

        public Map<String, Object> getRewards() {
            return rewards;
        }

        public void setRewards(Map<String, Object> rewards) {
            this.rewards = rewards;
        }

        public boolean hasNextRank() {
            return nextRank != null && !nextRank.isEmpty();
        }
    }

    // =================== CLASES DE RESULTADO ===================

    public static class RankupResult {
        private final boolean success;
        private final String message;

        public RankupResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class RequirementCheckResult {
        private final boolean success;
        private final List<String> failedRequirements;

        public RequirementCheckResult(boolean success, List<String> failedRequirements) {
            this.success = success;
            this.failedRequirements = failedRequirements != null ? failedRequirements : new ArrayList<>();
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getFailedRequirements() {
            return failedRequirements;
        }

        public String getFailureMessage() {
            return String.join("\n", failedRequirements);
        }
    }

    public static class RankupProgress {
        private final String currentRank;
        private final String nextRank;
        private final Map<String, RequirementProgress> requirements;
        private final double overallProgress;

        public RankupProgress(String currentRank, String nextRank,
                              Map<String, RequirementProgress> requirements, double overallProgress) {
            this.currentRank = currentRank;
            this.nextRank = nextRank;
            this.requirements = requirements;
            this.overallProgress = overallProgress;
        }

        public String getCurrentRank() {
            return currentRank;
        }

        public String getNextRank() {
            return nextRank;
        }

        public Map<String, RequirementProgress> getRequirements() {
            return requirements;
        }

        public double getOverallProgress() {
            return overallProgress;
        }
    }

    public void debugPlayerRankDetection(Player player) {
        plugin.getLogger().info("╔══════════════════════════════════════╗");
        plugin.getLogger().info("║      DEBUG DETECCIÓN DE RANGO       ║");
        plugin.getLogger().info("║ Jugador: " + String.format("%-24s", player.getName()) + " ║");
        plugin.getLogger().info("╠══════════════════════════════════════╣");

        try {
            if (luckPerms == null) {
                plugin.getLogger().info("║ ❌ LuckPerms: NO DISPONIBLE          ║");
                plugin.getLogger().info("╚══════════════════════════════════════╝");
                return;
            }

            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                plugin.getLogger().info("║ ❌ Usuario LuckPerms: NULL           ║");
                plugin.getLogger().info("╚══════════════════════════════════════╝");
                return;
            }

            // Información básica
            plugin.getLogger().info("║ ✅ LuckPerms: DISPONIBLE            ║");
            plugin.getLogger().info("║ Método detección: " + String.format("%-15s", detectionMethod) + " ║");
            plugin.getLogger().info("║ Prefijo grupo: '" + String.format("%-16s", groupPrefix) + "'║");
            plugin.getLogger().info("║ Rango por defecto: " + String.format("%-13s", defaultRank) + " ║");
            plugin.getLogger().info("╠══════════════════════════════════════╣");

            // Grupo primario
            String primaryGroup = user.getPrimaryGroup();
            plugin.getLogger().info("║ Grupo primario: " + String.format("%-17s", primaryGroup) + " ║");

            String processedPrimary = primaryGroup;
            if (!groupPrefix.isEmpty() && primaryGroup.startsWith(groupPrefix)) {
                processedPrimary = primaryGroup.substring(groupPrefix.length());
            }
            plugin.getLogger().info("║ Procesado: " + String.format("%-21s", processedPrimary) + " ║");
            plugin.getLogger().info("║ ¿Existe en config? " + String.format("%-14s", ranks.containsKey(processedPrimary)) + " ║");

            // Todos los grupos
            plugin.getLogger().info("╠══════════════════════════════════════╣");
            plugin.getLogger().info("║           TODOS LOS GRUPOS           ║");
            plugin.getLogger().info("╠══════════════════════════════════════╣");

            user.getInheritedGroups(user.getQueryOptions()).forEach(group -> {
                String name = group.getName();
                String processed = name;
                if (!groupPrefix.isEmpty() && name.startsWith(groupPrefix)) {
                    processed = name.substring(groupPrefix.length());
                }

                boolean exists = ranks.containsKey(processed);
                int order = exists ? ranks.get(processed).getOrder() : -1;

                plugin.getLogger().info("║ " + String.format("%-15s", name) + " → " + String.format("%-8s", processed) +
                        " [" + (exists ? "✓" : "✗") + "] " +
                        (exists ? "orden:" + order : "     ") + " ║");
            });

            // Rangos disponibles en configuración
            plugin.getLogger().info("╠══════════════════════════════════════╣");
            plugin.getLogger().info("║       RANGOS EN CONFIGURACIÓN       ║");
            plugin.getLogger().info("╠══════════════════════════════════════╣");

            ranks.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(SimpleRankData::getOrder)))
                    .forEach(entry -> {
                        String id = entry.getKey();
                        SimpleRankData data = entry.getValue();
                        plugin.getLogger().info("║ " + String.format("%-12s", id) + " orden:" + String.format("%2d", data.getOrder()) +
                                " → " + String.format("%-10s", data.getNextRank() != null ? data.getNextRank() : "FINAL") + " ║");
                    });

            // Resultado final
            plugin.getLogger().info("╠══════════════════════════════════════╣");
            String detectedRank = getCurrentRank(player);
            plugin.getLogger().info("║ 🎯 RANGO DETECTADO: " + String.format("%-13s", detectedRank) + " ║");
            plugin.getLogger().info("╚══════════════════════════════════════╝");

        } catch (Exception e) {
            plugin.getLogger().info("║ ❌ ERROR: " + String.format("%-24s", e.getMessage()) + " ║");
            plugin.getLogger().info("╚══════════════════════════════════════╝");
            e.printStackTrace();
        }
    }
    public static class RequirementProgress {
        private final String type;
        private final double current;
        private final double required;
        private final double percentage;
        private final boolean completed;

        public RequirementProgress(String type, double current, double required,
                                   double percentage, boolean completed) {
            this.type = type;
            this.current = current;
            this.required = required;
            this.percentage = percentage;
            this.completed = completed;
        }

        public String getType() {
            return type;
        }

        public double getCurrent() {
            return current;
        }

        public double getRequired() {
            return required;
        }

        public double getPercentage() {
            return percentage;
        }

        public boolean isCompleted() {
            return completed;
        }
    }

    public void showPlayerProgress(Player player) {
        showPlayerProgressWithPage(player, 1);
    }

    public void showPlayerProgressWithPage(Player player, int page) {
        getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    messageManager.sendProgressWithPagination(player, progress, page);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error mostrando progreso paginado: " + e.getMessage());

                    // Fallback a mensaje simple
                    player.sendMessage(ChatColor.RED + "❌ Error mostrando progreso detallado");
                    showSimpleProgress(player, progress);
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().severe("Error obteniendo progreso del jugador: " + throwable.getMessage());
                player.sendMessage(ChatColor.RED + "❌ Error obteniendo tu progreso. Contacta a un administrador.");
            });
            return null;
        });
    }

    public void showRanksList(Player player) {
        showRanksListWithPage(player, 1);
    }

    public void showRanksListWithPage(Player player, int page) {
        try {
            String currentRank = getCurrentRank(player);
            Map<String, SimpleRankData> allRanks = getRanks();

            messageManager.sendRanksListWithPagination(player, allRanks, currentRank, page);

        } catch (Exception e) {
            plugin.getLogger().severe("Error mostrando lista de rangos: " + e.getMessage());

            // Fallback a mensaje simple
            player.sendMessage(ChatColor.RED + "❌ Error mostrando lista de rangos");
            showSimpleRanksList(player);
        }
    }

    private void showSimpleProgress(Player player, RankupProgress progress) {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "📊 " + ChatColor.BOLD + "TU PROGRESO (MODO SIMPLE)");
        player.sendMessage("");

        if (progress.getCurrentRank() != null) {
            player.sendMessage(ChatColor.WHITE + "🎯 Rango actual: " + ChatColor.YELLOW + progress.getCurrentRank());
        }

        if (progress.getNextRank() != null) {
            player.sendMessage(ChatColor.WHITE + "⬆ Siguiente: " + ChatColor.GREEN + progress.getNextRank());

            double overallProgress = progress.getOverallProgress();
            String progressBar = createSimpleProgressBar(overallProgress);
            player.sendMessage(ChatColor.WHITE + "📈 Progreso: " + progressBar +
                    ChatColor.WHITE + " " + String.format("%.1f%%", overallProgress));

            player.sendMessage("");

            // Mostrar solo los primeros 3 requisitos
            int count = 0;
            for (Map.Entry<String, RequirementProgress> entry : progress.getRequirements().entrySet()) {
                if (count >= 3) break;

                RequirementProgress req = entry.getValue();
                String icon = req.isCompleted() ? ChatColor.GREEN + "✅" : ChatColor.RED + "❌";
                String name = messageManager.getRequirementName(entry.getKey());

                player.sendMessage(icon + ChatColor.WHITE + " " + name + ": " +
                        formatSimpleProgress(req.getCurrent(), req.getRequired()));
                count++;
            }

            if (progress.getRequirements().size() > 3) {
                int remaining = progress.getRequirements().size() - 3;
                player.sendMessage(ChatColor.GRAY + "... y " + remaining + " requisitos más");
            }

        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ¡Rango máximo alcanzado!");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 El sistema de paginación no está disponible temporalmente");
        player.sendMessage("");
    }

    private void showSimpleRanksList(Player player) {
        String currentRank = getCurrentRank(player);
        Map<String, SimpleRankData> allRanks = getRanks();

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "📋 " + ChatColor.BOLD + "RANGOS DEL SERVIDOR (MODO SIMPLE)");
        player.sendMessage("");

        // Mostrar solo los primeros 5 rangos
        List<SimpleRankData> sortedRanks = allRanks.values().stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .limit(5)
                .toList();

        for (SimpleRankData rank : sortedRanks) {
            String prefix;
            ChatColor nameColor;

            if (rank.getId().equals(currentRank)) {
                prefix = ChatColor.GREEN + "► ";
                nameColor = ChatColor.GREEN;
            } else if (currentRank != null) {
                SimpleRankData currentRankData = allRanks.get(currentRank);
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

            player.sendMessage(prefix + nameColor + rank.getDisplayName() +
                    ChatColor.GRAY + " (#" + (rank.getOrder() + 1) + ")");
        }

        if (allRanks.size() > 5) {
            player.sendMessage(ChatColor.GRAY + "... y " + (allRanks.size() - 5) + " rangos más");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "🎯 Tu rango: " +
                (currentRank != null ? getDisplayName(currentRank) : "Desconocido"));
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 El sistema de paginación no está disponible temporalmente");
        player.sendMessage("");
    }

    private String createSimpleProgressBar(double percentage) {
        int length = 20;
        int filled = (int) Math.round(percentage / 100.0 * length);
        filled = Math.max(0, Math.min(filled, length));

        StringBuilder bar = new StringBuilder();

        // Color según porcentaje
        if (percentage >= 100) bar.append(ChatColor.GREEN);
        else if (percentage >= 75) bar.append(ChatColor.YELLOW);
        else if (percentage >= 50) bar.append(ChatColor.GOLD);
        else if (percentage >= 25) bar.append(ChatColor.RED);
        else bar.append(ChatColor.DARK_RED);

        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("▓");
        }

        return bar.toString();
    }

    private String formatSimpleProgress(double current, double required) {
        if (current >= required) {
            return ChatColor.GREEN + String.format("%.0f/%.0f", current, required);
        } else {
            return ChatColor.GRAY + String.format("%.0f", current) + ChatColor.RED + "/" +
                    ChatColor.GRAY + String.format("%.0f", required);
        }
    }

    /**
     * Obtiene estadísticas del sistema de menús
     */
    public Map<String, Object> getMenuStats() {
        Map<String, Object> stats = new HashMap<>();

        // Estadísticas básicas sin sistema de menús complejo
        stats.put("cachedMenus", 0);
        stats.put("playerSettings", 0);
        stats.put("autoRefreshInterval", 0);
        stats.put("menuSystemType", "BASIC_COMMANDS");

        return stats;
    }

    /**
     * Verifica si el sistema de menús está disponible
     */
    public boolean isMenuSystemAvailable() {
        // Sin MenuManager complejo, solo comandos básicos
        return false;
    }

    /**
     * Limpia datos de menú de un jugador (compatibilidad)
     */
    public void cleanupPlayerMenuData(Player player) {
        // Sin sistema de menús complejo, solo limpiar cooldowns si es necesario
        // Los cooldowns se mantienen ya que son parte del sistema de rankup

        if (debugMode) {
            plugin.getLogger().info("🧹 Limpieza básica para jugador: " + player.getName());
        }
    }

    /**
     * Obtiene el MenuManager (null ya que no usamos sistema híbrido)
     */
    public Object getMenuManager() {
        // Sin sistema híbrido, devolver null
        return null;
    }


    /**
     * Método de shutdown para limpieza (compatibilidad con Main.java)
     */
    public void shutdown() {
        try {
            plugin.getLogger().info("🔄 Finalizando sistema de Rankup 2.0...");

            // Limpiar cooldowns
            cooldowns.clear();

            // Limpiar caché de rangos si existe
            ranks.clear();

            plugin.getLogger().info("✅ Sistema de Rankup 2.0 finalizado correctamente");

        } catch (Exception e) {
            plugin.getLogger().warning("Error finalizando sistema de Rankup: " + e.getMessage());
        }
    }

    /**
     * 🆕 NUEVO: Método para configurar paginación dinámicamente
     */
    public void configurePagination(int requirementsPerPage, int ranksPerPage) {
        if (messageManager != null) {
            messageManager.setMaxRequirementsPerPage(requirementsPerPage);
            messageManager.setMaxRanksPerPage(ranksPerPage);

            plugin.getLogger().info("📄 Paginación configurada: " +
                    requirementsPerPage + " requisitos, " +
                    ranksPerPage + " rangos por página");
        }
    }

    /**
     * 🆕 NUEVO: Método para obtener información de paginación
     */
    public Map<String, Object> getPaginationInfo() {
        Map<String, Object> info = new HashMap<>();

        if (messageManager != null) {
            Map<String, Object> messageStats = messageManager.getStats();
            info.put("requirements_per_page", messageStats.get("max_requirements_per_page"));
            info.put("ranks_per_page", messageStats.get("max_ranks_per_page"));
            info.put("compact_mode", messageStats.get("compact_mode_enabled"));
            info.put("navigation_enabled", messageStats.get("navigation_enabled"));
        } else {
            info.put("status", "MESSAGE_MANAGER_NOT_AVAILABLE");
        }

        return info;
    }

    /**
     * 🆕 NUEVO: Debug específico para el sistema de paginación
     */
    public void debugPaginationSystem(Player admin) {
        admin.sendMessage("");
        admin.sendMessage(ChatColor.AQUA + "🔍 " + ChatColor.BOLD + "DEBUG SISTEMA DE PAGINACIÓN");
        admin.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");

        try {
            Map<String, Object> paginationInfo = getPaginationInfo();

            if (paginationInfo.containsKey("status")) {
                admin.sendMessage(ChatColor.RED + "❌ MessageManager no disponible");
                return;
            }

            admin.sendMessage(ChatColor.WHITE + "📄 Configuración de paginación:");
            admin.sendMessage(ChatColor.WHITE + "  • Requisitos por página: " +
                    ChatColor.YELLOW + paginationInfo.get("requirements_per_page"));
            admin.sendMessage(ChatColor.WHITE + "  • Rangos por página: " +
                    ChatColor.YELLOW + paginationInfo.get("ranks_per_page"));
            admin.sendMessage(ChatColor.WHITE + "  • Modo compacto: " +
                    (((Boolean) paginationInfo.get("compact_mode")) ?
                            ChatColor.GREEN + "Activo" : ChatColor.RED + "Inactivo"));
            admin.sendMessage(ChatColor.WHITE + "  • Navegación: " +
                    (((Boolean) paginationInfo.get("navigation_enabled")) ?
                            ChatColor.GREEN + "Habilitada" : ChatColor.RED + "Deshabilitada"));

            // Test de paginación
            admin.sendMessage("");
            admin.sendMessage(ChatColor.AQUA + "🧪 Test de paginación:");

            // Simular progreso con muchos requisitos
            int totalRequirements = 8; // Simular 8 requisitos
            int reqsPerPage = (Integer) paginationInfo.get("requirements_per_page");
            int totalPages = (int) Math.ceil((double) totalRequirements / reqsPerPage);

            admin.sendMessage(ChatColor.WHITE + "  • Total requisitos simulados: " + ChatColor.YELLOW + totalRequirements);
            admin.sendMessage(ChatColor.WHITE + "  • Páginas necesarias: " + ChatColor.YELLOW + totalPages);

            // Simular rangos
            int totalRanks = ranks.size();
            int ranksPerPage = (Integer) paginationInfo.get("ranks_per_page");
            int rankPages = (int) Math.ceil((double) totalRanks / ranksPerPage);

            admin.sendMessage(ChatColor.WHITE + "  • Total rangos: " + ChatColor.YELLOW + totalRanks);
            admin.sendMessage(ChatColor.WHITE + "  • Páginas de rangos: " + ChatColor.YELLOW + rankPages);

            admin.sendMessage("");
            admin.sendMessage(ChatColor.GREEN + "✅ Sistema de paginación funcionando correctamente");

        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "❌ Error en debug de paginación: " + e.getMessage());
            e.printStackTrace();
        }

        admin.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");
    }

    private boolean forceSetPlayerRank(Player player, String targetRank) {
        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) return false;

            // Remover TODOS los rangos conocidos
            for (String rankId : ranks.keySet()) {
                String groupName = groupPrefix.isEmpty() ? rankId : groupPrefix + rankId;
                user.data().remove(InheritanceNode.builder(groupName).build());
            }

            // Añadir el rango objetivo
            String targetGroup = groupPrefix.isEmpty() ? targetRank : groupPrefix + targetRank;
            user.data().add(InheritanceNode.builder(targetGroup).build());

            // Guardar y esperar confirmación
            luckPerms.getUserManager().saveUser(user).join();

            plugin.getLogger().info("🔧 Forzada asignación de rango: " + player.getName() + " → " + targetRank);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error forzando asignación de rango: " + e.getMessage());
            return false;
        }
    }

    public void verifyPlayerRankConsistency(Player player) {
        try {
            plugin.getLogger().info("🔍 Verificando consistencia de rango para " + player.getName());

            // Obtener rango según nuestro sistema
            String detectedRank = getCurrentRank(player);

            // Obtener grupos reales en LuckPerms
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                plugin.getLogger().warning("⚠️ Usuario LuckPerms null para verificación");
                return;
            }

            String primaryGroup = user.getPrimaryGroup();
            java.util.Set<String> allGroups = user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .map(group -> group.getName())
                    .collect(java.util.stream.Collectors.toSet());

            plugin.getLogger().info("📊 Estado actual:");
            plugin.getLogger().info("  • Rango detectado por sistema: " + detectedRank);
            plugin.getLogger().info("  • Grupo primario LuckPerms: " + primaryGroup);
            plugin.getLogger().info("  • Todos los grupos: " + allGroups);

            // Verificar si hay inconsistencias
            String expectedGroup = groupPrefix.isEmpty() ? detectedRank : groupPrefix + detectedRank;

            if (!allGroups.contains(expectedGroup)) {
                plugin.getLogger().warning("🚨 INCONSISTENCIA DETECTADA:");
                plugin.getLogger().warning("  • Esperado: " + expectedGroup);
                plugin.getLogger().warning("  • Grupos reales: " + allGroups);

                // Sugerir corrección
                plugin.getLogger().info("💡 Para corregir, usa: /lp user " + player.getName() + " parent add " + expectedGroup);
            } else {
                plugin.getLogger().info("✅ Consistencia verificada - Todo correcto");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error verificando consistencia: " + e.getMessage());
        }
    }
// =================== CORRECCIÓN 5: MÉTODO debugPlayerRankDetection() CORREGIDO ===================
    /**
     * 🔧 Debug de detección de rango - CORREGIDO
     */

    }