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
 * MEJORADO v1.1 - Soporte para niveles de flores mágicas
 *
 * @author Brocolitx
 * @version 1.1
 */
public class MagicFlowerPotManager {

    private final JavaPlugin plugin;
    private final MagicFlowerPot potFactory;

    // Mapa de macetas activas: Location -> MagicFlowerPotData
    private final Map<Location, MagicFlowerPotData> activePots = new ConcurrentHashMap<>();

    // Tareas de efectos activas
    private BukkitTask effectTask;
    private BukkitTask particleTask;
    private BukkitTask cleanupTask;

    public MagicFlowerPotManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.potFactory = new MagicFlowerPot(plugin);
        startEffectTasks();
    }

    /**
     * Registra una maceta mágica colocada en el mundo
     */
    public void registerPot(Location location, String potId, int level, String flowerId) {
        registerPot(location, potId, level, flowerId, 1); // Nivel por defecto de flor
    }

    /**
     * 🆕 NUEVO: Registra una maceta mágica con nivel de flor específico
     */
    public void registerPot(Location location, String potId, int level, String flowerId, int flowerLevel) {
        MagicFlowerPotData data = new MagicFlowerPotData(
                potId,
                level,
                flowerId,
                flowerLevel,
                System.currentTimeMillis(),
                location.clone()
        );

        activePots.put(location.clone(), data);

        plugin.getLogger().info("Maceta mágica registrada en " +
                location.getWorld().getName() + " (" +
                location.getBlockX() + ", " +
                location.getBlockY() + ", " +
                location.getBlockZ() + ") - ID: " + potId +
                (!"none".equals(flowerId) ? " con " + flowerId + " Lv." + flowerLevel : ""));
    }

    /**
     * Desregistra una maceta mágica
     */
    public void unregisterPot(Location location) {
        unregisterPotSafe(location);
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
        return updateFlowerInPot(location, newFlowerId, 1); // Nivel por defecto
    }

    /**
     * 🆕 NUEVO: Actualiza la flor en una maceta existente con nivel específico
     */
    public boolean updateFlowerInPot(Location location, String newFlowerId, int flowerLevel) {
        MagicFlowerPotData data = activePots.get(location);
        if (data == null) return false;

        data.updateFlower(newFlowerId, flowerLevel);

        // Reproducir efectos de actualización
        playFlowerPlacementEffects(location, newFlowerId, flowerLevel);

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

        // 🆕 NUEVO: Tarea de limpieza automática (cada 5 minutos)
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performAutomaticCleanup();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // 5 minutos inicial, luego cada 5 minutos
    }

    /**
     * 🆕 NUEVO: Realiza limpieza automática de macetas fantasma
     */
    private void performAutomaticCleanup() {
        int initialCount = activePots.size();
        java.util.List<Location> toRemove = new java.util.ArrayList<>();

        // Verificar cada maceta registrada
        for (java.util.Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location location = entry.getKey();

            // Verificar que el chunk esté cargado
            if (!location.getChunk().isLoaded()) {
                continue; // Saltar chunks no cargados
            }

            Block block = location.getBlock();

            // Si el bloque ya no es una maceta, marcarlo para eliminación
            if (block.getType() != Material.FLOWER_POT) {
                toRemove.add(location);
            }
        }

        // Remover macetas fantasma
        for (Location location : toRemove) {
            MagicFlowerPotData removedData = activePots.remove(location);
            if (removedData != null) {
                plugin.getLogger().info("Limpieza automática: Maceta fantasma removida - ID: " +
                        removedData.getPotId() + " en " + formatLocation(location));
            }
        }

        int cleanedCount = toRemove.size();
        int finalCount = activePots.size();

        if (cleanedCount > 0) {
            plugin.getLogger().info(String.format("Limpieza automática completada: %d macetas fantasma eliminadas (%d -> %d)",
                    cleanedCount, initialCount, finalCount));
        }
    }

    /**
     * 🆕 NUEVO: Verifica la integridad de los datos de macetas
     */
    public int verifyDataIntegrity() {
        int inconsistencies = 0;
        java.util.List<Location> toRemove = new java.util.ArrayList<>();

        for (java.util.Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location location = entry.getKey();
            MagicFlowerPotData data = entry.getValue();

            // Verificar que el chunk esté cargado antes de verificar
            if (!location.getChunk().isLoaded()) {
                continue;
            }

            Block block = location.getBlock();

            // Verificar que realmente hay una maceta en esa ubicación
            if (block.getType() != Material.FLOWER_POT) {
                toRemove.add(location);
                inconsistencies++;
                plugin.getLogger().warning("Inconsistencia detectada: Maceta registrada sin bloque físico en " +
                        formatLocation(location) + " - ID: " + data.getPotId());
            }
        }

        // Limpiar inconsistencias encontradas
        for (Location location : toRemove) {
            activePots.remove(location);
        }

        if (inconsistencies > 0) {
            plugin.getLogger().info("Verificación de integridad completada: " + inconsistencies + " inconsistencias corregidas");
        }

        return inconsistencies;
    }

    /**
     * 🆕 NUEVO: Desregistra una maceta con verificación adicional
     */
    public boolean unregisterPotSafe(Location location) {
        MagicFlowerPotData removed = activePots.remove(location);

        if (removed != null) {
            plugin.getLogger().info("Maceta desregistrada correctamente - ID: " + removed.getPotId() +
                    " en " + formatLocation(location));
            return true;
        } else {
            plugin.getLogger().warning("Intento de desregistrar maceta inexistente en: " + formatLocation(location));
            return false;
        }
    }

    /**
     * 🔧 MÉTODO MEJORADO: Desregistra con logging mejorado
     */


    /**
     * 🆕 NUEVO: Fuerza la limpieza inmediata
     */
    public int forceCleanup() {
        plugin.getLogger().info("Forzando limpieza manual de macetas...");

        int beforeCount = activePots.size();
        performAutomaticCleanup();
        int afterCount = activePots.size();

        int cleaned = beforeCount - afterCount;

        plugin.getLogger().info("Limpieza manual completada: " + cleaned + " macetas limpiadas");
        return cleaned;
    }

    /**
     * 🆕 NUEVO: Obtiene información detallada de una ubicación
     */
    public String getLocationInfo(Location location) {
        StringBuilder info = new StringBuilder();

        info.append("=== INFORMACIÓN DE UBICACIÓN ===\n");
        info.append("Coordenadas: ").append(formatLocation(location)).append("\n");
        info.append("Chunk cargado: ").append(location.getChunk().isLoaded()).append("\n");
        info.append("Tipo de bloque: ").append(location.getBlock().getType()).append("\n");
        info.append("Registrada en manager: ").append(hasPotAt(location)).append("\n");

        if (hasPotAt(location)) {
            MagicFlowerPotData data = getPotData(location);
            if (data != null) {
                info.append("ID de maceta: ").append(data.getPotId()).append("\n");
                info.append("Nivel: ").append(data.getLevel()).append("\n");
                info.append("Tiene flor: ").append(data.hasFlower()).append("\n");
                if (data.hasFlower()) {
                    info.append("Flor: ").append(data.getFlowerId()).append(" (Lv.").append(data.getFlowerLevel()).append(")\n");
                }
            }
        }

        return info.toString();
    }


    /**
     * 🆕 NUEVO: Formatea una ubicación para logging consistente
     */
    private String formatLocation(Location location) {
        return String.format("%s(%d,%d,%d)",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * 🆕 NUEVO: Comando de debug para administradores
     */
    public String getDebugInfo() {
        StringBuilder debug = new StringBuilder();

        debug.append("=== DEBUG INFO - MAGIC FLOWER POT MANAGER ===\n");
        debug.append("Total macetas registradas: ").append(activePots.size()).append("\n");
        debug.append("Tareas activas: ");
        debug.append("Efectos=").append(effectTask != null && !effectTask.isCancelled());
        debug.append(", Partículas=").append(particleTask != null && !particleTask.isCancelled());
        debug.append(", Limpieza=").append(cleanupTask != null && !cleanupTask.isCancelled());
        debug.append("\n");

        // Información por mundo
        java.util.Map<String, Integer> potsByWorld = new java.util.HashMap<>();
        for (Location loc : activePots.keySet()) {
            String worldName = loc.getWorld().getName();
            potsByWorld.merge(worldName, 1, Integer::sum);
        }

        debug.append("Distribución por mundo:\n");
        for (java.util.Map.Entry<String, Integer> entry : potsByWorld.entrySet()) {
            debug.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" macetas\n");
        }

        return debug.toString();
    }

    /**
     * Aplica efectos de las flores a los jugadores en el área
     */
    private void applyFlowerEffects() {
        for (Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location potLocation = entry.getKey();
            MagicFlowerPotData data = entry.getValue();

            // Solo aplicar efectos si tiene flor
            if (!data.hasFlower()) continue;

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
     * 🔧 MEJORADO: Aplica el efecto específico de una flor considerando su nivel
     */
    private void applyFlowerEffect(Location potLocation, MagicFlowerPotData data) {
        String flowerId = data.getFlowerId();
        int potLevel = data.getLevel();
        int flowerLevel = data.getFlowerLevel();
        int range = data.getEffectRange();

        // Obtener todas las entidades en el área y filtrar solo jugadores
        Collection<Player> nearbyPlayers = new ArrayList<>();
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

            // 🆕 MEJORADO: Aplicar efecto considerando el nivel de la flor
            PotionEffect effect = getFlowerEffect(flowerId, potLevel, flowerLevel);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
    }

    /**
     * 🔧 MEJORADO: Obtiene el efecto de poción considerando nivel de maceta Y flor
     */
    private PotionEffect getFlowerEffect(String flowerId, int potLevel, int flowerLevel) {
        // Duración base: 4 segundos (80 ticks), modificada por nivel de maceta
        int duration = 80 + (potLevel - 1) * 20; // +1 segundo por nivel de maceta

        // Amplificador basado en el nivel de la flor (0-based)
        int amplifier = Math.max(0, flowerLevel - 1);

        switch (flowerId.toLowerCase()) {
            case "love_flower":
                // Regeneración - nivel aumenta con la flor
                return new PotionEffect(PotionEffectType.REGENERATION, duration, amplifier);

            case "healing_flower":
                // Curación instantánea - amplificador basado en nivel de flor
                return new PotionEffect(PotionEffectType.ABSORPTION, 4, amplifier);

            case "speed_flower":
                // Velocidad - amplificador basado en nivel de flor
                return new PotionEffect(PotionEffectType.SPEED, duration, amplifier);

            case "strength_flower":
                // Fuerza - amplificador basado en nivel de flor
                return new PotionEffect(PotionEffectType.INCREASE_DAMAGE, duration, amplifier);

            case "night_vision_flower":
                // Visión nocturna - duración extendida basada en nivel de flor
                int nightVisionDuration = duration * (2 + flowerLevel); // Más duración con mejor flor
                return new PotionEffect(PotionEffectType.NIGHT_VISION, nightVisionDuration, 0);

            default:
                return null;
        }
    }

    /**
     * 🔧 MEJORADO: Genera partículas considerando el nivel de la flor
     */
    private void spawnParticles() {
        for (Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location potLocation = entry.getKey();
            MagicFlowerPotData data = entry.getValue();

            // Verificar que el chunk esté cargado
            if (!potLocation.getChunk().isLoaded()) continue;

            // Partículas diferentes según si tiene flor o no
            if (!data.hasFlower()) {
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
     * 🔧 MEJORADO: Partículas cuando la maceta tiene una flor activa (considera nivel de flor)
     */
    private void spawnActiveParticles(Location location, MagicFlowerPotData data) {
        World world = location.getWorld();
        if (world == null) return;

        Location particleLocation = location.clone().add(0.5, 0.8, 0.5);
        String flowerId = data.getFlowerId();
        int potLevel = data.getLevel();
        int flowerLevel = data.getFlowerLevel();

        // Partículas específicas por tipo de flor
        Particle particleType = getFlowerParticle(flowerId);

        // 🆕 MEJORADO: Más partículas para niveles superiores de flor
        int baseCount = 3 + potLevel; // Partículas base por nivel de maceta
        int flowerBonus = (flowerLevel - 1) * 2; // Bonus por nivel de flor
        int particleCount = baseCount + flowerBonus;

        // 🆕 MEJORADO: Área de partículas más grande para flores de alto nivel
        double spread = 0.3 + (flowerLevel - 1) * 0.1;

        world.spawnParticle(
                particleType,
                particleLocation,
                particleCount,
                spread, 0.2, spread,
                0.05
        );

        // Partículas adicionales en área de efecto (menos frecuentes)
        double areaChance = 0.3 + (flowerLevel - 1) * 0.1; // Mayor chance con mejores flores
        if (Math.random() < areaChance) {
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
     * 🔧 MEJORADO: Partículas de efecto en el área considerando nivel de flor
     */
    private void spawnAreaEffectParticles(Location center, MagicFlowerPotData data) {
        World world = center.getWorld();
        if (world == null) return;

        int range = data.getEffectRange();
        int flowerLevel = data.getFlowerLevel();
        Particle particle = getFlowerParticle(data.getFlowerId());

        // 🆕 MEJORADO: Más puntos en el anillo para flores de mayor nivel
        int ringPoints = 8 + (flowerLevel - 1) * 2;

        // Crear un anillo de partículas en el suelo
        for (int i = 0; i < ringPoints; i++) {
            double angle = (i / (double) ringPoints) * 2 * Math.PI;
            double x = center.getX() + 0.5 + Math.cos(angle) * range;
            double z = center.getZ() + 0.5 + Math.sin(angle) * range;
            double y = center.getY() + 0.1;

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 🔧 MEJORADO: Reproduce efectos cuando se coloca una flor considerando su nivel
     */
    private void playFlowerPlacementEffects(Location location, String flowerId, int flowerLevel) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonido de colocación
        world.playSound(effectLocation, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);

        // 🆕 MEJORADO: Más partículas para flores de mayor nivel
        int particleCount = 20 + (flowerLevel - 1) * 10;
        double spread = 0.4 + (flowerLevel - 1) * 0.1;

        // Partículas de confirmación
        Particle particle = getFlowerParticle(flowerId);
        world.spawnParticle(particle, effectLocation, particleCount, spread, 0.3, spread, 0.1);

        // Sonido específico por flor
        Sound flowerSound = getFlowerSound(flowerId);
        if (flowerSound != null) {
            float pitch = 1.0f + (flowerLevel - 1) * 0.2f; // Pitch más alto para flores de mayor nivel
            world.playSound(effectLocation, flowerSound, 0.8f, pitch);
        }

        // 🆕 NUEVO: Efectos especiales para flores de alto nivel
        if (flowerLevel >= 4) {
            // Anillo de partículas especiales para flores de nivel 4+
            createSpecialFlowerRing(effectLocation, flowerLevel, particle);
        }
    }

    /**
     * 🆕 NUEVO: Crea un anillo especial para flores de alto nivel
     */
    private void createSpecialFlowerRing(Location center, int flowerLevel, Particle particle) {
        World world = center.getWorld();
        if (world == null) return;

        double radius = 1.0 + (flowerLevel - 4) * 0.5;
        int points = 12 + (flowerLevel - 4) * 4;

        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY();

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0.1, 0.1, 0.1, 0.02);
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

        // 🆕 NUEVO: Cancelar tarea de limpieza
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        activePots.clear();

        plugin.getLogger().info("MagicFlowerPotManager desactivado correctamente.");
    }

    /**
     * 🔧 MEJORADO: Obtiene estadísticas del manager incluyendo niveles de flores
     */
    public String getStatistics() {
        long activeFlowers = activePots.values().stream()
                .filter(MagicFlowerPotData::hasFlower)
                .count();

        // Estadísticas por nivel de flor
        java.util.Map<Integer, Long> flowerLevelCounts = new java.util.HashMap<>();
        for (int i = 1; i <= 5; i++) {
            flowerLevelCounts.put(i, 0L);
        }

        activePots.values().stream()
                .filter(MagicFlowerPotData::hasFlower)
                .forEach(data -> {
                    int level = data.getFlowerLevel();
                    flowerLevelCounts.put(level, flowerLevelCounts.get(level) + 1);
                });

        // Verificar integridad en tiempo real
        int loadedChunks = 0;
        int inconsistencies = 0;

        for (java.util.Map.Entry<Location, MagicFlowerPotData> entry : activePots.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getChunk().isLoaded()) {
                loadedChunks++;
                if (loc.getBlock().getType() != Material.FLOWER_POT) {
                    inconsistencies++;
                }
            }
        }

        StringBuilder stats = new StringBuilder();
        stats.append(String.format("Macetas activas: %d | Con flores: %d | Vacías: %d\n",
                activePots.size(), activeFlowers, activePots.size() - activeFlowers));

        stats.append("Distribución por nivel de flor:\n");
        for (int i = 1; i <= 5; i++) {
            stats.append(String.format("  Nivel %d: %d flores\n", i, flowerLevelCounts.get(i)));
        }

        stats.append(String.format("Chunks cargados: %d/%d\n", loadedChunks, activePots.size()));

        if (inconsistencies > 0) {
            stats.append(String.format("⚠ INCONSISTENCIAS DETECTADAS: %d\n", inconsistencies));
        } else {
            stats.append("✓ Integridad de datos: OK\n");
        }

        return stats.toString();
    }


    /**
     * Fuerza la actualización de efectos (útil para testing)
     */
    public void forceUpdate() {
        applyFlowerEffects();
        spawnParticles();
    }

    // =================== MÉTODOS AUXILIARES PARA EL LISTENER ===================

    /**
     * 🆕 NUEVO: Obtiene el nivel de una flor en una maceta específica
     */
    public int getFlowerLevel(Location location) {
        MagicFlowerPotData data = activePots.get(location);
        return data != null ? data.getFlowerLevel() : 1;
    }

    /**
     * 🆕 NUEVO: Actualiza solo el nivel de la flor en una maceta
     */
    public boolean updateFlowerLevel(Location location, int newLevel) {
        MagicFlowerPotData data = activePots.get(location);
        if (data == null || !data.hasFlower()) return false;

        data.setFlowerLevel(newLevel);
        return true;
    }

    /**
     * 🆕 NUEVO: Obtiene todas las macetas con un tipo específico de flor
     */
    public List<MagicFlowerPotData> getPotsWithFlower(String flowerId) {
        return activePots.values().stream()
                .filter(data -> data.hasFlower() && data.getFlowerId().equals(flowerId))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 🆕 NUEVO: Obtiene todas las macetas con flores de un nivel mínimo
     */
    public List<MagicFlowerPotData> getPotsWithMinFlowerLevel(int minLevel) {
        return activePots.values().stream()
                .filter(data -> data.hasFlower() && data.getFlowerLevel() >= minLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 🆕 NUEVO: Cuenta cuántas macetas tienen flores de nivel máximo
     */
    public long getMaxLevelFlowerCount() {
        return activePots.values().stream()
                .filter(MagicFlowerPotData::isMaxLevelFlower)
                .count();
    }

    /**
     * 🆕 NUEVO: Obtiene estadísticas detalladas para administradores
     */
    public String getDetailedStatistics() {
        StringBuilder stats = new StringBuilder();

        stats.append("=== ESTADÍSTICAS DETALLADAS DE MACETAS MÁGICAS ===\n");
        stats.append(getStatistics());

        stats.append("\nTipos de flores activas:\n");
        Map<String, Long> flowerTypeCounts = activePots.values().stream()
                .filter(MagicFlowerPotData::hasFlower)
                .collect(java.util.stream.Collectors.groupingBy(
                        MagicFlowerPotData::getFlowerId,
                        java.util.stream.Collectors.counting()
                ));

        flowerTypeCounts.forEach((type, count) ->
                stats.append(String.format("  %s: %d macetas\n", getFlowerDisplayName(type), count))
        );

        stats.append(String.format("\nMacetas con flores de nivel máximo: %d\n", getMaxLevelFlowerCount()));

        return stats.toString();
    }

    /**
     * Obtiene el nombre de display de una flor
     */
    private String getFlowerDisplayName(String flowerId) {
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return "Flor del Amor";
            case "healing_flower":
                return "Flor Sanadora";
            case "speed_flower":
                return "Flor de Velocidad";
            case "strength_flower":
                return "Flor de Fuerza";
            case "night_vision_flower":
                return "Flor Nocturna";
            default:
                return "Flor Desconocida";
        }
    }
}