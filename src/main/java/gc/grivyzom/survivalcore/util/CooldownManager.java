package gc.grivyzom.survivalcore.util;

import java.util.HashMap;
import java.util.Map;

public class CooldownManager {
    private final Map<String, Long> cooldowns;
    private final long defaultCooldown;

    /**
     * @param defaultCooldown Tiempo de espera predeterminado en milisegundos.
     */
    public CooldownManager(long defaultCooldown) {
        this.cooldowns = new HashMap<>();
        this.defaultCooldown = defaultCooldown;
    }

    // Verifica si una clave está en cooldown
    public boolean isOnCooldown(String key) {
        if (!cooldowns.containsKey(key)) {
            return false;
        }
        return cooldowns.get(key) > System.currentTimeMillis();
    }

    // Obtiene el tiempo restante de cooldown en milisegundos
    public long getRemainingCooldown(String key) {
        if (!cooldowns.containsKey(key)) {
            return 0;
        }
        return Math.max(0, cooldowns.get(key) - System.currentTimeMillis());
    }

    // Establece el cooldown para una clave con tiempo personalizado
    public void setCooldown(String key, long cooldownTime) {
        cooldowns.put(key, System.currentTimeMillis() + cooldownTime);
    }

    // Establece el cooldown usando el tiempo predeterminado
    public void setCooldown(String key) {
        setCooldown(key, defaultCooldown);
    }

    // Limpia el cooldown de una clave específica
    public void clearCooldown(String key) {
        cooldowns.remove(key);
    }
}