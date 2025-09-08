package gc.grivyzom.survivalcore.effects;

import gc.grivyzom.survivalcore.recipes.RecipeCategory;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de Efectos Visuales Avanzado para Lectern Magic
 * Inspirado en los efectos de Minecraft (Beacon, Enchanting Table, etc.)
 *
 * @author Mojang Team (Brocolitx)
 * @version 1.0
 */
public class LecternEffectsManager {

    private final JavaPlugin plugin;
    private final Map<Location, EffectTask> activeEffects = new ConcurrentHashMap<>();
    private final Map<Location, BukkitTask> previewTasks = new ConcurrentHashMap<>();

    // === CONFIGURACIÓN DE EFECTOS POR NIVEL ===
    private static final LecternEffectConfig[] LEVEL_EFFECTS = {
            // Nivel 1: Efectos básicos (estilo mesa de encantamientos)
            new LecternEffectConfig(
                    Particle.ENCHANTMENT_TABLE,
                    Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                    new Color(85, 255, 85), // Verde claro
                    1.0f, 1.0f
            ),

            // Nivel 2: Efectos mejorados
            new LecternEffectConfig(
                    Particle.CRIT_MAGIC,
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    new Color(85, 170, 255), // Azul
                    1.2f, 1.1f
            ),

            // Nivel 3: Efectos mágicos
            new LecternEffectConfig(
                    Particle.SPELL_WITCH,
                    Sound.ENTITY_ILLUSIONER_CAST_SPELL,
                    new Color(170, 85, 255), // Púrpura
                    1.4f, 1.2f
            ),

            // Nivel 4: Efectos avanzados
            new LecternEffectConfig(
                    Particle.END_ROD,
                    Sound.BLOCK_BEACON_AMBIENT,
                    new Color(255, 255, 85), // Amarillo dorado
                    1.6f, 1.3f
            ),

            // Nivel 5+: Efectos épicos (estilo beacon)
            new LecternEffectConfig(
                    Particle.SOUL_FIRE_FLAME,
                    Sound.UI_TOAST_CHALLENGE_COMPLETE,
                    new Color(255, 85, 85), // Rojo épico
                    2.0f, 1.5f
            )
    };

    public LecternEffectsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia efectos ambientales para un lectern activo
     */
    public void startAmbientEffects(Location lecternLoc, int level) {
        stopEffects(lecternLoc); // Limpiar efectos previos

        LecternEffectConfig config = getEffectConfig(level);

        EffectTask task = new EffectTask(lecternLoc, config, level);
        task.taskId = Bukkit.getScheduler().runTaskTimer(plugin, task, 0L, 10L).getTaskId();

        activeEffects.put(lecternLoc, task);
    }

    /**
     * Efectos cuando se coloca un item (input)
     */
    public void playInputEffects(Location lecternLoc, int level) {
        LecternEffectConfig config = getEffectConfig(level);
        World world = lecternLoc.getWorld();

        if (world == null) return;

        // Partículas de input
        world.spawnParticle(config.particle,
                lecternLoc.clone().add(0.5, 1.2, 0.5),
                8, 0.3, 0.2, 0.3, 0.05);

        // Sonido de input
        world.playSound(lecternLoc, Sound.ENTITY_ITEM_PICKUP,
                config.volume * 0.7f, config.pitch + 0.2f);
    }

    /**
     * Muestra efectos de fusión exitosa
     */
    public void playSuccessEffects(Location lecternLoc, int level, RecipeCategory category) {
        LecternEffectConfig config = getEffectConfig(level);
        World world = lecternLoc.getWorld();

        if (world == null) return;

        // Efectos de partículas en espiral ascendente
        createSpiralEffect(world, lecternLoc, config, category);

        // Sonido de éxito con tono según nivel
        world.playSound(lecternLoc, config.successSound,
                config.volume, config.pitch + (level * 0.1f));

        // Efecto de rayo de luz (estilo beacon)
        if (level >= 4) {
            createBeaconBeam(world, lecternLoc, config.color, level);
        }

        // Ondas de energía
        createEnergyWaves(world, lecternLoc, level);
    }

