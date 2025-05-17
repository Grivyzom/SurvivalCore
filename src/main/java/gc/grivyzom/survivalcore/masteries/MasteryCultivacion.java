package gc.grivyzom.survivalcore.masteries;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class MasteryCultivacion extends Mastery {

    private static final List<String> DESCRIPTION = Arrays.asList(
            ChatColor.GRAY + "Acelera el crecimiento de cultivos cercanos.",
            ChatColor.GRAY + "Efecto: " + ChatColor.AQUA + "Crecimiento acelerado",
            ChatColor.GRAY + "Rango: 16 bloques",
            ChatColor.GRAY + "Aceleración: 10%",
            ChatColor.GRAY + "Partículas: indican cultivos afectados"
    );

    private final Main plugin;

    public MasteryCultivacion(Main plugin, int level) {
        super("cultivacion", "Cultivación", level, DESCRIPTION);
        this.plugin = plugin;
    }

    @Override
    public String getProfession() {
        return "farming";
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getXPRequiredForLevel(int level) {
        switch (level) {
            case 1: return 15;
            case 2: return 20;
            case 3: return 25;
            case 4: return 30;
            case 5: return 35;
            default: return 0;
        }
    }

    @Override
    public void applyEffect(final Player player) {
        // Se establece un rango fijo de 16 bloques y aceleración constante del 10%
        final int range = 16;
        final double acceleration = 0.10;
        final int durationTicks = 200;
        final Color particleColor = getParticleColorForLevel(level);

        // Efectos de sonido y partículas al activar la maestría
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 30,
                new Particle.DustOptions(particleColor, 2.0f));

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= durationTicks) {
                    // Efecto al finalizar
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
                    cancel();
                    return;
                }

                World world = player.getWorld();
                int centerX = player.getLocation().getBlockX();
                int centerY = player.getLocation().getBlockY();
                int centerZ = player.getLocation().getBlockZ();

                for (int x = centerX - range; x <= centerX + range; x++) {
                    for (int z = centerZ - range; z <= centerZ + range; z++) {
                        for (int y = centerY - 2; y <= centerY + 1; y++) { // Se busca en un rango vertical amplio
                            Block block = world.getBlockAt(x, y, z);

                            if (block.getBlockData() instanceof Ageable) {
                                Ageable crop = (Ageable) block.getBlockData();
                                if (crop.getAge() < crop.getMaximumAge()) {
                                    if (Math.random() < acceleration) {
                                        crop.setAge(crop.getAge() + 1);
                                        block.setBlockData(crop);

                                        // Efectos visuales para indicar el cultivo afectado
                                        world.spawnParticle(Particle.VILLAGER_HAPPY,
                                                block.getLocation().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0.1);

                                        if (level >= 3) { // Efecto adicional para niveles altos
                                            world.spawnParticle(Particle.REDSTONE,
                                                    block.getLocation().add(0.5, 0.5, 0.5), 3,
                                                    new Particle.DustOptions(particleColor, 1.0f));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                elapsed += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        player.sendMessage(ChatColor.GREEN + "¡Cultivación activada! Los cultivos cercanos crecerán más rápido.");
    }

    private Color getParticleColorForLevel(int level) {
        switch (level) {
            case 1: return Color.LIME;
            case 2: return Color.GREEN;
            case 3: return Color.TEAL;
            case 4: return Color.AQUA;
            case 5: return Color.PURPLE;
            default: return Color.LIME;
        }
    }
}
