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
import org.bukkit.event.block.Action;         // ← nuevo
import org.bukkit.ChatColor;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Listener para todo lo relativo a los Atriles Mágicos. Maneja la colocación, rotura,
 * interacción y lógica de recetas. Garantiza que los jugadores reciban exactamente
 * los mismos objetos y cantidades cuando una combinación no es válida.
 */
public class MagicLecternListener implements Listener {

    /* ==============================================================
       ======================= CAMPOS ===============================
       ============================================================== */

    private final Main plugin;
    private final NamespacedKey keyLectern;   // Marca que identifica un atril mágico
    private final NamespacedKey keyLevel;     // Nivel del atril mágico

    // Tiempo (en ticks) para colocar el segundo ítem (6 s = 120 ticks)
    private static final long TIMEOUT_TICKS = 20L * 6;

    // Por ubicación: tarea de espera y lista de ítems pendientes
    private final Map<Location, PendingProcess> pending = new HashMap<>();


    // Recetas cargadas desde lectern_recipes.yml → nivel → clave(inputs) → resultado
    // private final Map<Integer, Map<Set<Material>, ItemStack>> recipesByLevel = new HashMap<>();

    /* ==== NUEVAS CONSTANTES  ===================================== */
    private static final Sound  SOUND_IDLE   = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    private static final Sound  SOUND_INPUT  = Sound.ENTITY_ENDER_EYE_LAUNCH;
    private static final Sound  SOUND_DENIED = Sound.BLOCK_NOTE_BLOCK_BASS;
    private final LecternRecipeManager recipeManager;
    private static final String PERM_USE = "survivalcore.use.lectern";
    private static final int MAX_DENIED_CLICKS = 6;
    private final Map<UUID, Integer> deniedClicks = new HashMap<>();
    private final PreviewManager previewMgr;
    private static class PendingProcess {
        ItemStack  first;       // primer ítem recibido
        BukkitTask idleTask;    // partículas “idle”
        BukkitTask timeoutTask; // límite de 6 s
    }
    /* ==============================================================
       ===================== CONSTRUCTOR ============================
       ============================================================== */

    public MagicLecternListener(Main plugin) {
        this.plugin = plugin;
        this.keyLectern = new NamespacedKey(plugin, "is_magic_lectern");
        this.keyLevel = new NamespacedKey(plugin, "lectern_level");
        this.recipeManager = plugin.getLecternRecipeManager();
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
            copyMeta(placed, ts);
            ts.update(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.LECTERN) return;

        BlockState state = b.getState();
        if (!(state instanceof TileState ts)) return;
        if (!ts.getPersistentDataContainer().has(keyLectern, PersistentDataType.BYTE)) return;

        // Cancelamos drops vanilla y soltamos nuestro ítem custom
        e.setDropItems(false);
        ItemStack drop = new ItemStack(Material.LECTERN);
        ItemMeta meta = drop.getItemMeta();
        copyMeta(ts, meta);
        drop.setItemMeta(meta);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
    }

    /* ==============================================================
       ===================== INTERACCIONES ==========================
       ============================================================== */

    /* ==== ONINTERACT — ahora permite romper agachado ================ */
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
        /* ───────────────────────────────────────────────────────────── */

