package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.config.CropExperienceConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;
import org.bukkit.ChatColor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CropExperienceListener implements Listener {
    private final Main plugin;
    private final double bonusPerLevel = 0.05;
    private final Random random = new Random();
    private final List<Material> genericCrops = Arrays.asList(
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT
    );

    // Buffers para batch de XP y tareas de flush
    private final Map<UUID, Integer> xpBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> xpFlushTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastBreakLocation = new ConcurrentHashMap<>();

    public CropExperienceListener(Main plugin) {
        this.plugin = plugin;
    }

    private String mapMaterialToCropKey(String materialName) {
        switch (materialName) {
            case "CARROT": return "CARROTS";
            case "CROPS": return "WHEAT";
            default: return materialName;
        }
    }

    private int calculateBaseXP(String cropKey) {
        int[] range = plugin.getCropExperienceConfig().getExperienceRange(cropKey);
        if (range == null || (range[0] == 0 && range[1] == 0)) return 0;
        return range[0] + (range[1] > range[0] ? random.nextInt(range[1] - range[0] + 1) : 0);
    }

    private boolean isFullyGrown(Block block, String cropKey) {
        CropExperienceConfig cfg = plugin.getCropExperienceConfig();
        if (!cfg.mustBeFullyGrown(cropKey)) return true;
        if (block.getBlockData() instanceof Ageable) {
            Ageable ag = (Ageable) block.getBlockData();
            return ag.getAge() >= ag.getMaximumAge();
        }
        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Block block = event.getBlock();
        Material type = block.getType();
        String key = mapMaterialToCropKey(type.name());

        // XP normal
        int baseXP = calculateBaseXP(key);
        if (baseXP <= 0 || Math.random() > plugin.getCropExperienceConfig().getXpChance(key)) {
            event.setExpToDrop(0);
            return;
        }
        if (!isFullyGrown(block, key)) {
            event.setExpToDrop(0);
            return;
        }

        // Y usar directamente:
        int xpAward = baseXP;

        // Acumular XP para batch
        xpBuffer.merge(uuid, xpAward, Integer::sum);
        lastBreakLocation.put(uuid, block.getLocation().add(0.5, 1.2, 0));

        // Programar flush si no existe
        xpFlushTasks.computeIfAbsent(uuid, u -> {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    int totalXp = xpBuffer.remove(u);
                    xpFlushTasks.remove(u);
                    Location loc = lastBreakLocation.remove(u);

                    // Dar XP al jugador y efectos
                    if (plugin.getCropExperienceConfig().dropAsOrbs(key)) {
                        event.setExpToDrop(event.getExpToDrop() + totalXp);
                    } else {
                        player.giveExp(totalXp);
                    }

                    // Sonido
                    String snd = plugin.getCropExperienceConfig().getSound(key);
                    if (snd != null && !snd.isEmpty()) {
                        try {
                            player.getWorld().playSound(loc, Sound.valueOf(snd.toUpperCase()), 1f, 1f);
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("Sonido inválido '" + snd + "' para cultivo " + key);
                        }
                    }

                    // Holograma agregado a total
                    ArmorStand holo = loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
                        stand.setVisible(false);
                        stand.setMarker(true);
                        stand.setGravity(false);
                        stand.setCustomName(ChatColor.YELLOW + "+" + totalXp + " XP");
                        stand.setCustomNameVisible(true);
                    });
                    Bukkit.getScheduler().runTaskLater(plugin, holo::remove, 40L);

                    // Mensaje una sola vez
                    String msg = plugin.getCropExperienceConfig().getMessage(key);
                    if (msg != null && !msg.isEmpty()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%exp%", String.valueOf(totalXp))));
                    }
                }
            };
            task.runTaskLater(plugin, 20L); // flush después de 20 ticks (1s)
            return task;
        });

        // Evitar los orbes por defecto
        event.setExpToDrop(0);
    }
}
