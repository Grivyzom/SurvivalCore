package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RecipeUnlockManager {
    private static final Main plugin = Main.getPlugin(Main.class);
    private static final File file = new File(plugin.getDataFolder(), "lectern_unlocked_recipes.yml");
    private static FileConfiguration cfg;
    private static final Map<UUID, Set<String>> unlocked = new HashMap<>();

    /** Carga inicial desde disco. */
    public static void load() {
        if (!file.exists()) plugin.saveResource("lectern_unlocked_recipes.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.contains("unlocked")) {
            for (String key : cfg.getConfigurationSection("unlocked").getKeys(false)) {
                List<String> list = cfg.getStringList("unlocked." + key);
                unlocked.put(UUID.fromString(key), new HashSet<>(list));
            }
        }
    }

    /** Guarda cambios al disco. */
    private static void save() {
        for (Map.Entry<UUID, Set<String>> e : unlocked.entrySet()) {
            cfg.set("unlocked." + e.getKey().toString(), new ArrayList<>(e.getValue()));
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar lectern_unlocked_recipes.yml: " + ex.getMessage());
        }
    }

    /** Registra una receta como desbloqueada para este jugador. */
    public static void unlockRecipe(UUID uuid, String resultMat) {
        Set<String> set = unlocked.computeIfAbsent(uuid, k -> new HashSet<>());
        if (set.add(resultMat)) {  // sólo si era nueva
            save();
        }
    }

    /** Devuelve el conjunto de materiales desbloqueados. */
    public static Set<String> getUnlocked(UUID uuid) {
        return unlocked.getOrDefault(uuid, Collections.emptySet());
    }
}
