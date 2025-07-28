package gc.grivyzom.survivalcore.flowers.config;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowers.MagicFlowerFactory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * Factory que crea flores mágicas basadas en las configuraciones YAML
 * Integra el sistema nuevo con el existente
 *
 * @author Brocolitx
 * @version 1.0
 */
public class ConfigurableFlowerFactory {

    private final Main plugin;
    private final FlowerConfigManager configManager;
    private final NamespacedKey isMagicFlowerKey;
    private final NamespacedKey flowerIdKey;
    private final NamespacedKey flowerLevelKey;
    private final NamespacedKey isConfigurableKey;

    public ConfigurableFlowerFactory(Main plugin, FlowerConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.isMagicFlowerKey = new NamespacedKey(plugin, "is_magic_flower");
        this.flowerIdKey = new NamespacedKey(plugin, "flower_id");
        this.flowerLevelKey = new NamespacedKey(plugin, "flower_level");
        this.isConfigurableKey = new NamespacedKey(plugin, "is_configurable_flower");
    }

    /**
     * Crea una flor mágica configurable
     */
    public ItemStack createConfigurableFlower(String flowerId, int level) {
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) {
            plugin.getLogger().warning("Definición de flor no encontrada: " + flowerId);
            return null;
        }

        // Validar nivel
        if (!flowerDef.isValidLevel(level)) {
            plugin.getLogger().warning("Nivel inválido para flor " + flowerId + ": " + level);
            level = Math.max(1, Math.min(level, flowerDef.getMaxLevel()));
        }

        // Crear el item base
        ItemStack flower = new ItemStack(flowerDef.getMaterial());
        ItemMeta meta = flower.getItemMeta();

        if (meta == null) {
            plugin.getLogger().severe("No se pudo obtener ItemMeta para material: " + flowerDef.getMaterial());
            return null;
        }

        // Configurar nombre con formato del tier
        String formattedName = flowerDef.getFormattedName(level);
        meta.setDisplayName(formattedName);

        // Configurar lore
        List<String> formattedLore = flowerDef.getFormattedLore(level);
        meta.setLore(formattedLore);

        // Añadir efecto de encantamiento si está habilitado
        if (flowerDef.hasEnchantEffect() && configManager.getGlobalSettings().isEnchantGlintEnabled()) {
            meta.addEnchant(Enchantment.LURE, 1, true);
        }

        // Configurar datos persistentes
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(isMagicFlowerKey, PersistentDataType.BYTE, (byte) 1);
        container.set(flowerIdKey, PersistentDataType.STRING, flowerId);
        container.set(flowerLevelKey, PersistentDataType.INTEGER, level);
        container.set(isConfigurableKey, PersistentDataType.BYTE, (byte) 1);

