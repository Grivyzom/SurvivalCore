package gc.grivyzom.survivalcore.flowers.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Representa la definición completa de una flor mágica configurable
 *
 * @author Brocolitx
 * @version 1.0
 */
public class FlowerDefinition {

    // =================== DATOS BÁSICOS ===================
    private final String id;
    private final Material material;
    private final TierDefinition tier;
    private final int maxLevel;
    private final boolean enchantEffect;

    // =================== CONFIGURACIÓN VISUAL ===================
    private final ParticleConfig particles;
    private final SoundConfig sounds;
    private final DisplayConfig display;

    // =================== EFECTOS Y MECÁNICAS ===================
    private final List<FlowerEffect> effects;
    private final SpecialConditions specialConditions;
    private final Map<String, SpecialMechanic> specialMechanics;

    public FlowerDefinition(String id, Material material, TierDefinition tier, int maxLevel,
                            boolean enchantEffect, ParticleConfig particles, SoundConfig sounds,
                            DisplayConfig display, List<FlowerEffect> effects,
                            SpecialConditions specialConditions,
                            Map<String, SpecialMechanic> specialMechanics) {
        this.id = id;
        this.material = material;
        this.tier = tier;
        this.maxLevel = maxLevel > 0 ? maxLevel : tier.getMaxLevel(); // Override del tier si se especifica
        this.enchantEffect = enchantEffect;
        this.particles = particles;
        this.sounds = sounds;
        this.display = display;
        this.effects = new ArrayList<>(effects);
        this.specialConditions = specialConditions;
        this.specialMechanics = new HashMap<>(specialMechanics);
    }