    /**
     * Efectos para fusión fallida
     */
    public void playFailureEffects(Location lecternLoc, int level) {
        World world = lecternLoc.getWorld();

        if (world == null) return;

        // Humo y cenizas
        world.spawnParticle(Particle.SMOKE_LARGE,
                lecternLoc.clone().add(0.5, 1.0, 0.5),
                15, 0.3, 0.4, 0.3, 0.02);

        world.spawnParticle(Particle.ASH,
                lecternLoc.clone().add(0.5, 1.2, 0.5),
                8, 0.2, 0.3, 0.2, 0.01);

        // Sonido de fallo
        world.playSound(lecternLoc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.7f);

        // Pequeñas chispas rojas
        world.spawnParticle(Particle.REDSTONE,
                lecternLoc.clone().add(0.5, 1.0, 0.5),
                10, 0.4, 0.3, 0.4, 0.0,
                new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
    }

    /**
     * Detiene todos los efectos de un lectern
     */
    public void stopEffects(Location lecternLoc) {
        EffectTask task = activeEffects.remove(lecternLoc);
        if (task != null && task.taskId != -1) {
            Bukkit.getScheduler().cancelTask(task.taskId);
        }

        // También detener preview si existe
        removePreview(lecternLoc);
    }

    /**
     * Muestra una preview del item resultado flotando sobre el lectern
     */
    public void showPreview(Location lecternLoc, org.bukkit.inventory.ItemStack result) {
        removePreview(lecternLoc); // Limpiar preview anterior

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 100) { // 5 segundos
                    cancel();
                    previewTasks.remove(lecternLoc);
                    return;
                }

                World world = lecternLoc.getWorld();
                if (world == null) return;

                // Altura flotante con movimiento suave
                double height = 2.0 + Math.sin(ticks * 0.1) * 0.1;
                Location previewLoc = lecternLoc.clone().add(0.5, height, 0.5);

                // Rotación del item
                double angle = (ticks * 0.1) % (Math.PI * 2);

                // Partículas alrededor del item
                for (int i = 0; i < 8; i++) {
                    double particleAngle = angle + (i * Math.PI / 4);
                    double x = previewLoc.getX() + Math.cos(particleAngle) * 0.3;
                    double z = previewLoc.getZ() + Math.sin(particleAngle) * 0.3;

                    world.spawnParticle(Particle.ENCHANTMENT_TABLE,
                            new Location(world, x, previewLoc.getY(), z),
                            1, 0, 0, 0, 0);
                }

                // Efecto del item (simulado con partículas)
                Material material = result.getType();
                Particle itemParticle = getItemParticle(material);
                if (itemParticle != null) {
                    world.spawnParticle(itemParticle, previewLoc, 1, 0.1, 0.1, 0.1, 0);
                }

                // Sonido suave cada 2 segundos
                if (ticks % 40 == 0) {
                    world.playSound(previewLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3f, 1.5f);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        previewTasks.put(lecternLoc, task);
    }

    public void removePreview(Location lecternLoc) {
        BukkitTask task = previewTasks.remove(lecternLoc);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Limpia todos los efectos activos
     */
    public void cleanup() {
        activeEffects.values().forEach(task -> {
            if (task.taskId != -1) {
                Bukkit.getScheduler().cancelTask(task.taskId);
            }
        });
        activeEffects.clear();

        previewTasks.values().forEach(BukkitTask::cancel);
        previewTasks.clear();
    }

    // === MÉTODOS PRIVADOS PARA EFECTOS ESPECÍFICOS ===

    private void createSpiralEffect(World world, Location center, LecternEffectConfig config, RecipeCategory category) {
        // Efecto en espiral ascendente con partículas temáticas
        new BukkitRunnable() {
            double angle = 0;
            double height = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 60) { // 3 segundos
                    cancel();
                    return;
                }

                // Posición en espiral
                double x = center.getX() + 0.5 + Math.cos(angle) * (0.8 - height/3);
                double y = center.getY() + 1.0 + height;
                double z = center.getZ() + 0.5 + Math.sin(angle) * (0.8 - height/3);

                Location particleLoc = new Location(world, x, y, z);

                // Partícula principal
                world.spawnParticle(config.particle, particleLoc, 1, 0, 0, 0, 0);

                // Partículas temáticas según categoría
                Particle categoryParticle = getCategoryParticle(category);
                if (categoryParticle != null) {
                    world.spawnParticle(categoryParticle, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                }

                angle += 0.3;
                height += 0.03;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createBeaconBeam(World world, Location center, Color color, int level) {
        // Rayo de luz vertical (simulado con partículas)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 40) { // 2 segundos
                    cancel();
                    return;
                }

                for (int y = 0; y < 15; y++) {
                    Location beamLoc = center.clone().add(0.5, 1.0 + y, 0.5);

                    // Partículas de colores según el nivel
                    org.bukkit.Color bukkitColor = org.bukkit.Color.fromRGB(
                            color.getRed(), color.getGreen(), color.getBlue());

                    world.spawnParticle(Particle.REDSTONE, beamLoc, 1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(bukkitColor, 1.5f));

                    // Efectos adicionales para niveles altos
                    if (level >= 7) {
                        world.spawnParticle(Particle.END_ROD, beamLoc, 1, 0.05, 0.05, 0.05, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }

    private void createEnergyWaves(World world, Location center, int level) {
        // Ondas expansivas de energía
        new BukkitRunnable() {
            double radius = 0;
            int waves = 0;

            @Override
            public void run() {
                if (waves++ > 3) {
                    cancel();
                    return;
                }

                // Círculo de partículas expandiéndose
                for (double angle = 0; angle < Math.PI * 2; angle += 0.2) {
                    double x = center.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
                    Location waveLoc = new Location(world, x, center.getY() + 0.1, z);

                    world.spawnParticle(Particle.CRIT_MAGIC, waveLoc, 1, 0, 0, 0, 0);
                }

                radius += 0.8;

                // Programar siguiente onda
                Bukkit.getScheduler().runTaskLater(plugin, this, 8L);
            }
        }.runTaskLater(plugin, 10L);
    }

    private Particle getCategoryParticle(RecipeCategory category) {
        return switch (category) {
            case BASIC -> Particle.VILLAGER_HAPPY;
            case TOOLS -> Particle.CRIT;
            case COMBAT -> Particle.DAMAGE_INDICATOR;
            case MAGIC -> Particle.ENCHANTMENT_TABLE;
            case BUILDING -> Particle.BLOCK_CRACK;
            case FOOD -> Particle.ITEM_CRACK;
            case UTILITY -> Particle.REDSTONE;
            case LEGENDARY -> Particle.DRAGON_BREATH;
        };
    }

    private Particle getItemParticle(Material material) {
        // Mapear materiales a partículas representativas
        return switch (material.toString()) {
            case "DIAMOND", "DIAMOND_SWORD", "DIAMOND_PICKAXE" -> Particle.CRIT;
            case "GOLD_INGOT", "GOLDEN_APPLE" -> Particle.VILLAGER_HAPPY;
            case "REDSTONE", "REDSTONE_BLOCK" -> Particle.REDSTONE;
            case "EMERALD", "EMERALD_BLOCK" -> Particle.VILLAGER_HAPPY;
            default -> material.isEdible() ? Particle.ITEM_CRACK : Particle.CRIT_MAGIC;
        };
    }

    private LecternEffectConfig getEffectConfig(int level) {
        int index = Math.min(level - 1, LEVEL_EFFECTS.length - 1);
        return LEVEL_EFFECTS[Math.max(0, index)];
    }

    // === CLASES AUXILIARES ===

    private static class LecternEffectConfig {
        final Particle particle;
        final Sound successSound;
        final Color color;
        final float volume;
        final float pitch;

        LecternEffectConfig(Particle particle, Sound successSound, Color color, float volume, float pitch) {
            this.particle = particle;
            this.successSound = successSound;
            this.color = color;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static class EffectTask implements Runnable {
        final Location location;
        final LecternEffectConfig config;
        final int level;
        int taskId = -1;
        int tickCount = 0;

        EffectTask(Location location, LecternEffectConfig config, int level) {
            this.location = location;
            this.config = config;
            this.level = level;
        }

        @Override
        public void run() {
            World world = location.getWorld();
            if (world == null) return;

            // Efectos ambientales según el nivel
            switch (level) {
                case 1 -> createBasicAmbient(world);
                case 2, 3 -> createIntermediateAmbient(world);
                case 4, 5 -> createAdvancedAmbient(world);
                default -> createLegendaryAmbient(world);
            }

            tickCount++;
        }

        private void createBasicAmbient(World world) {
            // Partículas flotantes suaves
            if (tickCount % 20 == 0) { // Cada segundo
                world.spawnParticle(config.particle,
                        location.clone().add(0.5, 1.2, 0.5),
                        3, 0.3, 0.2, 0.3, 0.01);
            }
        }

        private void createIntermediateAmbient(World world) {
            // Partículas orbitales
            if (tickCount % 10 == 0) {
                double angle = (tickCount * 0.1) % (Math.PI * 2);
                double x = location.getX() + 0.5 + Math.cos(angle) * 0.6;
                double z = location.getZ() + 0.5 + Math.sin(angle) * 0.6;

                world.spawnParticle(config.particle,
                        new Location(world, x, location.getY() + 1.3, z),
                        1, 0, 0, 0, 0);
            }
        }

        private void createAdvancedAmbient(World world) {
            // Múltiples orbitas con diferentes velocidades
            if (tickCount % 5 == 0) {
                for (int i = 0; i < 2; i++) {
                    double angle = (tickCount * 0.05 * (i + 1)) % (Math.PI * 2);
                    double radius = 0.4 + (i * 0.3);
                    double x = location.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = location.getZ() + 0.5 + Math.sin(angle) * radius;
                    double y = location.getY() + 1.1 + Math.sin(angle * 2) * 0.2;

                    world.spawnParticle(config.particle,
                            new Location(world, x, y, z),
                            1, 0, 0, 0, 0);
                }
            }
        }

        private void createLegendaryAmbient(World world) {
            // Efectos complejos con múltiples elementos
            if (tickCount % 3 == 0) {
                // Doble hélice
                double mainAngle = (tickCount * 0.08) % (Math.PI * 2);
                for (int helix = 0; helix < 2; helix++) {
                    double angle = mainAngle + (helix * Math.PI);
                    double height = Math.sin(tickCount * 0.05) * 0.3;
                    double x = location.getX() + 0.5 + Math.cos(angle) * 0.5;
                    double z = location.getZ() + 0.5 + Math.sin(angle) * 0.5;
                    double y = location.getY() + 1.2 + height;

                    world.spawnParticle(config.particle,
                            new Location(world, x, y, z), 1, 0, 0, 0, 0);

                    // Partículas adicionales para efecto legendary
                    world.spawnParticle(Particle.END_ROD,
                            new Location(world, x, y, z), 1, 0.05, 0.05, 0.05, 0);
                }
            }

            // Pulso de energía cada 5 segundos
            if (tickCount % 100 == 0) {
                world.spawnParticle(Particle.EXPLOSION_LARGE,
                        location.clone().add(0.5, 1.0, 0.5), 1, 0, 0, 0, 0);
                world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 2.0f);
            }
        }
    }
}