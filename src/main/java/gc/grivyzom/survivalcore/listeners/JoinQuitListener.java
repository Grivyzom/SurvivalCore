package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public class JoinQuitListener implements Listener {
    private final Main plugin;

    public JoinQuitListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        Player player = event.getPlayer();

        if (shouldSilent(player, "join")) return;

        String type = player.hasPlayedBefore() ? "join" : "first-join";
        String raw = getCustomMessage(player, type);

        String greeting = getTimeGreeting();
        String fullRaw = greeting.isEmpty() ? raw : greeting + " " + raw;

        String msg = format(fullRaw, player.getName());
        if (!msg.isEmpty()) {
            plugin.getServer().broadcastMessage(msg);
        }

        if ("first-join".equals(type)) {
            List<String> cmds = plugin.getConfig()
                    .getStringList("messages.first-join.commands");
            cmds.forEach(cmd -> {
                String command = cmd.replace("%player%", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        Player player = event.getPlayer();

        if (shouldSilent(player, "quit")) return;

        String raw = getCustomMessage(player, "quit");
        String greeting = getTimeGreeting();
        String fullRaw = greeting.isEmpty() ? raw : greeting + " " + raw;

        String msg = format(fullRaw, player.getName());
        if (!msg.isEmpty()) {
            plugin.getServer().broadcastMessage(msg);
        }
    }

    private boolean shouldSilent(Player player, String type) {
        return plugin.getConfig()
                .getStringList("messages." + type + ".silent-permissions")
                .stream()
                .anyMatch(player::hasPermission);
    }

    private String getCustomMessage(Player player, String type) {
        String defaultMsg = plugin.getConfig().getString("messages." + type + ".default", "");
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("messages." + type + ".permission-messages");
        if (section != null) {
            return section.getKeys(false).stream()
                    .filter(key -> (key.equalsIgnoreCase("op") && player.isOp()) || player.hasPermission(key))
                    .map(section::getString)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(defaultMsg);
        }
        return defaultMsg;
    }

    private String getTimeGreeting() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        String path = hour >= 5 && hour < 12
                ? "time-greetings.morning"
                : hour >= 12 && hour < 18
                ? "time-greetings.afternoon"
                : "time-greetings.night";

        String raw = plugin.getConfig().getString(path, "");
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }

    private String format(String raw, String playerName) {
        if (raw == null) return "";
        return ChatColor.translateAlternateColorCodes('&', raw.replace("%player%", playerName));
    }
}
