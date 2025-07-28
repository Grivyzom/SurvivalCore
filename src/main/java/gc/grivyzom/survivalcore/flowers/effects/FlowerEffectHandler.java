package gc.grivyzom.survivalcore.flowers.effects;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowers.config.FlowerConfigManager;
import gc.grivyzom.survivalcore.flowers.config.FlowerDefinition;
import gc.grivyzom.survivalcore.flowers.config.FormulaCalculator;
import gc.grivyzom.survivalcore.flowers.config.TierDefinition;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotData;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler principal para aplicar efectos de flores mágicas configurables
 * Integra con el sistema de configuración YAML
 *
 * @author Brocolitx
 * @version 1.0
 */
public class FlowerEffectHandler {

    private final Main plugin;
    private final FlowerConfigManager configManager;

    // Tareas activas para efectos y partículas
    private BukkitTask effectTask;
    private BukkitTask particleTask;
    private BukkitTask specialMechanicsTask;

    // Cache de efectos activos por jugador
    private final Map<UUID, Set<String>> activeFlowerEffects = new ConcurrentHashMap<>();

    // Cache de mecánicas especiales activas
    private final Map<String, SpecialMechanicInstance> activeMechanics = new ConcurrentHashMap<>();

    public FlowerEffectHandler(Main plugin, FlowerConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        startEffectTasks();
    }

