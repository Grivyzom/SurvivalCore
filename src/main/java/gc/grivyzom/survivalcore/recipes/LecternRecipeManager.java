package gc.grivyzom.survivalcore.recipes;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class LecternRecipeManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Integer, List<LecternRecipe>> byLevel = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private CompletableFuture<Void> loadingTask = null;

    public LecternRecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "lectern_recipes.yml");
        if (!file.exists()) plugin.saveResource("lectern_recipes.yml", false);
        reloadAsync();
    }

    /* -------------- API pública -------------- */

    /** Async reload – seguro de llamar desde cualquier hilo. */
    public void reloadAsync() {
        loadingTask = CompletableFuture.supplyAsync(this::readYaml)
                .thenAccept(fresh ->
                        Bukkit.getScheduler().runTask(plugin, () -> replaceCacheSafe(fresh))
                );
    }


    /** @return resultado o null si no existe receta válida. */
    public ItemStack findResult(int lecternLevel, ItemStack a, ItemStack b) {
        lock.readLock().lock();
        try {
            for (int lvl = lecternLevel; lvl >= 1; lvl--) {
                List<LecternRecipe> list = byLevel.get(lvl);
                if (list == null) continue;
                for (LecternRecipe r : list) if (r.matches(a,b)) return r.getResult();
            }
            return null;
        } finally { lock.readLock().unlock(); }
    }
    public LecternRecipe findRecipe(int lecternLevel, ItemStack a, ItemStack b) {
        lock.readLock().lock();
        try {
            for (int lvl = lecternLevel; lvl >= 1; lvl--) {
                List<LecternRecipe> list = byLevel.get(lvl);
                if (list == null) continue;
                for (LecternRecipe r : list) if (r.matches(a,b)) return r;
            }
            return null;
        } finally { lock.readLock().unlock(); }
    }
    /** Guarda y añade una receta creada en tiempo real. */
    public void addAndSave(LecternRecipe r) {
        lock.writeLock().lock();
        try { byLevel.computeIfAbsent(r.getLevel(), k->new ArrayList<>()).add(r); }
        finally { lock.writeLock().unlock(); }
        saveYaml();                     // síncrono, pero es IO mínimo
    }

    /* -------------- Interno -------------- */

    private Map<Integer, List<LecternRecipe>> readYaml() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<Integer,List<LecternRecipe>> map = new HashMap<>();

        ConfigurationSection levels = cfg.getConfigurationSection("levels");
        if (levels==null) return map;

        for (String key : levels.getKeys(false)) {
            int lvl = Integer.parseInt(key);
            List<Map<?,?>> list = cfg.getMapList("levels."+key);
            for (Map<?, ?> raw : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = (Map<String, Object>) raw;   // ← down-cast seguro

                ItemStack in1 = (ItemStack) entry.get("input1");
                ItemStack in2 = (ItemStack) entry.get("input2");
                ItemStack out = (ItemStack) entry.get("result");

                String id  = (String)  entry.getOrDefault("id",  "");
                int cost   = ((Number) entry.getOrDefault("cost", 0)).intValue();

                if (in1 == null || in2 == null || out == null) continue;
                map.computeIfAbsent(lvl, k -> new ArrayList<>())
                        .add(new LecternRecipe(id, lvl, cost, in1, in2, out));
            }
        }
        return map;
    }

    private void replaceCacheSafe(Map<Integer,List<LecternRecipe>> fresh) {
        lock.writeLock().lock();
        try {
            byLevel.clear();
            byLevel.putAll(fresh);
            plugin.getLogger().info("[Lectern] Recetas cargadas: "+fresh.values().stream().mapToInt(List::size).sum());
        } finally { lock.writeLock().unlock(); }
    }

    private void saveYaml() {
        YamlConfiguration cfg = new YamlConfiguration();
        lock.readLock().lock();
        try {
            ConfigurationSection levels = cfg.createSection("levels");
            byLevel.forEach((lvl,list)->{
                List<Map<String,Object>> out = new ArrayList<>();
                for (LecternRecipe r: list) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("id",    r.getId());
                    m.put("cost",  r.getXpCost());
                    m.put("input1",r.getInputs()[0]);
                    m.put("input2",r.getInputs()[1]);
                    m.put("result",r.getResult());
                    out.add(m);
                }
                levels.set(String.valueOf(lvl), out);
            });
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("No pudo guardarse lectern_recipes.yml: "+e.getMessage());
        } finally { lock.readLock().unlock(); }
    }

    public void replaceRecipe(LecternRecipe oldRecipe, LecternRecipe newRecipe) {
        lock.writeLock().lock();
        try {
            List<LecternRecipe> list = byLevel.get(oldRecipe.getLevel());
            if (list != null) {
                list.remove(oldRecipe);        // elimina la antigua
            }
            byLevel.computeIfAbsent(newRecipe.getLevel(), k -> new ArrayList<>())
                    .add(newRecipe);          // añade la nueva
        } finally {
            lock.writeLock().unlock();
        }
        saveYaml();                          // persiste el cambio
    }


}
