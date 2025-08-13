package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando separado para coordenadas
 * Maneja /coords y /coordenadas
 *
 * @author Brocolitx
 */
public class CoordsCommand implements CommandExecutor {

    private final Main plugin;

    public CoordsCommand(Main plugin) {
        this.plugin = plugin;
        // 🔍 DEBUG: Log cuando se crea el comando
        plugin.getLogger().info("🔍 CoordsCommand inicializado correctamente");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 🔍 DEBUG: Log cuando se ejecuta el comando
        plugin.getLogger().info("🔍 CoordsCommand ejecutado por: " + sender.getName() + " con comando: " + label);

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden obtener sus coordenadas.");
            return true;
        }

        Player player = (Player) sender;

        // Verificar permisos
        if (!player.hasPermission("survivalcore.coords")) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            plugin.getLogger().info("🔍 " + player.getName() + " no tiene permisos para coords");
            return true;
        }

        // 🔍 DEBUG: Log exitoso
        plugin.getLogger().info("✅ Ejecutando comando coords para " + player.getName());

        var location = player.getLocation();

        // Obtener coordenadas redondeadas
        int x = (int) Math.round(location.getX());
        int y = (int) Math.round(location.getY());
        int z = (int) Math.round(location.getZ());
        String world = location.getWorld() != null ? location.getWorld().getName() : "unknown";

        // Formatear las coordenadas para copiar
        String coordsText = x + " " + y + " " + z;

        try {
            // ======= MENSAJE PROFESIONAL, CENTRADO Y SIN "====" =======

            // 1) Título centrado
            String titlePlain = "TUS COORDENADAS";
            String titlePad = padForCenter(titlePlain);
            player.sendMessage(titlePad + ChatColor.WHITE + "" + ChatColor.BOLD + titlePlain);

            // 2) Línea con coordenadas clickeables (centrada)
            String labelPlain = "📍 Coordenadas: ";
            String instrPlain = " (haz clic para copiar)";
            String fullPlainForPad = labelPlain + coordsText + instrPlain;
            String linePad = padForCenter(fullPlainForPad);

            net.md_5.bungee.api.chat.TextComponent padComp = new net.md_5.bungee.api.chat.TextComponent(linePad);

            net.md_5.bungee.api.chat.TextComponent labelComp =
                    new net.md_5.bungee.api.chat.TextComponent(labelPlain);
            labelComp.setColor(net.md_5.bungee.api.ChatColor.WHITE);

            net.md_5.bungee.api.chat.TextComponent clickableCoords =
                    new net.md_5.bungee.api.chat.TextComponent(coordsText);
            clickableCoords.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            clickableCoords.setBold(true);

            // Click -> copiar al portapapeles
            clickableCoords.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,
                    coordsText
            ));

            // Hover
            clickableCoords.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text("§aHaz clic para copiar: §f" + coordsText)
            ));

            net.md_5.bungee.api.chat.TextComponent instrComp =
                    new net.md_5.bungee.api.chat.TextComponent(instrPlain);
            instrComp.setColor(net.md_5.bungee.api.ChatColor.GRAY);

            player.spigot().sendMessage(padComp, labelComp, clickableCoords, instrComp);

            // 3) Línea con el mundo (centrada)
            String worldPlain = "🌍 Mundo: " + world;
            String worldPad = padForCenter(worldPlain);
            player.sendMessage(worldPad + ChatColor.GRAY + "🌍 Mundo: " + ChatColor.GREEN + world);

            // Log en consola
            plugin.getLogger().info("Jugador " + player.getName() + " consultó sus coordenadas: " + coordsText + " en " + world);

        } catch (Exception e) {
            // Fallback: también centrado y sin decoraciones
            String titlePlain = "TUS COORDENADAS";
            String worldPlain = "🌍 Mundo: " + world;

            player.sendMessage(padForCenter(titlePlain) + ChatColor.WHITE + "" + ChatColor.BOLD + titlePlain);
            player.sendMessage(padForCenter("📍 Coordenadas: " + coordsText) + ChatColor.WHITE + "📍 Coordenadas: " + ChatColor.AQUA + coordsText);
            player.sendMessage(padForCenter(worldPlain) + ChatColor.GRAY + "🌍 Mundo: " + ChatColor.GREEN + world);

            plugin.getLogger().warning("Error creando mensaje clickeable para coords: " + e.getMessage());
        }

        return true;
    }

    /**
     * Centrado simple por padding de espacios.
     * Ajusta CHAT_WIDTH_CHARS si quieres más/menos ancho lógico.
     */
    private static String padForCenter(String plain) {
        final int CHAT_WIDTH_CHARS = 60; // ancho lógico aproximado
        if (plain == null) return "";
        int len = plain.length();
        int pad = Math.max((CHAT_WIDTH_CHARS - len) / 2, 0);
        return " ".repeat(pad);
    }
}
