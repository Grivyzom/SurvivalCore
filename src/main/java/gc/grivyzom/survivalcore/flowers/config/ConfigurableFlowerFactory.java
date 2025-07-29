package gc.grivyzom.survivalcore.flowers.config;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory para crear flores mágicas configurables desde flowers.yml
 *
 * @author Brocolitx
 * @version 1.0
 */
public class ConfigurableFlowerFactory {

    private final Main plugin;
    private final FlowerConfigManager configManager;

    // Claves para metadatos persistentes
    private final NamespacedKey isMagicFlowerKey;
    private final NamespacedKey isConfigurableKey;
    private final NamespacedKey flowerIdKey;
    private final NamespacedKey flowerLevelKey;
    private final NamespacedKey flowerTierKey;

    public ConfigurableFlowerFactory(Main plugin, FlowerConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // Inicializar claves de metadatos
        this.isMagicFlowerKey = new NamespacedKey(plugin, "is_magic_flower");
        this.isConfigurableKey = new NamespacedKey(plugin, "is_configurable_flower");
        this.flowerIdKey = new NamespacedKey(plugin, "flower_id");
        this.flowerLevelKey = new NamespacedKey(plugin, "flower_level");
        this.flowerTierKey = new NamespacedKey(plugin, "flower_tier");
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
        int maxLevel = flowerDef.getConfig().getMaxLevel();
        if (level < 1 || level > maxLevel) {
            plugin.getLogger().warning("Nivel inválido para " + flowerId + ": " + level + " (máximo: " + maxLevel + ")");
            level = Math.max(1, Math.min(level, maxLevel));
        }

        // Crear item base
        ItemStack flower = new ItemStack(flowerDef.getConfig().getType());
        ItemMeta meta = flower.getItemMeta();
        if (meta == null) {
            plugin.getLogger().severe("No se pudo obtener ItemMeta para " + flowerDef.getConfig().getType());
            return null;
        }

        // Configurar nombre
        String displayName = processDisplayName(flowerDef.getDisplay().getName(), flowerDef, level);
        meta.setDisplayName(displayName);

        // Configurar lore
        List<String> lore = processLore(flowerDef.getDisplay().getLore(), flowerDef, level);
        meta.setLore(lore);

        // Configurar encantamiento (brillo)
        if (flowerDef.getConfig().hasEnchantEffect() && configManager.getGlobalSettings().isEnchantGlintEnabled()) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Configurar metadatos persistentes
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(isMagicFlowerKey, PersistentDataType.BYTE, (byte) 1);
        container.set(isConfigurableKey, PersistentDataType.BYTE, (byte) 1);
        container.set(flowerIdKey, PersistentDataType.STRING, flowerId);
        container.set(flowerLevelKey, PersistentDataType.INTEGER, level);
        container.set(flowerTierKey, PersistentDataType.STRING, flowerDef.getTier().getName());

        // Aplicar metadatos al item
        flower.setItemMeta(meta);

        plugin.getLogger().info("Flor configurable creada: " + flowerId + " (Lv." + level + ")");
        return flower;
    }

    /**
     * Procesa el nombre de display reemplazando variables
     */
    private String processDisplayName(String rawName, FlowerDefinition flowerDef, int level) {
        String name = rawName
                .replace("{flower_level}", String.valueOf(level))
                .replace("{tier_color}", flowerDef.getTier().getColor())
                .replace("{tier_name}", flowerDef.getTier().getName())
                .replace("{max_level}", String.valueOf(flowerDef.getConfig().getMaxLevel()));

        return ChatColor.translateAlternateColorCodes('&', name);
    }

