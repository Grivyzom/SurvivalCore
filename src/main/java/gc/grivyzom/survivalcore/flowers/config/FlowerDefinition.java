package gc.grivyzom.survivalcore.flowers.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

/**
 * Definición completa de una flor mágica configurable
 *
 * @author Brocolitx
 * @version 1.0
 */
public class FlowerDefinition {

    private final String id;
    private final FlowerConfig config;
    private final DisplayConfig display;
    private final List<FlowerEffect> effects;
    private final ParticleConfig particles;
    private final SoundConfig sounds;
    private final SpecialConditions specialConditions;
    private final Map<String, SpecialMechanic> specialMechanics;

    public FlowerDefinition(String id, FlowerConfig config, DisplayConfig display,
                            List<FlowerEffect> effects, ParticleConfig particles,
                            SoundConfig sounds, SpecialConditions specialConditions,
                            Map<String, SpecialMechanic> specialMechanics) {
        this.id = id;
        this.config = config;
        this.display = display;
        this.effects = effects;
        this.particles = particles;
        this.sounds = sounds;
        this.specialConditions = specialConditions;
        this.specialMechanics = specialMechanics;
    }

    // =================== GETTERS ===================

    public String getId() { return id; }
    public FlowerConfig getConfig() { return config; }
    public DisplayConfig getDisplay() { return display; }
    public List<FlowerEffect> getEffects() { return effects; }
    public ParticleConfig getParticles() { return particles; }
    public SoundConfig getSounds() { return sounds; }
    public SpecialConditions getSpecialConditions() { return specialConditions; }
    public Map<String, SpecialMechanic> getSpecialMechanics() { return specialMechanics; }

    // Métodos de conveniencia
    public TierDefinition getTier() { return config.getTier(); }
    public Material getMaterial() { return config.getType(); }
    public int getMaxLevel() { return config.getMaxLevel(); }

    // =================== CLASES INTERNAS ===================

    /**
     * Configuración básica de la flor
     */
    public static class FlowerConfig {
        private final Material type;
        private final TierDefinition tier;
        private final int maxLevel;
        private final boolean enchantEffect;

        public FlowerConfig(Material type, TierDefinition tier, int maxLevel, boolean enchantEffect) {
            this.type = type;
            this.tier = tier;
            this.maxLevel = maxLevel;
            this.enchantEffect = enchantEffect;
        }

        public Material getType() { return type; }
        public TierDefinition getTier() { return tier; }
        public int getMaxLevel() { return maxLevel; }
        public boolean hasEnchantEffect() { return enchantEffect; }
    }

    /**
     * Configuración de display (nombre, lore, etc.)
     */
    public static class DisplayConfig {
        private final String name;
        private final List<String> lore;

        public DisplayConfig(String name, List<String> lore) {
            this.name = name;
            this.lore = lore;
        }

        public String getName() { return name; }
        public List<String> getLore() { return lore; }
    }

    /**
     * Definición de un efecto de la flor
     */
    public static class FlowerEffect {
        private final PotionEffectType type;
        private final String levelFormula;
        private final String durationFormula;
        private final List<EffectCondition> conditions;

        public FlowerEffect(PotionEffectType type, String levelFormula,
                            String durationFormula, List<EffectCondition> conditions) {
            this.type = type;
            this.levelFormula = levelFormula;
            this.durationFormula = durationFormula;
            this.conditions = conditions;
        }

        public PotionEffectType getType() { return type; }
        public String getLevelFormula() { return levelFormula; }
        public String getDurationFormula() { return durationFormula; }
        public List<EffectCondition> getConditions() { return conditions; }

        /**
         * Calcula el nivel del efecto basado en el nivel de la flor
         */
        public int calculateLevel(int flowerLevel) {
            return FormulaCalculator.evaluateFormula(levelFormula, flowerLevel, 1, 1);
        }

