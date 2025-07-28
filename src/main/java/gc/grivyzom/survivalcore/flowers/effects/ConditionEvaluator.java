package gc.grivyzom.survivalcore.flowers.effects;

import gc.grivyzom.survivalcore.flowers.config.FlowerDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.time.LocalTime;
import java.util.List;

/**
 * Evaluador de condiciones para determinar si los efectos de una flor deben aplicarse
 *
 * @author Brocolitx
 * @version 1.0
 */
public class ConditionEvaluator {

    /**
     * Evalúa si todas las condiciones se cumplen para aplicar un efecto
     */
    public static boolean evaluateConditions(FlowerDefinition.FlowerEffect effect,
                                             EffectContext context) {
        List<FlowerDefinition.EffectCondition> conditions = effect.getConditions();

        // Si no hay condiciones, siempre aplicar
        if (conditions.isEmpty()) {
            return true;
        }

        // Todas las condiciones deben cumplirse (AND lógico)
        for (FlowerDefinition.EffectCondition condition : conditions) {
            if (!evaluateCondition(condition, context)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evalúa si las condiciones especiales de una flor se cumplen
     */
    public static boolean evaluateSpecialConditions(FlowerDefinition.SpecialConditions conditions,
                                                    EffectContext context) {
        Location location = context.getPotLocation();
        World world = location.getWorld();

        if (world == null) return false;

        // Verificar mundo
        if (conditions.isDisabledInNether() && world.getEnvironment() == World.Environment.NETHER) {
            return false;
        }

        if (conditions.isDisabledInEnd() && world.getEnvironment() == World.Environment.THE_END) {
            return false;
        }

        // Verificar altura
        int y = location.getBlockY();
        if (y < conditions.getMinYLevel() || y > conditions.getMaxYLevel()) {
            return false;
        }

        // Verificar luz de luna
        if (conditions.requiresMoonlight() && !hasMoonlight(world, location)) {
            return false;
        }

        // Verificar luz solar
        if (conditions.requiresSunlight() && !hasSunlight(world, location)) {
            return false;
        }

        return true;
    }

    /**
     * Evalúa una condición específica
     */
    private static boolean evaluateCondition(FlowerDefinition.EffectCondition condition,
                                             EffectContext context) {
        String type = condition.getType().toLowerCase();
        String value = condition.getValue().toLowerCase();

        switch (type) {
            case "time":
                return evaluateTimeCondition(value, context);

            case "weather":
                return evaluateWeatherCondition(value, context);

            case "world_type":
                return evaluateWorldTypeCondition(value, context);

            case "permission":
                return evaluatePermissionCondition(value, context);

            case "player_health":
                return evaluatePlayerHealthCondition(value, context);

            case "moon_phase":
                return evaluateMoonPhaseCondition(value, context);

            case "biome_temperature":
                return evaluateBiomeTemperatureCondition(value, context);

            case "nearby_blocks":
                return evaluateNearbyBlocksCondition(value, context);

            default:
                // Condición desconocida - no aplicar por seguridad
                return false;
        }
    }

    /**
     * Evalúa condiciones de tiempo (day, night, dawn, dusk, any)
     */
    private static boolean evaluateTimeCondition(String timeCondition, EffectContext context) {
        if ("any".equals(timeCondition)) {
            return true;
        }

        World world = context.getPotLocation().getWorld();
        if (world == null) return false;

        long time = world.getTime();

        switch (timeCondition) {
            case "day":
                return time >= 0 && time < 12300; // 6:00 AM a 6:18 PM

            case "night":
                return time >= 12300 && time < 23850; // 6:18 PM a 5:51 AM

            case "dawn":
                return time >= 23000 || time < 1000; // 5:00 AM a 7:00 AM

            case "dusk":
                return time >= 12000 && time < 14000; // 6:00 PM a 8:00 PM

            default:
                return false;
        }
    }

    /**
     * Evalúa condiciones de clima (clear, rain, storm, any)
     */
    private static boolean evaluateWeatherCondition(String weatherCondition, EffectContext context) {
        if ("any".equals(weatherCondition)) {
            return true;
        }

        World world = context.getPotLocation().getWorld();
        if (world == null) return false;

        switch (weatherCondition) {
            case "clear":
                return !world.hasStorm() && !world.isThundering();

            case "rain":
                return world.hasStorm() && !world.isThundering();

            case "storm":
                return world.hasStorm() && world.isThundering();

            default:
                return false;
        }
    }

    /**
     * Evalúa condiciones de tipo de mundo
     */
    private static boolean evaluateWorldTypeCondition(String worldTypeCondition, EffectContext context) {
        if ("any".equals(worldTypeCondition)) {
            return true;
        }

        World world = context.getPotLocation().getWorld();
        if (world == null) return false;

        World.Environment environment = world.getEnvironment();

        switch (worldTypeCondition) {
            case "overworld":
                return environment == World.Environment.NORMAL;

            case "nether":
                return environment == World.Environment.NETHER;

            case "end":
                return environment == World.Environment.THE_END;

            default:
                return false;
        }
    }

    /**
     * Evalúa condiciones de permisos
     */
    private static boolean evaluatePermissionCondition(String permission, EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) return true; // Si no hay jugador específico, no verificar permisos

        return player.hasPermission(permission);
    }

    /**
     * Evalúa condiciones de salud del jugador
     */
    private static boolean evaluatePlayerHealthCondition(String healthCondition, EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        double currentHealth = player.getHealth();
        double maxHealth = player.getMaxHealth();
        double healthPercentage = (currentHealth / maxHealth) * 100;

        if (healthCondition.startsWith("<")) {
            // Menor que cierto porcentaje (ej: "<50%")
            String percentageStr = healthCondition.substring(1).replace("%", "");
            try {
                double threshold = Double.parseDouble(percentageStr);
                return healthPercentage < threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (healthCondition.startsWith(">")) {
            // Mayor que cierto porcentaje (ej: ">80%")
            String percentageStr = healthCondition.substring(1).replace("%", "");
            try {
                double threshold = Double.parseDouble(percentageStr);
                return healthPercentage > threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Evalúa condiciones de fase lunar
     */
    private static boolean evaluateMoonPhaseCondition(String moonPhase, EffectContext context) {
        World world = context.getPotLocation().getWorld();
        if (world == null) return false;

        long fullTime = world.getFullTime();
        int currentPhase = (int) ((fullTime / 24000) % 8);

        switch (moonPhase) {
            case "full":
                return currentPhase == 0; // Luna llena

            case "new":
                return currentPhase == 4; // Luna nueva

            case "waxing":
                return currentPhase >= 1 && currentPhase <= 3; // Creciente

            case "waning":
                return currentPhase >= 5 && currentPhase <= 7; // Menguante

            default:
                return false;
        }
    }

    /**
     * Evalúa condiciones de temperatura del bioma
     */
    private static boolean evaluateBiomeTemperatureCondition(String tempCondition, EffectContext context) {
        Location location = context.getPotLocation();
        float temperature = location.getBlock().getBiome().getTemperature();

        if (tempCondition.startsWith(">")) {
            try {
                float threshold = Float.parseFloat(tempCondition.substring(1));
                return temperature > threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (tempCondition.startsWith("<")) {
            try {
                float threshold = Float.parseFloat(tempCondition.substring(1));
                return temperature < threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Condiciones predefinidas
        switch (tempCondition) {
            case "hot":
                return temperature > 1.0f;

            case "warm":
                return temperature > 0.5f && temperature <= 1.0f;

            case "temperate":
                return temperature > 0.2f && temperature <= 0.5f;

            case "cold":
                return temperature <= 0.2f;

            default:
                return false;
        }
    }

    /**
     * Evalúa condiciones de bloques cercanos
     */
    private static boolean evaluateNearbyBlocksCondition(String blockCondition, EffectContext context) {
        // Formato: "block_type:distance" (ej: "FIRE:5")
        String[] parts = blockCondition.split(":");
        if (parts.length != 2) return false;

        try {
            org.bukkit.Material blockType = org.bukkit.Material.valueOf(parts[0].toUpperCase());
            int distance = Integer.parseInt(parts[1]);

            Location center = context.getPotLocation();

            // Buscar el tipo de bloque en el área especificada
            for (int x = -distance; x <= distance; x++) {
                for (int y = -distance; y <= distance; y++) {
                    for (int z = -distance; z <= distance; z++) {
                        Location checkLoc = center.clone().add(x, y, z);
                        if (checkLoc.getBlock().getType() == blockType) {
                            return true;
                        }
                    }
                }
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica si hay luz de luna en la ubicación
     */
    private static boolean hasMoonlight(World world, Location location) {
        // Verificar que sea de noche
        long time = world.getTime();
        if (time < 12300 || time > 23850) {
            return false; // No es de noche
        }

        // Verificar que no esté lloviendo (las nubes bloquean la luna)
        if (world.hasStorm()) {
            return false;
        }

        // Verificar que haya cielo abierto
        Location checkLocation = location.clone();
        checkLocation.setY(world.getHighestBlockYAt(location));

        return checkLocation.getY() <= location.getY() + 1; // Está cerca del cielo
    }

    /**
     * Verifica si hay luz solar en la ubicación
     */
    private static boolean hasSunlight(World world, Location location) {
        // Verificar que sea de día
        long time = world.getTime();
        if (time < 0 || time > 12300) {
            return false; // No es de día
        }

        // Verificar que no esté lloviendo
        if (world.hasStorm()) {
            return false;
        }

        // Verificar nivel de luz solar
        byte lightLevel = location.getBlock().getLightFromSky();
        return lightLevel >= 12; // Suficiente luz solar
    }

    /**
     * Clase de contexto para evaluación de condiciones
     */
    public static class EffectContext {
        private final Player player;
        private final Location potLocation;
        private final int flowerLevel;
        private final int potLevel;

        public EffectContext(Player player, Location potLocation, int flowerLevel, int potLevel) {
            this.player = player;
            this.potLocation = potLocation.clone();
            this.flowerLevel = flowerLevel;
            this.potLevel = potLevel;
        }

        public Player getPlayer() { return player; }
        public Location getPotLocation() { return potLocation.clone(); }
        public int getFlowerLevel() { return flowerLevel; }
        public int getPotLevel() { return potLevel; }

        /**
         * Crea un contexto básico sin jugador específico
         */
        public static EffectContext createBasic(Location potLocation, int flowerLevel, int potLevel) {
            return new EffectContext(null, potLocation, flowerLevel, potLevel);
        }

        /**
         * Crea un contexto para un jugador específico
         */
        public static EffectContext createForPlayer(Player player, Location potLocation,
                                                    int flowerLevel, int potLevel) {
            return new EffectContext(player, potLocation, flowerLevel, potLevel);
        }
    }
}