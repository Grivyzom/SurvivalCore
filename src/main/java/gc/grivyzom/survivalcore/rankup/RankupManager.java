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
            groupPrefix = config.getString("luckperms.group_prefix", "");  // 🔧 CORREGIDO: vacío por defecto
            defaultRank = config.getString("luckperms.default_rank", "default");

            // Limpiar y cargar rangos
            ranks.clear();
            loadSimpleRanks();

            // 🔧 INICIALIZAR MessageManager AQUÍ
            this.messageManager = new MessageManager(plugin, config);

            if (config.getBoolean("advanced.validate_config", true)) {
                validateConfiguration();
            }

            plugin.getLogger().info("📊 Cargados " + ranks.size() + " rangos.");

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error cargando configuración: " + e.getMessage());
            throw new RuntimeException(e);
        }
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
                    plugin.getLogger().info("✓ Rango cargado: " + rankId + " (orden: " + rank.getOrder() + ")");
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
        return switch (key.toLowerCase()) {
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
            default -> key.replace("_", " ");
        };
    }


    /**
     * Verifica un requisito individual
     */
    private boolean checkSingleRequirement(Player player, String type, Object required) {
        try {
            double requiredValue = ((Number) required).doubleValue();
            double currentValue = getCurrentRequirementValue(player, type);

            return currentValue >= requiredValue;

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("Error verificando requisito " + type + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Obtiene el valor actual de un requisito
     */
    private double getCurrentRequirementValue(Player player, String type) {
        return switch (type) {
            case "money" -> getPlayerMoney(player);
            case "level" -> player.getLevel();
            case "playtime_hours" -> getPlaytimeHours(player);
            case "mob_kills" -> player.getStatistic(org.bukkit.Statistic.MOB_KILLS);
            case "blocks_mined" -> player.getStatistic(org.bukkit.Statistic.MINE_BLOCK);
            case "farming_level" -> getFarmingLevel(player);
            case "mining_level" -> getMiningLevel(player);
            case "animals_bred" -> player.getStatistic(org.bukkit.Statistic.ANIMALS_BRED);
            case "fish_caught" -> player.getStatistic(org.bukkit.Statistic.FISH_CAUGHT);
            case "ender_dragon_kills" -> getEntityKills(player, "ENDER_DRAGON");
            case "wither_kills" -> getEntityKills(player, "WITHER");
            default -> handleCustomRequirement(player, type);
        };
    }

    /**
     * Obtiene el rango actual de forma más robusta
     */
    public String getCurrentRank(Player player) {
        if (luckPerms == null) return defaultRank;

        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) return defaultRank;

            if ("primary_group".equals(detectionMethod)) {
                String primaryGroup = user.getPrimaryGroup();
                String rankId = primaryGroup.startsWith(groupPrefix) ?
                        primaryGroup.substring(groupPrefix.length()) : primaryGroup;

                if (ranks.containsKey(rankId)) {
                    return rankId;
                }
            }

            // Fallback: buscar el rango de mayor orden
            String highestRank = user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .map(group -> {
                        String name = group.getName();
                        return name.startsWith(groupPrefix) ? name.substring(groupPrefix.length()) : name;
                    })
                    .filter(ranks::containsKey)
                    .max(Comparator.comparingInt(rankId -> ranks.get(rankId).getOrder()))
                    .orElse(defaultRank);

            if (debugMode) {
                plugin.getLogger().info("🎯 Rango detectado para " + player.getName() + ": " + highestRank);
            }

            return highestRank;

        } catch (Exception e) {
            plugin.getLogger().warning("Error detectando rango de " + player.getName() + ": " + e.getMessage());
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
                return false;
            }

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
            return false;
        }
    }

    /**
     * Actualiza el grupo del jugador en LuckPerms
     */
    private boolean updatePlayerGroup(Player player, String fromRank, String toRank) {
        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) return false;

            // Remover grupo anterior
            String oldGroup = groupPrefix + fromRank;
            user.data().remove(InheritanceNode.builder(oldGroup).build());

            // Añadir nuevo grupo
            String newGroup = groupPrefix + toRank;
            user.data().add(InheritanceNode.builder(newGroup).build());

            // Guardar cambios
            luckPerms.getUserManager().saveUser(user).join();

            if (debugMode) {
                plugin.getLogger().info("✅ Grupo actualizado: " + oldGroup + " -> " + newGroup);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error actualizando grupo: " + e.getMessage());
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

    private double handleCustomRequirement(Player player, String type) {
        if (!placeholderAPIEnabled) return 0;

        try {
            // Buscar en requisitos personalizados
            String placeholderTemplate = config.getString("custom_requirements." + type);
            if (placeholderTemplate == null) return 0;

            String placeholder = placeholderTemplate.replace("{value}", "0");
            String result = PlaceholderAPI.setPlaceholders(player, placeholder);

            return Double.parseDouble(result.replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("Error en requisito personalizado " + type + ": " + e.getMessage());
            }
            return 0;
        }
    }

    private String formatRequirementFailure(Player player, String type, Object required) {
        double requiredValue = ((Number) required).doubleValue();
        double currentValue = getCurrentRequirementValue(player, type);

        String messageKey = "messages.requirements." + type;
        String template = config.getString(messageKey, "&7• " + type + ": &c{current}&7/&a{required}");

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
                String title = ChatColor.translateAlternateColorCodes('&',
                        effectsConfig.getString("title.title", "&6&l¡RANKUP!"));
                String subtitle = ChatColor.translateAlternateColorCodes('&',
                        effectsConfig.getString("title.subtitle", "&fAhora eres {new_rank}")
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
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getNextRank() { return nextRank; }
        public void setNextRank(String nextRank) { this.nextRank = nextRank; }

        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }

        public Map<String, Object> getRequirements() { return requirements; }
        public void setRequirements(Map<String, Object> requirements) { this.requirements = requirements; }

        public Map<String, Object> getRewards() { return rewards; }
        public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }

        public boolean hasNextRank() { return nextRank != null && !nextRank.isEmpty(); }
    }

    // =================== CLASES DE RESULTADO ===================

    public static class RankupResult {
        private final boolean success;
        private final String message;

        public RankupResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class RequirementCheckResult {
        private final boolean success;
        private final List<String> failedRequirements;

        public RequirementCheckResult(boolean success, List<String> failedRequirements) {
            this.success = success;
            this.failedRequirements = failedRequirements != null ? failedRequirements : new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public List<String> getFailedRequirements() { return failedRequirements; }
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

        public String getCurrentRank() { return currentRank; }
        public String getNextRank() { return nextRank; }
        public Map<String, RequirementProgress> getRequirements() { return requirements; }
        public double getOverallProgress() { return overallProgress; }
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

        public String getType() { return type; }
        public double getCurrent() { return current; }
        public double getRequired() { return required; }
        public double getPercentage() { return percentage; }
        public boolean isCompleted() { return completed; }
    }

    public void showPlayerProgress(Player player) {
        getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Enviar cabecera
                messageManager.sendProgressHeader(player,
                        progress.getCurrentRank(),
                        progress.getNextRank(),
                        progress.getOverallProgress());

                if (progress.getNextRank() == null) {
                    // Rango máximo alcanzado
                    messageManager.sendMaxRankMessage(player, progress.getCurrentRank());
                    return;
                }

                // Mostrar requisitos
                int completed = 0;
                int total = progress.getRequirements().size();

                for (Map.Entry<String, RequirementProgress> entry : progress.getRequirements().entrySet()) {
                    RequirementProgress reqProgress = entry.getValue();
                    String reqName = messageManager.getRequirementName(entry.getKey());

                    messageManager.sendProgressRequirement(player,
                            reqName,
                            reqProgress.getCurrent(),
                            reqProgress.getRequired(),
                            reqProgress.isCompleted());

                    if (reqProgress.isCompleted()) completed++;
                }

                // Enviar pie
                String status = completed == total ?
                        messageManager.getStatusMessage("ready") :
                        messageManager.getStatusMessage("missing_requirements")
                                .replace("{count}", String.valueOf(total - completed));

                messageManager.sendProgressFooter(player, status, completed, total - completed);
            });
        });
    }

    public void showRanksList(Player player) {
        String currentRank = getCurrentRank(player);
        Map<String, SimpleRankData> allRanks = getRanks();

        // Enviar cabecera
        messageManager.sendRankListHeader(player);

        // Ordenar rangos
        List<SimpleRankData> sortedRanks = allRanks.values().stream()
                .sorted(Comparator.comparingInt(SimpleRankData::getOrder))
                .collect(Collectors.toList());

        // Mostrar cada rango
        for (SimpleRankData rank : sortedRanks) {
            MessageManager.RankStatus status;

            if (rank.getId().equals(currentRank)) {
                status = MessageManager.RankStatus.CURRENT;
            } else if (currentRank != null) {
                SimpleRankData currentRankData = allRanks.get(currentRank);
                if (currentRankData != null && rank.getOrder() < currentRankData.getOrder()) {
                    status = MessageManager.RankStatus.COMPLETED;
                } else {
                    status = MessageManager.RankStatus.LOCKED;
                }
            } else {
                status = MessageManager.RankStatus.LOCKED;
            }

            messageManager.sendRankLine(player, rank.getDisplayName(), rank.getOrder(), status);
        }

        // Enviar pie
        int currentPosition = currentRank != null && allRanks.containsKey(currentRank) ?
                allRanks.get(currentRank).getOrder() + 1 : 1;

        messageManager.sendRankListFooter(player,
                currentRank != null ? getDisplayName(currentRank) : "Desconocido",
                currentPosition,
                allRanks.size());
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

}