    /**
     * Inicia las tareas periódicas para efectos
     */
    private void startEffectTasks() {
        FlowerConfigManager.GlobalSettings settings = configManager.getGlobalSettings();

        // Tarea de efectos de área
        long effectInterval = settings.getEffectRefreshInterval() * 20L; // Convertir a ticks
        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                processFlowerEffects();
            }
        }.runTaskTimer(plugin, 20L, effectInterval);

        // Tarea de partículas
        long particleInterval = settings.getParticleSpawnInterval() * 20L;
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                processFlowerParticles();
            }
        }.runTaskTimer(plugin, 10L, particleInterval);

        // Tarea de mecánicas especiales (cada 5 segundos)
        specialMechanicsTask = new BukkitRunnable() {
            @Override
            public void run() {
                processSpecialMechanics();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    /**
     * Procesa todos los efectos de flores activas
     */
    private void processFlowerEffects() {
        Collection<MagicFlowerPotData> activePots = plugin.getMagicFlowerPotManager().getAllActivePots();

        for (MagicFlowerPotData potData : activePots) {
            if (!potData.hasFlower()) continue;

            processFlowerEffectsForPot(potData);
        }
    }

    /**
     * Procesa efectos para una maceta específica
     */
    private void processFlowerEffectsForPot(MagicFlowerPotData potData) {
        String flowerId = potData.getFlowerId();
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) {
            plugin.getLogger().warning("Definición de flor no encontrada: " + flowerId);
            return;
        }

        Location potLocation = potData.getLocation();
        World world = potLocation.getWorld();
        if (world == null) return;

        // Verificar condiciones especiales de la flor
        ConditionEvaluator.EffectContext baseContext = ConditionEvaluator.EffectContext
                .createBasic(potLocation, potData.getFlowerLevel(), potData.getLevel());

        if (!ConditionEvaluator.evaluateSpecialConditions(flowerDef.getSpecialConditions(), baseContext)) {
            return; // Condiciones especiales no se cumplen
        }

        // Obtener jugadores en el área de efecto
        Collection<Player> nearbyPlayers = getNearbyPlayers(potLocation, potData.getEffectRange());

        // Aplicar efectos a cada jugador
        for (Player player : nearbyPlayers) {
            applyFlowerEffectsToPlayer(player, potData, flowerDef);
        }
    }

    /**
     * Aplica efectos de una flor a un jugador específico
     */
    private void applyFlowerEffectsToPlayer(Player player, MagicFlowerPotData potData,
                                            FlowerDefinition flowerDef) {
        ConditionEvaluator.EffectContext playerContext = ConditionEvaluator.EffectContext
                .createForPlayer(player, potData.getLocation(), potData.getFlowerLevel(), potData.getLevel());

        // Procesar cada efecto de la flor
        for (FlowerDefinition.FlowerEffect flowerEffect : flowerDef.getEffects()) {
            if (ConditionEvaluator.evaluateConditions(flowerEffect, playerContext)) {
                applyEffectToPlayer(player, flowerEffect, potData, flowerDef);
            }
        }

        // Registrar que este jugador tiene efectos activos de esta flor
        registerActiveEffect(player, flowerDef.getId());
    }

    /**
     * Aplica un efecto específico a un jugador
     */
    private void applyEffectToPlayer(Player player, FlowerDefinition.FlowerEffect flowerEffect,
                                     MagicFlowerPotData potData, FlowerDefinition flowerDef) {
        PotionEffectType effectType = flowerEffect.getType();

        // Calcular nivel y duración usando las fórmulas
        int effectLevel = flowerEffect.calculateLevel(potData.getFlowerLevel());
        int duration = flowerEffect.calculateDuration(potData.getFlowerLevel());

        // Aplicar multiplicadores del tier
        TierDefinition tier = flowerDef.getTier();
        double tierMultiplier = tier.getEffectMultiplierForLevel(potData.getFlowerLevel());

        effectLevel = (int) Math.max(0, effectLevel * tierMultiplier);
        duration = (int) (duration * tierMultiplier);

        // Convertir duración a ticks
        int durationTicks = duration * 20;

        // Aplicar el efecto (solo si el jugador no tiene uno más fuerte)
        PotionEffect existingEffect = player.getPotionEffect(effectType);
        if (existingEffect == null ||
                existingEffect.getAmplifier() < effectLevel ||
                existingEffect.getDuration() < durationTicks / 2) {

            PotionEffect newEffect = new PotionEffect(
                    effectType,
                    durationTicks,
                    effectLevel,
                    true, // ambient (partículas menos visibles)
                    configManager.getGlobalSettings().isAdvancedParticlesEnabled(), // particles
                    true  // icon
            );

            player.addPotionEffect(newEffect, true);

            // Reproducir sonido de activación si está habilitado
            if (configManager.getGlobalSettings().isCustomSoundsEnabled()) {
                Sound activationSound = flowerDef.getSounds().getActivation();
                player.playSound(player.getLocation(), activationSound, 0.5f, 1.2f);
            }
        }
    }

    /**
     * Procesa partículas para todas las flores activas
     */
    private void processFlowerParticles() {
        if (!configManager.getGlobalSettings().isAdvancedParticlesEnabled()) {
            return;
        }

        Collection<MagicFlowerPotData> activePots = plugin.getMagicFlowerPotManager().getAllActivePots();

        for (MagicFlowerPotData potData : activePots) {
            if (!potData.hasFlower()) continue;

            processParticlesForPot(potData);
        }
    }

    /**
     * Procesa partículas para una maceta específica
     */
    private void processParticlesForPot(MagicFlowerPotData potData) {
        String flowerId = potData.getFlowerId();
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) return;

        Location potLocation = potData.getLocation();
        World world = potLocation.getWorld();
        if (world == null || !world.getChunkAt(potLocation).isLoaded()) return;

        // Verificar condiciones especiales
        ConditionEvaluator.EffectContext context = ConditionEvaluator.EffectContext
                .createBasic(potLocation, potData.getFlowerLevel(), potData.getLevel());

        if (!ConditionEvaluator.evaluateSpecialConditions(flowerDef.getSpecialConditions(), context)) {
            return;
        }

        // Generar partículas
        generateFlowerParticles(potData, flowerDef);
    }

    /**
     * Genera partículas para una flor específica
     */
    private void generateFlowerParticles(MagicFlowerPotData potData, FlowerDefinition flowerDef) {
        Location potLocation = potData.getLocation();
        World world = potLocation.getWorld();
        if (world == null) return;

        FlowerDefinition.ParticleConfig particles = flowerDef.getParticles();
        TierDefinition tier = flowerDef.getTier();

        // Calcular cantidad de partículas con multiplicadores
        double tierMultiplier = tier.getParticleMultiplierForLevel(potData.getFlowerLevel());
        int particleCount = (int) (particles.getAmount() * tierMultiplier);

        // Partículas principales en la maceta
        Location particleLocation = potLocation.clone().add(0.5, 0.8, 0.5);
        world.spawnParticle(
                particles.getAreaEffect(),
                particleLocation,
                particleCount,
                0.3, 0.2, 0.3,
                0.05
        );

        // Partículas ambientales ocasionales
        if (Math.random() < 0.3) { // 30% de probabilidad
            world.spawnParticle(
                    particles.getAmbient(),
                    particleLocation,
                    particleCount / 2,
                    0.5, 0.3, 0.5,
                    0.02
            );
        }

        // Partículas en el área de efecto (menos frecuentes)
        if (Math.random() < 0.1) { // 10% de probabilidad
            generateAreaParticles(potData, flowerDef);
        }
    }

    /**
     * Genera partículas en el área de efecto
     */
    private void generateAreaParticles(MagicFlowerPotData potData, FlowerDefinition flowerDef) {
        Location center = potData.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        int range = potData.getEffectRange();
        Particle particle = flowerDef.getParticles().getAreaEffect();

        // Crear anillo de partículas
        int points = 8 + (potData.getFlowerLevel() - 1) * 2;
        double radius = range * 0.8; // 80% del rango total

        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            double y = center.getY() + 0.1;

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Procesa mecánicas especiales de todas las flores
     */
    private void processSpecialMechanics() {
        Collection<MagicFlowerPotData> activePots = plugin.getMagicFlowerPotManager().getAllActivePots();

        for (MagicFlowerPotData potData : activePots) {
            if (!potData.hasFlower()) continue;

            processSpecialMechanicsForPot(potData);
        }

        // Limpiar mecánicas expiradas
        cleanupExpiredMechanics();
    }

    /**
     * Procesa mecánicas especiales para una maceta específica
     */
    private void processSpecialMechanicsForPot(MagicFlowerPotData potData) {
        String flowerId = potData.getFlowerId();
        FlowerDefinition flowerDef = configManager.getFlower(flowerId);

        if (flowerDef == null) return;

        Map<String, FlowerDefinition.SpecialMechanic> mechanics = flowerDef.getSpecialMechanics();

        for (FlowerDefinition.SpecialMechanic mechanic : mechanics.values()) {
            if (mechanic.isEnabled()) {
                processSpecialMechanic(potData, flowerDef, mechanic);
            }
        }
    }

    /**
     * Procesa una mecánica especial específica
     */
    private void processSpecialMechanic(MagicFlowerPotData potData, FlowerDefinition flowerDef,
                                        FlowerDefinition.SpecialMechanic mechanic) {
        String mechanicName = mechanic.getName();
        String mechanicKey = potData.getPotId() + ":" + mechanicName;

        switch (mechanicName.toLowerCase()) {
            case "lunar_boost":
                processLunarBoostMechanic(potData, flowerDef, mechanic, mechanicKey);
                break;

            case "star_alignment":
                processStarAlignmentMechanic(potData, flowerDef, mechanic, mechanicKey);
                break;

            case "phoenix_rebirth":
                processPhoenixRebirthMechanic(potData, flowerDef, mechanic, mechanicKey);
                break;

            case "flame_aura":
                processFlameAuraMechanic(potData, flowerDef, mechanic, mechanicKey);
                break;

            default:
                // Mecánica personalizada - delegar a un handler extensible
                processCustomMechanic(potData, flowerDef, mechanic, mechanicKey);
                break;
        }
    }

    /**
     * Procesa la mecánica de potenciación lunar
     */
    private void processLunarBoostMechanic(MagicFlowerPotData potData, FlowerDefinition flowerDef,
                                           FlowerDefinition.SpecialMechanic mechanic, String mechanicKey) {
        // Verificar si es luna llena
        World world = potData.getLocation().getWorld();
        if (world == null) return;

        long fullTime = world.getFullTime();
        int moonPhase = (int) ((fullTime / 24000) % 8);

        if (moonPhase == 0) { // Luna llena
            double multiplier = mechanic.getProperty("effect_multiplier", Double.class, 1.5);

            // Aplicar boost a jugadores cercanos
            Collection<Player> nearbyPlayers = getNearbyPlayers(potData.getLocation(), potData.getEffectRange());

            for (Player player : nearbyPlayers) {
                // Crear efecto especial de boost lunar
                applyLunarBoost(player, multiplier, flowerDef);
            }

            // Registrar mecánica activa
            SpecialMechanicInstance instance = new SpecialMechanicInstance(
                    mechanicKey, System.currentTimeMillis(), 60000 // 1 minuto de duración
            );
            activeMechanics.put(mechanicKey, instance);
        }
    }

    /**
     * Procesa la mecánica de alineación estelar
     */
    private void processStarAlignmentMechanic(MagicFlowerPotData potData, FlowerDefinition flowerDef,
                                              FlowerDefinition.SpecialMechanic mechanic, String mechanicKey) {
        double triggerChance = mechanic.getProperty("trigger_chance", Double.class, 0.1);

        if (Math.random() < triggerChance) {
            @SuppressWarnings("unchecked")
            List<String> bonusEffects = mechanic.getProperty("bonus_effects", List.class, new ArrayList<>());

            Collection<Player> nearbyPlayers = getNearbyPlayers(potData.getLocation(), potData.getEffectRange());

            for (Player player : nearbyPlayers) {
                // Aplicar efectos bonus de alineación estelar
                applyStarAlignmentEffects(player, bonusEffects, flowerDef);
            }

            // Efectos visuales especiales
            createStarAlignmentVisuals(potData.getLocation(), flowerDef);

            // Registrar evento
            SpecialMechanicInstance instance = new SpecialMechanicInstance(
                    mechanicKey, System.currentTimeMillis(), 120000 // 2 minutos de cooldown
            );
            activeMechanics.put(mechanicKey, instance);
        }
    }

    /**
     * Procesa la mecánica de renacimiento del fénix
     */
    private void processPhoenixRebirthMechanic(MagicFlowerPotData potData, FlowerDefinition flowerDef,
                                               FlowerDefinition.SpecialMechanic mechanic, String mechanicKey) {
        boolean triggerOnDeath = mechanic.getProperty("trigger_on_death", Boolean.class, true);
        long cooldown = mechanic.getProperty("cooldown", Long.class, 3600L) * 1000; // Convertir a ms

        if (!triggerOnDeath) return; // Solo activar en muerte por ahora

        // Verificar si la mecánica está en cooldown
        SpecialMechanicInstance existingInstance = activeMechanics.get(mechanicKey);
        if (existingInstance != null &&
                System.currentTimeMillis() - existingInstance.getStartTime() < cooldown) {
            return; // En cooldown
        }

        // Esta mecánica se activa en el listener de muerte de jugador
        // Aquí solo registramos que está disponible
        Collection<Player> nearbyPlayers = getNearbyPlayers(potData.getLocation(), potData.getEffectRange());

        for (Player player : nearbyPlayers) {
            // Marcar al jugador como elegible para renacimiento
            markPlayerForPhoenixRebirth(player, mechanicKey, mechanic);
        }
    }

    /**
     * Procesa la mecánica de aura flamígera
     */
    private void processFlameAuraMechanic(MagicFlowerPotData potData, FlowerDefinition flowerDef,
                                          FlowerDefinition.SpecialMechanic mechanic, String mechanicKey) {
        boolean damageNearbyMobs = mechanic.getProperty("damage_nearby_mobs", Boolean.class, true);
        double damageAmount = mechanic.getProperty("damage_amount", Double.class, 2.0);
        int damageRadius = mechanic.getProperty("damage_radius", Integer.class, 3);

        if (!damageNearbyMobs) return;

        Location center = potData.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        // Dañar entidades hostiles cercanas
        world.getNearbyEntities(center, damageRadius, damageRadius, damageRadius).stream()
                .filter(entity -> entity instanceof org.bukkit.entity.Monster)
                .forEach(entity -> {
                    entity.setFireTicks(60); // 3 segundos de fuego
                    if (entity instanceof org.bukkit.entity.Damageable) {
                        ((org.bukkit.entity.Damageable) entity).damage(damageAmount);
                    }
                });

        // Efectos visuales de llamas
        createFlameAuraVisuals(center, damageRadius);
    }

    /**
     * Procesa mecánicas personalizadas (extensible para futuras mecánicas)
     */
    private void processCustomMechanic(MagicFlowerPotData potData, FlowerDefinition flowerDef,
                                       FlowerDefinition.SpecialMechanic mechanic, String mechanicKey) {
        // Sistema extensible para mecánicas personalizadas
        // Se puede expandir con un registro de handlers personalizados
        plugin.getLogger().info("Mecánica personalizada no implementada: " + mechanic.getName());
    }

    /**
     * Aplica boost lunar a un jugador
     */
    private void applyLunarBoost(Player player, double multiplier, FlowerDefinition flowerDef) {
        // Potenciar efectos existentes temporalmente
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (isFlowerEffect(effect.getType(), flowerDef)) {
                int boostedLevel = (int) (effect.getAmplifier() * multiplier);
                PotionEffect boostedEffect = new PotionEffect(
                        effect.getType(),
                        Math.min(effect.getDuration(), 1200), // Max 1 minuto
                        Math.min(boostedLevel, 3), // Max nivel 4 (3 en 0-based)
                        true, true, true
                );
                player.addPotionEffect(boostedEffect, true);
            }
        }

        // Efectos visuales
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

        if (configManager.getGlobalSettings().isCustomSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
        }
    }

    /**
     * Aplica efectos de alineación estelar
     */
    private void applyStarAlignmentEffects(Player player, List<String> bonusEffects, FlowerDefinition flowerDef) {
        for (String effectString : bonusEffects) {
            // Formato: "EFFECT:LEVEL:DURATION"
            String[] parts = effectString.split(":");
            if (parts.length == 3) {
                try {
                    PotionEffectType effectType = PotionEffectType.getByName(parts[0]);
                    int level = Integer.parseInt(parts[1]);
                    int duration = Integer.parseInt(parts[2]) * 20; // Convertir a ticks

                    if (effectType != null) {
                        PotionEffect bonusEffect = new PotionEffect(effectType, duration, level, true, true, true);
                        player.addPotionEffect(bonusEffect, true);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Efecto de alineación estelar mal formateado: " + effectString);
                }
            }
        }

        // Mensaje especial al jugador
        player.sendMessage("§6✦ §dLas estrellas se han alineado a tu favor §6✦");
    }

    /**
     * Crea efectos visuales de alineación estelar
     */
    private void createStarAlignmentVisuals(Location center, FlowerDefinition flowerDef) {
        World world = center.getWorld();
        if (world == null) return;

        // Crear múltiples anillos de partículas ascendentes
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 60; // 3 segundos

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / maxTicks;
                double height = progress * 5; // Subir 5 bloques
                double radius = 2 + progress * 3; // Expandir el radio

                int points = 12;
                for (int i = 0; i < points; i++) {
                    double angle = (i / (double) points) * 2 * Math.PI;
                    double x = center.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
                    double y = center.getY() + height;

                    Location particleLocation = new Location(world, x, y, z);
                    world.spawnParticle(Particle.END_ROD, particleLocation, 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Sonido especial
        if (configManager.getGlobalSettings().isCustomSoundsEnabled()) {
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        }
    }

    /**
     * Marca a un jugador para renacimiento del fénix
     */
    private void markPlayerForPhoenixRebirth(Player player, String mechanicKey,
                                             FlowerDefinition.SpecialMechanic mechanic) {
        // Almacenar en metadata temporal del jugador o un mapa interno
        String metadataKey = "phoenix_rebirth_" + mechanicKey;
        player.setMetadata(metadataKey, new org.bukkit.metadata.FixedMetadataValue(plugin, mechanic));
    }

    /**
     * Crea efectos visuales de aura flamígera
     */
    private void createFlameAuraVisuals(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        // Anillo de partículas de fuego
        int points = radius * 4;
        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            double y = center.getY() + 0.1;

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(Particle.FLAME, particleLocation, 2, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * Verifica si un efecto pertenece a una flor específica
     */
    private boolean isFlowerEffect(PotionEffectType effectType, FlowerDefinition flowerDef) {
        return flowerDef.getEffects().stream()
                .anyMatch(effect -> effect.getType().equals(effectType));
    }

    /**
     * Obtiene jugadores cercanos a una ubicación
     */
    private Collection<Player> getNearbyPlayers(Location center, int range) {
        World world = center.getWorld();
        if (world == null) return Collections.emptyList();

        Collection<Player> nearbyPlayers = new ArrayList<>();

        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, range, range, range)) {
            if (entity instanceof Player) {
                nearbyPlayers.add((Player) entity);
            }
        }

        return nearbyPlayers;
    }

    /**
     * Registra que un jugador tiene efectos activos de una flor
     */
    private void registerActiveEffect(Player player, String flowerId) {
        UUID playerId = player.getUniqueId();
        activeFlowerEffects.computeIfAbsent(playerId, k -> new HashSet<>()).add(flowerId);
    }

    /**
     * Limpia mecánicas especiales expiradas
     */
    private void cleanupExpiredMechanics() {
        long currentTime = System.currentTimeMillis();
        activeMechanics.entrySet().removeIf(entry -> {
            SpecialMechanicInstance instance = entry.getValue();
            return currentTime - instance.getStartTime() > instance.getDuration();
        });
    }

    /**
     * Detiene todas las tareas y limpia recursos
     */
    public void shutdown() {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }

        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
        }

        if (specialMechanicsTask != null && !specialMechanicsTask.isCancelled()) {
            specialMechanicsTask.cancel();
        }

        activeFlowerEffects.clear();
        activeMechanics.clear();

        plugin.getLogger().info("FlowerEffectHandler desactivado correctamente.");
    }

    /**
     * Recarga la configuración y reinicia las tareas
     */
    public void reload() {
        shutdown();
        startEffectTasks();
    }

    // =================== GETTERS PARA ESTADÍSTICAS ===================

    public int getActiveEffectCount() {
        return activeFlowerEffects.size();
    }

    public int getActiveMechanicCount() {
        return activeMechanics.size();
    }

    public Set<String> getActiveFlowersForPlayer(Player player) {
        return new HashSet<>(activeFlowerEffects.getOrDefault(player.getUniqueId(), Collections.emptySet()));
    }

    // =================== CLASE INTERNA PARA INSTANCIAS DE MECÁNICAS ===================

    private static class SpecialMechanicInstance {
        private final String mechanicKey;
        private final long startTime;
        private final long duration;

        public SpecialMechanicInstance(String mechanicKey, long startTime, long duration) {
            this.mechanicKey = mechanicKey;
            this.startTime = startTime;
            this.duration = duration;
        }

        public String getMechanicKey() { return mechanicKey; }
        public long getStartTime() { return startTime; }
        public long getDuration() { return duration; }
    }
}