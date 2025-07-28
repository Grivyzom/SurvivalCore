package gc.grivyzom.survivalcore.flowers.config;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor principal de configuración para flores mágicas configurables
 * Lee y administra flowers.yml
 *
 * @author Brocolitx
 * @version 1.0
 */
public class FlowerConfigManager {

    private final Main plugin;
    private YamlConfiguration flowersConfig;

    // Cache de configuraciones cargadas
    private final Map<String, FlowerDefinition> flowers = new ConcurrentHashMap<>();
    private final Map<String, TierDefinition> tiers = new ConcurrentHashMap<>();
    private GlobalSettings globalSettings;

    public FlowerConfigManager(Main plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    /**
     * Carga toda la configuración desde flowers.yml
     */
    public void loadConfiguration() {
        try {
            File flowersFile = new File(plugin.getDataFolder(), "flowers.yml");

            if (!flowersFile.exists()) {
                plugin.getLogger().warning("flowers.yml no encontrado, creando uno por defecto...");
                plugin.saveResource("flowers.yml", false);
            }

            flowersConfig = YamlConfiguration.loadConfiguration(flowersFile);

            // Limpiar cache anterior
            flowers.clear();
            tiers.clear();

            // Cargar configuraciones
            loadGlobalSettings();
            loadTiers();
            loadFlowers();

            plugin.getLogger().info("Configuración de flores cargada correctamente:");
            plugin.getLogger().info("  - " + tiers.size() + " tiers definidos");
            plugin.getLogger().info("  - " + flowers.size() + " flores configuradas");

        } catch (Exception e) {
            plugin.getLogger().severe("Error cargando flowers.yml: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Fallo crítico cargando configuración de flores", e);
        }
    }

    /**
     * Carga la configuración global
     */
    private void loadGlobalSettings() {
        ConfigurationSection globalSection = flowersConfig.getConfigurationSection("global_settings");
        if (globalSection == null) {
            plugin.getLogger().warning("Sección 'global_settings' no encontrada, usando valores por defecto");
            globalSettings = new GlobalSettings();
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

        plugin.getLogger().info("Configuración global cargada correctamente");
    }

    /**
     * Carga todas las definiciones de tiers
     */
    private void loadTiers() {
        ConfigurationSection tiersSection = flowersConfig.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().severe("Sección 'tiers' no encontrada en flowers.yml");
            throw new RuntimeException("Configuración de tiers faltante");
        }

        for (String tierName : tiersSection.getKeys(false)) {
            ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierName);
            if (tierSection == null) continue;

            try {
                TierDefinition tier = new TierDefinition(
                        tierName,
                        tierSection.getString("color", "&f"),
                        tierSection.getInt("max_level", 5),
                        tierSection.getDouble("effect_multiplier", 1.0),
                        tierSection.getDouble("particle_multiplier", 1.0),
                        tierSection.getInt("rarity_weight", 100)
                );

                tiers.put(tierName.toUpperCase(), tier);
                plugin.getLogger().info("Tier cargado: " + tierName);

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando tier '" + tierName + "': " + e.getMessage());
            }
        }

        if (tiers.isEmpty()) {
            plugin.getLogger().severe("No se cargaron tiers válidos");
            throw new RuntimeException("No hay tiers válidos en la configuración");
        }
    }

