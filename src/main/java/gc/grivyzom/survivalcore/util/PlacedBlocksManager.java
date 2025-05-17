package gc.grivyzom.survivalcore.util;

import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manager para rastrear bloques colocados por jugadores.
 * Permite distinguir entre bloques naturales y bloques colocados,
 * evitando que el sistema de experiencia otorgue XP por minería de
 * bloques colocados.
 */
public class PlacedBlocksManager implements Listener {
    private final Set<Location> placedBlocks = Collections.synchronizedSet(new HashSet<>());

    /**
     * Registra este listener en el servidor.
     * @param plugin instancia de JavaPlugin donde se registra
     */
    public PlacedBlocksManager(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Evento que se dispara cuando un jugador coloca un bloque.
     * Añade la ubicación del bloque al conjunto.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocks.add(event.getBlock().getLocation());
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