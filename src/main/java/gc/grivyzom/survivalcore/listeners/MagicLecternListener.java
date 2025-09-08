package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.gui.MagicLecternMenu;
import gc.grivyzom.survivalcore.gui.RecipeUnlockManager;
import gc.grivyzom.survivalcore.recipes.LecternRecipe;
import gc.grivyzom.survivalcore.recipes.LecternRecipeManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.block.Action;
import org.bukkit.ChatColor;
import gc.grivyzom.survivalcore.effects.LecternEffectsManager;
import gc.grivyzom.survivalcore.recipes.RecipeCategory;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Listener para todo lo relativo a los Atriles Mágicos. Maneja la colocación, rotura,
 * interacción y lógica de recetas. Garantiza que los jugadores reciban exactamente
 * los mismos objetos y cantidades cuando una combinación no es válida.
 *
 * VERSIÓN CORREGIDA: Conserva nombre y lore al romper
 * NUEVA FUNCIONALIDAD: Detecta jugadores cercanos y emite partículas
 */
public class MagicLecternListener implements Listener {

    // === CAMPOS - VERSIÓN CORREGIDA ===
    private final Main plugin;
    private final NamespacedKey keyLectern;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keyDisplayName; // 🆕 NUEVO
    private final NamespacedKey keyLore;        // 🆕 NUEVO
    private final LecternRecipeManager recipeManager;
    private final LecternEffectsManager effectsManager;

    // 🆕 NUEVO: Sistema de detección de proximidad
    private final Map<Location, BukkitTask> proximityTasks = new HashMap<>();
    private static final double DETECTION_RADIUS = 8.0; // Radio en bloques para detectar jugadores
    private static final long PROXIMITY_CHECK_INTERVAL = 40L; // Verificar cada segundo (20 ticks)

    // Configuración de timeouts
    private static final long TIMEOUT_TICKS = 20L * 6; // 6 segundos

    // Estado de procesos pendientes
    private final Map<Location, PendingProcess> pending = new HashMap<>();

    // Configuración de sonidos y permisos
    private static final Sound SOUND_IDLE = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    private static final Sound SOUND_INPUT = Sound.ENTITY_ENDER_EYE_LAUNCH;
    private static final Sound SOUND_DENIED = Sound.BLOCK_NOTE_BLOCK_BASS;
    private static final String PERM_USE = "survivalcore.use.lectern";
    private static final int MAX_DENIED_CLICKS = 6;

    // Control de acceso denegado
    private final Map<UUID, Integer> deniedClicks = new HashMap<>();

    // Manager de previews
    private final PreviewManager previewMgr;

    // Clase auxiliar para procesos pendientes
    private static class PendingProcess {
        ItemStack first;        // primer ítem recibido
        BukkitTask idleTask;    // partículas "idle"
        BukkitTask timeoutTask; // límite de 6 s
    }

    // === CONSTRUCTOR CORREGIDO ===
    public MagicLecternListener(Main plugin) {
        this.plugin = plugin;
        this.keyLectern = new NamespacedKey(plugin, "is_magic_lectern");
        this.keyLevel = new NamespacedKey(plugin, "lectern_level");
        this.keyDisplayName = new NamespacedKey(plugin, "lectern_display_name"); // 🆕 NUEVO
        this.keyLore = new NamespacedKey(plugin, "lectern_lore");               // 🆕 NUEVO
        this.recipeManager = plugin.getLecternRecipeManager();
        this.effectsManager = new LecternEffectsManager(plugin);
        this.previewMgr = new PreviewManager(plugin);
    }

    /* ==============================================================
       ==================== COLOCAR / ROMPER ========================
       ============================================================== */

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack placed = e.getItemInHand();
        if (!isMagicLectern(placed)) return;

        BlockState state = e.getBlock().getState();
        if (state instanceof TileState ts) {
            copyMetaImproved(placed, ts); // 🔧 MEJORADO
            ts.update(true);

            // 🆕 NUEVO: Iniciar detección de proximidad
            startProximityDetection(e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.LECTERN) return;

        BlockState state = b.getState();
        if (!(state instanceof TileState ts)) return;
        if (!ts.getPersistentDataContainer().has(keyLectern, PersistentDataType.BYTE)) return;

        // 🆕 NUEVO: Detener detección de proximidad
        stopProximityDetection(b.getLocation());

        // Cancelamos drops vanilla y soltamos nuestro ítem custom
        e.setDropItems(false);
        ItemStack drop = createMagicLecternFromTileState(ts); // 🔧 MEJORADO
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
    }

