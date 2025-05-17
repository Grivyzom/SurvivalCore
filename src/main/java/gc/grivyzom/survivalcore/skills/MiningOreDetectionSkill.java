package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashSet;
import java.util.Set;

public class MiningOreDetectionSkill extends Skill {
    private final Main plugin;
    private BukkitRunnable scanTask;

    // Configuración para partículas de contorno
    private final DustOptions outlineDust = new DustOptions(Color.FUCHSIA, 1.5f);

    // Efectos para minerales raros (ahora con colores específicos)
    private final DustOptions diamondDust = new DustOptions(Color.BLUE, 2.0f);
    private final DustOptions emeraldDust = new DustOptions(Color.GREEN, 2.0f);
    private final DustOptions ancientDebrisDust = new DustOptions(Color.PURPLE, 2.0f);

    public MiningOreDetectionSkill(Main plugin, int level, long duration) {
        super("Detección de Menas", level, 160L + (level - 1) * 20L); // 8s base +1s por nivel
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Configuración escalable
        int baseRadius = 40; // Radio base de 40 bloques
        int effectiveRadius = baseRadius + (getLevel() - 1) * 3; // +3 bloques por nivel
        int verticalRange = 4 + (getLevel() - 1) / 3; // +1 bloque cada 3 niveles
        int step = Math.max(1, effectiveRadius / 20); // Paso dinámico para optimización

        player.sendMessage(ChatColor.GREEN + "¡Detección de Menas nivel " + getLevel() +
                " activada! Detectando minerales en un radio de " + effectiveRadius +
                " bloques (±" + verticalRange + " vertical) durante " + (getDuration()/20) + " segundos.");

        // Tarea de escaneo optimizada
        scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                World world = center.getWorld();
                if (world == null) return;

                Set<String> processedBlocks = new HashSet<>();

                for (int x = -effectiveRadius; x <= effectiveRadius; x += step) {
                    for (int z = -effectiveRadius; z <= effectiveRadius; z += step) {
                        for (int y = -verticalRange; y <= verticalRange; y++) {
                            Location blockLoc = center.clone().add(x, y, z);
                            if (center.distance(blockLoc) > effectiveRadius) continue;

                            String key = blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ();
                            if (processedBlocks.contains(key)) continue;

                            processedBlocks.add(key);
                            Material blockType = blockLoc.getBlock().getType();

                            if (blockType.name().contains("ORE")) {
                                spawnBlockOutline(world, blockLoc);
                                if (isRareMineral(blockType)) {
                                    spawnRareEffect(world, blockLoc.clone().add(0.5, 0.5, 0.5), blockType);
                                }
                            }
                        }
                    }
                }
            }
        };
        scanTask.runTaskTimer(plugin, 0L, 10L);

        // Desactivación automática
        new BukkitRunnable() {
            @Override
            public void run() {
                if (scanTask != null) {
                    scanTask.cancel();
                }
                plugin.getSkillManager().deactivateSkill(player);
            }
        }.runTaskLater(plugin, getDuration());
    }

    private void spawnBlockOutline(World world, Location blockLoc) {
        int bx = blockLoc.getBlockX();
        int by = blockLoc.getBlockY();
        int bz = blockLoc.getBlockZ();
        Location[] vertices = {
                new Location(world, bx, by, bz),
                new Location(world, bx + 1, by, bz),
                new Location(world, bx, by + 1, bz),
                new Location(world, bx, by, bz + 1),
                new Location(world, bx + 1, by + 1, bz),
                new Location(world, bx + 1, by, bz + 1),
                new Location(world, bx, by + 1, bz + 1),
                new Location(world, bx + 1, by + 1, bz + 1)
        };

        // Vértices
        for (Location vertex : vertices) {
            world.spawnParticle(Particle.REDSTONE, vertex, 1, 0, 0, 0, 0, outlineDust, true);
        }

        // Aristas (con menos partículas)
        int[][] edges = {{0,1}, {0,2}, {0,3}, {1,4}, {1,5}, {2,4}, {2,6}, {3,5}, {3,6}, {4,7}, {5,7}, {6,7}};
        for (int[] edge : edges) {
            spawnLineBetween(world, vertices[edge[0]], vertices[edge[1]], outlineDust);
        }
    }

    private void spawnLineBetween(World world, Location start, Location end, DustOptions dust) {
        double distance = start.distance(end);
        double step = 1.0; // Menos partículas que antes (0.5)
        int count = (int) Math.ceil(distance / step);
        for (int i = 0; i <= count; i++) {
            double ratio = i / (double) count;
            Location loc = interpolate(start, end, ratio);
            world.spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust, true);
        }
    }

    private Location interpolate(Location start, Location end, double ratio) {
        return new Location(
                start.getWorld(),
                start.getX() + (end.getX() - start.getX()) * ratio,
                start.getY() + (end.getY() - start.getY()) * ratio,
                start.getZ() + (end.getZ() - start.getZ()) * ratio
        );
    }

    private boolean isRareMineral(Material material) {
        String name = material.name();
        return name.equals("DIAMOND_ORE") || name.equals("EMERALD_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private DustOptions getRareDust(Material material) {
        return switch (material.name()) {
            case "DIAMOND_ORE" -> diamondDust;
            case "EMERALD_ORE" -> emeraldDust;
            case "ANCIENT_DEBRIS" -> ancientDebrisDust;
            default -> outlineDust;
        };
    }

    private void spawnRareEffect(World world, Location center, Material mineral) {
        new BukkitRunnable() {
            double angle = 0;
            int iterations = 0;
            final DustOptions dust = getRareDust(mineral);

            @Override
            public void run() {
                if (iterations > 20) {
                    cancel();
                    return;
                }
                double radius = 0.5 + 0.05 * iterations;
                double xOffset = Math.cos(angle) * radius;
                double zOffset = Math.sin(angle) * radius;
                Location loc = center.clone().add(xOffset, 0, zOffset);
                world.spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust, true);
                angle += Math.PI / 8;
                iterations++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void onDeactivate(Player player) {
        if (scanTask != null) {
            scanTask.cancel();
        }
        player.sendMessage(ChatColor.RED + "Detección de Menas ha finalizado.");
    }

    @Override
    public long getEffectiveCooldown() {
        long baseCooldown = 60000; // 60s base
        long reduction = (getLevel() - 1) * 1500; // -1.5s por nivel
        return Math.max(baseCooldown - reduction, 30000); // Mínimo 30s
    }
}