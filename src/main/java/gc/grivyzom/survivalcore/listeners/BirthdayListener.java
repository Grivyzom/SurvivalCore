package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

public class BirthdayListener implements Listener {

    private final Main plugin;

    public BirthdayListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Usar DatabaseManager en lugar de JSONManager
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        if (data != null && data.getCumpleaños() != null && !data.getCumpleaños().isEmpty()) {
            if (isTodayBirthday(data.getCumpleaños())) {
                // Ejecuta la acción en unos ticks para asegurar que el jugador ya esté completamente en el mundo.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    launchFireworks(player);
                    Bukkit.broadcastMessage(ChatColor.GOLD + "¡Feliz cumpleaños " + player.getName() + "! Que tengas un día espectacular.");
                }, 20L);
            }
        }
    }

    // Comprueba si hoy es el cumpleaños (compara MM/dd)
    private boolean isTodayBirthday(String storedIsoDate) {
        try {
            LocalDate date = LocalDate.parse(storedIsoDate);   // yyyy-MM-dd
            LocalDate today = LocalDate.now();
            return date.getMonth() == today.getMonth()
                    && date.getDayOfMonth() == today.getDayOfMonth();
        } catch (Exception e) {
            return false;
        }
    }

    // Lanza un fuego artificial en la ubicación del jugador
    private void launchFireworks(Player player) {
        World world = player.getWorld();
        // Ajustamos la ubicación para que los fuegos se vean bien
        Firework firework = world.spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        // Configuramos el efecto: color, tipo, parpadeo y estela
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(Color.AQUA)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.BALL_LARGE)
                .flicker(true)
                .trail(true)
                .build();
        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
}
