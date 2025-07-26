package gc.grivyzom.survivalcore.flowerpot;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager que controla todas las macetas mágicas colocadas en el mundo
 * Maneja efectos, partículas y persistencia de datos
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MagicFlowerPotManager {

    private final JavaPlugin plugin;
    private final MagicFlowerPot potFactory;

    // Mapa de macetas activas: Location -> MagicFlowerPotData
    private final Map<Location, MagicFlowerPotData> activePots = new ConcurrentHashMap<>();

    // Tareas de efectos activas
    private BukkitTask effectTask;
    private BukkitTask particleTask;

    public MagicFlowerPotManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.potFactory = new MagicFlowerPot(plugin);
        startEffectTasks();
    }

    /**
     * Registra una maceta mágica colocada en el mundo
     */
    public void registerPot(Location location, String potId, int level, String flowerId) {
        MagicFlowerPotData data = new MagicFlowerPotData(
                potId,
                level,
                flowerId,
                System.currentTimeMillis(),
                location.clone()
        );

        activePots.put(location.clone(), data);

        plugin.getLogger().info("Maceta mágica registrada en " +
                location.getWorld().getName() + " (" +
                location.getBlockX() + ", " +
                location.getBlockY() + ", " +
                location.getBlockZ() + ") - ID: " + potId);
    }

    /**
     * Desregistra una maceta mágica
     */
    public void unregisterPot(Location location) {
        MagicFlowerPotData removed = activePots.remove(location);
        if (removed != null) {
            plugin.getLogger().info("Maceta mágica removida - ID: " + removed.getPotId());
        }
    }

    /**
     * Verifica si hay una maceta mágica en la ubicación
     */
    public boolean hasPotAt(Location location) {
        return activePots.containsKey(location);
    }

    /**
     * Obtiene los datos de una maceta en la ubicación específica
     */
    public MagicFlowerPotData getPotData(Location location) {
        return activePots.get(location);
    }

    /**
     * Actualiza la flor en una maceta existente
     */
    public boolean updateFlowerInPot(Location location, String newFlowerId) {
        MagicFlowerPotData data = activePots.get(location);
        if (data == null) return false;

        data.setFlowerId(newFlowerId);
        data.setLastUpdate(System.currentTimeMillis());

        // Reproducir efectos de actualización
        playFlowerPlacementEffects(location, newFlowerId);

        return true;
    }

    /**
     * Obtiene todas las macetas activas
     */
    public Collection<MagicFlowerPotData> getAllActivePots() {
        return new ArrayList<>(activePots.values());
    }

    /**
     * Obtiene el número de macetas activas
     */
    public int getActivePotCount() {
        return activePots.size();
    }

    /**
     * Inicia las tareas de efectos y partículas
     */
    private void startEffectTasks() {
        // Tarea de efectos de área (cada 60 ticks = 3 segundos)
        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                applyFlowerEffects();
            }
        }.runTaskTimer(plugin, 20L, 60L);

        // Tarea de partículas (cada 20 ticks = 1 segundo)
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnParticles();
            }
        }.runTaskTimer(plugin, 10L, 20L);
    }

    /**
     * Aplica efectos de las flores a los jugadores en el área
     */
    private void applyFlowerEffects() {
        for (Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location potLocation = entry.getKey();
            MagicFlowerPotData data = entry.getValue();

            // Solo aplicar efectos si tiene flor
            if (data.getFlowerId().equals("none")) continue;

            // Verificar que el bloque siga siendo una maceta
            Block block = potLocation.getBlock();
            if (block.getType() != Material.FLOWER_POT) {
                // La maceta fue destruida, removerla
                activePots.remove(potLocation);
                continue;
            }

            // Aplicar efectos según la flor
            applyFlowerEffect(potLocation, data);
        }
    }

    /**
     * Aplica el efecto específico de una flor
     */
    private void applyFlowerEffect(Location potLocation, MagicFlowerPotData data) {
        String flowerId = data.getFlowerId();
        int level = data.getLevel();
        int range = calculateRange(level);

        // CORRECCIÓN: Usar getNearbyEntities en lugar de getNearbyPlayers
        Collection<Player> nearbyPlayers = new ArrayList<>();

        // Obtener todas las entidades en el área y filtrar solo jugadores
        Collection<org.bukkit.entity.Entity> entities = potLocation.getWorld().getNearbyEntities(
                potLocation, range, range, range
        );

        for (org.bukkit.entity.Entity entity : entities) {
            if (entity instanceof Player) {
                nearbyPlayers.add((Player) entity);
            }
        }

        for (Player player : nearbyPlayers) {
            // Verificar línea de vista (opcional, configurable)
            if (!hasLineOfSight(potLocation, player.getLocation())) {
                continue;
            }

            // Aplicar efecto según el tipo de flor
            PotionEffect effect = getFlowerEffect(flowerId, level);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
    }

    /**
     * Obtiene el efecto de poción para una flor específica
     */
    private PotionEffect getFlowerEffect(String flowerId, int potLevel) {
        // Duración base: 4 segundos (80 ticks)
        int duration = 80;

        switch (flowerId.toLowerCase()) {
            case "love_flower":
                // Regeneración (nivel basado en nivel de maceta)
                return new PotionEffect(PotionEffectType.ABSORPTION, duration, potLevel - 1);

            case "healing_flower":
                // Curación instantánea
                return new PotionEffect(PotionEffectType.REGENERATION, 1, 0);

            case "speed_flower":
                // Velocidad
                return new PotionEffect(PotionEffectType.SPEED, duration, potLevel - 1);

            case "strength_flower":
                // Fuerza
                return new PotionEffect(PotionEffectType.INCREASE_DAMAGE, duration, potLevel - 1);

            case "night_vision_flower":
                // Visión nocturna (duración extendida)
                return new PotionEffect(PotionEffectType.NIGHT_VISION, duration * 3, 0);

            default:
                return null;
        }
    }

    /**
     * Genera partículas para las macetas
     */
    private void spawnParticles() {
        for (Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location potLocation = entry.getKey();
            MagicFlowerPotData data = entry.getValue();

            // Verificar que el chunk esté cargado
            if (!potLocation.getChunk().isLoaded()) continue;

            // Partículas diferentes según si tiene flor o no
            if (data.getFlowerId().equals("none")) {
                spawnIdleParticles(potLocation);
            } else {
                spawnActiveParticles(potLocation, data);
            }
        }
    }

    /**
     * Partículas cuando la maceta está vacía
     */
    private void spawnIdleParticles(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location particleLocation = location.clone().add(0.5, 0.8, 0.5);

        // Partículas sutiles indicando que está esperando una flor
        world.spawnParticle(
                Particle.VILLAGER_HAPPY,
                particleLocation,
                2,
                0.2, 0.1, 0.2,
                0.02
        );
    }

    /**
     * Partículas cuando la maceta tiene una flor activa
     */
    private void spawnActiveParticles(Location location, MagicFlowerPotData data) {
        World world = location.getWorld();
        if (world == null) return;

        Location particleLocation = location.clone().add(0.5, 0.8, 0.5);
        String flowerId = data.getFlowerId();
        int level = data.getLevel();

        // Partículas específicas por tipo de flor
        Particle particleType = getFlowerParticle(flowerId);
        int particleCount = 3 + level; // Más partículas para niveles superiores

        world.spawnParticle(
                particleType,
                particleLocation,
                particleCount,
                0.3, 0.2, 0.3,
                0.05
        );

        // Partículas adicionales en área de efecto (menos frecuentes)
        if (Math.random() < 0.3) { // 30% de probabilidad
            spawnAreaEffectParticles(location, data);
        }
    }

    /**
     * Obtiene el tipo de partícula para una flor
     */
    private Particle getFlowerParticle(String flowerId) {
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return Particle.HEART;
            case "healing_flower":
                return Particle.VILLAGER_HAPPY;
            case "speed_flower":
                return Particle.CRIT;
            case "strength_flower":
                return Particle.CRIT_MAGIC;
            case "night_vision_flower":
                return Particle.ENCHANTMENT_TABLE;
            default:
                return Particle.VILLAGER_HAPPY;
        }
    }

    /**
     * Partículas de efecto en el área
     */
    private void spawnAreaEffectParticles(Location center, MagicFlowerPotData data) {
        World world = center.getWorld();
        if (world == null) return;

        int range = calculateRange(data.getLevel());
        Particle particle = getFlowerParticle(data.getFlowerId());

        // Crear un anillo de partículas en el suelo
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * 2 * Math.PI;
            double x = center.getX() + 0.5 + Math.cos(angle) * range;
            double z = center.getZ() + 0.5 + Math.sin(angle) * range;
            double y = center.getY() + 0.1;

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Reproduce efectos cuando se coloca una flor
     */
    private void playFlowerPlacementEffects(Location location, String flowerId) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonido de colocación
        world.playSound(effectLocation, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);

        // Partículas de confirmación
        Particle particle = getFlowerParticle(flowerId);
        world.spawnParticle(particle, effectLocation, 20, 0.4, 0.3, 0.4, 0.1);

        // Sonido específico por flor
        Sound flowerSound = getFlowerSound(flowerId);
        if (flowerSound != null) {
            world.playSound(effectLocation, flowerSound, 0.8f, 1.0f);
        }
    }

    /**
     * Obtiene el sonido específico para una flor
     */
    private Sound getFlowerSound(String flowerId) {
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "healing_flower":
                return Sound.ENTITY_PLAYER_LEVELUP;
            case "speed_flower":
                return Sound.ENTITY_HORSE_GALLOP;
            case "strength_flower":
                return Sound.ENTITY_IRON_GOLEM_ATTACK;
            case "night_vision_flower":
                return Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            default:
                return Sound.BLOCK_GRASS_PLACE;
        }
    }

    /**
     * Calcula el rango de efectos según el nivel
     */
    private int calculateRange(int level) {
        return 3 + (level - 1) * 2;
    }

    /**
     * Verifica línea de vista básica (puede ser optimizada)
     */
    private boolean hasLineOfSight(Location from, Location to) {
        // Verificación simple - puede expandirse para ser más sofisticada
        return from.getWorld().equals(to.getWorld()) &&
                from.distance(to) <= calculateRange(5); // Rango máximo
    }

    /**
     * Limpia las tareas al desactivar el plugin
     */
    public void shutdown() {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }

        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
        }

        activePots.clear();

        plugin.getLogger().info("MagicFlowerPotManager desactivado correctamente.");
    }

    /**
     * Obtiene estadísticas del manager
     */
    public String getStatistics() {
        long activeFlowers = activePots.values().stream()
                .filter(data -> !data.getFlowerId().equals("none"))
                .count();

        return String.format("Macetas activas: %d | Con flores: %d | Vacías: %d",
                activePots.size(),
                activeFlowers,
                activePots.size() - activeFlowers
        );
    }

    /**
     * Fuerza la actualización de efectos (útil para testing)
     */
    public void forceUpdate() {
        applyFlowerEffects();
        spawnParticles();
    }
}