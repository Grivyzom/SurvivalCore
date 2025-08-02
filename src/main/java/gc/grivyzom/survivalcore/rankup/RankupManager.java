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
            groupPrefix = config.getString("luckperms.group_prefix", "group.");
            defaultRank = config.getString("luckperms.default_rank", "default");

            // Limpiar y cargar rangos
            ranks.clear();
            loadSimpleRanks();

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
     * Intenta hacer rankup de forma simplificada
     */
    public CompletableFuture<RankupResult> attemptRankup(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();

            // Verificar cooldown
            if (isOnCooldown(uuid)) {
                long remaining = getRemainingCooldown(uuid);
                return new RankupResult(false, formatMessage("cooldown",
                        Map.of("seconds", String.valueOf(remaining / 1000))));
            }

            // Obtener rango actual
            String currentRank = getCurrentRank(player);
            if (currentRank == null) {
                return new RankupResult(false, "❌ No se pudo determinar tu rango actual");
            }

            SimpleRankData rankData = ranks.get(currentRank);
            if (rankData == null || rankData.getNextRank() == null) {
                return new RankupResult(false, formatMessage("max_rank", Map.of()));
            }

            // Verificar requisitos
            RequirementCheckResult check = checkAllRequirements(player, rankData);
            if (!check.isSuccess()) {
                return new RankupResult(false, formatMessage("failed", Map.of()) + "\n" + check.getFailureMessage());
            }

            // Realizar rankup
            if (performRankupProcess(player, currentRank, rankData.getNextRank(), rankData)) {
                setCooldown(uuid);
                return new RankupResult(true, formatMessage("success",
                        Map.of("new_rank", getDisplayName(rankData.getNextRank()))));
            } else {
                return new RankupResult(false, "❌ Error interno realizando rankup");
            }
        });
    }

    /**
     * Verifica todos los requisitos de forma eficiente
     */
    private RequirementCheckResult checkAllRequirements(Player player, SimpleRankData rankData) {
        Map<String, Object> requirements = rankData.getRequirements();
        List<String> failures = new ArrayList<>();

        if (debugMode) {
            plugin.getLogger().info("🔍 Verificando " + requirements.size() + " requisitos para " + player.getName());
        }

        for (Map.Entry<String, Object> req : requirements.entrySet()) {
            String type = req.getKey();
            Object required = req.getValue();

            if (!checkSingleRequirement(player, type, required)) {
                String message = formatRequirementFailure(player, type, required);
                failures.add(message);

                if (debugMode) {
                    plugin.getLogger().info("  ❌ " + type + ": " + message);
                }
            } else if (debugMode) {
                plugin.getLogger().info("  ✅ " + type + ": cumplido");
            }
        }

        if (failures.isEmpty()) {
            return new RequirementCheckResult(true, "");
        } else {
            return new RequirementCheckResult(false, String.join("\n", failures));
        }
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
            admin.sendMessage(ChatColor.RED + "❌ Error: No se pudo detectar el rango");
            return;
        }

        SimpleRankData rankData = ranks.get(currentRank);
        if (rankData == null) {
            admin.sendMessage(ChatColor.RED + "❌ Error: No hay datos para el rango " + currentRank);
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
        Map<String, Object> requirements = rankData.getRequirements();

        for (Map.Entry<String, Object> req : requirements.entrySet()) {
            String type = req.getKey();
            double required = ((Number) req.getValue()).doubleValue();
            double current = getCurrentRequirementValue(player, type);
            boolean met = current >= required;

            String status = met ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
            admin.sendMessage("  " + status + ChatColor.WHITE + " " + type +
                    ": " + ChatColor.YELLOW + formatValue(type, current) +
                    ChatColor.GRAY + "/" + ChatColor.GREEN + formatValue(type, required));
        }

        if (isOnCooldown(player.getUniqueId())) {
            long remaining = getRemainingCooldown(player.getUniqueId());
            admin.sendMessage(ChatColor.YELLOW + "⏰ Cooldown: " + (remaining / 1000) + "s restantes");
        } else {
            admin.sendMessage(ChatColor.GREEN + "✅ Sin cooldown activo");
        }
    }

    // =================== GETTERS Y CONFIGURACIÓN ===================

    public void reloadConfig() {
        try {
            checkPlaceholderAPI();
            loadConfiguration();
            plugin.getLogger().info("✅ Configuración de Rankup recargada");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error recargando configuración: " + e.getMessage());
            throw new RuntimeException(e);
        }
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
        private final String failureMessage;

        public RequirementCheckResult(boolean success, String failureMessage) {
            this.success = success;
            this.failureMessage = failureMessage;
        }

        public boolean isSuccess() { return success; }
        public String getFailureMessage() { return failureMessage; }
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
}