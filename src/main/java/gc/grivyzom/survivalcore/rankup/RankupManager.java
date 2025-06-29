package gc.grivyzom.survivalcore.rankup;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.api.events.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestor principal del sistema de rankups.
 * Maneja toda la lógica de ascensos de rango con integración a LuckPerms.
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RankupManager {

    private final Main plugin;
    private final Map<String, RankupData> rankups = new ConcurrentHashMap<>();
    private final Map<String, PrestigeData> prestiges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private File configFile;
    private FileConfiguration config;
    private LuckPerms luckPerms;

    // Configuración cache
    private long cooldownTime;
    private boolean enablePrestige;
    private boolean enableEffects;
    private boolean enableBroadcast;
    private int maxRankupHistory;

    public RankupManager(Main plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "rankups.yml");

        // Inicializar LuckPerms con mejor manejo de errores
        if (!initLuckPerms()) {
            throw new RuntimeException("No se pudo inicializar LuckPerms - Sistema de Rankup no disponible");
        }

        // Cargar configuración
        try {
            loadConfig();
        } catch (Exception e) {
            plugin.getLogger().severe("Error cargando configuración de rankups: " + e.getMessage());
            throw new RuntimeException("Error en configuración de rankups", e);
        }

        // Crear tabla de historial
        try {
            createHistoryTable();
        } catch (Exception e) {
            plugin.getLogger().warning("Error creando tabla de historial: " + e.getMessage());
            // No es crítico, el sistema puede funcionar sin historial
        }

        plugin.getLogger().info("Sistema de Rankup inicializado correctamente.");
    }

    /**
     * Inicializa la integración con LuckPerms
     */
    private boolean initLuckPerms() {
        try {
            // Verificar que LuckPerms esté disponible
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                plugin.getLogger().severe("LuckPerms no está instalado!");
                plugin.getLogger().severe("El sistema de Rankup requiere LuckPerms para funcionar.");
                plugin.getLogger().severe("Descarga LuckPerms desde: https://luckperms.net/download");
                return false;
            }

            // Verificar que LuckPerms esté habilitado
            if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                plugin.getLogger().severe("LuckPerms está instalado pero no habilitado!");
                return false;
            }

            // Intentar obtener la API de LuckPerms
            this.luckPerms = LuckPermsProvider.get();

            if (this.luckPerms == null) {
                plugin.getLogger().severe("No se pudo obtener la API de LuckPerms!");
                return false;
            }

            plugin.getLogger().info("Integración con LuckPerms establecida correctamente.");
            plugin.getLogger().info("Versión de LuckPerms: " + luckPerms.getPluginMetadata().getVersion());
            return true;

        } catch (IllegalStateException e) {
            plugin.getLogger().severe("LuckPerms no está disponible: " + e.getMessage());
            plugin.getLogger().severe("Asegúrate de que LuckPerms esté instalado y cargado antes que SurvivalCore.");
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Error inesperado al conectar con LuckPerms: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Carga la configuración desde rankups.yml
     */
    public void loadConfig() {
        try {
            // Crear archivo de configuración si no existe
            if (!configFile.exists()) {
                plugin.getLogger().info("Creando archivo de configuración rankups.yml...");
                createDefaultConfig();
            }

            config = YamlConfiguration.loadConfiguration(configFile);

            // Limpiar datos anteriores
            rankups.clear();
            prestiges.clear();

            // Cargar configuración general con valores por defecto
            cooldownTime = config.getLong("settings.cooldown_seconds", 5) * 1000L;
            enablePrestige = config.getBoolean("settings.enable_prestige", true);
            enableEffects = config.getBoolean("settings.enable_effects", true);
            enableBroadcast = config.getBoolean("settings.enable_broadcast", true);
            maxRankupHistory = config.getInt("settings.max_history_entries", 100);

            // Cargar rankups
            int ranksLoaded = loadRankups();
            plugin.getLogger().info("Cargados " + ranksLoaded + " rangos desde la configuración.");

            // Cargar prestiges si están habilitados
            int prestigesLoaded = 0;
            if (enablePrestige) {
                prestigesLoaded = loadPrestiges();
                plugin.getLogger().info("Cargados " + prestigesLoaded + " prestiges desde la configuración.");
            }

            plugin.getLogger().info(String.format("Configuración de Rankup cargada: %d rangos, %d prestiges",
                    ranksLoaded, prestigesLoaded));

        } catch (Exception e) {
            plugin.getLogger().severe("Error crítico cargando configuración de rankups:");
            plugin.getLogger().severe("Archivo: " + configFile.getAbsolutePath());
            plugin.getLogger().severe("Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error en configuración de rankups", e);
        }
    }

    /**
     * Crea configuración por defecto
     */
    private void createDefaultConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Si el plugin tiene el recurso, usarlo
            try {
                plugin.saveResource("rankups.yml", false);
                plugin.getLogger().info("Archivo rankups.yml creado desde recursos del plugin.");
                return;
            } catch (IllegalArgumentException e) {
                // El recurso no existe, crear configuración básica
                plugin.getLogger().info("Creando configuración básica de rankups...");
            }

            // Crear configuración básica
            FileConfiguration defaultConfig = new YamlConfiguration();

            // Configuración general
            defaultConfig.set("settings.cooldown_seconds", 5);
            defaultConfig.set("settings.enable_prestige", true);
            defaultConfig.set("settings.enable_effects", true);
            defaultConfig.set("settings.enable_broadcast", true);
            defaultConfig.set("settings.max_history_entries", 100);

            // Rangos de ejemplo
            defaultConfig.set("ranks.novato.display_name", "&7[&fNovato&7]");
            defaultConfig.set("ranks.novato.next_rank", "aprendiz");
            defaultConfig.set("ranks.novato.order", 1);
            defaultConfig.set("ranks.novato.permission_node", "group.novato");
            defaultConfig.set("ranks.novato.requirements.money", 1000);
            defaultConfig.set("ranks.novato.requirements.playtime", 1);
            defaultConfig.set("ranks.novato.rewards.commands", Arrays.asList("say %player% ha ascendido a Aprendiz!"));

            defaultConfig.set("ranks.aprendiz.display_name", "&a[&2Aprendiz&a]");
            defaultConfig.set("ranks.aprendiz.next_rank", "experto");
            defaultConfig.set("ranks.aprendiz.order", 2);
            defaultConfig.set("ranks.aprendiz.permission_node", "group.aprendiz");
            defaultConfig.set("ranks.aprendiz.requirements.money", 5000);
            defaultConfig.set("ranks.aprendiz.requirements.playtime", 5);
            defaultConfig.set("ranks.aprendiz.requirements.level", 10);
            defaultConfig.set("ranks.aprendiz.rewards.commands", Arrays.asList("say %player% ha ascendido a Experto!"));

            defaultConfig.set("ranks.experto.display_name", "&6[&eExperto&6]");
            defaultConfig.set("ranks.experto.next_rank", null);
            defaultConfig.set("ranks.experto.order", 3);
            defaultConfig.set("ranks.experto.permission_node", "group.experto");
            defaultConfig.set("ranks.experto.requirements.money", 25000);
            defaultConfig.set("ranks.experto.requirements.playtime", 20);
            defaultConfig.set("ranks.experto.requirements.level", 30);
            defaultConfig.set("ranks.experto.rewards.commands", Arrays.asList("say %player% ha alcanzado el rango máximo!"));

            defaultConfig.save(configFile);
            plugin.getLogger().info("Configuración por defecto de rankups creada.");

        } catch (Exception e) {
            plugin.getLogger().severe("Error creando configuración por defecto: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Carga los rankups desde la configuración con mejor manejo de errores
     */
    private int loadRankups() {
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks");
        if (ranksSection == null) {
            plugin.getLogger().warning("No se encontró la sección 'ranks' en rankups.yml");
            return 0;
        }

        int loaded = 0;
        for (String rankKey : ranksSection.getKeys(false)) {
            try {
                ConfigurationSection rankSection = ranksSection.getConfigurationSection(rankKey);
                if (rankSection == null) {
                    plugin.getLogger().warning("Sección de rango inválida: " + rankKey);
                    continue;
                }

                RankupData rankup = new RankupData();
                rankup.setRankId(rankKey);
                rankup.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        rankSection.getString("display_name", rankKey)));
                rankup.setNextRank(rankSection.getString("next_rank"));
                rankup.setOrder(rankSection.getInt("order", 0));
                rankup.setRequirements(loadRequirements(rankSection.getConfigurationSection("requirements")));
                rankup.setRewards(loadRewards(rankSection.getConfigurationSection("rewards")));
                rankup.setPermissionNode(rankSection.getString("permission_node"));

                rankups.put(rankKey, rankup);
                loaded++;

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando rango '" + rankKey + "': " + e.getMessage());
            }
        }

        return loaded;
    }

    /**
     * Carga los prestiges desde la configuración
     */
    private int loadPrestiges() {
        ConfigurationSection prestigeSection = config.getConfigurationSection("prestiges");
        if (prestigeSection == null) return 0;

        int loaded = 0;

        for (String prestigeKey : prestigeSection.getKeys(false)) {
            try {
                ConfigurationSection section = prestigeSection.getConfigurationSection(prestigeKey);
                if (section == null) continue;

                PrestigeData prestige = new PrestigeData();
                prestige.setPrestigeId(prestigeKey);
                prestige.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        section.getString("display_name", prestigeKey)));
                prestige.setLevel(section.getInt("level", 1));
                prestige.setRequirements(loadRequirements(section.getConfigurationSection("requirements")));
                prestige.setRewards(loadRewards(section.getConfigurationSection("rewards")));
                prestige.setResetRanks(section.getBoolean("reset_ranks", true));
                prestige.setKeepProgress(section.getStringList("keep_progress"));

                prestiges.put(prestigeKey, prestige);
                loaded++;

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando prestige '" + prestigeKey + "': " + e.getMessage());
            }
        }

        return loaded;
    }

    /**
     * Carga los requisitos desde una sección de configuración
     */
    private Map<String, Object> loadRequirements(ConfigurationSection section) {
        Map<String, Object> requirements = new HashMap<>();
        if (section == null) return requirements;

        for (String key : section.getKeys(false)) {
            requirements.put(key, section.get(key));
        }

        return requirements;
    }

    /**
     * Carga las recompensas desde una sección de configuración
     */
    private Map<String, Object> loadRewards(ConfigurationSection section) {
        Map<String, Object> rewards = new HashMap<>();
        if (section == null) return rewards;

        for (String key : section.getKeys(false)) {
            rewards.put(key, section.get(key));
        }

        return rewards;
    }

    /**
     * Intenta hacer rankup a un jugador
     */
    public CompletableFuture<RankupResult> attemptRankup(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();

            // Verificar cooldown
            if (isOnCooldown(uuid)) {
                long remaining = getRemainingCooldown(uuid);
                return new RankupResult(false, "§cDebes esperar " + (remaining / 1000) + " segundos antes de intentar rankup nuevamente.");
            }

            // Obtener rango actual
            String currentRank = getCurrentRank(player);
            if (currentRank == null) {
                return new RankupResult(false, "§cNo se pudo determinar tu rango actual.");
            }

            // Buscar datos del rankup
            RankupData rankupData = rankups.get(currentRank);
            if (rankupData == null) {
                return new RankupResult(false, "§cNo hay rankup disponible para tu rango actual.");
            }

            String nextRank = rankupData.getNextRank();
            if (nextRank == null || nextRank.isEmpty()) {
                return new RankupResult(false, "§cHas alcanzado el rango máximo.");
            }

            // Verificar requisitos
            RequirementCheckResult checkResult = checkRequirements(player, rankupData);
            if (!checkResult.isSuccess()) {
                return new RankupResult(false, checkResult.getMessage());
            }

            // Realizar rankup
            boolean success = performRankup(player, currentRank, nextRank, rankupData);
            if (success) {
                // Establecer cooldown
                setCooldown(uuid);

                // Registrar en historial
                recordRankupHistory(player, currentRank, nextRank, "RANKUP");

                return new RankupResult(true, "§a¡Felicidades! Has ascendido al rango " +
                        rankups.getOrDefault(nextRank, new RankupData()).getDisplayName() + "§a!");
            } else {
                return new RankupResult(false, "§cError al procesar el rankup. Contacta a un administrador.");
            }
        });
    }

    /**
     * Verifica los requisitos para un rankup
     */
    private RequirementCheckResult checkRequirements(Player player, RankupData rankupData) {
        Map<String, Object> requirements = rankupData.getRequirements();
        List<String> failedRequirements = new ArrayList<>();

        for (Map.Entry<String, Object> requirement : requirements.entrySet()) {
            String type = requirement.getKey();
            Object value = requirement.getValue();

            boolean met = switch (type.toLowerCase()) {
                case "money", "eco", "economy" -> checkMoneyRequirement(player, value);
                case "xp", "experience" -> checkXpRequirement(player, value);
                case "level", "levels" -> checkLevelRequirement(player, value);
                case "playtime", "time_played" -> checkPlaytimeRequirement(player, value);
                case "farming_level" -> checkFarmingLevelRequirement(player, value);
                case "mining_level" -> checkMiningLevelRequirement(player, value);
                case "kills", "mob_kills" -> checkKillsRequirement(player, value);
                case "blocks_broken" -> checkBlocksBrokenRequirement(player, value);
                case "permission" -> checkPermissionRequirement(player, value);
                case "custom" -> checkCustomRequirement(player, value);
                default -> {
                    plugin.getLogger().warning("Tipo de requisito desconocido: " + type);
                    yield true; // No fallar por requisitos desconocidos
                }
            };

            if (!met) {
                failedRequirements.add(formatRequirementMessage(type, value, player));
            }
        }

        if (failedRequirements.isEmpty()) {
            return new RequirementCheckResult(true, "§aTodos los requisitos cumplidos.");
        } else {
            StringBuilder message = new StringBuilder("§cRequisitos no cumplidos:\n");
            for (String failed : failedRequirements) {
                message.append("§7- ").append(failed).append("\n");
            }
            return new RequirementCheckResult(false, message.toString().trim());
        }
    }

    /**
     * Verifica requisito de dinero
     */
    private boolean checkMoneyRequirement(Player player, Object value) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return true; // Si no hay Vault, no verificar dinero
        }

        try {
            double required = ((Number) value).doubleValue();
            // Aquí integrarías con Vault Economy
            // return VaultAPI.getBalance(player) >= required;
            return true; // Placeholder - implementar con Vault
        } catch (Exception e) {
            plugin.getLogger().warning("Error verificando requisito de dinero: " + e.getMessage());
            return true;
        }
    }

    /**
     * Verifica requisito de experiencia
     */
    private boolean checkXpRequirement(Player player, Object value) {
        try {
            int required = ((Number) value).intValue();
            return getTotalXp(player) >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de niveles
     */
    private boolean checkLevelRequirement(Player player, Object value) {
        try {
            int required = ((Number) value).intValue();
            return player.getLevel() >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de tiempo jugado
     */
    private boolean checkPlaytimeRequirement(Player player, Object value) {
        try {
            long required = ((Number) value).longValue(); // Horas requeridas
            long playtime = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 50L; // Ticks a ms
            long hoursPlayed = playtime / (1000L * 60 * 60); // Convertir a horas
            return hoursPlayed >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de nivel de farming
     */
    private boolean checkFarmingLevelRequirement(Player player, Object value) {
        try {
            int required = ((Number) value).intValue();
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getFarmingLevel() >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de nivel de minería
     */
    private boolean checkMiningLevelRequirement(Player player, Object value) {
        try {
            int required = ((Number) value).intValue();
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            return userData != null && userData.getMiningLevel() >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de kills
     */
    private boolean checkKillsRequirement(Player player, Object value) {
        try {
            int required = ((Number) value).intValue();
            int kills = player.getStatistic(org.bukkit.Statistic.MOB_KILLS);
            return kills >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de bloques rotos
     */
    private boolean checkBlocksBrokenRequirement(Player player, Object value) {
        try {
            int required = ((Number) value).intValue();
            int broken = player.getStatistic(org.bukkit.Statistic.MINE_BLOCK);
            return broken >= required;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito de permiso
     */
    private boolean checkPermissionRequirement(Player player, Object value) {
        try {
            String permission = value.toString();
            return player.hasPermission(permission);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica requisito personalizado
     */
    private boolean checkCustomRequirement(Player player, Object value) {
        // Implementar lógica personalizada según necesidades
        return true;
    }

    /**
     * Formatea el mensaje de un requisito no cumplido
     */
    private String formatRequirementMessage(String type, Object value, Player player) {
        return switch (type.toLowerCase()) {
            case "money", "eco", "economy" -> String.format("§c$%,.2f de dinero", ((Number) value).doubleValue());
            case "xp", "experience" -> String.format("§c%,d puntos de experiencia", ((Number) value).intValue());
            case "level", "levels" -> String.format("§cNivel %d", ((Number) value).intValue());
            case "playtime", "time_played" -> String.format("§c%d horas jugadas", ((Number) value).longValue());
            case "farming_level" -> String.format("§cNivel de granjería %d", ((Number) value).intValue());
            case "mining_level" -> String.format("§cNivel de minería %d", ((Number) value).intValue());
            case "kills", "mob_kills" -> String.format("§c%,d kills", ((Number) value).intValue());
            case "blocks_broken" -> String.format("§c%,d bloques rotos", ((Number) value).intValue());
            case "permission" -> String.format("§cPermiso: %s", value.toString());
            default -> String.format("§c%s: %s", type, value.toString());
        };
    }

    /**
     * Realiza el rankup efectivo
     */
    private boolean performRankup(Player player, String fromRank, String toRank, RankupData rankupData) {
        if (luckPerms == null) return false;

        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);

            // Remover rango anterior si hay nodo de permiso
            if (rankupData.getPermissionNode() != null) {
                InheritanceNode oldNode = InheritanceNode.builder(fromRank).build();
                user.data().remove(oldNode);
            }

            // Añadir nuevo rango
            RankupData nextRankData = rankups.get(toRank);
            if (nextRankData != null && nextRankData.getPermissionNode() != null) {
                InheritanceNode newNode = InheritanceNode.builder(toRank).build();
                user.data().add(newNode);
            }

            // Guardar cambios
            luckPerms.getUserManager().saveUser(user);

            // Aplicar recompensas
            applyRewards(player, rankupData);

            // Efectos y mensajes
            if (enableEffects) {
                playRankupEffects(player);
            }

            if (enableBroadcast) {
                broadcastRankup(player, fromRank, toRank);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error realizando rankup", e);
            return false;
        }
    }

    /**
     * Aplica las recompensas del rankup
     */
    private void applyRewards(Player player, RankupData rankupData) {
        Map<String, Object> rewards = rankupData.getRewards();

        for (Map.Entry<String, Object> reward : rewards.entrySet()) {
            String type = reward.getKey();
            Object value = reward.getValue();

            switch (type.toLowerCase()) {
                case "money", "eco", "economy" -> {
                    // Implementar con Vault Economy
                }
                case "xp", "experience" -> {
                    int amount = ((Number) value).intValue();
                    player.giveExp(amount);
                }
                case "levels" -> {
                    int amount = ((Number) value).intValue();
                    player.giveExpLevels(amount);
                }
                case "commands" -> {
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> commands = (List<String>) value;
                        for (String command : commands) {
                            String processedCommand = command.replace("%player%", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        }
                    }
                }
                case "items" -> {
                    // Implementar entrega de items
                }
            }
        }
    }

    /**
     * Reproduce efectos de rankup
     */
    private void playRankupEffects(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Efectos de partículas
        player.getWorld().spawnParticle(
                org.bukkit.Particle.FIREWORKS_SPARK,
                player.getLocation().add(0, 1, 0),
                50, 0.5, 0.5, 0.5, 0.1
        );
    }

    /**
     * Anuncia el rankup al servidor
     */
    private void broadcastRankup(Player player, String fromRank, String toRank) {
        RankupData fromData = rankups.get(fromRank);
        RankupData toData = rankups.get(toRank);

        String fromDisplay = fromData != null ? fromData.getDisplayName() : fromRank;
        String toDisplay = toData != null ? toData.getDisplayName() : toRank;

        String message = String.format("§6★ §e%s §aha ascendido de %s §aa %s§a! §6★",
                player.getName(), fromDisplay, toDisplay);

        Bukkit.broadcastMessage(message);
    }

    /**
     * Obtiene el rango actual del jugador
     */
    public String getCurrentRank(Player player) {
        if (luckPerms == null) return null;

        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            String primaryGroup = user.getPrimaryGroup();

            // Verificar si el grupo primario está en nuestros rankups
            if (rankups.containsKey(primaryGroup)) {
                return primaryGroup;
            }

            // Si no, buscar en los grupos del usuario
            return user.getInheritedGroups(user.getQueryOptions())
                    .stream()
                    .map(group -> group.getName())
                    .filter(rankups::containsKey)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            plugin.getLogger().warning("Error obteniendo rango actual: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene la experiencia total del jugador
     */
    private int getTotalXp(Player player) {
        int total = Math.round(player.getExp() * getXpToNextLevel(player.getLevel()));
        for (int i = 0; i < player.getLevel(); i++) {
            total += getXpToNextLevel(i);
        }
        return total;
    }

    /**
     * Obtiene XP necesaria para el siguiente nivel
     */
    private int getXpToNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else if (level >= 15) {
            return 37 + (level - 15) * 5;
        } else {
            return 7 + level * 2;
        }
    }

    /**
     * Crea la tabla de historial en la base de datos
     */
    private void createHistoryTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS rankup_history (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
            player_uuid CHAR(36) NOT NULL,
            player_name VARCHAR(16) NOT NULL,
            from_rank VARCHAR(64) NOT NULL,
            to_rank VARCHAR(64) NOT NULL,
            rankup_type ENUM('RANKUP', 'PRESTIGE') NOT NULL,
            timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_player_uuid (player_uuid),
            INDEX idx_timestamp (timestamp)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                plugin.getLogger().info("Tabla rankup_history creada/verificada correctamente.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error creando tabla rankup_history", e);
            }
        });
    }

    /**
     * Registra un rankup en el historial
     */
    private void recordRankupHistory(Player player, String fromRank, String toRank, String type) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
            INSERT INTO rankup_history (player_uuid, player_name, from_rank, to_rank, rankup_type)
            VALUES (?, ?, ?, ?, ?)
            """;

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, fromRank);
                ps.setString(4, toRank);
                ps.setString(5, type);

                ps.executeUpdate();

                // Limpiar historial antiguo si excede el límite
                cleanupHistory();

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error registrando historial de rankup", e);
            }
        });
    }

    /**
     * Limpia el historial antiguo
     */
    private void cleanupHistory() {
        if (maxRankupHistory <= 0) return;

        String sql = """
        DELETE FROM rankup_history 
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM rankup_history 
                ORDER BY timestamp DESC 
                LIMIT ?
            ) AS recent
        )
        """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maxRankupHistory);
            int deleted = ps.executeUpdate();

            if (deleted > 0) {
                plugin.getLogger().info("Limpiados " + deleted + " registros antiguos del historial de rankups.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error limpiando historial de rankup", e);
        }
    }

    /**
     * Verifica si un jugador está en cooldown
     */
    public boolean isOnCooldown(UUID uuid) {
        Long lastUse = cooldowns.get(uuid);
        return lastUse != null && System.currentTimeMillis() - lastUse < cooldownTime;
    }

    /**
     * Obtiene el tiempo restante de cooldown
     */
    public long getRemainingCooldown(UUID uuid) {
        Long lastUse = cooldowns.get(uuid);
        if (lastUse == null) return 0;
        return Math.max(0, cooldownTime - (System.currentTimeMillis() - lastUse));
    }

    /**
     * Establece cooldown para un jugador
     */
    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    /**
     * Obtiene información de progreso de un jugador
     */
    public CompletableFuture<RankupProgress> getPlayerProgress(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String currentRank = getCurrentRank(player);
            if (currentRank == null) {
                return new RankupProgress(null, null, new HashMap<>(), 0.0);
            }

            RankupData rankupData = rankups.get(currentRank);
            if (rankupData == null || rankupData.getNextRank() == null) {
                return new RankupProgress(currentRank, null, new HashMap<>(), 100.0);
            }

            Map<String, Object> requirements = rankupData.getRequirements();
            Map<String, RequirementProgress> progress = new HashMap<>();
            double totalProgress = 0.0;

            for (Map.Entry<String, Object> requirement : requirements.entrySet()) {
                String type = requirement.getKey();
                Object requiredValue = requirement.getValue();

                RequirementProgress reqProgress = calculateRequirementProgress(player, type, requiredValue);
                progress.put(type, reqProgress);
                totalProgress += reqProgress.getPercentage();
            }

            if (!requirements.isEmpty()) {
                totalProgress /= requirements.size();
            }

            return new RankupProgress(currentRank, rankupData.getNextRank(), progress, totalProgress);
        });
    }

    /**
     * Calcula el progreso de un requisito específico
     */
    private RequirementProgress calculateRequirementProgress(Player player, String type, Object requiredValue) {
        try {
            double required = ((Number) requiredValue).doubleValue();
            double current = getCurrentValue(player, type);
            double percentage = Math.min((current / required) * 100.0, 100.0);

            return new RequirementProgress(
                    type,
                    current,
                    required,
                    percentage,
                    current >= required
            );
        } catch (Exception e) {
            return new RequirementProgress(type, 0, 0, 0, false);
        }
    }

    /**
     * Obtiene el valor actual de un requisito para un jugador
     */
    private double getCurrentValue(Player player, String type) {
        return switch (type.toLowerCase()) {
            case "money", "eco", "economy" -> 0.0; // Implementar con Vault
            case "xp", "experience" -> getTotalXp(player);
            case "level", "levels" -> player.getLevel();
            case "playtime", "time_played" -> {
                long playtime = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 50L; // Ticks a ms
                yield playtime / (1000L * 60 * 60); // Convertir a horas
            }
            case "farming_level" -> {
                try {
                    UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
                    yield userData != null ? userData.getFarmingLevel() : 0;
                } catch (Exception e) {
                    yield 0;
                }
            }
            case "mining_level" -> {
                try {
                    UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
                    yield userData != null ? userData.getMiningLevel() : 0;
                } catch (Exception e) {
                    yield 0;
                }
            }
            case "kills", "mob_kills" -> player.getStatistic(org.bukkit.Statistic.MOB_KILLS);
            case "blocks_broken" -> player.getStatistic(org.bukkit.Statistic.MINE_BLOCK);
            default -> 0.0;
        };
    }

    /**
     * Recarga la configuración
     */
    public void reloadConfig() {
        try {
            loadConfig();
            plugin.getLogger().info("Configuración de Rankup recargada correctamente.");
        } catch (Exception e) {
            plugin.getLogger().severe("Error recargando configuración de Rankup: " + e.getMessage());
            throw new RuntimeException("Error recargando configuración", e);
        }
    }

    // Getters
    public Map<String, RankupData> getRankups() { return new HashMap<>(rankups); }
    public Map<String, PrestigeData> getPrestiges() { return new HashMap<>(prestiges); }
    public boolean isPrestigeEnabled() { return enablePrestige; }
    public boolean areEffectsEnabled() { return enableEffects; }
    public boolean isBroadcastEnabled() { return enableBroadcast; }
    public long getCooldownTime() { return cooldownTime; }

    // Clases internas para resultados
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
        private final String message;

        public RequirementCheckResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
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