    /* ==============================================================
       ========= 🆕 NUEVO: SISTEMA DE DETECCIÓN DE PROXIMIDAD =======
       ============================================================== */

    /**
     * Inicia la detección de jugadores cercanos para un lectern magic
     */
    private void startProximityDetection(Location lecternLoc) {
        // Evitar duplicados
        stopProximityDetection(lecternLoc);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                World world = lecternLoc.getWorld();
                if (world == null) {
                    cancel();
                    proximityTasks.remove(lecternLoc);
                    return;
                }

                // Verificar que el bloque sigue siendo un lectern magic
                Block block = lecternLoc.getBlock();
                if (block.getType() != Material.LECTERN || !isMagicLecternBlock(block)) {
                    cancel();
                    proximityTasks.remove(lecternLoc);
                    return;
                }

                // Buscar jugadores cercanos
                boolean playerNearby = world.getPlayers().stream()
                        .anyMatch(player -> player.getLocation().distance(lecternLoc) <= DETECTION_RADIUS);

                if (playerNearby) {
                    // Emitir partículas de encantamiento cuando hay jugadores cerca
                    spawnProximityParticles(lecternLoc);
                }
            }
        }.runTaskTimer(plugin, 0L, PROXIMITY_CHECK_INTERVAL);

        proximityTasks.put(lecternLoc, task);
    }

    /**
     * Detiene la detección de proximidad para un lectern
     */
    private void stopProximityDetection(Location lecternLoc) {
        BukkitTask task = proximityTasks.remove(lecternLoc);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Genera partículas de proximidad cuando hay jugadores cerca
     */
    private void spawnProximityParticles(Location lecternLoc) {
        World world = lecternLoc.getWorld();
        if (world == null) return;

        // Partículas de encantamiento flotando alrededor del lectern
        Location particleLoc = lecternLoc.clone().add(0.5, 1.2, 0.5);

        // Efecto principal: partículas de enchantment_table
        world.spawnParticle(
                Particle.ENCHANTMENT_TABLE,
                particleLoc,
                8,      // cantidad
                0.4,    // spread X
                0.3,    // spread Y
                0.4,    // spread Z
                0.02    // velocidad
        );

        // Efecto secundario sutil: algunas partículas más brillantes
        world.spawnParticle(
                Particle.CRIT_MAGIC,
                particleLoc,
                2,      // cantidad menor
                0.2,    // spread menor
                0.2,
                0.2,
                0.01
        );
    }

    /**
     * Verifica si un bloque es un lectern magic
     */
    private boolean isMagicLecternBlock(Block block) {
        if (block.getType() != Material.LECTERN) return false;

        BlockState state = block.getState();
        if (!(state instanceof TileState ts)) return false;

        return ts.getPersistentDataContainer().has(keyLectern, PersistentDataType.BYTE);
    }

    /* ==============================================================
       ================= MÉTODO DE LIMPIEZA MEJORADO ===============
       ============================================================== */

    /**
     * Limpia todos los efectos y tareas activas
     */
    public void cleanup() {
        if (effectsManager != null) {
            effectsManager.cleanup();
        }

        // 🆕 NUEVO: Limpiar tareas de proximidad
        proximityTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        proximityTasks.clear();

        pending.clear();

        plugin.getLogger().info("✓ Efectos de Lectern Magic limpiados");
    }

    /* ==============================================================
       ===================== INTERACCIONES ==========================
       ============================================================== */

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // 1) Sólo interesan clics con la mano principal
        if (e.getHand() != EquipmentSlot.HAND) return;

        // 2) Sólo bloques LECTERN
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.LECTERN) return;

        // 3) Sólo aquellos lecterns marcados como "mágicos"
        BlockState state = b.getState();
        if (!(state instanceof TileState ts)
                || !ts.getPersistentDataContainer().has(keyLectern, PersistentDataType.BYTE)) {
            return;
        }

        Player p = e.getPlayer();

        /* ──────────────── 4) Interceptor de permisos ──────────────── */
        if (!p.hasPermission(PERM_USE)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "No tienes el poder necesario para usar este Atril Mágico");
            p.playSound(p.getLocation(), SOUND_DENIED, 1f, .8f);

            int tries = deniedClicks.getOrDefault(p.getUniqueId(), 0) + 1;
            deniedClicks.put(p.getUniqueId(), tries);

            if (tries > MAX_DENIED_CLICKS) {
                deniedClicks.remove(p.getUniqueId());
                punish(p);
            }
            return;
        }
        // Limpia contador si ya tiene permiso
        deniedClicks.remove(p.getUniqueId());

        // 5) Si está agachado y hace clic izquierdo, permitimos recoger el Atril
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && p.isSneaking()) {
            e.setCancelled(true);

            // 🆕 NUEVO: Detener detección al recoger
            stopProximityDetection(b.getLocation());

            ItemStack drop = createMagicLecternFromTileState(ts); // 🔧 MEJORADO

            b.setType(Material.AIR, false);
            b.getWorld().dropItemNaturally(b.getLocation().add(.5, .5, .5), drop);
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 1f);
            p.swingMainHand();
            return;
        }

        // 6) Bloquea la interacción vanilla y abre tu menú o gestiona clicks
        e.setCancelled(true);
        switch (e.getAction()) {
            case RIGHT_CLICK_BLOCK -> MagicLecternMenu.open(p, b);
            case LEFT_CLICK_BLOCK  -> handleLeftClick(p, b.getLocation());
        }
    }

    /* ==============================================================
       ================ 🔧 MÉTODOS MEJORADOS =======================
       ============================================================== */

    /**
     * 🔧 MÉTODO MEJORADO: Copia metadatos incluyendo nombre y lore
     */
    private void copyMetaImproved(ItemStack src, TileState target) {
        ItemMeta srcMeta = src.getItemMeta();
        if (srcMeta == null) return;

        PersistentDataContainer from = srcMeta.getPersistentDataContainer();
        PersistentDataContainer to = target.getPersistentDataContainer();

        // Copiar metadatos básicos
        to.set(keyLectern, PersistentDataType.BYTE, (byte) 1);
        to.set(keyLevel, PersistentDataType.INTEGER, from.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1));

        // 🆕 NUEVO: Guardar nombre y lore en el TileState
        if (srcMeta.hasDisplayName()) {
            to.set(keyDisplayName, PersistentDataType.STRING, srcMeta.getDisplayName());
        }

        if (srcMeta.hasLore() && srcMeta.getLore() != null) {
            String loreString = String.join("||LORE_SEPARATOR||", srcMeta.getLore());
            to.set(keyLore, PersistentDataType.STRING, loreString);
        }
    }

    /**
     * 🆕 MÉTODO NUEVO: Crea un Lectern Magic completo desde un TileState
     */
    private ItemStack createMagicLecternFromTileState(TileState ts) {
        PersistentDataContainer container = ts.getPersistentDataContainer();

        // Crear item base
        ItemStack lectern = new ItemStack(Material.LECTERN);
        ItemMeta meta = lectern.getItemMeta();

        if (meta == null) {
            plugin.getLogger().warning("No se pudo obtener ItemMeta para Lectern Magic");
            return lectern;
        }

        PersistentDataContainer itemContainer = meta.getPersistentDataContainer();

        // Copiar metadatos básicos
        itemContainer.set(keyLectern, PersistentDataType.BYTE, (byte) 1);
        int level = container.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
        itemContainer.set(keyLevel, PersistentDataType.INTEGER, level);

        // 🔧 CRÍTICO: Restaurar nombre y lore desde el TileState
        String savedDisplayName = container.get(keyDisplayName, PersistentDataType.STRING);
        String savedLore = container.get(keyLore, PersistentDataType.STRING);

        if (savedDisplayName != null && !savedDisplayName.isEmpty()) {
            meta.setDisplayName(savedDisplayName);
        } else {
            // Fallback: nombre por defecto basado en nivel
            meta.setDisplayName(ChatColor.DARK_PURPLE + "✦ Atril Mágico " + ChatColor.GOLD + "Nivel " + level + ChatColor.DARK_PURPLE + " ✦");
        }

        if (savedLore != null && !savedLore.isEmpty()) {
            List<String> loreList = Arrays.asList(savedLore.split("\\|\\|LORE_SEPARATOR\\|\\|"));
            meta.setLore(loreList);
        } else {
            // Fallback: lore por defecto
            List<String> defaultLore = Arrays.asList(
                    ChatColor.GRAY + "Un atril imbuido con energía arcana",
                    ChatColor.GRAY + "capaz de fusionar objetos mágicamente.",
                    "",
                    ChatColor.YELLOW + "Nivel: " + ChatColor.GOLD + level,
                    ChatColor.YELLOW + "Capacidad: " + ChatColor.GREEN + "Recetas nivel " + level + " y menor",
                    "",
                    ChatColor.AQUA + "▶ Clic derecho: Abrir menú",
                    ChatColor.AQUA + "▶ Clic izquierdo: Añadir ingredientes",
                    ChatColor.RED + "▶ Shift + Clic izq: Recoger atril",
                    "",
                    ChatColor.DARK_PURPLE + "✦ Item Mágico ✦"
            );
            meta.setLore(defaultLore);
        }

        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        lectern.setItemMeta(meta);

        plugin.getLogger().info("Lectern Magic creado con nombre: " +
                (savedDisplayName != null ? savedDisplayName : "por defecto") +
                " y lore: " + (savedLore != null ? "restaurado" : "por defecto"));

        return lectern;
    }

    /**
     * 🔧 MÉTODO MEJORADO: Copia desde TileState a ItemMeta (por compatibilidad)
     */
    private void copyMeta(TileState src, ItemMeta target) {
        PersistentDataContainer from = src.getPersistentDataContainer();
        PersistentDataContainer to = target.getPersistentDataContainer();

        // Copiar metadatos básicos
        to.set(keyLectern, PersistentDataType.BYTE, (byte) 1);
        to.set(keyLevel, PersistentDataType.INTEGER, from.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1));

        // 🆕 NUEVO: Restaurar nombre y lore
        String savedDisplayName = from.get(keyDisplayName, PersistentDataType.STRING);
        String savedLore = from.get(keyLore, PersistentDataType.STRING);

        if (savedDisplayName != null && !savedDisplayName.isEmpty()) {
            target.setDisplayName(savedDisplayName);
        }

        if (savedLore != null && !savedLore.isEmpty()) {
            List<String> loreList = Arrays.asList(savedLore.split("\\|\\|LORE_SEPARATOR\\|\\|"));
            target.setLore(loreList);
        }
    }

    /* ==============================================================
       ============= RESTO DEL CÓDIGO EXISTENTE ===================
       ============================================================== */

    private void handleLeftClick(Player player, Location loc) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            effectsManager.playFailureEffects(loc, getLecternLevel(loc));
            return;
        }

        ItemStack single = inHand.clone();
        single.setAmount(1);
        handleRecipeInput(player, loc, single);

        if (player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
            effectsManager.playInputEffects(loc, getLecternLevel(loc));
        }

        player.swingMainHand();
        player.playSound(player, SOUND_INPUT, 1f, 1.2f);
    }

    private void handleRecipeInput(Player player, Location loc, ItemStack item) {
        PendingProcess pp = pending.get(loc);
        if (pp == null) {
            pp = new PendingProcess();
            pp.first = item;

            effectsManager.startAmbientEffects(loc, getLecternLevel(loc));

            pp.timeoutTask = new BukkitRunnable() {
                @Override public void run() {
                    PendingProcess current = pending.remove(loc);
                    if (current != null) {
                        if (current.idleTask != null) current.idleTask.cancel();
                        player.sendMessage(ChatColor.RED +
                                "Combinación cancelada (tardaste demasiado).");
                        dropBack(loc, List.of(current.first));
                        previewMgr.removePreview(loc);
                    }
                }
            }.runTaskLater(plugin, TIMEOUT_TICKS);

            pp.idleTask = new BukkitRunnable() {
                @Override public void run() {
                    int idx = fxIndex(getLecternLevel(loc));
                    loc.getWorld().spawnParticle(
                            LEVEL_PARTICLES[idx],
                            loc.clone().add(0.5, 1.1, 0.5),
                            5, 0.25, 0.4, 0.25, 0.01
                    );
                }
            }.runTaskTimer(plugin, 0L, 6L);

            pending.put(loc, pp);
            return;
        }

        if (pp.idleTask   != null) pp.idleTask.cancel();
        if (pp.timeoutTask != null) pp.timeoutTask.cancel();

        effectsManager.stopEffects(loc);

        pending.remove(loc);
        attemptCombine(player, loc, pp.first, item);
    }

    private void attemptCombine(Player p, Location loc, ItemStack a, ItemStack b) {
        int level = getLecternLevel(loc);
        LecternRecipe recipe = recipeManager.findRecipe(level, a, b);

        if (recipe == null) {
            effectsManager.playFailureEffects(loc, level);
            p.sendMessage(ChatColor.RED + "Combinación inválida.");
            dropBack(loc, List.of(a, b));
            return;
        }

        int cost    = recipe.getXpCost();
        int barXp   = getPlayerRawXp(p);
        String uuid = p.getUniqueId().toString();
        long bankXp = plugin.getDatabaseManager().getBankedXp(uuid);
        long available = (long) barXp + bankXp;

        if (available < cost) {
            effectsManager.playFailureEffects(loc, level);
            p.sendMessage(ChatColor.RED + "Necesitas " + cost + " puntos de experiencia.");
            dropBack(loc, List.of(a, b));
            return;
        }

        int remaining = cost;
        if (barXp >= remaining) {
            p.giveExp(-remaining);
            remaining = 0;
        } else {
            p.giveExp(-barXp);
            remaining -= barXp;
        }
        if (remaining > 0) {
            plugin.getDatabaseManager().withdrawBankedXp(uuid, remaining);
        }

        ItemStack result = recipe.getResult();
        ItemMeta meta = result.getItemMeta();
        RecipeCategory category = determineCategory(recipe);
        effectsManager.playSuccessEffects(loc, level, category);

        RecipeUnlockManager.unlockRecipe(p.getUniqueId(), result.getType().toString());

        World w = p.getWorld();
        int idx = fxIndex(level);
        w.spawnParticle(
                LEVEL_PARTICLES[idx],
                loc.clone().add(0.5, 1.0, 0.5),
                80, 0.4, 0.6, 0.4, 0.2
        );
        w.playSound(loc, LEVEL_SOUNDS[idx], 1f, 1f);

        String displayName;
        if (meta != null && meta.hasDisplayName()) {
            displayName = meta.getDisplayName();
        } else if (!recipe.getId().isEmpty()) {
            displayName = recipe.getId();
        } else {
            displayName = beautify(result.getType());
        }

        p.getInventory().addItem(result.clone());
        p.sendMessage(ChatColor.GREEN + "¡Has creado " +
                ChatColor.YELLOW + getDisplayName(result) +
                ChatColor.GREEN + "!");

        effectsManager.showPreview(loc, result.clone());
    }

    /* ==============================================================
       ===================== MÉTODOS UTILERÍA =======================
       ============================================================== */

    private void dropBack(Location loc, List<ItemStack> items) {
        World w = loc.getWorld();
        effectsManager.playFailureEffects(loc, getLecternLevel(loc));
        items.forEach(it ->
                w.dropItemNaturally(loc.clone().add(0.5, 1, 0.5), it)
        );
        if (previewMgr != null) {
            previewMgr.removePreview(loc);
        }
    }

    private void cleanup(Location loc) {
        pending.remove(loc);
        previewMgr.removePreview(loc);
    }

    private int getLecternLevel(Location loc) {
        BlockState state = loc.getBlock().getState();
        if (state instanceof TileState ts) {
            return ts.getPersistentDataContainer().getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
        }
        return 1;
    }

    private boolean isMagicLectern(ItemStack it) {
        return it != null && it.hasItemMeta() &&
                it.getItemMeta().getPersistentDataContainer().has(keyLectern, PersistentDataType.BYTE);
    }

    private String beautify(Material m) {
        return Arrays.stream(m.toString().split("_"))
                .map(s -> s.charAt(0) + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private RecipeCategory determineCategory(LecternRecipe recipe) {
        return RecipeCategory.fromRecipeName(recipe.getId());
    }

    private String getDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return beautify(item.getType());
    }

    // ---------- arrays cíclicos ----------
    private static final Particle[] LEVEL_PARTICLES = {
            Particle.END_ROD,              // lvl 1
            Particle.CRIT_MAGIC,           // lvl 2
            Particle.ENCHANTMENT_TABLE,    // lvl 3
            Particle.SOUL,                 // lvl 4
            Particle.SPELL_WITCH           // lvl 5+
    };
    private static final Sound[] LEVEL_SOUNDS = {
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,   // lvl 1
            Sound.BLOCK_AMETHYST_BLOCK_RESONATE,  // lvl 2
            Sound.ENTITY_ILLUSIONER_CAST_SPELL,   // lvl 3
            Sound.BLOCK_BEACON_AMBIENT,           // lvl 4
            Sound.UI_TOAST_CHALLENGE_COMPLETE     // lvl 5+
    };

    private int fxIndex(int level) {
        return Math.min(level-1, LEVEL_PARTICLES.length-1);
    }

    private int xpToNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else if (level >= 15) {
            return 37 + (level - 15) * 5;
        } else {
            return 7 + level * 2;
        }
    }

    private int getPlayerRawXp(Player p) {
        int total = Math.round(p.getExp() * xpToNextLevel(p.getLevel()));
        for (int lvl = 0; lvl < p.getLevel(); lvl++) {
            total += xpToNextLevel(lvl);
        }
        return total;
    }

    private void punish(Player p){
        World w = p.getWorld();
        Location loc = p.getLocation();
        w.strikeLightning(loc);
        p.setFireTicks(100);
        p.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, .5f);
        p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "¡La magia te castiga por tu insolencia!");
    }
}