package gc.grivyzom.survivalcore.util;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PlacedBlocksManager implements Listener {
    private final JavaPlugin plugin;
    private final Set<Location> placedBlocks = Collections.synchronizedSet(new HashSet<>());
    private final Map<Location, ItemStack> magicLecterns = new HashMap<>();
    private final NamespacedKey lecternKey;

    public PlacedBlocksManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lecternKey = new NamespacedKey(plugin, "is_magic_lectern");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        placedBlocks.add(loc);

        if (isMagicLectern(event.getItemInHand())) {
            magicLecterns.put(loc, event.getItemInHand().clone());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        placedBlocks.remove(loc);

        if (magicLecterns.containsKey(loc)) {
            handleMagicLecternBreak(event, loc);
        }
    }

    private void handleMagicLecternBreak(BlockBreakEvent event, Location loc) {
        ItemStack lecternItem = magicLecterns.remove(loc);
        Player player = event.getPlayer();

        if (lecternItem == null) {
            return;
        }

        event.setCancelled(true);
        event.getBlock().setType(Material.AIR);

        // Clonar el ítem para evitar modificar el original
        ItemStack itemToGive = lecternItem.clone();

        // Verificar si el ítem tiene metadatos y asegurarse de que se conserven
        ItemMeta meta = itemToGive.getItemMeta();
        if (meta != null) {
            itemToGive.setItemMeta(meta);
        }

        // Dar el ítem al jugador
        if (player.getInventory().addItem(itemToGive).isEmpty()) {
            player.updateInventory();
        } else {
            player.getWorld().dropItemNaturally(loc, itemToGive);
        }

        // Efectos
        player.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.BLOCK_CRACK, loc, 10,
                Material.LECTERN.createBlockData());
    }

    private boolean isMagicLectern(ItemStack item) {
        if (item == null || item.getType() != Material.LECTERN) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        // Verifica si el ítem tiene la clave persistente "is_magic_lectern"
        return meta.getPersistentDataContainer().has(lecternKey, PersistentDataType.BYTE);
    }

    /**
     * Verifica si la ubicación corresponde a un bloque colocado previamente.
     * @param loc ubicación del bloque
     * @return true si el bloque fue colocado por un jugador
     */
    public boolean isBlockPlaced(Location loc) {
        return placedBlocks.contains(loc);
    }

    /**
     * Elimina la marca de bloque colocado, por ejemplo al romperlo.
     * @param loc ubicación del bloque
     */
    public void removeBlock(Location loc) {
        placedBlocks.remove(loc);
    }

    /**
     * Añade manualmente un bloque a la lista de bloques colocados.
     * @param loc ubicación del bloque
     */
    public void addBlock(Location loc) {
        placedBlocks.add(loc);
    }
}