    /**
     * Carga todas las definiciones de flores
     */
    private void loadFlowers() {
        ConfigurationSection flowersSection = flowersConfig.getConfigurationSection("flowers");
        if (flowersSection == null) {
            plugin.getLogger().severe("Sección 'flowers' no encontrada en flowers.yml");
            throw new RuntimeException("Configuración de flores faltante");
        }

        for (String flowerId : flowersSection.getKeys(false)) {
            ConfigurationSection flowerSection = flowersSection.getConfigurationSection(flowerId);
            if (flowerSection == null) continue;

            try {
                FlowerDefinition flower = loadFlowerDefinition(flowerId, flowerSection);
                flowers.put(flowerId.toLowerCase(), flower);
                plugin.getLogger().info("Flor cargada: " + flowerId + " (" + flower.getTier().getName() + ")");

            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando flor '" + flowerId + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (flowers.isEmpty()) {
            plugin.getLogger().warning("No se cargaron flores válidas");
        }
    }

    /**
     * Carga una definición específica de flor
     */
    private FlowerDefinition loadFlowerDefinition(String flowerId, ConfigurationSection flowerSection) {
        // Secciones principales
        ConfigurationSection configSection = flowerSection.getConfigurationSection("config");
        ConfigurationSection displaySection = flowerSection.getConfigurationSection("display");

        if (configSection == null || displaySection == null) {
            throw new RuntimeException("Secciones 'config' o 'display' faltantes para flor: " + flowerId);
        }

        // Cargar configuración básica
        FlowerDefinition.FlowerConfig config = loadFlowerConfig(configSection);
        FlowerDefinition.DisplayConfig display = loadDisplayConfig(displaySection);

        // Cargar efectos
        List<FlowerDefinition.FlowerEffect> effects = loadFlowerEffects(configSection);

        // Cargar configuraciones opcionales
        FlowerDefinition.ParticleConfig particles = loadParticleConfig(configSection);
        FlowerDefinition.SoundConfig sounds = loadSoundConfig(configSection);
        FlowerDefinition.SpecialConditions specialConditions = loadSpecialConditions(configSection);
        Map<String, FlowerDefinition.SpecialMechanic> specialMechanics = loadSpecialMechanics(configSection);

        return new FlowerDefinition(
                flowerId,
                config,
                display,
                effects,
                particles,
                sounds,
                specialConditions,
                specialMechanics
        );
    }

    /**
     * Carga la configuración básica de una flor
     */
    private FlowerDefinition.FlowerConfig loadFlowerConfig(ConfigurationSection configSection) {
        String materialName = configSection.getString("type", "DANDELION");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido: " + materialName + ", usando DANDELION");
            material = Material.DANDELION;
        }

        String tierName = configSection.getString("tier", "COMMON").toUpperCase();
        TierDefinition tier = tiers.get(tierName);
        if (tier == null) {
            plugin.getLogger().warning("Tier inválido: " + tierName + ", usando COMMON");
            tier = tiers.get("COMMON");
            if (tier == null) {
                throw new RuntimeException("Tier COMMON no encontrado - configuración corrupta");
            }
        }

        int maxLevel = configSection.getInt("max_level", tier.getMaxLevel());
        boolean enchantEffect = configSection.getBoolean("enchant_effect", true);

        return new FlowerDefinition.FlowerConfig(material, tier, maxLevel, enchantEffect);
    }

    /**
     * Carga la configuración de display de una flor
     */
    private FlowerDefinition.DisplayConfig loadDisplayConfig(ConfigurationSection displaySection) {
        String name = displaySection.getString("name", "&fFlor Mágica");
        List<String> lore = displaySection.getStringList("lore");
        if (lore.isEmpty()) {
            lore = Arrays.asList(
                    "&7Una flor mágica misteriosa",
                    "&7con poderes sobrenaturales."
            );
        }

        return new FlowerDefinition.DisplayConfig(name, lore);
    }

    /**
     * Carga los efectos de una flor
     */
    private List<FlowerDefinition.FlowerEffect> loadFlowerEffects(ConfigurationSection configSection) {
        List<String> effectStrings = configSection.getStringList("effects");
        List<FlowerDefinition.FlowerEffect> effects = new ArrayList<>();

        for (String effectString : effectStrings) {
            try {
                FlowerDefinition.FlowerEffect effect = parseEffectString(effectString);
                effects.add(effect);
            } catch (Exception e) {
                plugin.getLogger().warning("Error parseando efecto: " + effectString + " - " + e.getMessage());
            }
        }

        return effects;
    }

    /**
     * Parsea un string de efecto al formato interno
     */
    private FlowerDefinition.FlowerEffect parseEffectString(String effectString) {
        // Formato: "EFFECT_TYPE:level_formula:duration_formula"
        // Ejemplo: "SPEED:{flower_level}:60"
        String[] parts = effectString.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Formato de efecto inválido: " + effectString);
        }

        String effectTypeName = parts[0].trim().toUpperCase();
        PotionEffectType effectType = PotionEffectType.getByName(effectTypeName);
        if (effectType == null) {
            throw new IllegalArgumentException("Tipo de efecto inválido: " + effectTypeName);
        }