        /**
         * Calcula la duración del efecto basado en el nivel de la flor
         */
        public int calculateDuration(int flowerLevel) {
            return FormulaCalculator.evaluateFormula(durationFormula, flowerLevel, 1, 1);
        }
    }

    /**
     * Condición para activar un efecto
     */
    public static class EffectCondition {
        private final String type;
        private final String value;

        public EffectCondition(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() { return type; }
        public String getValue() { return value; }
    }

    /**
     * Configuración de partículas
     */
    public static class ParticleConfig {
        private final Particle areaEffect;
        private final Particle placement;
        private final Particle ambient;
        private final int amount;

        public ParticleConfig(Particle areaEffect, Particle placement,
                              Particle ambient, int amount) {
            this.areaEffect = areaEffect;
            this.placement = placement;
            this.ambient = ambient;
            this.amount = amount;
        }

        public Particle getAreaEffect() { return areaEffect; }
        public Particle getPlacement() { return placement; }
        public Particle getAmbient() { return ambient; }
        public int getAmount() { return amount; }
    }

    /**
     * Configuración de sonidos
     */
    public static class SoundConfig {
        private final Sound placement;
        private final Sound activation;
        private final Sound ambient;

        public SoundConfig(Sound placement, Sound activation, Sound ambient) {
            this.placement = placement;
            this.activation = activation;
            this.ambient = ambient;
        }

        public Sound getPlacement() { return placement; }
        public Sound getActivation() { return activation; }
        public Sound getAmbient() { return ambient; }
    }

    /**
     * Condiciones especiales para la flor
     */
    public static class SpecialConditions {
        private final boolean requiresMoonlight;
        private final boolean requiresSunlight;
        private final boolean disabledInNether;
        private final boolean disabledInEnd;
        private final int minYLevel;
        private final int maxYLevel;

        public SpecialConditions() {
            this(false, false, false, false, -64, 320);
        }

        public SpecialConditions(boolean requiresMoonlight, boolean requiresSunlight,
                                 boolean disabledInNether, boolean disabledInEnd,
                                 int minYLevel, int maxYLevel) {
            this.requiresMoonlight = requiresMoonlight;
            this.requiresSunlight = requiresSunlight;
            this.disabledInNether = disabledInNether;
            this.disabledInEnd = disabledInEnd;
            this.minYLevel = minYLevel;
            this.maxYLevel = maxYLevel;
        }

        public boolean requiresMoonlight() { return requiresMoonlight; }
        public boolean requiresSunlight() { return requiresSunlight; }
        public boolean isDisabledInNether() { return disabledInNether; }
        public boolean isDisabledInEnd() { return disabledInEnd; }
        public int getMinYLevel() { return minYLevel; }
        public int getMaxYLevel() { return maxYLevel; }
    }

    /**
     * Mecánica especial de la flor
     */
    public static class SpecialMechanic {
        private final String name;
        private final boolean enabled;
        private final Map<String, Object> properties;

        public SpecialMechanic(String name, boolean enabled, Map<String, Object> properties) {
            this.name = name;
            this.enabled = enabled;
            this.properties = properties;
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public Map<String, Object> getProperties() { return properties; }

        /**
         * Obtiene una propiedad específica con tipo seguro
         */
        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key, Class<T> type, T defaultValue) {
            Object value = properties.get(key);
            if (value != null && type.isInstance(value)) {
                return (T) value;
            }
            return defaultValue;
        }

        /**
         * Verifica si tiene una propiedad específica
         */
        public boolean hasProperty(String key) {
            return properties.containsKey(key);
        }
    }

    @Override
    public String toString() {
        return String.format("FlowerDefinition{id='%s', tier='%s', maxLevel=%d, effects=%d}",
                id, config.getTier().getName(), config.getMaxLevel(), effects.size()); // 🔧 CORREGIDO: getName() en lugar de name()
    }
}

// ═══════════════════════════════════════════════════════════════════

/**
 * Definición de un tier (calidad) de flor
 *
 * @author Brocolitx
 * @version 1.0
 */
