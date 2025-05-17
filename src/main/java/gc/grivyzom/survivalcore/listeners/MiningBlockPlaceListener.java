package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.util.PlacedBlocksManager;
import gc.grivyzom.survivalcore.config.MiningExperienceConfig;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class MiningBlockPlaceListener implements Listener {
    private final Main plugin;
    private final MiningExperienceConfig miningConfig;
    private final PlacedBlocksManager placedBlocksManager;

    public MiningBlockPlaceListener(Main plugin, MiningExperienceConfig config) {
        this.plugin = plugin;
        this.miningConfig = config;
        this.placedBlocksManager = plugin.getPlacedBlocksManager(); // Obtener la instancia
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        // Si el bloque tiene experiencia (según el archivo mining.yml)
        int[] range = miningConfig.getExperienceRange(type.name());
        if (range[0] > 0) {
            placedBlocksManager.addBlock(event.getBlock().getLocation()); // Usar la instancia
        }
    }
}