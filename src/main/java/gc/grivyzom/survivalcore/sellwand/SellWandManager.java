package gc.grivyzom.survivalcore.sellwand;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Sistema de gestión de SellWands - Permite vender contenidos de cofres con un palo especial
 */
public class SellWandManager {

    private final Main plugin;
    private final NamespacedKey sellWandKey;
    private final NamespacedKey usesKey;

    // Configuración
    private FileConfiguration sellWandConfig;
    private File sellWandConfigFile;

    // Cache de configuración
    private Material wandMaterial;
    private String wandName;
    private List<String> wandLore;
    private boolean wandGlow;
    private int defaultUses;
    private boolean infiniteUsesDefault;
    private long cooldownMs;
    private boolean soundsEnabled;
    private Map<String, SellWandSound> soundEffects;

    // Precios de items
    private Map<Material, Double> itemPrices;

    // Sistema de límites de venta
    private Map<UUID, Map<Material, SellLimit>> playerSellLimits;
    private Map<UUID, Long> lastUsage;

    // Cooldowns por jugador
    private Map<UUID, Long> playerCooldowns;

    public SellWandManager(Main plugin) {
        this.plugin = plugin;
        this.sellWandKey = new NamespacedKey(plugin, "sell_wand");
        this.usesKey = new NamespacedKey(plugin, "sell_wand_uses");

        this.itemPrices = new ConcurrentHashMap<>();
        this.playerSellLimits = new ConcurrentHashMap<>();
        this.lastUsage = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.soundEffects = new HashMap<>();

        loadConfig();
        loadPrices();
        loadSoundEffects();

        // Limpiar datos caducados cada 5 minutos
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredData, 6000L, 6000L);
    }

    /**
     * Carga la configuración del archivo sellwand.yml
     */
    private void loadConfig() {
        sellWandConfigFile = new File(plugin.getDataFolder(), "sellwand.yml");
        if (!sellWandConfigFile.exists()) {
            plugin.saveResource("sellwand.yml", false);
        }

        sellWandConfig = YamlConfiguration.loadConfiguration(sellWandConfigFile);

        // Cargar configuración básica
        wandMaterial = Material.valueOf(sellWandConfig.getString("sellwand.material", "STICK").toUpperCase());
        wandName = ChatColor.translateAlternateColorCodes('&',
                sellWandConfig.getString("sellwand.name", "&6&lSell Wand"));
        wandGlow = sellWandConfig.getBoolean("sellwand.glow", true);
        defaultUses = sellWandConfig.getInt("sellwand.default_uses", 100);
        infiniteUsesDefault = sellWandConfig.getBoolean("sellwand.infinite_uses_default", false);
        cooldownMs = sellWandConfig.getLong("sellwand.cooldown_ms", 1000);
        soundsEnabled = sellWandConfig.getBoolean("sounds.enabled", true);

        // Cargar lore
        wandLore = new ArrayList<>();
        List<String> loreList = sellWandConfig.getStringList("sellwand.lore");
        for (String line : loreList) {
            wandLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        plugin.getLogger().info("SellWand configuración cargada correctamente.");
    }

    /**
     * Carga los precios de los items desde la configuración
     */
    private void loadPrices() {
        itemPrices.clear();
        ConfigurationSection pricesSection = sellWandConfig.getConfigurationSection("prices");

        if (pricesSection != null) {
            for (String materialName : pricesSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    double price = pricesSection.getDouble(materialName);
                    if (price > 0) {
                        itemPrices.put(material, price);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Material inválido en precios: " + materialName);
                }
            }
        }

        plugin.getLogger().info("Cargados " + itemPrices.size() + " precios de items para SellWand.");
    }

    /**
     * Carga los efectos de sonido desde la configuración
     */
    private void loadSoundEffects() {
        soundEffects.clear();

        if (!soundsEnabled) return;

        ConfigurationSection soundsSection = sellWandConfig.getConfigurationSection("sounds.effects");
        if (soundsSection != null) {
            for (String key : soundsSection.getKeys(false)) {
                try {
                    String soundName = soundsSection.getString(key + ".sound");
                    float volume = (float) soundsSection.getDouble(key + ".volume", 1.0);
                    float pitch = (float) soundsSection.getDouble(key + ".pitch", 1.0);
                    double minPrice = soundsSection.getDouble(key + ".min_price", 0.0);
                    double maxPrice = soundsSection.getDouble(key + ".max_price", Double.MAX_VALUE);

                    if (soundName != null) {
                        org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                        soundEffects.put(key, new SellWandSound(sound, volume, pitch, minPrice, maxPrice));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error cargando sonido: " + key + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * Crea una nueva SellWand
     */
    public ItemStack createSellWand(int uses) {
        ItemStack wand = new ItemStack(wandMaterial);
        ItemMeta meta = wand.getItemMeta();

        if (meta != null) {
            // Nombre y lore básicos
            meta.setDisplayName(wandName);

            List<String> finalLore = new ArrayList<>(wandLore);
            if (!infiniteUsesDefault) {
                finalLore.add("");
                finalLore.add(ChatColor.GRAY + "Usos restantes: " + ChatColor.YELLOW + uses);
            }
            meta.setLore(finalLore);

            // Glow effect
            if (wandGlow) {
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // Marcar como SellWand
            meta.getPersistentDataContainer().set(sellWandKey, PersistentDataType.BYTE, (byte) 1);

            // Guardar usos
            if (!infiniteUsesDefault) {
                meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
            }

            wand.setItemMeta(meta);
        }

        return wand;
    }

    /**
     * Verifica si un item es una SellWand
     */
    public boolean isSellWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(sellWandKey, PersistentDataType.BYTE);
    }

    /**
     * Obtiene los usos restantes de una SellWand
     */
    public int getUsesRemaining(ItemStack wand) {
        if (!isSellWand(wand) || infiniteUsesDefault) return -1;

        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            return meta.getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, defaultUses);
        }

        return 0;
    }

    /**
     * Reduce los usos de una SellWand
     */
    public boolean reduceUses(ItemStack wand) {
        if (!isSellWand(wand) || infiniteUsesDefault) return true;

        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            int currentUses = meta.getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, defaultUses);

            if (currentUses <= 1) {
                return false; // SellWand agotada
            }

            currentUses--;
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, currentUses);

            // Actualizar lore
            updateWandLore(meta, currentUses);
            wand.setItemMeta(meta);

            return true;
        }

        return false;
    }

    /**
     * Actualiza el lore de la SellWand con los usos restantes
     */
    private void updateWandLore(ItemMeta meta, int uses) {
        List<String> newLore = new ArrayList<>(wandLore);
        newLore.add("");

        if (uses <= 10) {
            newLore.add(ChatColor.RED + "Usos restantes: " + ChatColor.YELLOW + uses);
            newLore.add(ChatColor.RED + "¡Casi agotada!");
        } else if (uses <= 25) {
            newLore.add(ChatColor.YELLOW + "Usos restantes: " + ChatColor.YELLOW + uses);
            newLore.add(ChatColor.YELLOW + "Pocos usos restantes");
        } else {
            newLore.add(ChatColor.GRAY + "Usos restantes: " + ChatColor.YELLOW + uses);
        }

        meta.setLore(newLore);
    }

    /**
     * Verifica si un jugador está en cooldown
     */
    public boolean isOnCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUse = playerCooldowns.get(uuid);

        if (lastUse == null) return false;

        return System.currentTimeMillis() - lastUse < cooldownMs;
    }

    /**
     * Obtiene el tiempo restante de cooldown
     */
    public long getRemainingCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastUse = playerCooldowns.get(uuid);

        if (lastUse == null) return 0;

        long remaining = cooldownMs - (System.currentTimeMillis() - lastUse);
        return Math.max(0, remaining);
    }

    /**
     * Establece el cooldown para un jugador
     */
    public void setCooldown(Player player) {
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Obtiene el precio de un item
     */
    public double getItemPrice(Material material) {
        return itemPrices.getOrDefault(material, 0.0);
    }

    /**
     * Verifica si un item se puede vender
     */
    public boolean canSellItem(Material material) {
        return itemPrices.containsKey(material) && itemPrices.get(material) > 0;
    }

    /**
     * Verifica los límites de venta para un jugador
     */
    public boolean checkSellLimit(Player player, Material material, int amount) {
        ConfigurationSection limitsSection = sellWandConfig.getConfigurationSection("sell_limits");
        if (limitsSection == null) return true;

        String materialName = material.name().toLowerCase();
        if (!limitsSection.contains(materialName)) return true;

        int maxAmount = limitsSection.getInt(materialName + ".max_amount", -1);
        long timeWindowMs = limitsSection.getLong(materialName + ".time_window_hours", 24) * 3600000L;

        if (maxAmount == -1) return true; // Sin límite

        UUID uuid = player.getUniqueId();
        Map<Material, SellLimit> playerLimits = playerSellLimits.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        SellLimit limit = playerLimits.get(material);
        long now = System.currentTimeMillis();

        if (limit == null || now - limit.getTimestamp() > timeWindowMs) {
            // Crear nuevo límite o renovar
            playerLimits.put(material, new SellLimit(amount, now));
            return true;
        }

        // Verificar si supera el límite
        if (limit.getAmount() + amount > maxAmount) {
            return false;
        }

        // Actualizar cantidad
        limit.addAmount(amount);
        return true;
    }

    /**
     * Reproduce el sonido apropiado basado en el precio total
     */
    public void playSound(Player player, double totalPrice) {
        if (!soundsEnabled) return;

        for (SellWandSound soundEffect : soundEffects.values()) {
            if (totalPrice >= soundEffect.getMinPrice() && totalPrice <= soundEffect.getMaxPrice()) {
                player.playSound(player.getLocation(), soundEffect.getSound(),
                        soundEffect.getVolume(), soundEffect.getPitch());
                break;
            }
        }
    }

    /**
     * Limpia datos caducados de límites de venta y cooldowns
     */
    private void cleanupExpiredData() {
        long now = System.currentTimeMillis();

        // Limpiar cooldowns expirados
        playerCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > cooldownMs * 2);

        // Limpiar límites de venta expirados
        ConfigurationSection limitsSection = sellWandConfig.getConfigurationSection("sell_limits");
        if (limitsSection != null) {
            for (Map.Entry<UUID, Map<Material, SellLimit>> playerEntry : playerSellLimits.entrySet()) {
                Map<Material, SellLimit> limits = playerEntry.getValue();
                limits.entrySet().removeIf(entry -> {
                    String materialName = entry.getKey().name().toLowerCase();
                    long timeWindowMs = limitsSection.getLong(materialName + ".time_window_hours", 24) * 3600000L;
                    return now - entry.getValue().getTimestamp() > timeWindowMs;
                });

                if (limits.isEmpty()) {
                    playerSellLimits.remove(playerEntry.getKey());
                }
            }
        }
    }


    /**
     * Recarga la configuración completamente
     * CORREGIDO: Ahora recarga el archivo desde disco antes de cargar los valores
     */
    public void reloadConfig() {
        try {
            // PASO 1: Recargar el archivo físico desde el disco
            sellWandConfig = YamlConfiguration.loadConfiguration(sellWandConfigFile);

            // PASO 2: Limpiar todos los mapas/caches existentes
            itemPrices.clear();
            soundEffects.clear();
            playerSellLimits.clear();
            playerCooldowns.clear();

            // PASO 3: Recargar toda la configuración
            loadConfig();
            loadPrices();
            loadSoundEffects();

            plugin.getLogger().info("SellWand: Configuración recargada correctamente desde sellwand.yml");
            plugin.getLogger().info("SellWand: " + itemPrices.size() + " precios de items cargados");
            plugin.getLogger().info("SellWand: " + soundEffects.size() + " efectos de sonido cargados");

        } catch (Exception e) {
            plugin.getLogger().severe("ERROR al recargar configuración de SellWand: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("No se pudo recargar la configuración de SellWand", e);
        }
    }

    /**
     * Obtiene información de límites para un jugador
     */
    public Map<Material, SellLimitInfo> getSellLimitInfo(Player player) {
        Map<Material, SellLimitInfo> info = new HashMap<>();
        ConfigurationSection limitsSection = sellWandConfig.getConfigurationSection("sell_limits");

        if (limitsSection == null) return info;

        UUID uuid = player.getUniqueId();
        Map<Material, SellLimit> playerLimits = playerSellLimits.get(uuid);

        for (String materialName : limitsSection.getKeys(false)) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                int maxAmount = limitsSection.getInt(materialName + ".max_amount", -1);
                long timeWindowHours = limitsSection.getLong(materialName + ".time_window_hours", 24);

                if (maxAmount == -1) continue;

                int usedAmount = 0;
                long resetTime = 0;

                if (playerLimits != null && playerLimits.containsKey(material)) {
                    SellLimit limit = playerLimits.get(material);
                    long timeWindowMs = timeWindowHours * 3600000L;

                    if (System.currentTimeMillis() - limit.getTimestamp() <= timeWindowMs) {
                        usedAmount = limit.getAmount();
                        resetTime = limit.getTimestamp() + timeWindowMs;
                    }
                }

                info.put(material, new SellLimitInfo(maxAmount, usedAmount, resetTime, timeWindowHours));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Material inválido en límites: " + materialName);
            }
        }

        return info;
    }

    /**
     * Clase para almacenar información de límites de venta
     */
    public static class SellLimit {
        private int amount;
        private final long timestamp;

        public SellLimit(int amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }

        public int getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
        public void addAmount(int amount) { this.amount += amount; }
    }

    public int getLoadedPriceCount() {
        return itemPrices.size();
    }
    /**
     * Clase para información de límites de venta
     */
    public static class SellLimitInfo {
        private final int maxAmount;
        private final int usedAmount;
        private final long resetTime;
        private final long timeWindowHours;

        public SellLimitInfo(int maxAmount, int usedAmount, long resetTime, long timeWindowHours) {
            this.maxAmount = maxAmount;
            this.usedAmount = usedAmount;
            this.resetTime = resetTime;
            this.timeWindowHours = timeWindowHours;
        }

        public int getMaxAmount() { return maxAmount; }
        public int getUsedAmount() { return usedAmount; }
        public int getRemainingAmount() { return Math.max(0, maxAmount - usedAmount); }
        public long getResetTime() { return resetTime; }
        public long getTimeWindowHours() { return timeWindowHours; }

        public boolean isExpired() {
            return System.currentTimeMillis() > resetTime;
        }
    }

    /**
     * Clase para efectos de sonido
     */
    public static class SellWandSound {
        private final org.bukkit.Sound sound;
        private final float volume;
        private final float pitch;
        private final double minPrice;
        private final double maxPrice;

        public SellWandSound(org.bukkit.Sound sound, float volume, float pitch, double minPrice, double maxPrice) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
        }

        public org.bukkit.Sound getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
        public double getMinPrice() { return minPrice; }
        public double getMaxPrice() { return maxPrice; }
    }

    /**
     * Método auxiliar para debugging - muestra los precios cargados
     */
    public void debugPrices() {
        plugin.getLogger().info("=== DEBUG: Precios cargados en SellWand ===");
        if (itemPrices.isEmpty()) {
            plugin.getLogger().warning("¡NO HAY PRECIOS CARGADOS!");
        } else {
            for (Map.Entry<Material, Double> entry : itemPrices.entrySet()) {
                plugin.getLogger().info("  " + entry.getKey() + " = " + entry.getValue());
            }
        }
        plugin.getLogger().info("=== Fin debug precios ===");
    }

}