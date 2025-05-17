package gc.grivyzom.survivalcore.config;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MiningExperienceConfig {

    private final Main plugin;
    private File configFile;
    private FileConfiguration config;

    // Mapas para la configuración de minería
    private final Map<String, int[]> experienceRangeMap = new HashMap<>();
    private final Map<String, Double> xpChanceMap = new HashMap<>();
    private final Map<String, String> messageMap = new HashMap<>();
    private final Map<String, String> soundMap = new HashMap<>();

    private String defaultMessage;

    public MiningExperienceConfig(Main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "mining.yml");
        if (!configFile.exists()) {
            plugin.saveResource("mining.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Limpiar los mapas
        experienceRangeMap.clear();
        xpChanceMap.clear();
        messageMap.clear();
        soundMap.clear();

        ConfigurationSection miningSection = config.getConfigurationSection("mining");
        if (miningSection != null) {
            for (String oreKey : miningSection.getKeys(false)) {
                String key = oreKey.toUpperCase();

                // Leer experiencia: puede ser un entero o un rango "min-max"
                Object xpObj = miningSection.get(oreKey + ".experience");
                int minXP = 0, maxXP = 0;
                if (xpObj instanceof Integer) {
                    minXP = (Integer) xpObj;
                    maxXP = minXP;
                } else if (xpObj instanceof String) {
                    String xpStr = (String) xpObj;
                    if (xpStr.contains("-")) {
                        String[] parts = xpStr.split("-");
                        try {
                            minXP = Integer.parseInt(parts[0].trim());
                            maxXP = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            minXP = 0;
                            maxXP = 0;
                        }
                    } else {
                        try {
                            minXP = Integer.parseInt(xpStr.trim());
                            maxXP = minXP;
                        } catch (NumberFormatException e) {
                            minXP = 0;
                            maxXP = 0;
                        }
                    }
                }
                experienceRangeMap.put(key, new int[]{minXP, maxXP});

                // Leer xp-chance
                double xpChance = miningSection.getDouble(oreKey + ".xp-chance", 0.8);
                xpChanceMap.put(key, xpChance);

                // Leer mensaje (si se pone "[]" se considera desactivado)
                String msg = miningSection.getString(oreKey + ".message");
                if (msg != null) {
                    messageMap.put(key, msg);
                }

                // Leer sonido
                String sound = miningSection.getString(oreKey + ".sound");
                if (sound != null) {
                    soundMap.put(key, sound);
                }
            }
        }
        defaultMessage = config.getString("global-options.default-message", "&aHas obtenido &e%exp% &ade experiencia de minería.");
    }

    public void reload() {
        loadConfig();
    }

    public int[] getExperienceRange(String oreKey) {
        return experienceRangeMap.getOrDefault(oreKey.toUpperCase(), new int[]{0, 0});
    }

    public double getXpChance(String oreKey) {
        return xpChanceMap.getOrDefault(oreKey.toUpperCase(), 0.8);
    }

    public String getMessage(String oreKey) {
        String key = oreKey.toUpperCase();
        String msg = messageMap.get(key);
        if (msg != null && msg.trim().equals("[]")) {
            return "";
        }
        return msg != null ? msg : defaultMessage;
    }

    public String getSound(String oreKey) {
        return soundMap.get(oreKey.toUpperCase());
    }
}
