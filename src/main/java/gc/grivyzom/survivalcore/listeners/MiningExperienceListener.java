package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.config.MiningExperienceConfig;
import gc.grivyzom.survivalcore.skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MiningExperienceListener implements Listener {
    private final Main plugin;
    private final MiningExperienceConfig miningConfig;
    private final double bonusPerLevel = 0.05;
    private final Random random = new Random();

    // Buffers para batch de XP y tareas de flush
    private final Map<UUID, Integer> xpBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> xpFlushTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastBreakLocation = new ConcurrentHashMap<>();

    public MiningExperienceListener(Main plugin, MiningExperienceConfig miningConfig) {
        this.plugin = plugin;
        this.miningConfig = miningConfig;
    }

    private boolean isMineable(Material material) {
        int[] range = miningConfig.getExperienceRange(material.name());
        return range != null && range.length >= 2 && (range[1] > 0 || range[0] > 0);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (plugin.getPlacedBlocksManager().isBlockPlaced(block.getLocation())) {
            plugin.getPlacedBlocksManager().removeBlock(block.getLocation());
            event.setExpToDrop(0);
            return;
        }
        if (event.isCancelled()) return;

        Material type = block.getType();
        if (!isMineable(type)) {
            return;
        }

        String oreKey = type.name();
        int[] range = miningConfig.getExperienceRange(oreKey);
        int baseXP = range[0] + (range[1] > range[0] ? random.nextInt(range[1] - range[0] + 1) : 0);
        if (baseXP <= 0 || Math.random() > miningConfig.getXpChance(oreKey)) {
            event.setExpToDrop(0);
            return;
        }

        Player player = event.getPlayer();
        SkillManager skills = plugin.getSkillManager();
        int lvl = skills.getSkillLevel(player, "Aceleración Minera");
        int xpAward = (int) Math.round(baseXP * (1 + (lvl - 1) * bonusPerLevel));

        UUID uuid = player.getUniqueId();
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

                    // Asignar XP de una vez
                    skills.addSkillXP(player, "Aceleración Minera", totalXp);

                    // Entregar XP y efectos
                    player.giveExp(totalXp);

                    // Sonido
                    String soundName = miningConfig.getSound(oreKey);
                    if (soundName != null && !soundName.isEmpty()) {
                        try {
                            Sound sound = Sound.valueOf(soundName.toUpperCase());
                            block.getWorld().playSound(loc, sound, 1.0f, 1.0f);
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("Sonido inválido '" + soundName + "' para '" + oreKey + "'.");
                        }
                    }

                    // Holograma acumulado
                    ArmorStand holo = loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
                        stand.setVisible(false);
                        stand.setMarker(true);
                        stand.setGravity(false);
                        stand.setCustomName(ChatColor.YELLOW + "+" + totalXp + " XP");
                        stand.setCustomNameVisible(true);
                    });
                    Bukkit.getScheduler().runTaskLater(plugin, holo::remove, 40L);

                    // Mensaje una sola vez
                    String msg = miningConfig.getMessage(oreKey);
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