    // =================== GETTERS BÁSICOS ===================

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public TierDefinition getTier() {
        return tier;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean hasEnchantEffect() {
        return enchantEffect;
    }

    public ParticleConfig getParticles() {
        return particles;
    }

    public SoundConfig getSounds() {
        return sounds;
    }

    public DisplayConfig getDisplay() {
        return display;
    }

    public List<FlowerEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    public SpecialConditions getSpecialConditions() {
        return specialConditions;
    }

    public Map<String, SpecialMechanic> getSpecialMechanics() {
        return new HashMap<>(specialMechanics);
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Verifica si la flor tiene un efecto específico
     */
    public boolean hasEffect(PotionEffectType effectType) {
        return effects.stream().anyMatch(effect -> effect.getType().equals(effectType));
    }

    /**
     * Obtiene el efecto de un tipo específico
     */
    public Optional<FlowerEffect> getEffect(PotionEffectType effectType) {
        return effects.stream()
                .filter(effect -> effect.getType().equals(effectType))
                .findFirst();
    }

    /**
     * Verifica si la flor tiene una mecánica especial
     */
    public boolean hasSpecialMechanic(String mechanicName) {
        return specialMechanics.containsKey(mechanicName);
    }

    /**
     * Obtiene una mecánica especial
     */
    public Optional<SpecialMechanic> getSpecialMechanic(String mechanicName) {
        return Optional.ofNullable(specialMechanics.get(mechanicName));
    }

    /**
     * Verifica si la flor es válida para un nivel específico
     */
    public boolean isValidLevel(int level) {
        return level > 0 && level <= maxLevel;
    }

    /**
     * Obtiene el nombre formateado para un nivel específico
     */
    public String getFormattedName(int level) {
        String baseName = ChatColor.translateAlternateColorCodes('&', display.getName());
        String tierColor = tier.getColorCode();

        if (level > 1) {
            return baseName + " " + tierColor + "[Nivel " + level + "]";
        }
        return baseName;
    }

    /**
     * Obtiene el lore formateado para un nivel específico
     */
    public List<String> getFormattedLore(int level) {
        List<String> formattedLore = new ArrayList<>();

        for (String line : display.getLore()) {
            String formatted = ChatColor.translateAlternateColorCodes('&', line);

            // Reemplazar placeholders
            formatted = formatted.replace("{level}", String.valueOf(level));
            formatted = formatted.replace("{tier_color}", tier.getColor());
            formatted = formatted.replace("{tier_name}", tier.getName());
            formatted = formatted.replace("{max_level}", String.valueOf(maxLevel));

            // Reemplazar información de efectos
            for (FlowerEffect effect : effects) {
                int effectLevel = effect.calculateLevel(level);
                int duration = effect.calculateDuration(level);

                formatted = formatted.replace("{" + effect.getType().getName().toLowerCase() + "_level}",
                        String.valueOf(effectLevel));
                formatted = formatted.replace("{duration}", String.valueOf(duration));
            }

            formattedLore.add(formatted);
        }

        return formattedLore;
    }

    // =================== VALIDACIÓN ===================

    /**
     * Valida que la definición de la flor sea correcta
     */
    public boolean isValid() {
        List<String> errors = getValidationErrors();
        return errors.isEmpty();
    }

    /**
     * Obtiene una lista de errores de validación
     */
    public List<String> getValidationErrors() {
        List<String> errors = new ArrayList<>();

        // Validaciones básicas
        if (id == null || id.trim().isEmpty()) {
            errors.add("ID de flor no puede estar vacío");
        }

        if (material == null) {
            errors.add("Material no puede ser nulo");
        }

        if (tier == null) {
            errors.add("Tier no puede ser nulo");
        } else if (!tier.isValid()) {
            errors.add("Tier inválido: " + tier.getValidationErrors());
        }

        if (maxLevel <= 0) {
            errors.add("Nivel máximo debe ser mayor a 0");
        }

        // Validar configuraciones
        if (particles != null && !particles.isValid()) {
            errors.addAll(particles.getValidationErrors());
        }

        if (sounds != null && !sounds.isValid()) {
            errors.addAll(sounds.getValidationErrors());
        }

        if (display != null && !display.isValid()) {
            errors.addAll(display.getValidationErrors());
        }

        // Validar efectos
        if (effects.isEmpty()) {
            errors.add("La flor debe tener al menos un efecto");
        } else {
            for (int i = 0; i < effects.size(); i++) {
                FlowerEffect effect = effects.get(i);
                if (!effect.isValid()) {
                    errors.add("Efecto " + i + " inválido: " + effect.getValidationErrors());
                }
            }
        }

        return errors;
    }

    // =================== CLASES INTERNAS ===================

    /**
     * Configuración de partículas
     */
    public static class ParticleConfig {
        private final Particle areaEffect;
        private final Particle placement;
        private final Particle ambient;
        private final int amount;

        public ParticleConfig(Particle areaEffect, Particle placement, Particle ambient, int amount) {
            this.areaEffect = areaEffect;
            this.placement = placement;
            this.ambient = ambient;
            this.amount = amount;
        }

        public Particle getAreaEffect() { return areaEffect; }
        public Particle getPlacement() { return placement; }
        public Particle getAmbient() { return ambient; }
        public int getAmount() { return amount; }

        public boolean isValid() {
            return areaEffect != null && placement != null && ambient != null && amount > 0;
        }

        public List<String> getValidationErrors() {
            List<String> errors = new ArrayList<>();
            if (areaEffect == null) errors.add("Partícula de área no puede ser nula");
            if (placement == null) errors.add("Partícula de colocación no puede ser nula");
            if (ambient == null) errors.add("Partícula ambiental no puede ser nula");
            if (amount <= 0) errors.add("Cantidad de partículas debe ser mayor a 0");
            return errors;
        }
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

        public boolean isValid() {
            return placement != null && activation != null && ambient != null;
        }

        public List<String> getValidationErrors() {
            List<String> errors = new ArrayList<>();
            if (placement == null) errors.add("Sonido de colocación no puede ser nulo");
            if (activation == null) errors.add("Sonido de activación no puede ser nulo");
            if (ambient == null) errors.add("Sonido ambiental no puede ser nulo");
            return errors;
        }
    }

    /**
     * Configuración de display (nombre y lore)
     */
    public static class DisplayConfig {
        private final String name;
        private final List<String> lore;

        public DisplayConfig(String name, List<String> lore) {
            this.name = name;
            this.lore = new ArrayList<>(lore);
        }

        public String getName() { return name; }
        public List<String> getLore() { return new ArrayList<>(lore); }

        public boolean isValid() {
            return name != null && !name.trim().isEmpty();
        }

        public List<String> getValidationErrors() {
            List<String> errors = new ArrayList<>();
            if (name == null || name.trim().isEmpty()) {
                errors.add("Nombre de display no puede estar vacío");
            }
            return errors;
        }
    }

    /**
     * Efecto de flor mágica
     */
    public static class FlowerEffect {
        private final PotionEffectType type;
        private final String levelFormula;
        private final String durationFormula;
        private final List<EffectCondition> conditions;

        public FlowerEffect(PotionEffectType type, String levelFormula, String durationFormula,
                            List<EffectCondition> conditions) {
            this.type = type;
            this.levelFormula = levelFormula;
            this.durationFormula = durationFormula;
            this.conditions = new ArrayList<>(conditions);
        }

        public PotionEffectType getType() { return type; }
        public String getLevelFormula() { return levelFormula; }
        public String getDurationFormula() { return durationFormula; }
        public List<EffectCondition> getConditions() { return new ArrayList<>(conditions); }

        /**
         * Calcula el nivel del efecto para un nivel de flor específico
         */
        public int calculateLevel(int flowerLevel) {
            return FormulaCalculator.calculate(levelFormula, flowerLevel);
        }

        /**
         * Calcula la duración del efecto para un nivel de flor específico
         */
        public int calculateDuration(int flowerLevel) {
            return FormulaCalculator.calculate(durationFormula, flowerLevel);
        }

        public boolean isValid() {
            return type != null && levelFormula != null && durationFormula != null;
        }

        public List<String> getValidationErrors() {
            List<String> errors = new ArrayList<>();
            if (type == null) errors.add("Tipo de efecto no puede ser nulo");
            if (levelFormula == null) errors.add("Fórmula de nivel no puede ser nula");
            if (durationFormula == null) errors.add("Fórmula de duración no puede ser nula");
            return errors;
        }
    }

    /**
     * Condiciones especiales para el funcionamiento de la flor
     */
    public static class SpecialConditions {
        private final boolean requiresMoonlight;
        private final boolean requiresSunlight;
        private final boolean disabledInNether;
        private final boolean disabledInEnd;
        private final int minYLevel;
        private final int maxYLevel;

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

        // Getters
        public boolean requiresMoonlight() { return requiresMoonlight; }
        public boolean requiresSunlight() { return requiresSunlight; }
        public boolean isDisabledInNether() { return disabledInNether; }
        public boolean isDisabledInEnd() { return disabledInEnd; }
        public int getMinYLevel() { return minYLevel; }
        public int getMaxYLevel() { return maxYLevel; }
    }

    /**
     * Condición específica para efectos
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
     * Mecánica especial personalizada
     */
    public static class SpecialMechanic {
        private final String name;
        private final boolean enabled;
        private final Map<String, Object> properties;

        public SpecialMechanic(String name, boolean enabled, Map<String, Object> properties) {
            this.name = name;
            this.enabled = enabled;
            this.properties = new HashMap<>(properties);
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public Map<String, Object> getProperties() { return new HashMap<>(properties); }

        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key, Class<T> type, T defaultValue) {
            Object value = properties.get(key);
            if (type.isInstance(value)) {
                return (T) value;
            }
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return String.format("FlowerDefinition{id='%s', material=%s, tier=%s, maxLevel=%d}",
                id, material, tier.getName(), maxLevel);
    }
}