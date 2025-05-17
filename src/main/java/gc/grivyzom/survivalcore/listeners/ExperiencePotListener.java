package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.gui.ExperiencePotMenu;
import gc.grivyzom.survivalcore.util.PlacedBlocksManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Arrays;

public class ExperiencePotListener implements Listener {
    private final Main plugin;
    private final PlacedBlocksManager placedMgr;
    private final NamespacedKey xpKey;
    private final NamespacedKey potKey;

    public ExperiencePotListener(Main plugin) {
        this.plugin = plugin;
        this.placedMgr = plugin.getPlacedBlocksManager();
        this.xpKey = new NamespacedKey(plugin, "banked_xp");
        this.potKey = new NamespacedKey(plugin, "is_xp_pot");
    }

    /**
     * Al colocar la vasija especial, transfiere su metadata al bloque.
     */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.DECORATED_POT) return;

        BlockState bs = b.getState();
        if (!(bs instanceof TileState ts)) return;

        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        if (!pdc.has(potKey, PersistentDataType.BYTE)) {
            return; // Es una vasija normal, dejar comportamiento vanilla
        }

        // Es nuestra vasija especial
        e.setDropItems(false);
        e.setExpToDrop(0);
        Location loc = b.getLocation();

        long stored = pdc.getOrDefault(xpKey, PersistentDataType.LONG, 0L);

        // Crear la nueva vasija con toda la metadata
        ItemStack pot = new ItemStack(Material.DECORATED_POT);
        ItemMeta meta = pot.getItemMeta();
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();

        // Copiar toda la metadata del bloque al item
        itemPdc.set(potKey, PersistentDataType.BYTE, (byte) 1);
        itemPdc.set(xpKey, PersistentDataType.LONG, stored);

        // Mantener nombre y lore
        meta.setDisplayName(ChatColor.GREEN + "Ánfora de Experiencia");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Una ánfora forjada por antiguos sabios,",
                ChatColor.GRAY + "capaz de contener fragmentos de tu experiencia.",
                "",
                ChatColor.GOLD + "Coloca para usar"
        ));

        pot.setItemMeta(meta);
        loc.getWorld().dropItemNaturally(loc, pot);

        // Registrar como bloque colocado por el plugin si no estaba registrado
        if (!placedMgr.isBlockPlaced(loc)) {
            placedMgr.addBlock(loc);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item.getType() != Material.DECORATED_POT || !item.hasItemMeta()) return;

        PersistentDataContainer idc = item.getItemMeta().getPersistentDataContainer();
        if (!idc.has(potKey, PersistentDataType.BYTE)) return;

        Block b = e.getBlockPlaced();
        BlockState bs = b.getState();
        if (bs instanceof TileState ts) {
            // Copiar toda la metadata del item al bloque
            PersistentDataContainer pdc = ts.getPersistentDataContainer();
            idc.getKeys().forEach(key -> {
                if (idc.has(key, PersistentDataType.BYTE)) {
                    pdc.set(key, PersistentDataType.BYTE, idc.get(key, PersistentDataType.BYTE));
                } else if (idc.has(key, PersistentDataType.LONG)) {
                    pdc.set(key, PersistentDataType.LONG, idc.get(key, PersistentDataType.LONG));
                }
                // Añadir más tipos de datos si es necesario
            });
            ts.update(true);

            // Registrar el bloque como colocado por el plugin
            placedMgr.addBlock(b.getLocation());
        }
    }

    /**
     * Solo la vasija especial bloquea el inventario vanilla y abre el menú personalizado.
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getInventory().getLocation() == null) return;
        Block b = e.getInventory().getLocation().getBlock();
        if (b.getType() != Material.DECORATED_POT) return;

        BlockState bs = b.getState();
        if (!(bs instanceof TileState ts)) return;
        if (!ts.getPersistentDataContainer().has(potKey, PersistentDataType.BYTE)) return;

        e.setCancelled(true);
        Player p = (Player) e.getPlayer();
        ExperiencePotMenu.open(p, b); // Usamos el bloque que ya obtuvimos
    }

    /* =======================================================================
       CLICK SOBRE LA ÁNFORA
       -----------------------------------------------------------------------
       • Shift + LEFT  ⇒ Depositar todo
       • Shift + RIGHT ⇒ Retirar todo (ya implementado)
       • RIGHT normal  ⇒ Abrir GUI
       ======================================================================= */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {

        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.DECORATED_POT) return;

        BlockState bs = b.getState();
        if (!(bs instanceof TileState ts)) return;

        if (!ts.getPersistentDataContainer().has(potKey, PersistentDataType.BYTE)) return; // vasija normal

        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();

        /* ------------------- 1. Shift + LEFT  = Depositar TODO ------------------- */
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && p.isSneaking()) {
            e.setCancelled(true); // evita romper la vasija

            int playerLv = p.getLevel();
            if (playerLv <= 0) {
                p.sendMessage(ChatColor.RED + "No tienes niveles para guardar.");
                return;
            }

            long xpPlayer = playerLv * 68L;
            long storedXp = plugin.getDatabaseManager().addXpCapped(uuid, xpPlayer);

            if (storedXp == 0) {
                p.sendMessage(ChatColor.RED + "Has alcanzado el límite de tu banco.");
                return;
            }

            int storedLv = (int) (storedXp / 68L);
            p.setLevel(playerLv - storedLv);
            p.setExp(0);

            // Pequeño efecto visual
            Location potCenter = b.getLocation().clone().add(0.5, 0.7, 0.5);
            p.getWorld().spawnParticle(Particle.CLOUD, potCenter, 20, 0.3, 0.2, 0.3, 0.02);
            p.getWorld().playSound(potCenter, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.1f);

            p.sendMessage(ChatColor.GREEN + "Has guardado " + storedLv +
                    " niveles (" + storedXp + " XP).");
            return;
        }


        /* ------------------- 2. Shift + RIGHT = Retirar TODO ------------------- */
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && p.isSneaking()) {
            e.setCancelled(true);

            long storedXp = plugin.getDatabaseManager().getBankedXp(uuid);
            if (storedXp <= 0) {
                p.sendMessage(ChatColor.RED + "No tienes experiencia almacenada.");
                return;
            }
            if (!plugin.getDatabaseManager().withdrawBankedXp(uuid, storedXp)) {
                p.sendMessage(ChatColor.RED + "Error al retirar experiencia. Intenta de nuevo.");
                return;
            }

            long levels = storedXp / 68L;
            int  leftover = (int) (storedXp % 68L);

            if (levels > 0) p.giveExpLevels((int) levels);
            if (leftover > 0) p.giveExp(leftover);

            // Efecto visual inverso
            Location potCenter = b.getLocation().clone().add(0.5, 0.7, 0.5);
            Location playerLoc = p.getLocation().add(0, 1.0, 0);
            Vector dir = playerLoc.toVector().subtract(potCenter.toVector());
            for (int i = 1; i <= 6; i++) {
                potCenter.getWorld().spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        potCenter.clone().add(dir.clone().multiply(i / 6.0)),
                        1
                );
            }
            p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, playerLoc, 10, 0.2, 0.1, 0.2, 0.01);
            p.getWorld().playSound(playerLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);

            p.sendMessage(ChatColor.GREEN + "Has retirado " + storedXp + " puntos de XP (" + levels + " niveles).");
            return;
        }

        /* ------------------- 3. RIGHT normal = abrir GUI ------------------- */
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            ExperiencePotMenu.open(p, b);
        }
    }
}