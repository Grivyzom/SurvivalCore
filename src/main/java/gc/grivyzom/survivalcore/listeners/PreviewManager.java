package gc.grivyzom.survivalcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class PreviewManager {
    private final Map<Location, Display> previews = new HashMap<>();
    private final JavaPlugin plugin;
    public PreviewManager(JavaPlugin plugin) { this.plugin = plugin; }

    /** Muestra o actualiza un holograma con el posible resultado. */
    public void showPreview(Location lecternLoc, ItemStack result) {
        removePreview(lecternLoc);                      // evita duplicados
        World w = lecternLoc.getWorld();
        // Usa ItemDisplay (≥ 1.20) o ArmorStand si vas en 1.19: cambia aquí.
        ItemDisplay disp = (ItemDisplay) w.spawn(lecternLoc.clone()
                .add(0.5, 1.85, 0.5), ItemDisplay.class);
        disp.setItemStack(result);
        disp.setBillboard(Display.Billboard.CENTER);
        disp.setGlowing(true);
        previews.put(lecternLoc, disp);

        // Desvanece tras 6 s como el timeout:
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> removePreview(lecternLoc), 20L*6);
    }

    public void removePreview(Location lecternLoc) {
        Display d = previews.remove(lecternLoc);
        if (d != null && !d.isDead()) d.remove();
    }
}