    /**
     * Procesa el lore reemplazando variables
     */
    private List<String> processLore(List<String> rawLore, FlowerDefinition flowerDef, int level) {
        List<String> processedLore = new ArrayList<>();

        for (String line : rawLore) {
            String processedLine = line
                    .replace("{flower_level}", String.valueOf(level))
                    .replace("{tier_color}", flowerDef.getTier().getColor())
                    .replace("{tier_name}", flowerDef.getTier().getName())
                    .replace("{max_level}", String.valueOf(flowerDef.getConfig().getMaxLevel()))
                    .replace("{effect_count}", String.valueOf(flowerDef.getEffects().size()));

            // Procesar efectos dinámicos
            if (processedLine.contains("{effects_list}")) {
                processedLore.addAll(generateEffectsList(flowerDef, level));
                continue;
            }

            processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
        }

        return processedLore;
    }

    /**
     * Genera una lista de efectos para el lore
     */
    private List<String> generateEffectsList(FlowerDefinition flowerDef, int level) {
        List<String> effectsList = new ArrayList<>();

        for (FlowerDefinition.FlowerEffect effect : flowerDef.getEffects()) {
            int effectLevel = effect.calculateLevel(level);
            int duration = effect.calculateDuration(level);

            String effectName = formatEffectName(effect.getType().getName());
            String effectLine = String.format("&f• &a%s %d &7(%ds)",
                    effectName, effectLevel + 1, duration);

            effectsList.add(ChatColor.translateAlternateColorCodes('&', effectLine));
        }

        return effectsList;
    }

    /**
     * Formatea el nombre de un efecto para que sea más legible
     */
    private String formatEffectName(String effectName) {
        if (effectName == null) return "Efecto Desconocido";

        return effectName.toLowerCase()
                .replace("_", " ")
                .replace("increase damage", "Fuerza")
                .replace("speed", "Velocidad")
                .replace("regeneration", "Regeneración")
                .replace("night vision", "Visión Nocturna")
                .replace("jump boost", "Salto")
                .replace("damage resistance", "Resistencia")
                .replace("fire resistance", "Resistencia al Fuego")
                .replace("water breathing", "Respiración Acuática")
                .replace("absorption", "Absorción")
                .replace("health boost", "Vida Extra")
                .replace("luck", "Suerte")
                .replace("unluck", "Mala Suerte");
    }

    // =================== MÉTODOS DE VERIFICACIÓN ===================

