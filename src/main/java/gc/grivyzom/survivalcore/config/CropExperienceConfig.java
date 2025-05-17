package gc.grivyzom.survivalcore.config;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CropExperienceConfig {

    private final Main plugin;
    private File configFile;
    private FileConfiguration config;

    // Mapas existentes
    private final Map<String, int[]> experienceRangeMap = new HashMap<>();
    private final Map<String, Boolean> mustBeFullyGrownMap = new HashMap<>();
    private final Map<String, Boolean> dropAsOrbsMap = new HashMap<>();
    private final Map<String, String> messageMap = new HashMap<>();
    private final Map<String, Double> xpChanceMap = new HashMap<>();
    // Nuevo mapa para el sonido
    private final Map<String, String> soundMap = new HashMap<>();

    private String defaultMessage;

    public CropExperienceConfig(Main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "crops.yml");
        if (!configFile.exists()) {
            plugin.saveResource("crops.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Limpiar mapas antes de recargar
        experienceRangeMap.clear();
        mustBeFullyGrownMap.clear();
        dropAsOrbsMap.clear();
        messageMap.clear();
        xpChanceMap.clear();
        soundMap.clear();

        ConfigurationSection cropsSection = config.getConfigurationSection("crops");
        if (cropsSection != null) {
            for (String cropKey : cropsSection.getKeys(false)) {
                String upperKey = cropKey.toUpperCase();

                // Leer experiencia (soporta formato numérico o rango "min-max")
                Object xpObj = cropsSection.get(cropKey + ".experience");
                int minXP = 0;
                int maxXP = 0;
                if (xpObj instanceof Integer) {
                    minXP = (Integer) xpObj;
                    maxXP = (Integer) xpObj;
                } else if (xpObj instanceof String) {
                    String xpStr = (String) xpObj;
                    if (xpStr.contains("-")) {
                        String[] parts = xpStr.split("-");
                        try {
                            minXP = Integer.parseInt(parts[0].trim());
                            maxXP = Integer.parseInt(parts[1].trim());
                            // Validar que el mínimo no sea mayor que el máximo
                            if (minXP > maxXP) {
                                plugin.getLogger().warning("En el cultivo '" + cropKey + "', el valor mínimo de XP (" + minXP +
                                        ") es mayor que el máximo (" + maxXP + "). Se usarán valores por defecto (0,0).");
                                minXP = 0;
                                maxXP = 0;
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Error parseando la experiencia para '" + cropKey + "': " + e.getMessage());
                            minXP = 0;
                            maxXP = 0;
                        }
                    } else {
                        try {
                            minXP = Integer.parseInt(xpStr.trim());
                            maxXP = minXP;
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Error parseando la experiencia para '" + cropKey + "': " + e.getMessage());
                            minXP = 0;
                            maxXP = 0;
                        }
                    }
                }
                experienceRangeMap.put(upperKey, new int[]{minXP, maxXP});

                // Leer must-be-fully-grown
                boolean mustBeFull = cropsSection.getBoolean(cropKey + ".must-be-fully-grown", true);
                mustBeFullyGrownMap.put(upperKey, mustBeFull);

                // Leer drop-as-orbs
                boolean dropOrbs = cropsSection.getBoolean(cropKey + ".drop-as-orbs", false);
                dropAsOrbsMap.put(upperKey, dropOrbs);

                // Leer message
                String msg = cropsSection.getString(cropKey + ".message");
                if (msg != null) {
                    messageMap.put(upperKey, msg);
                }

                // Leer el porcentaje de XP (xp-chance)
                double xpChance = cropsSection.getDouble(cropKey + ".xp-chance", plugin.getCropXpChance());
                xpChanceMap.put(upperKey, xpChance);

                // Leer el sonido (opcional)
                String sound = cropsSection.getString(cropKey + ".sound");
                if (sound != null) {
                    soundMap.put(upperKey, sound);
                }
            }
        }

        // Leer default-message global
        defaultMessage = config.getString("global-options.default-message", "&aHas obtenido &e%exp% &ade experiencia.");
    }

    public void reload() {
        loadConfig();
    }

    // Método de recarga asíncrona
    public void reloadAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            loadConfig();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("La configuración de crops.yml se ha recargado asíncronamente.");
            });
        });
    }

    // Devuelve el rango de experiencia [min, max] para el cultivo
    public int[] getExperienceRange(String cropName) {
        return experienceRangeMap.getOrDefault(cropName.toUpperCase(), new int[]{0, 0});
    }

    public boolean mustBeFullyGrown(String cropName) {
        return mustBeFullyGrownMap.getOrDefault(cropName.toUpperCase(), true);
    }

    public boolean dropAsOrbs(String cropName) {
        return dropAsOrbsMap.getOrDefault(cropName.toUpperCase(), false);
    }

    public String getMessage(String cropName) {
        String key = cropName.toUpperCase();
        String msg = messageMap.get(key);
        if (msg != null && msg.trim().equals("[]")) {
            return "";
        }
        return msg != null ? msg : defaultMessage;
    }

    // Devuelve la probabilidad individual de XP
    public double getXpChance(String cropName) {
        return xpChanceMap.getOrDefault(cropName.toUpperCase(), plugin.getCropXpChance());
    }

    // Devuelve el sonido configurado para el cultivo (puede ser null o vacío)
    public String getSound(String cropName) {
        return soundMap.get(cropName.toUpperCase());
    }
}