        // 5) Si está agachado y hace clic izquierdo, permitimos recoger el Atril
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && p.isSneaking()) {
            e.setCancelled(true);

            ItemStack drop = new ItemStack(Material.LECTERN);
            ItemMeta meta  = drop.getItemMeta();
            copyMeta(ts, meta);
            drop.setItemMeta(meta);

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


    /* ==== HANDLELEFTCLICK – idle FX al primer ítem ================== */
    private void handleLeftClick(Player player, Location loc) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            player.playSound(player, SOUND_DENIED, 1f, .8f);
            return;
        }

        // Clonamos solo una unidad para el procesamiento de la receta
        ItemStack single = inHand.clone();
        single.setAmount(1);
        handleRecipeInput(player, loc, single);

        // Si no está en modo creativo, consumimos 1 unidad del ítem real
        if (player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);

            // ===== Aquí ajustamos el sonido según el nivel del lectern =====
            int idx = fxIndex(getLecternLevel(loc));
            player.playSound(player, LEVEL_SOUNDS[idx], 1f, 1.2f);
        }

        // Animación y sonido de input genérico
        player.swingMainHand();
        player.playSound(player, SOUND_INPUT, 1f, 1.2f);
    }



    /* ==============================================================
       ==================== LÓGICA DE RECETAS =======================
       ============================================================== */

    private void handleRecipeInput(Player player, Location loc, ItemStack item) {

        // Si NO hay proceso pendiente, este ítem es el primero
        PendingProcess pp = pending.get(loc);
        if (pp == null) {

            pp = new PendingProcess();
            pp.first = item;

            // ➊ Tarea timeout (6 s)
            pp.timeoutTask = new BukkitRunnable() {
                @Override public void run() {
                    // Si nadie metió el 2.º ítem:
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

            // ➋ Tarea idle FX (cada 6 ticks)
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

            pending.put(loc, pp);          // registra el proceso
            return;                        // fin del 1.º ítem
        }

        /* --------- segundo ítem --------- */
        // Cancela las dos tareas pendientes
        if (pp.idleTask   != null) pp.idleTask.cancel();
        if (pp.timeoutTask != null) pp.timeoutTask.cancel();

        pending.remove(loc);              // limpia la entrada
        attemptCombine(player, loc, pp.first, item);
    }



    private void attemptCombine(Player p, Location loc, ItemStack a, ItemStack b) {
        int level = getLecternLevel(loc);

        // 1⃣  buscamos la receta con coste
        LecternRecipe recipe = recipeManager.findRecipe(level, a, b);
        if (recipe == null) {
            p.playSound(p.getLocation(), SOUND_DENIED, 1f, 0.8f);
            p.sendMessage(ChatColor.RED + "Combinación inválida.");
            dropBack(loc, List.of(a, b));
            return;
        }

        // 2⃣  validamos XP propia + banco
        int cost    = recipe.getXpCost();
        int barXp   = getPlayerRawXp(p);
        String uuid = p.getUniqueId().toString();
        long bankXp = plugin.getDatabaseManager().getBankedXp(uuid);
        long available = (long) barXp + bankXp;
        if (available < cost) {
            p.playSound(p.getLocation(), SOUND_DENIED, 1f, 0.8f);
            p.sendMessage(ChatColor.RED + "Necesitas " + cost + " puntos de experiencia.");
            dropBack(loc, List.of(a, b));
            return;
        }

        // 3⃣  descontamos primero de la barra
        int remaining = cost;
        if (barXp >= remaining) {
            p.giveExp(-remaining);
            remaining = 0;
        } else {
            p.giveExp(-barXp);
            remaining -= barXp;
        }
        // 4⃣  si aún queda, lo restamos del banco
        if (remaining > 0) {
            plugin.getDatabaseManager().withdrawBankedXp(uuid, remaining);
        }

        // 5⃣ obtenemos resultado
        ItemStack result = recipe.getResult();
        ItemMeta meta = result.getItemMeta();

        // Desbloqueo de receta
        RecipeUnlockManager.unlockRecipe(p.getUniqueId(), result.getType().toString());

        World w = p.getWorld();
        int idx = fxIndex(level);
        w.spawnParticle(
                LEVEL_PARTICLES[idx],
                loc.clone().add(0.5, 1.0, 0.5),
                80,    // count
                0.4,   // offsetX
                0.6,   // offsetY
                0.4,   // offsetZ
                0.2    // extra
        );
        w.playSound(
                loc,
                LEVEL_SOUNDS[idx],
                1f,    // volumen
                1f     // pitch
        );

        // 6⃣ entregamos el resultado al jugador
        String displayName;
        if (meta != null && meta.hasDisplayName()) {
            displayName = meta.getDisplayName();
        } else if (!recipe.getId().isEmpty()) {
            displayName = recipe.getId();
        } else {
            displayName = beautify(result.getType());
        }

// ➌ damos el ítem y enviamos el mensaje
        p.getInventory().addItem(result.clone());
        p.sendMessage(ChatColor.GREEN + "¡Has creado "
                + ChatColor.YELLOW + displayName
                + ChatColor.GREEN + "!");
        previewMgr.showPreview(loc, result.clone());
    }

    /* ==============================================================
       ===================== MÉTODOS UTILERÍA =======================
       ============================================================== */

    private void dropBack(Location loc, List<ItemStack> items) {
        World w = loc.getWorld();
        // Calcula índice según nivel de lectern y ajusta el pitch
        int idx = fxIndex(getLecternLevel(loc));
        w.playSound(
                loc,
                Sound.BLOCK_NOTE_BLOCK_BASS,
                0.7f,
                0.5f + 0.1f * idx
        );
        items.forEach(it ->
                w.dropItemNaturally(loc.clone().add(0.5, 1, 0.5), it)
        );
        previewMgr.removePreview(loc);
    }


    // Limpia estado pendiente y quita la pre-view
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

    private void copyMeta(ItemStack src, TileState target) {
        PersistentDataContainer from = src.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer to = target.getPersistentDataContainer();
        to.set(keyLectern, PersistentDataType.BYTE, (byte) 1);
        to.set(keyLevel, PersistentDataType.INTEGER, from.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1));
    }

    private void copyMeta(TileState src, ItemMeta target) {
        PersistentDataContainer from = src.getPersistentDataContainer();
        PersistentDataContainer to = target.getPersistentDataContainer();
        to.set(keyLectern, PersistentDataType.BYTE, (byte) 1);
        to.set(keyLevel, PersistentDataType.INTEGER, from.getOrDefault(keyLevel, PersistentDataType.INTEGER, 1));
    }

    /* ==============================================================
       =================== CARGA DE RECETAS YAML ====================
       ============================================================== */

    private String beautify(Material m) {
        return Arrays.stream(m.toString().split("_"))
                .map(s -> s.charAt(0) + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
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
    /** Devuelve un índice válido para los arrays de FX. */
    private int fxIndex(int level) {
        return Math.min(level-1, LEVEL_PARTICLES.length-1);
    }
    /**
     * Calcula cuánta XP “cruda” (puntos) hay que ganar para subir del nivel n al n+1.
     */
    private int xpToNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else if (level >= 15) {
            return 37 + (level - 15) * 5;
        } else {
            return 7 + level * 2;
        }
    }

    /**
     * Devuelve la XP total “cruda” que un jugador tiene en la barra:
     * suma de todos los niveles + la fracción actual.
     */
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
        // Relámpago + fuego 5 s y sonido dramático
        w.strikeLightning(loc);
        p.setFireTicks(100); // 5 s / 20 tps
        p.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, .5f);
        p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "¡La magia te castiga por tu insolencia!");

    }

}