        String levelFormula = parts[1].trim();
        String durationFormula = parts[2].trim();

        // Cargar condiciones (por ahora vacías, se pueden expandir)
        List<FlowerDefinition.EffectCondition> conditions = new ArrayList<>();

        return new FlowerDefinition.FlowerEffect(effectType, levelFormula, durationFormula, conditions);
    }

    /**
     * Carga la configuración de partículas
     */
    private FlowerDefinition.ParticleConfig loadParticleConfig(ConfigurationSection configSection) {
        ConfigurationSection particlesSection = configSection.getConfigurationSection("particles");
        if (particlesSection == null) {
            // Valores por defecto
            return new FlowerDefinition.ParticleConfig(
                    Particle.VILLAGER_HAPPY,
                    Particle.HEART,
                    Particle.ENCHANTMENT_TABLE,
                    5
            );
        }

        Particle areaEffect = parseParticle(particlesSection.getString("area_effect", "VILLAGER_HAPPY"));
        Particle placement = parseParticle(particlesSection.getString("placement", "HEART"));
        Particle ambient = parseParticle(particlesSection.getString("ambient", "ENCHANTMENT_TABLE"));
        int amount = particlesSection.getInt("amount", 5);

        return new FlowerDefinition.ParticleConfig(areaEffect, placement, ambient, amount);
    }

    /**
     * Convierte string a Particle de forma segura
     */
    private Particle parseParticle(String particleName) {
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Partícula inválida: " + particleName + ", usando VILLAGER_HAPPY");
            return Particle.VILLAGER_HAPPY;
        }
    }

    /**
     * Carga la configuración de sonidos
     */
    private FlowerDefinition.SoundConfig loadSoundConfig(ConfigurationSection configSection) {
        ConfigurationSection soundsSection = configSection.getConfigurationSection("sounds");
        if (soundsSection == null) {
            // Valores por defecto
            return new FlowerDefinition.SoundConfig(
                    Sound.BLOCK_GRASS_PLACE,
                    Sound.ENTITY_PLAYER_LEVELUP,
                    Sound.BLOCK_ENCHANTMENT_TABLE_USE
            );
        }

        Sound placement = parseSound(soundsSection.getString("placement", "BLOCK_GRASS_PLACE"));
        Sound activation = parseSound(soundsSection.getString("activation", "ENTITY_PLAYER_LEVELUP"));
        Sound ambient = parseSound(soundsSection.getString("ambient", "BLOCK_ENCHANTMENT_TABLE_USE"));

        return new FlowerDefinition.SoundConfig(placement, activation, ambient);
    }

    /**
     * Convierte string a Sound de forma segura
     */
    private Sound parseSound(String soundName) {
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Sonido inválido: " + soundName + ", usando BLOCK_GRASS_PLACE");
            return Sound.BLOCK_GRASS_PLACE;
        }
    }

    /**
     * Carga las condiciones especiales
     */
    private FlowerDefinition.SpecialConditions loadSpecialConditions(ConfigurationSection configSection) {
        ConfigurationSection conditionsSection = configSection.getConfigurationSection("special_conditions");
        if (conditionsSection == null) {
            return new FlowerDefinition.SpecialConditions();
        }

        return new FlowerDefinition.SpecialConditions(
                conditionsSection.getBoolean("requires_moonlight", false),
                conditionsSection.getBoolean("requires_sunlight", false),
                conditionsSection.getBoolean("disabled_in_nether", false),
                conditionsSection.getBoolean("disabled_in_end", false),
                conditionsSection.getInt("min_y_level", -64),
                conditionsSection.getInt("max_y_level", 320)
        );
    }

    /**
     * Carga las mecánicas especiales
     */
    private Map<String, FlowerDefinition.SpecialMechanic> loadSpecialMechanics(ConfigurationSection configSection) {
        ConfigurationSection mechanicsSection = configSection.getConfigurationSection("special_mechanics");
        Map<String, FlowerDefinition.SpecialMechanic> mechanics = new HashMap<>();

        if (mechanicsSection == null) {
            return mechanics;
        }

        for (String mechanicName : mechanicsSection.getKeys(false)) {
            ConfigurationSection mechanicSection = mechanicsSection.getConfigurationSection(mechanicName);
            if (mechanicSection == null) continue;

            boolean enabled = mechanicSection.getBoolean("enabled", false);
            Map<String, Object> properties = new HashMap<>();

            // Cargar todas las propiedades de la mecánica
            for (String key : mechanicSection.getKeys(false)) {
                if (!key.equals("enabled")) {
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

    // =================== MÉTODOS PÚBLICOS DE ACCESO ===================

    /**
     * Obtiene una flor por su ID
     */
    public FlowerDefinition getFlower(String flowerId) {
        return flowers.get(flowerId.toLowerCase());
    }

    /**
     * Verifica si existe una flor
     */
    public boolean hasFlower(String flowerId) {
        return flowers.containsKey(flowerId.toLowerCase());
    }

    /**
     * Obtiene todos los IDs de flores disponibles
     */
    public Set<String> getAllFlowerIds() {
        return new HashSet<>(flowers.keySet());
    }

    /**
     * Obtiene un tier por su nombre
     */
    public TierDefinition getTier(String tierName) {
        return tiers.get(tierName.toUpperCase());
    }

    /**
     * Obtiene todos los nombres de tiers disponibles
     */
    public Set<String> getAllTierNames() {
        return new HashSet<>(tiers.keySet());
    }

    /**
     * Obtiene el color de un tier
     */
    public String getTierColor(String tierName) {
        TierDefinition tier = getTier(tierName);
        return tier != null ? tier.getColor() : "&f";
    }

    /**
     * Obtiene la configuración global
     */
    public GlobalSettings getGlobalSettings() {
        return globalSettings;
    }

    /**
     * Recarga toda la configuración
     */
    public void reloadConfig() {
        plugin.getLogger().info("Recargando configuración de flores...");
        loadConfiguration();
    }

    // =================== CLASE INTERNA PARA CONFIGURACIÓN GLOBAL ===================

    public static class GlobalSettings {
        private final boolean enchantGlintEnabled;
        private final boolean advancedParticlesEnabled;
        private final boolean customSoundsEnabled;
        private final boolean tierRestrictionsEnabled;
        private final int defaultEffectDuration;
        private final int effectRefreshInterval;
        private final int particleSpawnInterval;
        private final boolean allowFlowerStacking;
        private final int maxFlowersPerPot;

        public GlobalSettings() {
            this(true, true, true, false, 60, 3, 1, false, 1);
        }

        public GlobalSettings(boolean enchantGlintEnabled, boolean advancedParticlesEnabled,
                              boolean customSoundsEnabled, boolean tierRestrictionsEnabled,
                              int defaultEffectDuration, int effectRefreshInterval,
                              int particleSpawnInterval, boolean allowFlowerStacking,
                              int maxFlowersPerPot) {
            this.enchantGlintEnabled = enchantGlintEnabled;
            this.advancedParticlesEnabled = advancedParticlesEnabled;
            this.customSoundsEnabled = customSoundsEnabled;
            this.tierRestrictionsEnabled = tierRestrictionsEnabled;
            this.defaultEffectDuration = defaultEffectDuration;
            this.effectRefreshInterval = effectRefreshInterval;
            this.particleSpawnInterval = particleSpawnInterval;
            this.allowFlowerStacking = allowFlowerStacking;
            this.maxFlowersPerPot = maxFlowersPerPot;
        }

        // Getters
        public boolean isEnchantGlintEnabled() { return enchantGlintEnabled; }
        public boolean isAdvancedParticlesEnabled() { return advancedParticlesEnabled; }
        public boolean isCustomSoundsEnabled() { return customSoundsEnabled; }
        public boolean isTierRestrictionsEnabled() { return tierRestrictionsEnabled; }
        public int getDefaultEffectDuration() { return defaultEffectDuration; }
        public int getEffectRefreshInterval() { return effectRefreshInterval; }
        public int getParticleSpawnInterval() { return particleSpawnInterval; }
        public boolean isAllowFlowerStacking() { return allowFlowerStacking; }
        public int getMaxFlowersPerPot() { return maxFlowersPerPot; }
    }
}