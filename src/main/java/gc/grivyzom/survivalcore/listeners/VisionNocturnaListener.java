package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.masteries.MasteryManager;
import gc.grivyzom.survivalcore.masteries.MasteryVisionNocturna;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener que aplica Visión Nocturna cuando el jugador se encuentra en
 * oscuridad (nivel de luz &lt; 8). Incluye:
 *   • Cool-down por maestría
 *   • Protección de efectos vanilla
 *   • Action-bar y partículas al activarse
 *   • Limpieza de mapas al desconectarse
 */
public class VisionNocturnaListener implements Listener {

    private static final int LIGHT_THRESHOLD = 8;      // Nivel de luz máximo para que cuente como “oscuro”
    private final Main plugin;
    private final MasteryManager masteryManager;

    // Última vez que se aplicó el efecto para cumplir el cool-down
    private final Map<UUID, Long> lastActivation = new ConcurrentHashMap<>();
    // Jugadores que poseen el efecto otorgado por ESTA habilidad
    private final Set<UUID> pluginEffect = ConcurrentHashMap.newKeySet();

    public VisionNocturnaListener(Main plugin, MasteryManager masteryManager) {
        this.plugin = plugin;
        this.masteryManager = masteryManager;
    }

    /* ============ EVENTOS ============ */

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        // Filtrar movimientos dentro del mismo bloque para reducir llamadas (~80 % menos)
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        // Oscuridad real
        boolean dark = player.getLocation().getBlock().getLightLevel() < LIGHT_THRESHOLD;

        // Cargar datos y nivel de la maestría
        UserData data = plugin.getDatabaseManager().getUserData(uuid.toString());
        MasteryVisionNocturna proto = (MasteryVisionNocturna) masteryManager.getMastery("vision_nocturna");
        if (data == null || proto == null) return;

        int level = data.getMasteryLevels().getOrDefault("vision_nocturna", proto.getLevel());
        if (level <= 0) return;

        MasteryVisionNocturna mastery = new MasteryVisionNocturna(level);

        long now = System.currentTimeMillis();
        long cooldown = mastery.getCooldownMillis();
        long last     = lastActivation.getOrDefault(uuid, 0L);

        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);

        /* --------- EN OSCURIDAD --------- */
        if (dark) {
            // Si ya tiene visión nocturna (de cualquier fuente) y no proviene de esta habilidad → no hacer nada
            if (current != null && !pluginEffect.contains(uuid)) return;

            // Cool-down
            if (now - last < cooldown) return;

            // Aplicar efecto si resta <5 s para evitar spam
            if (current == null || current.getDuration() <= 20 * 5) {
                mastery.applyEffect(player);
                pluginEffect.add(uuid);
                lastActivation.put(uuid, now);

                // UX: action-bar + partículas sutiles
                String msg = ChatColor.YELLOW + "¡Has activado " + ChatColor.WHITE + "Visión Nocturna" + ChatColor.YELLOW + "!";
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                player.getWorld().spawnParticle(
                        Particle.END_ROD,
                        player.getEyeLocation(), 10,
                        0.3, 0.3, 0.3, 0.01
                );
            }
            return;
        }

        /* --------- FUERA DE OSCURIDAD --------- */
        // Solo retirar si fue nuestra habilidad la que lo puso
        if (pluginEffect.remove(uuid)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    /** Limpia los mapas para evitar fugas de memoria */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastActivation.remove(id);
        pluginEffect.remove(id);
    }
}
