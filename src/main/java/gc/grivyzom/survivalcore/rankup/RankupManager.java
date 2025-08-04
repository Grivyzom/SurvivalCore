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
import net.luckperms.api.node.NodeEqualityPredicate;

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
        List<String> warnings = new ArrayList<>();

        // Verificar que existe rango por defecto
        if (!ranks.containsKey(defaultRank)) {
            errors.add("Rango por defecto '" + defaultRank + "' no existe en configuración");
        } else {
            // Verificar que el grupo por defecto existe en LuckPerms
            String defaultGroupName = groupPrefix.isEmpty() ? defaultRank : groupPrefix + defaultRank;
            if (!groupExists(defaultGroupName)) {
                errors.add("Grupo por defecto '" + defaultGroupName + "' no existe en LuckPerms");
            }
        }

        // Verificar cadena de rangos y grupos en LuckPerms
        for (SimpleRankData rank : ranks.values()) {
            String rankId = rank.getId();
            String groupName = groupPrefix.isEmpty() ? rankId : groupPrefix + rankId;

            // Verificar que el grupo existe en LuckPerms
            if (!groupExists(groupName)) {
                errors.add("Grupo '" + groupName + "' para rango '" + rankId + "' no existe en LuckPerms");
            }

            // Verificar siguiente rango
            String nextRank = rank.getNextRank();
            if (nextRank != null) {
                if (!ranks.containsKey(nextRank)) {
                    errors.add("Rango '" + rankId + "' apunta a rango inexistente: " + nextRank);
                } else {
                    // Verificar que el siguiente grupo existe en LuckPerms
                    String nextGroupName = groupPrefix.isEmpty() ? nextRank : groupPrefix + nextRank;
                    if (!groupExists(nextGroupName)) {
                        errors.add("Siguiente grupo '" + nextGroupName + "' para rango '" + nextRank + "' no existe en LuckPerms");
                    }
                }
            }
        }

        // Verificar orden de rangos (no debe haber duplicados)
        Map<Integer, String> orderMap = new HashMap<>();
        for (SimpleRankData rank : ranks.values()) {
            int order = rank.getOrder();
            if (orderMap.containsKey(order)) {
                warnings.add("Orden " + order + " duplicado entre rangos '" + orderMap.get(order) + "' y '" + rank.getId() + "'");
            } else {
                orderMap.put(order, rank.getId());
            }
        }

        // Verificar PlaceholderAPI si es necesario
        List<String> requiresPAPI = config.getStringList("advanced.requires_placeholderapi");
        if (!requiresPAPI.isEmpty() && !placeholderAPIEnabled) {
            warnings.add("Algunos requisitos requieren PlaceholderAPI pero no está disponible");
        }

        // Mostrar errores
        if (!errors.isEmpty()) {
            plugin.getLogger().severe("❌ ERRORES CRÍTICOS de configuración:");
            errors.forEach(error -> plugin.getLogger().severe("  • " + error));
            plugin.getLogger().severe("");
            plugin.getLogger().severe("🔧 SOLUCIONES SUGERIDAS:");
            plugin.getLogger().severe("  1. Verifica que todos los grupos estén creados en LuckPerms:");
            ranks.values().forEach(rank -> {
                String groupName = groupPrefix.isEmpty() ? rank.getId() : groupPrefix + rank.getId();
                plugin.getLogger().severe("     /lp creategroup " + groupName);
            });
            plugin.getLogger().severe("  2. Verifica que el prefix en rankups.yml coincida con LuckPerms");
            plugin.getLogger().severe("  3. Usa /score debug rankup [jugador] para más información");

            throw new RuntimeException("Errores críticos en configuración de rangos - revisa los logs");
        }

        // Mostrar advertencias
        if (!warnings.isEmpty()) {
            plugin.getLogger().warning("⚠️ Advertencias de configuración:");
            warnings.forEach(warning -> plugin.getLogger().warning("  • " + warning));
        }

        if (debugMode) {
            plugin.getLogger().info("✅ Configuración validada correctamente");
            plugin.getLogger().info("📊 Resumen de validación:");
            plugin.getLogger().info("  • Rangos verificados: " + ranks.size());
            plugin.getLogger().info("  • Errores: " + errors.size());
            plugin.getLogger().info("  • Advertencias: " + warnings.size());
        }
    }
    /**
     * Intenta hacer rankup de forma simplificada
     */
    public CompletableFuture<RankupResult> attemptRankup(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();

            if (debugMode) {
                plugin.getLogger().info("🚀 Iniciando rankup para " + player.getName());
            }

            // Verificar cooldown
            if (isOnCooldown(uuid)) {
                long remaining = getRemainingCooldown(uuid);
                return new RankupResult(false, formatMessage("cooldown",
                        Map.of("seconds", String.valueOf(remaining / 1000))));
            }

            // Obtener rango actual con retry
            String currentRank = getCurrentRankWithRetry(player, 3);
            if (currentRank == null) {
                plugin.getLogger().warning("❌ No se pudo determinar el rango de " + player.getName());
                return new RankupResult(false, "❌ Error: No se pudo determinar tu rango actual. Contacta a un administrador.");
            }

            if (debugMode) {
                plugin.getLogger().info("🎯 Rango actual detectado: " + currentRank);
            }

            SimpleRankData rankData = ranks.get(currentRank);
            if (rankData == null) {
                plugin.getLogger().warning("❌ No hay datos para el rango: " + currentRank);
                return new RankupResult(false, "❌ Error: No hay configuración para tu rango actual (" + currentRank + ")");
            }

            if (rankData.getNextRank() == null) {
                return new RankupResult(false, formatMessage("max_rank", Map.of()));
            }

            if (debugMode) {
                plugin.getLogger().info("➡️ Siguiente rango: " + rankData.getNextRank());
            }

            // Verificar requisitos con timeout más largo
            try {
                RequirementCheckResult check = checkAllRequirementsWithTimeout(player, rankData, 10); // 10 segundos timeout

                if (!check.isSuccess()) {
                    if (debugMode) {
                        plugin.getLogger().info("❌ Requisitos no cumplidos para " + player.getName());
                    }
                    return new RankupResult(false, formatMessage("failed", Map.of()) + "\n" + check.getFailureMessage());
                }

                // Realizar rankup
                if (performRankupProcess(player, currentRank, rankData.getNextRank(), rankData)) {
                    setCooldown(uuid);

                    String successMessage = formatMessage("success",
                            Map.of("new_rank", getDisplayName(rankData.getNextRank())));

                    if (debugMode) {
                        plugin.getLogger().info("✅ Rankup exitoso: " + player.getName() + " → " + rankData.getNextRank());
                    }

                    return new RankupResult(true, successMessage);
                } else {
                    plugin.getLogger().severe("❌ Error interno en rankup de " + player.getName());
                    return new RankupResult(false, "❌ Error interno realizando rankup. Contacta a un administrador.");
                }

            } catch (Exception e) {
                plugin.getLogger().severe("❌ Error crítico en rankup de " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                return new RankupResult(false, "❌ Error crítico realizando rankup. Contacta a un administrador.");
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("❌ Error asíncrono en rankup de " + player.getName() + ": " + throwable.getMessage());
            return new RankupResult(false, "❌ Error procesando rankup. Intenta de nuevo en unos segundos.");
        });
    }

    private String getCurrentRankWithRetry(Player player, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String rank = getCurrentRank(player);
                if (rank != null) {
                    return rank;
                }

                if (debugMode) {
                    plugin.getLogger().info("🔄 Intento " + attempt + "/" + maxRetries + " para detectar rango de " + player.getName());
                }

                // Esperar un poco antes del siguiente intento
                Thread.sleep(500);

            } catch (Exception e) {
                if (debugMode) {
                    plugin.getLogger().warning("❌ Error en intento " + attempt + " para " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        plugin.getLogger().warning("❌ No se pudo detectar rango de " + player.getName() + " después de " + maxRetries + " intentos");
        return defaultRank; // Fallback al rango por defecto
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

                // 🔧 CORRECCIÓN: Manejar tanto con prefijo como sin prefijo
                String rankId;
                if (groupPrefix.isEmpty()) {
                    // Sin prefijo: el grupo ES el rango directamente
                    rankId = primaryGroup;
                } else {
                    // Con prefijo: quitar el prefijo del grupo
                    rankId = primaryGroup.startsWith(groupPrefix) ?
                            primaryGroup.substring(groupPrefix.length()) : primaryGroup;
                }

                if (debugMode) {
                    plugin.getLogger().info("🔍 Debug detección de rango:");
                    plugin.getLogger().info("  • Grupo primario: " + primaryGroup);
                    plugin.getLogger().info("  • Prefijo configurado: '" + groupPrefix + "'");
                    plugin.getLogger().info("  • Rango extraído: " + rankId);
                    plugin.getLogger().info("  • ¿Existe en configuración? " + ranks.containsKey(rankId));
                }

                if (ranks.containsKey(rankId)) {
                    return rankId;
                } else {
                    plugin.getLogger().warning("⚠️ Rango '" + rankId + "' no encontrado en configuración. Rangos disponibles: " + ranks.keySet());
                }
            }

            // Fallback: buscar el rango de mayor orden entre todos los grupos del jugador
            String highestRank = user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .map(group -> {
                        String name = group.getName();
                        if (groupPrefix.isEmpty()) {
                            return name;
                        } else {
                            return name.startsWith(groupPrefix) ? name.substring(groupPrefix.length()) : name;
                        }
                    })
                    .filter(ranks::containsKey)
                    .max(Comparator.comparingInt(rankId -> ranks.get(rankId).getOrder()))
                    .orElse(defaultRank);

            if (debugMode) {
                plugin.getLogger().info("🎯 Rango final detectado para " + player.getName() + ": " + highestRank);
            }

            return highestRank;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error detectando rango de " + player.getName() + ": " + e.getMessage());
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
            if (user == null) {
                plugin.getLogger().severe("❌ Usuario LuckPerms no encontrado para " + player.getName());
                return false;
            }

            // 🔧 CORRECCIÓN: Construir nombres de grupos correctamente
            String oldGroup, newGroup;

            if (groupPrefix.isEmpty()) {
                // Sin prefijo: el grupo ES el rango directamente
                oldGroup = fromRank;
                newGroup = toRank;
            } else {
                // Con prefijo: agregar prefijo al rango
                oldGroup = groupPrefix + fromRank;
                newGroup = groupPrefix + toRank;
            }

            if (debugMode) {
                plugin.getLogger().info("🔄 Actualizando grupos de " + player.getName() + ":");
                plugin.getLogger().info("  • Removiendo: " + oldGroup);
                plugin.getLogger().info("  • Agregando: " + newGroup);
                plugin.getLogger().info("  • Prefijo: '" + groupPrefix + "'");
            }

            // Verificar que los grupos existen en LuckPerms
            if (!groupExists(oldGroup)) {
                plugin.getLogger().warning("⚠️ Grupo origen '" + oldGroup + "' no existe en LuckPerms");
            }

            if (!groupExists(newGroup)) {
                plugin.getLogger().severe("❌ Grupo destino '" + newGroup + "' no existe en LuckPerms");
                return false;
            }

            // 🔧 CORRECCIÓN PRINCIPAL: Usar API actualizada de LuckPerms
            InheritanceNode oldNode = InheritanceNode.builder(oldGroup).build();

            // ANTES (INCORRECTO):
            // if (user.data().contains(oldNode).asBoolean()) {

            // DESPUÉS (CORRECTO):
            if (user.data().contains(oldNode, NodeEqualityPredicate.ONLY_KEY).asBoolean()) {
                user.data().remove(oldNode);
                if (debugMode) {
                    plugin.getLogger().info("✅ Grupo '" + oldGroup + "' removido exitosamente");
                }
            } else {
                plugin.getLogger().warning("⚠️ El jugador no tenía el grupo '" + oldGroup + "'");
            }

            // Añadir nuevo grupo
            InheritanceNode newNode = InheritanceNode.builder(newGroup).build();
            user.data().add(newNode);

            // 🆕 NUEVO: Establecer como grupo primario para asegurar detección correcta
            if ("primary_group".equals(detectionMethod)) {
                user.setPrimaryGroup(newGroup);
                if (debugMode) {
                    plugin.getLogger().info("✅ '" + newGroup + "' establecido como grupo primario");
                }
            }

            // Guardar cambios - MEJORADO con verificación
            try {
                luckPerms.getUserManager().saveUser(user).join();

                // Verificar que el cambio se aplicó correctamente
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    User updatedUser = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                    if (updatedUser != null) {
                        String verifyRank = getCurrentRank(player);
                        if (toRank.equals(verifyRank)) {
                            plugin.getLogger().info("✅ Rankup verificado: " + player.getName() + " ahora es " + toRank);
                        } else {
                            plugin.getLogger().warning("⚠️ Verificación fallida: se esperaba " + toRank + " pero se detectó " + verifyRank);
                        }
                    }
                }, 5L); // Verificar después de 5 ticks

            } catch (Exception saveError) {
                plugin.getLogger().severe("❌ Error guardando cambios en LuckPerms: " + saveError.getMessage());
                return false;
            }

            if (debugMode) {
                plugin.getLogger().info("✅ Grupos actualizados exitosamente: " + oldGroup + " → " + newGroup);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error crítico actualizando grupo de " + player.getName() + ": " + e.getMessage());
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

    private boolean groupExists(String groupName) {
        try {
            return luckPerms.getGroupManager().getGroup(groupName) != null;
        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("Error verificando grupo '" + groupName + "': " + e.getMessage());
            }
            return false;
        }
    }

    private double getPlayerMoney(Player player) {
        // Verificar si Vault está disponible
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            if (debugMode) {
                plugin.getLogger().warning("⚠️ Vault no está disponible para verificar dinero de " + player.getName());
            }
            return 0.0;
        }

        try {
            // Intentar obtener el balance usando Vault
            org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);

            if (rsp == null) {
                if (debugMode) {
                    plugin.getLogger().warning("⚠️ Proveedor de economía no encontrado para " + player.getName());
                }
                return 0.0;
            }

            net.milkbowl.vault.economy.Economy economy = rsp.getProvider();
            if (economy == null) {
                return 0.0;
            }

            double balance = economy.getBalance(player);

            if (debugMode) {
                plugin.getLogger().info("💰 Balance de " + player.getName() + ": $" + balance);
            }

            return balance;

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Error obteniendo dinero de " + player.getName() + ": " + e.getMessage());
            }
            return 0.0;
        }
    }

    private double getPlaytimeHours(Player player) {
        try {
            // Método 1: Usar estadística de Minecraft (más confiable)
            long ticks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            double hours = (ticks * 50L) / (1000.0 * 60 * 60); // Convertir ticks a horas

            if (debugMode) {
                plugin.getLogger().info("⏰ Tiempo jugado de " + player.getName() + ": " +
                        String.format("%.2f", hours) + " horas (" + ticks + " ticks)");
            }

            return hours;

        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Error obteniendo tiempo jugado de " + player.getName() + ": " + e.getMessage());
            }

            // Fallback: Usar PlaceholderAPI si está disponible
            if (placeholderAPIEnabled) {
                try {
                    String placeholder = "%cmi_user_playtime_hoursf%";
                    String result = PlaceholderAPI.setPlaceholders(player, placeholder);

                    if (!result.equals(placeholder)) { // Si el placeholder se resolvió
                        return Double.parseDouble(result.replaceAll("[^0-9.]", ""));
                    }
                } catch (Exception papiError) {
                    if (debugMode) {
                        plugin.getLogger().warning("❌ Error con PlaceholderAPI para tiempo jugado: " + papiError.getMessage());
                    }
                }
            }

            return 0.0;
        }
    }

    private RequirementCheckResult checkAllRequirementsWithTimeout(Player player, SimpleRankData rankData, int timeoutSeconds) {
        Map<String, Object> requirements = rankData.getRequirements();
        List<String> failures = new ArrayList<>();

        if (debugMode) {
            plugin.getLogger().info("🔍 Verificando " + requirements.size() + " requisitos para " + player.getName() +
                    " (timeout: " + timeoutSeconds + "s)");
        }

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        for (Map.Entry<String, Object> req : requirements.entrySet()) {
            // Verificar timeout
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                plugin.getLogger().warning("⏰ Timeout verificando requisitos para " + player.getName());
                failures.add("⏰ Timeout verificando requisitos. Intenta de nuevo.");
                break;
            }

            String type = req.getKey();
            Object required = req.getValue();

            try {
                if (!checkSingleRequirementSafe(player, type, required)) {
                    String message = formatRequirementFailure(player, type, required);
                    failures.add(message);

                    if (debugMode) {
                        plugin.getLogger().info("  ❌ " + type + ": " + message);
                    }
                } else if (debugMode) {
                    plugin.getLogger().info("  ✅ " + type + ": cumplido");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("❌ Error verificando requisito " + type + " para " + player.getName() + ": " + e.getMessage());
                failures.add("❌ Error verificando " + type + ". Contacta a un administrador.");
            }
        }

        if (failures.isEmpty()) {
            if (debugMode) {
                long duration = System.currentTimeMillis() - startTime;
                plugin.getLogger().info("✅ Todos los requisitos cumplidos para " + player.getName() + " en " + duration + "ms");
            }
            return new RequirementCheckResult(true, "");
        } else {
            return new RequirementCheckResult(false, String.join("\n", failures));
        }
    }

    private boolean checkSingleRequirementSafe(Player player, String type, Object required) {
        try {
            double requiredValue = ((Number) required).doubleValue();
            double currentValue = getCurrentRequirementValueSafe(player, type);

            if (debugMode) {
                plugin.getLogger().info("    🔍 " + type + ": " + currentValue + "/" + requiredValue);
            }

            return currentValue >= requiredValue;

        } catch (ClassCastException e) {
            plugin.getLogger().warning("❌ Valor de requisito inválido para " + type + ": " + required);
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Error verificando requisito " + type + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 🆕 NUEVO: Obtener valor de requisito de forma segura
     */
    private double getCurrentRequirementValueSafe(Player player, String type) {
        try {
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
                default -> handleCustomRequirementSafe(player, type);
            };
        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Error obteniendo valor para " + type + ": " + e.getMessage());
            }
            return 0.0;
        }
    }

    /**
     * 🆕 NUEVO: Manejo seguro de requisitos personalizados
     */
    private double handleCustomRequirementSafe(Player player, String type) {
        if (!placeholderAPIEnabled) {
            if (debugMode) {
                plugin.getLogger().warning("⚠️ PlaceholderAPI requerido para requisito personalizado: " + type);
            }
            return 0;
        }

        try {
            // Buscar en requisitos personalizados
            String placeholderTemplate = config.getString("custom_requirements." + type);
            if (placeholderTemplate == null) {
                if (debugMode) {
                    plugin.getLogger().warning("⚠️ Requisito personalizado no configurado: " + type);
                }
                return 0;
            }

            // Usar timeout para PlaceholderAPI
            String result = PlaceholderAPI.setPlaceholders(player, placeholderTemplate);

            // Verificar si el placeholder se resolvió
            if (result.equals(placeholderTemplate)) {
                if (debugMode) {
                    plugin.getLogger().warning("⚠️ Placeholder no resuelto: " + placeholderTemplate);
                }
                return 0;
            }

            // Extraer números de la respuesta
            String numberOnly = result.replaceAll("[^0-9.-]", "");
            if (numberOnly.isEmpty()) {
                return 0;
            }

            return Double.parseDouble(numberOnly);

        } catch (NumberFormatException e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Valor no numérico para requisito " + type + ": " + e.getMessage());
            }
            return 0;
        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Error en requisito personalizado " + type + ": " + e.getMessage());
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
            org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(entityType);
            int kills = player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, type);

            if (debugMode) {
                plugin.getLogger().info("🗡️ " + entityType + " matados por " + player.getName() + ": " + kills);
            }

            return kills;

        } catch (IllegalArgumentException e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Tipo de entidad inválido: " + entityType);
            }
            return 0.0;
        } catch (Exception e) {
            if (debugMode) {
                plugin.getLogger().warning("❌ Error obteniendo kills de " + entityType + " para " + player.getName() + ": " + e.getMessage());
            }
            return 0.0;
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
            try {
                if (debugMode) {
                    plugin.getLogger().info("📊 Obteniendo progreso para " + player.getName());
                }

                String currentRank = getCurrentRankWithRetry(player, 2);
                if (currentRank == null) {
                    plugin.getLogger().warning("❌ No se pudo obtener rango actual para progreso de " + player.getName());
                    return new RankupProgress(null, null, new HashMap<>(), 0.0);
                }

                SimpleRankData rankData = ranks.get(currentRank);
                if (rankData == null || rankData.getNextRank() == null) {
                    return new RankupProgress(currentRank, null, new HashMap<>(), 100.0);
                }

                Map<String, Object> requirements = rankData.getRequirements();
                Map<String, RequirementProgress> progress = new HashMap<>();
                double totalProgress = 0.0;
                int completedRequirements = 0;

                for (Map.Entry<String, Object> requirement : requirements.entrySet()) {
                    String type = requirement.getKey();

                    try {
                        double required = ((Number) requirement.getValue()).doubleValue();
                        double current = getCurrentRequirementValueSafe(player, type);
                        double percentage = Math.min((current / required) * 100.0, 100.0);
                        boolean completed = current >= required;

                        RequirementProgress reqProgress = new RequirementProgress(
                                type, current, required, percentage, completed
                        );
                        progress.put(type, reqProgress);

                        totalProgress += percentage;
                        if (completed) completedRequirements++;

                        if (debugMode) {
                            plugin.getLogger().info("  📈 " + type + ": " + String.format("%.1f", current) +
                                    "/" + String.format("%.1f", required) + " (" + String.format("%.1f", percentage) + "%)");
                        }

                    } catch (Exception e) {
                        plugin.getLogger().warning("❌ Error calculando progreso para " + type + ": " + e.getMessage());
                        // Agregar requisito con error
                        RequirementProgress errorProgress = new RequirementProgress(
                                type, 0, 1, 0, false
                        );
                        progress.put(type, errorProgress);
                    }
                }

                if (!requirements.isEmpty()) {
                    totalProgress /= requirements.size();
                }

                if (debugMode) {
                    plugin.getLogger().info("📊 Progreso total para " + player.getName() + ": " +
                            String.format("%.1f", totalProgress) + "% (" + completedRequirements + "/" + requirements.size() + " requisitos)");
                }

                return new RankupProgress(currentRank, rankData.getNextRank(), progress, totalProgress);

            } catch (Exception e) {
                plugin.getLogger().severe("❌ Error crítico obteniendo progreso para " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                return new RankupProgress(null, null, new HashMap<>(), 0.0);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("❌ Error asíncrono obteniendo progreso para " + player.getName() + ": " + throwable.getMessage());
            return new RankupProgress(null, null, new HashMap<>(), 0.0);
        });
    }
    /**
     * Debug de información del jugador (para admins)
     */
    public void debugPlayerRankup(Player player, Player admin) {
        admin.sendMessage(ChatColor.GOLD + "═══ Debug Rankup MEJORADO - " + player.getName() + " ═══");

        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                admin.sendMessage(ChatColor.RED + "❌ Usuario LuckPerms no encontrado");
                return;
            }

            // Información de LuckPerms
            admin.sendMessage(ChatColor.YELLOW + "🔍 Información de LuckPerms:");
            admin.sendMessage(ChatColor.WHITE + "  • Grupo primario: " + ChatColor.AQUA + user.getPrimaryGroup());

            // Todos los grupos del jugador
            Set<String> playerGroups = user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .map(group -> group.getName())
                    .collect(java.util.stream.Collectors.toSet());

            admin.sendMessage(ChatColor.WHITE + "  • Todos los grupos: " + ChatColor.GRAY + String.join(", ", playerGroups));

            // Configuración del sistema
            admin.sendMessage(ChatColor.YELLOW + "⚙️ Configuración del sistema:");
            admin.sendMessage(ChatColor.WHITE + "  • Prefijo de grupos: '" + ChatColor.AQUA + groupPrefix + ChatColor.WHITE + "'");
            admin.sendMessage(ChatColor.WHITE + "  • Método de detección: " + ChatColor.AQUA + detectionMethod);
            admin.sendMessage(ChatColor.WHITE + "  • Rango por defecto: " + ChatColor.AQUA + defaultRank);

            // Detección actual
            String currentRank = getCurrentRank(player);
            admin.sendMessage(ChatColor.YELLOW + "🎯 Detección de rango:");
            admin.sendMessage(ChatColor.WHITE + "  • Rango detectado: " + ChatColor.YELLOW +
                    (currentRank != null ? currentRank : "NULL"));

            // Verificar si existe en configuración
            SimpleRankData rankData = ranks.get(currentRank);
            if (rankData == null) {
                admin.sendMessage(ChatColor.RED + "  ❌ Error: No hay datos para el rango " + currentRank);
                admin.sendMessage(ChatColor.YELLOW + "  📋 Rangos disponibles en configuración:");
                ranks.keySet().forEach(rank -> admin.sendMessage(ChatColor.GRAY + "    - " + rank));
                return;
            }

            admin.sendMessage(ChatColor.WHITE + "  • Display: " + rankData.getDisplayName());
            admin.sendMessage(ChatColor.WHITE + "  • Orden: " + ChatColor.YELLOW + rankData.getOrder());
            admin.sendMessage(ChatColor.WHITE + "  • Siguiente: " + ChatColor.YELLOW +
                    (rankData.getNextRank() != null ? rankData.getNextRank() : "RANGO MÁXIMO"));

            // Verificar grupos en LuckPerms
            admin.sendMessage(ChatColor.YELLOW + "🔧 Verificación de grupos:");
            String currentGroupName = groupPrefix.isEmpty() ? currentRank : groupPrefix + currentRank;
            boolean currentGroupExists = groupExists(currentGroupName);
            admin.sendMessage(ChatColor.WHITE + "  • Grupo actual (" + currentGroupName + "): " +
                    (currentGroupExists ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NO EXISTE"));

            if (rankData.getNextRank() != null) {
                String nextGroupName = groupPrefix.isEmpty() ? rankData.getNextRank() : groupPrefix + rankData.getNextRank();
                boolean nextGroupExists = groupExists(nextGroupName);
                admin.sendMessage(ChatColor.WHITE + "  • Siguiente grupo (" + nextGroupName + "): " +
                        (nextGroupExists ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NO EXISTE"));
            }

            if (rankData.getNextRank() == null) {
                admin.sendMessage(ChatColor.GREEN + "✅ El jugador ya tiene el rango máximo");
                return;
            }

            // Verificar requisitos
            admin.sendMessage(ChatColor.YELLOW + "📋 Verificando requisitos:");
            Map<String, Object> requirements = rankData.getRequirements();

            if (requirements.isEmpty()) {
                admin.sendMessage(ChatColor.YELLOW + "  ⚠️ No hay requisitos configurados para el siguiente rango");
            } else {
                for (Map.Entry<String, Object> req : requirements.entrySet()) {
                    String type = req.getKey();
                    double required = ((Number) req.getValue()).doubleValue();
                    double current = getCurrentRequirementValue(player, type);
                    boolean met = current >= required;

                    String status = met ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                    admin.sendMessage("    " + status + ChatColor.WHITE + " " + type +
                            ": " + ChatColor.YELLOW + formatValue(type, current) +
                            ChatColor.GRAY + "/" + ChatColor.GREEN + formatValue(type, required));
                }
            }

            // Cooldown
            if (isOnCooldown(player.getUniqueId())) {
                long remaining = getRemainingCooldown(player.getUniqueId());
                admin.sendMessage(ChatColor.YELLOW + "⏰ Cooldown: " + (remaining / 1000) + "s restantes");
            } else {
                admin.sendMessage(ChatColor.GREEN + "✅ Sin cooldown activo");
            }

        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "❌ Error en debug: " + e.getMessage());
            plugin.getLogger().severe("Error en debug de rankup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =================== GETTERS Y CONFIGURACIÓN ===================

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