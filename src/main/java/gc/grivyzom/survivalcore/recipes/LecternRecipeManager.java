package gc.grivyzom.survivalcore.recipes;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import gc.grivyzom.survivalcore.recipes.RecipeCategory;
import org.bukkit.entity.Player;
import java.util.stream.Collectors;

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


    public boolean canPlayerAccessRecipe(Player player, LecternRecipe recipe) {
        return RecipeUnlockSystem.canAccessLevel(player, recipe.getLevel());
    }

    public List<LecternRecipe> getRecipesByCategory(RecipeCategory category, int maxLevel) {
        List<LecternRecipe> result = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (int level = 1; level <= maxLevel; level++) {
                List<LecternRecipe> levelRecipes = byLevel.get(level);
                if (levelRecipes != null) {
                    result.addAll(levelRecipes.stream()
                            .filter(recipe -> determineCategory(recipe) == category)
                            .collect(Collectors.toList()));
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return result;
    }

    public RecipeCategory determineCategory(LecternRecipe recipe) {
        return RecipeCategory.fromRecipeName(recipe.getId());
    }

    // 🆕 NUEVO: Obtener todas las recetas disponibles para un nivel
    public List<LecternRecipe> getAvailableRecipes(int lecternLevel) {
        List<LecternRecipe> result = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (int level = 1; level <= lecternLevel; level++) {
                List<LecternRecipe> levelRecipes = byLevel.get(level);
                if (levelRecipes != null) {
                    result.addAll(levelRecipes);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return result;
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

    public Map<String, Integer> getRecipeStats() {
        Map<String, Integer> stats = new HashMap<>();

        lock.readLock().lock();
        try {
            int totalRecipes = 0;
            for (Map.Entry<Integer, List<LecternRecipe>> entry : byLevel.entrySet()) {
                int levelCount = entry.getValue().size();
                stats.put("level_" + entry.getKey(), levelCount);
                totalRecipes += levelCount;
            }

            stats.put("total", totalRecipes);

            // Estadísticas por categoría
            Map<RecipeCategory, Integer> categoryStats = new HashMap<>();
            for (List<LecternRecipe> recipes : byLevel.values()) {
                for (LecternRecipe recipe : recipes) {
                    RecipeCategory category = determineCategory(recipe);
                    categoryStats.put(category, categoryStats.getOrDefault(category, 0) + 1);
                }
            }

            for (Map.Entry<RecipeCategory, Integer> entry : categoryStats.entrySet()) {
                stats.put("category_" + entry.getKey().name().toLowerCase(), entry.getValue());
            }

        } finally {
            lock.readLock().unlock();
        }

        return stats;
    }

    public List<LecternRecipe> searchRecipes(String query, int maxLevel) {
        List<LecternRecipe> results = new ArrayList<>();
        String searchTerm = query.toLowerCase();

        lock.readLock().lock();
        try {
            for (int level = 1; level <= maxLevel; level++) {
                List<LecternRecipe> levelRecipes = byLevel.get(level);
                if (levelRecipes != null) {
                    results.addAll(levelRecipes.stream()
                            .filter(recipe -> recipe.getId().toLowerCase().contains(searchTerm))
                            .collect(Collectors.toList()));
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return results;
    }

    public boolean isValidRecipeCreation(LecternRecipe recipe) {
        if (recipe == null) return false;
        if (recipe.getInputs().length != 2) return false;
        if (recipe.getResult() == null) return false;
        if (recipe.getLevel() < 1 || recipe.getLevel() > 10) return false;
        if (recipe.getXpCost() < 0) return false;

        return true;
    }

    public static class RecipeUnlockSystem {

    public static boolean canAccessLevel(Player player, int level) {
        // Nivel 1: Siempre accesible
        if (level <= 1) return true;

        // Niveles 2-3: Requiere haber usado recetas del nivel anterior
        if (level <= 3) {
            return getRecipesUnlocked(player, level - 1) >= 2;
        }

        // Niveles 4-6: Requiere experiencia y materiales raros
        if (level <= 6) {
            return player.getLevel() >= (level * 5) &&
                    hasRareMaterials(player, level);
        }

        // Niveles 7+: Requiere logros específicos
        return hasAdvancementRequirements(player, level);
    }

    private static int getRecipesUnlocked(Player player, int level) {
        // Implementar contador de recetas desbloqueadas por nivel
        // Esto puede conectarse con RecipeUnlockManager existente
        try {
            return gc.grivyzom.survivalcore.gui.RecipeUnlockManager
                    .getUnlocked(player.getUniqueId()).size();
        } catch (Exception e) {
            return 0; // En caso de error, permitir acceso
        }
    }

    private static boolean hasRareMaterials(Player player, int level) {
        org.bukkit.inventory.Inventory inv = player.getInventory();
        return switch (level) {
            case 4 -> inv.contains(org.bukkit.Material.DIAMOND) ||
                    inv.contains(org.bukkit.Material.EMERALD);
            case 5 -> inv.contains(org.bukkit.Material.NETHERITE_INGOT) ||
                    inv.contains(org.bukkit.Material.NETHER_STAR);
            case 6 -> inv.contains(org.bukkit.Material.DRAGON_EGG) ||
                    inv.contains(org.bukkit.Material.ELYTRA);
            default -> true;
        };
    }

    private static boolean hasAdvancementRequirements(Player player, int level) {
        // Verificar logros específicos según el nivel
        try {
            return switch (level) {
                case 7 -> {
                    var advancement = org.bukkit.Bukkit.getAdvancement(
                            org.bukkit.NamespacedKey.minecraft("end/kill_dragon"));
                    yield advancement != null &&
                            player.getAdvancementProgress(advancement).isDone();
                }
                case 8 -> {
                    var advancement = org.bukkit.Bukkit.getAdvancement(
                            org.bukkit.NamespacedKey.minecraft("nether/all_potions"));
                    yield advancement != null &&
                            player.getAdvancementProgress(advancement).isDone();
                }
                default -> {
                    var advancement = org.bukkit.Bukkit.getAdvancement(
                            org.bukkit.NamespacedKey.minecraft("adventure/hero_of_the_village"));
                    yield advancement != null &&
                            player.getAdvancementProgress(advancement).isDone();
                }
            };
        } catch (Exception e) {
            // Si hay error obteniendo logros, permitir acceso
            return true;
        }
    }
}
}