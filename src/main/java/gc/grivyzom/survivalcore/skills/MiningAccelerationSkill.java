package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class MiningAccelerationSkill extends Skill {
    private final Main plugin;
    private BukkitRunnable effectTask;

    public MiningAccelerationSkill(Main plugin, int level, long duration) {
        super("Aceleración Minera", level, duration);
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Calcular nivel de prisa basado en nivel de habilidad
        int hasteLevel = Math.min(getLevel() / 5 + 1, 3); // Máximo nivel 3

        // Calcular duración efectiva (+1 segundo por nivel)
        long effectiveDuration = getDuration() + ((getLevel() - 1) * 20);

        // Aplicar efecto de prisa minera al jugador
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FAST_DIGGING,
                (int) effectiveDuration,
                hasteLevel - 1, // Nivel de efecto (0 = I, 1 = II, etc)
                true, // Partículas visibles
                true // Efecto ambientales
        ));

        // Mensaje de activación con detalles
        player.sendMessage(ChatColor.GREEN + "¡Aceleración Minera nivel " + hasteLevel +
                " activada! Duración: " + (effectiveDuration / 20) + " segundos.");

        // Programar desactivación automática
        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getSkillManager().deactivateSkill(player);
            }
        };
        effectTask.runTaskLater(plugin, effectiveDuration);
    }

    @Override
    public void onDeactivate(Player player) {
        // Cancelar tarea si aún está activa
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }

        // Notificar al jugador
        player.sendMessage(ChatColor.RED + "Aceleración Minera ha finalizado.");
    }

    @Override
    public long getEffectiveCooldown() {
        // Cooldown base de 30 segundos, reducido por nivel
        long baseCooldown = 30000;
        long reduction = (getLevel() - 1) * 1500; // -1.5 segundos por nivel
        return Math.max(baseCooldown - reduction, 10000); // Mínimo 10 segundos
    }
}