        flower.setItemMeta(meta);
        return flower;
    }

    /**
     * Verifica si un item es una flor mágica configurable
     */
    public boolean isConfigurableFlower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(isConfigurableKey, PersistentDataType.BYTE) &&
                container.has(isMagicFlowerKey, PersistentDataType.BYTE);
    }

    /**
     * Verifica si un item es una flor mágica (configurable o tradicional)
     */
    public boolean isMagicFlower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(isMagicFlowerKey, PersistentDataType.BYTE);
    }

    /**
     * Obtiene el ID de una flor mágica
     */
    public String getFlowerId(ItemStack item) {
        if (!isMagicFlower(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(flowerIdKey, PersistentDataType.STRING);
    }

    /**
     * Obtiene el nivel de una flor mágica
     */
    public int getFlowerLevel(ItemStack item) {
        if (!isMagicFlower(item)) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(flowerLevelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * Obtiene la definición de una flor a partir de un item
     */
    public Optional<FlowerDefinition> getFlowerDefinition(ItemStack item) {
        String flowerId = getFlowerId(item);
        if (flowerId == null) {
            return Optional.empty();
        }

        FlowerDefinition definition = configManager.getFlower(flowerId);
        return Optional.ofNullable(definition);
    }

    /**
     * Mejora una flor al siguiente nivel
     */
    public ItemStack upgradeFlower(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return flower;
        }

        String flowerId = getFlowerId(flower);
        int currentLevel = getFlowerLevel(flower);
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null || currentLevel >= flowerDef.getMaxLevel()) {
            return flower; // No se puede mejorar más
        }

        return createConfigurableFlower(flowerId, currentLevel + 1);
    }

    /**
     * Verifica si una flor se puede mejorar
     */
    public boolean canUpgrade(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return false;
        }

        String flowerId = getFlowerId(flower);
        int currentLevel = getFlowerLevel(flower);
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        return flowerDef != null && currentLevel < flowerDef.getMaxLevel();
    }

    /**
     * Convierte una flor tradicional a una configurable (migración)
     */
    public ItemStack convertLegacyFlower(ItemStack legacyFlower) {
        // Intentar mapear flores tradicionales a configurables
        String legacyId = tryGetLegacyFlowerId(legacyFlower);
        if (legacyId == null) {
            return legacyFlower; // No es una flor conocida
        }

        // Buscar equivalente configurable
        String configurableId = mapLegacyToConfigurable(legacyId);
        if (configurableId == null || !configManager.hasFlower(configurableId)) {
            return legacyFlower; // No hay equivalente configurable
        }

        int level = getLegacyFlowerLevel(legacyFlower);
        return createConfigurableFlower(configurableId, level);
    }

    /**
     * Intenta obtener el ID de una flor tradicional
     */
    private String tryGetLegacyFlowerId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        // Usar la factory tradicional para verificar
        MagicFlowerFactory legacyFactory = new MagicFlowerFactory(plugin);
        if (!legacyFactory.isMagicFlower(item)) {
            return null;
        }

        return legacyFactory.getFlowerId(item);
    }

    /**
     * Obtiene el nivel de una flor tradicional
     */
    private int getLegacyFlowerLevel(ItemStack item) {
        MagicFlowerFactory legacyFactory = new MagicFlowerFactory(plugin);
        return legacyFactory.getFlowerLevel(item);
    }

    /**
     * Mapea IDs de flores tradicionales a configurables
     */
    private String mapLegacyToConfigurable(String legacyId) {
        switch (legacyId.toLowerCase()) {
            case "love_flower":
                return "love_flower"; // Mismo ID

            case "healing_flower":
                return "healing_flower";

            case "speed_flower":
                return "speed_flower";

            case "strength_flower":
                return "strength_flower";

            case "night_vision_flower":
                return "night_vision_flower";

            default:
                return null; // No hay mapeo
        }
    }

    /**
     * Crea todas las flores disponibles para testing/admin
     */
    public java.util.Map<String, ItemStack> createAllFlowers(int level) {
        java.util.Map<String, ItemStack> flowers = new java.util.HashMap<>();

        for (String flowerId : configManager.getFlowers().keySet()) {
            ItemStack flower = createConfigurableFlower(flowerId, level);
            if (flower != null) {
                flowers.put(flowerId, flower);
            }
        }

        return flowers;
    }

    /**
     * Obtiene información detallada de una flor para mostrar al jugador
     */
    public String getFlowerInfo(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return "Esta no es una flor mágica configurable.";
        }

        String flowerId = getFlowerId(flower);
        int level = getFlowerLevel(flower);
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) {
            return "Definición de flor no encontrada.";
        }

        StringBuilder info = new StringBuilder();
        info.append(ChatColor.LIGHT_PURPLE).append("=== INFORMACIÓN DE FLOR MÁGICA ===\n");
        info.append(ChatColor.WHITE).append("ID: ").append(ChatColor.GRAY).append(flowerId).append("\n");
        info.append(ChatColor.WHITE).append("Nivel: ").append(ChatColor.AQUA).append(level)
                .append(ChatColor.GRAY).append("/").append(flowerDef.getMaxLevel()).append("\n");
        info.append(ChatColor.WHITE).append("Tier: ").append(flowerDef.getTier().getFormattedName()).append("\n");
        info.append(ChatColor.WHITE).append("Material: ").append(ChatColor.YELLOW)
                .append(flowerDef.getMaterial().name()).append("\n");

        info.append(ChatColor.WHITE).append("Efectos:\n");
        for (FlowerDefinition.FlowerEffect effect : flowerDef.getEffects()) {
            int effectLevel = effect.calculateLevel(level);
            int duration = effect.calculateDuration(level);
            info.append(ChatColor.GRAY).append("  • ")
                    .append(ChatColor.GREEN).append(effect.getType().getName())
                    .append(ChatColor.WHITE).append(" (Nivel ").append(effectLevel)
                    .append(", ").append(duration).append("s)\n");
        }

        if (!flowerDef.getSpecialMechanics().isEmpty()) {
            info.append(ChatColor.WHITE).append("Mecánicas especiales:\n");
            for (FlowerDefinition.SpecialMechanic mechanic : flowerDef.getSpecialMechanics().values()) {
                if (mechanic.isEnabled()) {
                    info.append(ChatColor.GRAY).append("  • ")
                            .append(ChatColor.LIGHT_PURPLE).append(mechanic.getName()).append("\n");
                }
            }
        }

        return info.toString();
    }

    /**
     * Valida que una flor configurable sea válida
     */
    public boolean isValidConfigurableFlower(ItemStack item) {
        if (!isConfigurableFlower(item)) {
            return false;
        }

        String flowerId = getFlowerId(item);
        int level = getFlowerLevel(item);

        // Verificar que la definición existe
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) {
            return false;
        }

        // Verificar que el nivel es válido
        return flowerDef.isValidLevel(level);
    }

    /**
     * Repara una flor configurable (actualiza metadatos si es necesario)
     */
    public ItemStack repairConfigurableFlower(ItemStack item) {
        if (!isConfigurableFlower(item)) {
            return item;
        }

        String flowerId = getFlowerId(item);
        int level = getFlowerLevel(item);

        // Recrear la flor con la configuración actual
        return createConfigurableFlower(flowerId, level);
    }

    /**
     * Obtiene estadísticas de creación de flores
     */
    public FlowerCreationStats getCreationStats() {
        return new FlowerCreationStats(
                configManager.getFlowers().size(),
                configManager.getTiers().size(),
                configManager.getGlobalSettings().isEnchantGlintEnabled(),
                configManager.getGlobalSettings().isAdvancedParticlesEnabled()
        );
    }

    // =================== CLASE INTERNA PARA ESTADÍSTICAS ===================

    public static class FlowerCreationStats {
        private final int availableFlowers;
        private final int availableTiers;
        private final boolean enchantGlintEnabled;
        private final boolean particlesEnabled;

        public FlowerCreationStats(int availableFlowers, int availableTiers,
                                   boolean enchantGlintEnabled, boolean particlesEnabled) {
            this.availableFlowers = availableFlowers;
            this.availableTiers = availableTiers;
            this.enchantGlintEnabled = enchantGlintEnabled;
            this.particlesEnabled = particlesEnabled;
        }

        public int getAvailableFlowers() { return availableFlowers; }
        public int getAvailableTiers() { return availableTiers; }
        public boolean isEnchantGlintEnabled() { return enchantGlintEnabled; }
        public boolean isParticlesEnabled() { return particlesEnabled; }

        @Override
        public String toString() {
            return String.format("FlowerStats{flowers=%d, tiers=%d, enchant=%s, particles=%s}",
                    availableFlowers, availableTiers, enchantGlintEnabled, particlesEnabled);
        }
    }
}