    /**
     * Verifica si un ítem es una flor mágica configurable
     */
    public boolean isConfigurableFlower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(isMagicFlowerKey, PersistentDataType.BYTE) &&
                container.has(isConfigurableKey, PersistentDataType.BYTE);
    }

    /**
     * Verifica si un ítem es una flor mágica (configurable o tradicional)
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
     * Obtiene el ID de una flor configurable
     */
    public String getFlowerId(ItemStack item) {
        if (!isConfigurableFlower(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(flowerIdKey, PersistentDataType.STRING);
    }

    /**
     * Obtiene el nivel de una flor configurable
     */
    public int getFlowerLevel(ItemStack item) {
        if (!isConfigurableFlower(item)) {
            return 1;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(flowerLevelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * Obtiene el tier de una flor configurable
     */
    public String getFlowerTier(ItemStack item) {
        if (!isConfigurableFlower(item)) {
            return "UNKNOWN";
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(flowerTierKey, PersistentDataType.STRING, "COMMON");
    }

    // =================== MÉTODOS DE MANIPULACIÓN ===================

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
        if (flowerDef == null) {
            return flower;
        }

        int maxLevel = flowerDef.getConfig().getMaxLevel();
        if (currentLevel >= maxLevel) {
            return flower; // Ya está al máximo
        }

        return createConfigurableFlower(flowerId, currentLevel + 1);
    }

    /**
     * Verifica si una flor se puede mejorar
     */
    public boolean canUpgradeFlower(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return false;
        }

        String flowerId = getFlowerId(flower);
        int currentLevel = getFlowerLevel(flower);

        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) {
            return false;
        }

        return currentLevel < flowerDef.getConfig().getMaxLevel();
    }

    /**
     * Cambia el nivel de una flor existente
     */
    public ItemStack setFlowerLevel(ItemStack flower, int newLevel) {
        if (!isConfigurableFlower(flower)) {
            return flower;
        }

        String flowerId = getFlowerId(flower);
        return createConfigurableFlower(flowerId, newLevel);
    }

    // =================== MÉTODOS DE INFORMACIÓN ===================

    /**
     * Obtiene información detallada de una flor
     */
    public String getFlowerInfo(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return "No es una flor configurable";
        }

        String flowerId = getFlowerId(flower);
        int level = getFlowerLevel(flower);
        String tier = getFlowerTier(flower);

        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) {
            return "Definición de flor no encontrada: " + flowerId;
        }

        StringBuilder info = new StringBuilder();
        info.append("=== INFORMACIÓN DE FLOR CONFIGURABLE ===\n");
        info.append("ID: ").append(flowerId).append("\n");
        info.append("Nivel: ").append(level).append("/").append(flowerDef.getConfig().getMaxLevel()).append("\n");
        info.append("Tier: ").append(tier).append("\n");
        info.append("Material: ").append(flowerDef.getConfig().getType().name()).append("\n");
        info.append("Efectos: ").append(flowerDef.getEffects().size()).append("\n");

        if (!flowerDef.getSpecialMechanics().isEmpty()) {
            long enabledMechanics = flowerDef.getSpecialMechanics().values().stream()
                    .mapToLong(mechanic -> mechanic.isEnabled() ? 1 : 0)
                    .sum();
            info.append("Mecánicas especiales: ").append(enabledMechanics).append("/").append(flowerDef.getSpecialMechanics().size()).append("\n");
        }

        return info.toString();
    }

    /**
     * Obtiene estadísticas de una flor
     */
    public FlowerStats getFlowerStats(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return null;
        }

        String flowerId = getFlowerId(flower);
        int level = getFlowerLevel(flower);
        String tier = getFlowerTier(flower);

        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) {
            return null;
        }

        return new FlowerStats(
                flowerId,
                level,
                tier,
                flowerDef.getConfig().getMaxLevel(),
                flowerDef.getEffects().size(),
                flowerDef.getSpecialMechanics().size(),
                canUpgradeFlower(flower)
        );
    }

    // =================== MÉTODOS DE CONVERSIÓN ===================

    /**
     * Convierte una flor tradicional a configurable (si es posible)
     */
    public ItemStack convertFromTraditional(ItemStack traditionalFlower) {
        // Este método podría implementarse para migración automática
        plugin.getLogger().info("Conversión de flor tradicional a configurable no implementada");
        return null;
    }

    /**
     * Crea una copia exacta de una flor configurable
     */
    public ItemStack cloneConfigurableFlower(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return flower.clone();
        }

        String flowerId = getFlowerId(flower);
        int level = getFlowerLevel(flower);

        return createConfigurableFlower(flowerId, level);
    }

    // =================== MÉTODOS DE VALIDACIÓN ===================

    /**
     * Valida que una flor sea compatible con la configuración actual
     */
    public boolean isFlowerValid(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return false;
        }

        String flowerId = getFlowerId(flower);
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) {
            plugin.getLogger().warning("Flor inválida encontrada: " + flowerId + " - definición no existe");
            return false;
        }

        int level = getFlowerLevel(flower);
        int maxLevel = flowerDef.getConfig().getMaxLevel();

        if (level < 1 || level > maxLevel) {
            plugin.getLogger().warning("Flor con nivel inválido: " + flowerId + " Lv." + level + " (máximo: " + maxLevel + ")");
            return false;
        }

        return true;
    }

    /**
     * Repara una flor inválida intentando corregir sus datos
     */
    public ItemStack repairFlower(ItemStack flower) {
        if (!isConfigurableFlower(flower)) {
            return flower;
        }

        String flowerId = getFlowerId(flower);
        int level = getFlowerLevel(flower);

        FlowerDefinition flowerDef = configManager.getFlower(flowerId);
        if (flowerDef == null) {
            plugin.getLogger().warning("No se puede reparar flor con ID desconocido: " + flowerId);
            return flower;
        }

        // Corregir nivel si está fuera de rango
        int maxLevel = flowerDef.getConfig().getMaxLevel();
        if (level < 1 || level > maxLevel) {
            level = Math.max(1, Math.min(level, maxLevel));
            plugin.getLogger().info("Reparando nivel de flor " + flowerId + " a " + level);
        }

        return createConfigurableFlower(flowerId, level);
    }

    // =================== MÉTODOS DE BATCH ===================

    /**
     * Crea múltiples flores de una vez
     */
    public List<ItemStack> createMultipleFlowers(String flowerId, int level, int amount) {
        List<ItemStack> flowers = new ArrayList<>();

        for (int i = 0; i < amount; i++) {
            ItemStack flower = createConfigurableFlower(flowerId, level);
            if (flower != null) {
                flowers.add(flower);
            }
        }

        return flowers;
    }

    /**
     * Valida múltiples flores de una vez
     */
    public ValidationResult validateMultipleFlowers(List<ItemStack> flowers) {
        int valid = 0;
        int invalid = 0;
        int nonConfigurable = 0;
        List<String> errors = new ArrayList<>();

        for (ItemStack flower : flowers) {
            if (!isMagicFlower(flower)) {
                nonConfigurable++;
                continue;
            }

            if (!isConfigurableFlower(flower)) {
                nonConfigurable++;
                continue;
            }

            if (isFlowerValid(flower)) {
                valid++;
            } else {
                invalid++;
                String flowerId = getFlowerId(flower);
                errors.add("Flor inválida: " + flowerId);
            }
        }

        return new ValidationResult(valid, invalid, nonConfigurable, errors);
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Obtiene todas las flores configurables de un inventario
     */
    public List<ItemStack> getConfigurableFlowersFromInventory(org.bukkit.inventory.Inventory inventory) {
        List<ItemStack> configurableFlowers = new ArrayList<>();

        for (ItemStack item : inventory.getContents()) {
            if (isConfigurableFlower(item)) {
                configurableFlowers.add(item);
            }
        }

        return configurableFlowers;
    }

    /**
     * Cuenta las flores configurables de un tipo específico
     */
    public int countFlowersOfType(org.bukkit.inventory.Inventory inventory, String flowerId) {
        int count = 0;

        for (ItemStack item : inventory.getContents()) {
            if (isConfigurableFlower(item) && flowerId.equals(getFlowerId(item))) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Obtiene estadísticas de todas las flores en un inventario
     */
    public InventoryFlowerStats getInventoryStats(org.bukkit.inventory.Inventory inventory) {
        int totalFlowers = 0;
        int configurableFlowers = 0;
        int traditionalFlowers = 0;
        java.util.Map<String, Integer> flowerCounts = new java.util.HashMap<>();
        java.util.Map<String, Integer> tierCounts = new java.util.HashMap<>();

        for (ItemStack item : inventory.getContents()) {
            if (item == null) continue;

            if (isMagicFlower(item)) {
                totalFlowers += item.getAmount();

                if (isConfigurableFlower(item)) {
                    configurableFlowers += item.getAmount();

                    String flowerId = getFlowerId(item);
                    String tier = getFlowerTier(item);

                    flowerCounts.merge(flowerId, item.getAmount(), Integer::sum);
                    tierCounts.merge(tier, item.getAmount(), Integer::sum);
                } else {
                    traditionalFlowers += item.getAmount();
                }
            }
        }

        return new InventoryFlowerStats(
                totalFlowers, configurableFlowers, traditionalFlowers,
                flowerCounts, tierCounts
        );
    }

    // =================== CLASES INTERNAS AUXILIARES ===================

    /**
     * Estadísticas de una flor individual
     */
    public static class FlowerStats {
        private final String flowerId;
        private final int level;
        private final String tier;
        private final int maxLevel;
        private final int effectCount;
        private final int mechanicCount;
        private final boolean canUpgrade;

        public FlowerStats(String flowerId, int level, String tier, int maxLevel,
                           int effectCount, int mechanicCount, boolean canUpgrade) {
            this.flowerId = flowerId;
            this.level = level;
            this.tier = tier;
            this.maxLevel = maxLevel;
            this.effectCount = effectCount;
            this.mechanicCount = mechanicCount;
            this.canUpgrade = canUpgrade;
        }

        // Getters
        public String getFlowerId() { return flowerId; }
        public int getLevel() { return level; }
        public String getTier() { return tier; }
        public int getMaxLevel() { return maxLevel; }
        public int getEffectCount() { return effectCount; }
        public int getMechanicCount() { return mechanicCount; }
        public boolean canUpgrade() { return canUpgrade; }

        public double getProgressPercentage() {
            return (double) level / maxLevel * 100.0;
        }

        @Override
        public String toString() {
            return String.format("FlowerStats{id='%s', level=%d/%d, tier='%s', effects=%d}",
                    flowerId, level, maxLevel, tier, effectCount);
        }
    }

    /**
     * Resultado de validación para múltiples flores
     */
    public static class ValidationResult {
        private final int validCount;
        private final int invalidCount;
        private final int nonConfigurableCount;
        private final List<String> errors;

        public ValidationResult(int validCount, int invalidCount, int nonConfigurableCount, List<String> errors) {
            this.validCount = validCount;
            this.invalidCount = invalidCount;
            this.nonConfigurableCount = nonConfigurableCount;
            this.errors = errors;
        }

        // Getters
        public int getValidCount() { return validCount; }
        public int getInvalidCount() { return invalidCount; }
        public int getNonConfigurableCount() { return nonConfigurableCount; }
        public List<String> getErrors() { return errors; }
        public int getTotalCount() { return validCount + invalidCount + nonConfigurableCount; }

        public boolean hasErrors() { return invalidCount > 0 || !errors.isEmpty(); }
        public double getValidPercentage() {
            int total = getTotalCount();
            return total > 0 ? (double) validCount / total * 100.0 : 0.0;
        }
    }

    /**
     * Estadísticas de flores en un inventario
     */
    public static class InventoryFlowerStats {
        private final int totalFlowers;
        private final int configurableFlowers;
        private final int traditionalFlowers;
        private final java.util.Map<String, Integer> flowerCounts;
        private final java.util.Map<String, Integer> tierCounts;

        public InventoryFlowerStats(int totalFlowers, int configurableFlowers, int traditionalFlowers,
                                    java.util.Map<String, Integer> flowerCounts,
                                    java.util.Map<String, Integer> tierCounts) {
            this.totalFlowers = totalFlowers;
            this.configurableFlowers = configurableFlowers;
            this.traditionalFlowers = traditionalFlowers;
            this.flowerCounts = flowerCounts;
            this.tierCounts = tierCounts;
        }

        // Getters
        public int getTotalFlowers() { return totalFlowers; }
        public int getConfigurableFlowers() { return configurableFlowers; }
        public int getTraditionalFlowers() { return traditionalFlowers; }
        public java.util.Map<String, Integer> getFlowerCounts() { return flowerCounts; }
        public java.util.Map<String, Integer> getTierCounts() { return tierCounts; }

        public double getConfigurablePercentage() {
            return totalFlowers > 0 ? (double) configurableFlowers / totalFlowers * 100.0 : 0.0;
        }

        public String getMostCommonFlower() {
            return flowerCounts.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse("Ninguna");
        }

        public String getMostCommonTier() {
            return tierCounts.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse("Ninguno");
        }
    }
}