package gc.grivyzom.survivalcore.flowers.config;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Manager principal para cargar y gestionar la configuración de flores mágicas
 * desde el archivo flowers.yml
 *
 * @author Brocolitx
 * @version 1.0
 */
public class FlowerConfigManager {

    private final Main plugin;
    private final File flowersFile;
    private FileConfiguration flowersConfig;

    // Mapas de datos cargados
    private final Map<String, TierDefinition> tiers = new LinkedHashMap<>();
    private final Map<String, FlowerDefinition> flowers = new LinkedHashMap<>();

    // Configuración global
    private GlobalSettings globalSettings;

    public FlowerConfigManager(Main plugin) {
        this.plugin = plugin;
        this.flowersFile = new File(plugin.getDataFolder(), "flowers.yml");

        // Crear archivo por defecto si no existe
        createDefaultConfig();

        // Cargar configuración
        loadConfig();
    }

    /**
     * Crea el archivo flowers.yml por defecto si no existe
     */
    private void createDefaultConfig() {
        if (!flowersFile.exists()) {
            try {
                // Crear directorio si no existe
                flowersFile.getParentFile().mkdirs();

                // Copiar archivo por defecto desde resources
                try (InputStream defaultConfig = plugin.getResource("flowers.yml")) {
                    if (defaultConfig != null) {
                        Files.copy(defaultConfig, flowersFile.toPath());
                        plugin.getLogger().info("Archivo flowers.yml creado con configuración por defecto.");
                    } else {
                        // Si no hay archivo en resources, crear uno básico
                        createBasicConfig();
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Error creando flowers.yml: " + e.getMessage());
                createBasicConfig();
            }
        }
    }

    /**
     * Crea una configuración básica si no hay archivo por defecto
     */
    private void createBasicConfig() {
        flowersConfig = new YamlConfiguration();

        // Configuración global básica
        flowersConfig.set("global_settings.enable_enchant_glint", true);
        flowersConfig.set("global_settings.enable_advanced_particles", true);
        flowersConfig.set("global_settings.enable_custom_sounds", true);
        flowersConfig.set("global_settings.default_effect_duration", 60);
        flowersConfig.set("global_settings.effect_refresh_interval", 3);
        flowersConfig.set("global_settings.particle_spawn_interval", 1);

        // Tier básico
        flowersConfig.set("tiers.COMMON.color", "&f");
        flowersConfig.set("tiers.COMMON.max_level", 3);
        flowersConfig.set("tiers.COMMON.effect_multiplier", 1.0);
        flowersConfig.set("tiers.COMMON.particle_multiplier", 1.0);
        flowersConfig.set("tiers.COMMON.rarity_weight", 100);

        // Flor básica de ejemplo
        flowersConfig.set("flowers.strength_flower.config.type", "ALLIUM");
        flowersConfig.set("flowers.strength_flower.config.tier", "COMMON");
        flowersConfig.set("flowers.strength_flower.config.max_level", 3);
        flowersConfig.set("flowers.strength_flower.config.enchant_effect", true);

        flowersConfig.set("flowers.strength_flower.config.particles.area_effect", "CRIT_MAGIC");
        flowersConfig.set("flowers.strength_flower.config.particles.placement", "VILLAGER_HAPPY");
        flowersConfig.set("flowers.strength_flower.config.particles.ambient", "ENCHANTMENT_TABLE");
        flowersConfig.set("flowers.strength_flower.config.particles.amount", 5);

        flowersConfig.set("flowers.strength_flower.config.sounds.placement", "ENTITY_IRON_GOLEM_ATTACK");
        flowersConfig.set("flowers.strength_flower.config.sounds.activation", "ENTITY_PLAYER_LEVELUP");
        flowersConfig.set("flowers.strength_flower.config.sounds.ambient", "BLOCK_ENCHANTMENT_TABLE_USE");

        List<String> effects = Arrays.asList("INCREASE_DAMAGE:{flower_level}:60 + ({flower_level} * 10)");
        flowersConfig.set("flowers.strength_flower.config.effects", effects);

        flowersConfig.set("flowers.strength_flower.display.name", "&cFlor de Fuerza");
        List<String> lore = Arrays.asList(
                "&7Una flor que irradia poder físico",
                "&7y fortalece a quienes están cerca.",
                "",
                "&aEfecto: &fFuerza {level}",
                "&aTier: {tier_color}{tier_name}"
        );
        flowersConfig.set("flowers.strength_flower.display.lore", lore);

        try {
            flowersConfig.save(flowersFile);
            plugin.getLogger().info("Configuración básica de flowers.yml creada.");
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando configuración básica: " + e.getMessage());
        }
    }

    /**
     * Carga la configuración desde el archivo
     */
    public void loadConfig() {
        try {
            flowersConfig = YamlConfiguration.loadConfiguration(flowersFile);

            // Limpiar datos anteriores
            tiers.clear();
            flowers.clear();

            // Cargar configuración global
            loadGlobalSettings();

            // Cargar tiers
            loadTiers();

            // Cargar flores
            loadFlowers();

            // Validar configuración
            validateConfig();

            plugin.getLogger().info(String.format("Configuración de flores cargada: %d tiers, %d flores",
                    tiers.size(), flowers.size()));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando flowers.yml", e);
        }
    }

    /**
     * Carga la configuración global
     */
    private void loadGlobalSettings() {
        ConfigurationSection globalSection = flowersConfig.getConfigurationSection("global_settings");
        if (globalSection == null) {
            globalSettings = new GlobalSettings(); // Usar valores por defecto
            return;
        }

        globalSettings = new GlobalSettings(
                globalSection.getBoolean("enable_enchant_glint", true),
                globalSection.getBoolean("enable_advanced_particles", true),
                globalSection.getBoolean("enable_custom_sounds", true),
                globalSection.getBoolean("enable_tier_restrictions", false),
                globalSection.getInt("default_effect_duration", 60),
                globalSection.getInt("effect_refresh_interval", 3),
                globalSection.getInt("particle_spawn_interval", 1),
                globalSection.getBoolean("allow_flower_stacking", false),
                globalSection.getInt("max_flowers_per_pot", 1)
        );
    }

    /**
     * Carga las definiciones de tiers
     */
    private void loadTiers() {
        ConfigurationSection tiersSection = flowersConfig.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("No se encontró sección 'tiers' en flowers.yml");
            return;
        }

        for (String tierName : tiersSection.getKeys(false)) {
            try {
                ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierName);
                if (tierSection == null) continue;

                TierDefinition tier = new TierDefinition(
                        tierName,
                        tierSection.getString("color", "&f"),
                        tierSection.getInt("max_level", 5),
                        tierSection.getDouble("effect_multiplier", 1.0),
                        tierSection.getDouble("particle_multiplier", 1.0),
                        tierSection.getInt("rarity_weight", 100)
                );

                if (tier.isValid()) {
                    tiers.put(tierName.toUpperCase(), tier);
                } else {
                    plugin.getLogger().warning("Tier inválido ignorado: " + tierName +
                            " - Errores: " + tier.getValidationErrors());
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando tier '" + tierName + "': " + e.getMessage());
            }
        }
    }

    /**
     * Carga las definiciones de flores
     */
    private void loadFlowers() {
        ConfigurationSection flowersSection = flowersConfig.getConfigurationSection("flowers");
        if (flowersSection == null) {
            plugin.getLogger().warning("No se encontró sección 'flowers' en flowers.yml");
            return;
        }

        for (String flowerId : flowersSection.getKeys(false)) {
            try {
                ConfigurationSection flowerSection = flowersSection.getConfigurationSection(flowerId);
                if (flowerSection == null) continue;

                FlowerDefinition flower = loadFlowerDefinition(flowerId, flowerSection);
                if (flower != null && flower.isValid()) {
                    flowers.put(flowerId, flower);
                } else {
                    plugin.getLogger().warning("Flor inválida ignorada: " + flowerId +
                            (flower != null ? " - Errores: " + flower.getValidationErrors() : ""));
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando flor '" + flowerId + "': " + e.getMessage());
            }
        }
    }

    /**
     * Carga una definición de flor específica
     */
    private FlowerDefinition loadFlowerDefinition(String flowerId, ConfigurationSection flowerSection) {
        ConfigurationSection configSection = flowerSection.getConfigurationSection("config");
        ConfigurationSection displaySection = flowerSection.getConfigurationSection("display");

        if (configSection == null || displaySection == null) {
            plugin.getLogger().warning("Flor '" + flowerId + "' debe tener secciones 'config' y 'display'");
            return null;
        }

        // Cargar configuración básica
        String materialName = configSection.getString("type");
        String tierName = configSection.getString("tier");

        Material material = parseMaterial(materialName);
        TierDefinition tier = tiers.get(tierName != null ? tierName.toUpperCase() : "COMMON");

        if (material == null) {
            plugin.getLogger().warning("Material inválido para flor '" + flowerId + "': " + materialName);
            return null;
        }

        if (tier == null) {
            plugin.getLogger().warning("Tier inválido para flor '" + flowerId + "': " + tierName);
            return null;
        }

        int maxLevel = configSection.getInt("max_level", tier.getMaxLevel());
        boolean enchantEffect = configSection.getBoolean("enchant_effect", true);

        // Cargar configuraciones específicas
        FlowerDefinition.ParticleConfig particles = loadParticleConfig(configSection.getConfigurationSection("particles"));
        FlowerDefinition.SoundConfig sounds = loadSoundConfig(configSection.getConfigurationSection("sounds"));
        FlowerDefinition.DisplayConfig display = loadDisplayConfig(displaySection);
        List<FlowerDefinition.FlowerEffect> effects = loadEffects(configSection.getStringList("effects"));
        FlowerDefinition.SpecialConditions specialConditions = loadSpecialConditions(configSection.getConfigurationSection("special_conditions"));
        Map<String, FlowerDefinition.SpecialMechanic> specialMechanics = loadSpecialMechanics(configSection.getConfigurationSection("special_mechanics"));

        return new FlowerDefinition(
                flowerId, material, tier, maxLevel, enchantEffect,
                particles, sounds, display, effects, specialConditions, specialMechanics
        );
    }

    /**
     * Carga configuración de partículas
     */
    private FlowerDefinition.ParticleConfig loadParticleConfig(ConfigurationSection section) {
        if (section == null) {
            return new FlowerDefinition.ParticleConfig(
                    Particle.VILLAGER_HAPPY, Particle.VILLAGER_HAPPY, Particle.VILLAGER_HAPPY, 5
            );
        }

        Particle areaEffect = parseParticle(section.getString("area_effect", "VILLAGER_HAPPY"));
        Particle placement = parseParticle(section.getString("placement", "VILLAGER_HAPPY"));
        Particle ambient = parseParticle(section.getString("ambient", "VILLAGER_HAPPY"));
        int amount = section.getInt("amount", 5);

        return new FlowerDefinition.ParticleConfig(areaEffect, placement, ambient, amount);
    }

    /**
     * Carga configuración de sonidos
     */
    private FlowerDefinition.SoundConfig loadSoundConfig(ConfigurationSection section) {
        if (section == null) {
            return new FlowerDefinition.SoundConfig(
                    Sound.BLOCK_GRASS_PLACE, Sound.ENTITY_PLAYER_LEVELUP, Sound.BLOCK_ENCHANTMENT_TABLE_USE
            );
        }

        Sound placement = parseSound(section.getString("placement", "BLOCK_GRASS_PLACE"));
        Sound activation = parseSound(section.getString("activation", "ENTITY_PLAYER_LEVELUP"));
        Sound ambient = parseSound(section.getString("ambient", "BLOCK_ENCHANTMENT_TABLE_USE"));

        return new FlowerDefinition.SoundConfig(placement, activation, ambient);
    }

    /**
     * Carga configuración de display
     */
    private FlowerDefinition.DisplayConfig loadDisplayConfig(ConfigurationSection section) {
        String name = section.getString("name", "Flor Mágica");
        List<String> lore = section.getStringList("lore");

        return new FlowerDefinition.DisplayConfig(name, lore);
    }

    /**
     * Carga efectos de la flor
     */
    private List<FlowerDefinition.FlowerEffect> loadEffects(List<String> effectStrings) {
        List<FlowerDefinition.FlowerEffect> effects = new ArrayList<>();

        for (String effectString : effectStrings) {
            try {
                // Formato: EFFECT_TYPE:level_formula:duration_formula[:conditions]
                String[] parts = effectString.split(":");
                if (parts.length < 3) continue;

                PotionEffectType effectType = PotionEffectType.getByName(parts[0]);
                if (effectType == null) continue;

                String levelFormula = parts[1];
                String durationFormula = parts[2];

                // Por ahora, condiciones simples (se puede expandir)
                List<FlowerDefinition.EffectCondition> conditions = new ArrayList<>();

                FlowerDefinition.FlowerEffect effect = new FlowerDefinition.FlowerEffect(
                        effectType, levelFormula, durationFormula, conditions
                );

                effects.add(effect);

            } catch (Exception e) {
                plugin.getLogger().warning("Error procesando efecto: " + effectString + " - " + e.getMessage());
            }
        }

        return effects;
    }

    /**
     * Carga condiciones especiales
     */
    private FlowerDefinition.SpecialConditions loadSpecialConditions(ConfigurationSection section) {
        if (section == null) {
            return new FlowerDefinition.SpecialConditions(false, false, false, false, -64, 320);
        }

        return new FlowerDefinition.SpecialConditions(
                section.getBoolean("requires_moonlight", false),
                section.getBoolean("requires_sunlight", false),
                section.getBoolean("disabled_in_nether", false),
                section.getBoolean("disabled_in_end", false),
                section.getInt("min_y_level", -64),
                section.getInt("max_y_level", 320)
        );
    }

    /**
     * Carga mecánicas especiales
     */
    private Map<String, FlowerDefinition.SpecialMechanic> loadSpecialMechanics(ConfigurationSection section) {
        Map<String, FlowerDefinition.SpecialMechanic> mechanics = new HashMap<>();

        if (section == null) return mechanics;

        for (String mechanicName : section.getKeys(false)) {
            ConfigurationSection mechanicSection = section.getConfigurationSection(mechanicName);
            if (mechanicSection == null) continue;

            boolean enabled = mechanicSection.getBoolean("enabled", false);
            Map<String, Object> properties = new HashMap<>();

            // Copiar todas las propiedades excepto 'enabled'
            for (String key : mechanicSection.getKeys(false)) {
                if (!"enabled".equals(key)) {
                    properties.put(key, mechanicSection.get(key));
                }
            }

            FlowerDefinition.SpecialMechanic mechanic = new FlowerDefinition.SpecialMechanic(
                    mechanicName, enabled, properties
            );

            mechanics.put(mechanicName, mechanic);
        }

        return mechanics;
    }

    /**
     * Valida la configuración cargada
     */
    private void validateConfig() {
        List<String> errors = new ArrayList<>();

        // Validar que hay al menos un tier
        if (tiers.isEmpty()) {
            errors.add("Debe haber al menos un tier definido");
        }

        // Validar que hay al menos una flor
        if (flowers.isEmpty()) {
            errors.add("Debe haber al menos una flor definida");
        }

        // Validar que todas las flores tienen tiers válidos
        for (Map.Entry<String, FlowerDefinition> entry : flowers.entrySet()) {
            FlowerDefinition flower = entry.getValue();
            if (!tiers.containsKey(flower.getTier().getName())) {
                errors.add("Flor '" + entry.getKey() + "' usa tier inexistente: " + flower.getTier().getName());
            }
        }

        if (!errors.isEmpty()) {
            plugin.getLogger().warning("Errores de validación en flowers.yml:");
            errors.forEach(error -> plugin.getLogger().warning("  - " + error));
        }
    }

    // =================== MÉTODOS DE UTILIDAD PARA PARSING ===================

    private Material parseMaterial(String materialName) {
        if (materialName == null) return null;
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Particle parseParticle(String particleName) {
        if (particleName == null) return Particle.VILLAGER_HAPPY;
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Partícula inválida: " + particleName + ", usando VILLAGER_HAPPY");
            return Particle.VILLAGER_HAPPY;
        }
    }

    private Sound parseSound(String soundName) {
        if (soundName == null) return Sound.BLOCK_GRASS_PLACE;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Sonido inválido: " + soundName + ", usando BLOCK_GRASS_PLACE");
            return Sound.BLOCK_GRASS_PLACE;
        }
    }

    // =================== GETTERS PÚBLICOS ===================

    public GlobalSettings getGlobalSettings() {
        return globalSettings;
    }

    public Map<String, TierDefinition> getTiers() {
        return new LinkedHashMap<>(tiers);
    }

    public Map<String, FlowerDefinition> getFlowers() {
        return new LinkedHashMap<>(flowers);
    }

    public TierDefinition getTier(String tierName) {
        return tiers.get(tierName.toUpperCase());
    }

    public FlowerDefinition getFlower(String flowerId) {
        return flowers.get(flowerId);
    }

    public boolean hasTier(String tierName) {
        return tiers.containsKey(tierName.toUpperCase());
    }

    public boolean hasFlower(String flowerId) {
        return flowers.containsKey(flowerId);
    }

    /**
     * Recarga la configuración desde el archivo
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Guarda la configuración actual al archivo
     */
    public void saveConfig() {
        try {
            flowersConfig.save(flowersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando flowers.yml: " + e.getMessage());
        }
    }

    // =================== CLASE INTERNA PARA CONFIGURACIÓN GLOBAL ===================

    public static class GlobalSettings {
        private final boolean enableEnchantGlint;
        private final boolean enableAdvancedParticles;
        private final boolean enableCustomSounds;
        private final boolean enableTierRestrictions;
        private final int defaultEffectDuration;
        private final int effectRefreshInterval;
        private final int particleSpawnInterval;
        private final boolean allowFlowerStacking;
        private final int maxFlowersPerPot;

        public GlobalSettings() {
            this(true, true, true, false, 60, 3, 1, false, 1);
        }

        public GlobalSettings(boolean enableEnchantGlint, boolean enableAdvancedParticles,
                              boolean enableCustomSounds, boolean enableTierRestrictions,
                              int defaultEffectDuration, int effectRefreshInterval,
                              int particleSpawnInterval, boolean allowFlowerStacking,
                              int maxFlowersPerPot) {
            this.enableEnchantGlint = enableEnchantGlint;
            this.enableAdvancedParticles = enableAdvancedParticles;
            this.enableCustomSounds = enableCustomSounds;
            this.enableTierRestrictions = enableTierRestrictions;
            this.defaultEffectDuration = defaultEffectDuration;
            this.effectRefreshInterval = effectRefreshInterval;
            this.particleSpawnInterval = particleSpawnInterval;
            this.allowFlowerStacking = allowFlowerStacking;
            this.maxFlowersPerPot = maxFlowersPerPot;
        }

        // Getters
        public boolean isEnchantGlintEnabled() { return enableEnchantGlint; }
        public boolean isAdvancedParticlesEnabled() { return enableAdvancedParticles; }
        public boolean isCustomSoundsEnabled() { return enableCustomSounds; }
        public boolean isTierRestrictionsEnabled() { return enableTierRestrictions; }
        public int getDefaultEffectDuration() { return defaultEffectDuration; }
        public int getEffectRefreshInterval() { return effectRefreshInterval; }
        public int getParticleSpawnInterval() { return particleSpawnInterval; }
        public boolean isFlowerStackingAllowed() { return allowFlowerStacking; }
        public int getMaxFlowersPerPot() { return maxFlowersPerPot; }
    }
}