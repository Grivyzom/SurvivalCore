package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.util.SocialMediaValidator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Comando para gestionar redes sociales
 * /social <red> <valor> - Establecer red social
 * /social <red> remove - Eliminar red social
 * /social list - Ver tus redes sociales
 * /social <jugador> - Ver redes de otro jugador
 *
 * @author Brocolitx
 */
public class SocialCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final SocialMediaValidator validator;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // Redes sociales disponibles
    private final List<String> socialNetworks = Arrays.asList(
            "discord", "instagram", "github", "tiktok", "twitch", "kick", "youtube"
    );

    public SocialCommand(Main plugin) {
        this.plugin = plugin;
        this.validator = new SocialMediaValidator(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }

        // Sin argumentos - mostrar ayuda
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        // /social list - Ver tus redes
        if (subcommand.equals("list")) {
            showOwnSocials(player);
            return true;
        }

        // /social help - Mostrar ayuda
        if (subcommand.equals("help") || subcommand.equals("ayuda")) {
            showHelp(player);
            return true;
        }

        // /social <jugador> - Ver redes de otro jugador
        if (args.length == 1 && !socialNetworks.contains(subcommand)) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[0]);
                return true;
            }
            showPlayerSocials(player, target);
            return true;
        }

        // /social <red> <valor/remove> - Gestionar red social
        if (socialNetworks.contains(subcommand)) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Uso: /social " + subcommand + " <valor|remove>");
                return true;
            }

            // Verificar cooldown
            if (isOnCooldown(player)) {
                player.sendMessage(ChatColor.RED + "Debes esperar un momento antes de cambiar otra red social.");
                return true;
            }

            String value = args[1];

            // Remover red social
            if (value.equalsIgnoreCase("remove") || value.equalsIgnoreCase("eliminar")) {
                removeSocial(player, subcommand);
                return true;
            }

            // Concatenar todos los argumentos después del primero para el valor
            StringBuilder fullValue = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) fullValue.append(" ");
                fullValue.append(args[i]);
            }

            setSocial(player, subcommand, fullValue.toString());
            return true;
        }

        // Comando no reconocido
        player.sendMessage(ChatColor.RED + "Comando no reconocido. Usa /social help para ver la ayuda.");
        return true;
    }

    /**
     * Establece una red social para el jugador
     */
    private void setSocial(Player player, String network, String value) {
        // Validar formato
        if (!validator.isValid(network, value)) {
            player.sendMessage(ChatColor.RED + "❌ Formato inválido para " + getNetworkDisplayName(network));
            player.sendMessage(ChatColor.YELLOW + "Ejemplos válidos:");
            showExamples(player, network);
            return;
        }

        // Formatear el valor
        String formattedValue = validator.format(network, value);

        // Actualizar en base de datos
        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        switch (network) {
            case "discord" -> userData.setDiscord(formattedValue);
            case "instagram" -> userData.setInstagram(formattedValue);
            case "github" -> userData.setGithub(formattedValue);
            case "tiktok" -> userData.setTiktok(formattedValue);
            case "twitch" -> userData.setTwitch(formattedValue);
            case "kick" -> userData.setKick(formattedValue);
            case "youtube" -> userData.setYoutube(formattedValue);
        }

        // Guardar asíncronamente
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().saveUserData(userData);
        });

        // Establecer cooldown
        setCooldown(player);

        // Mensaje de éxito
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✅ " + getNetworkDisplayName(network) + " establecido correctamente:");
        player.sendMessage(ChatColor.WHITE + "   " + formattedValue);
        player.sendMessage("");
    }

    /**
     * Elimina una red social del jugador
     */
    private void removeSocial(Player player, String network) {
        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        // Verificar si tiene esa red configurada
        String currentValue = null;
        switch (network) {
            case "discord" -> currentValue = userData.getDiscord();
            case "instagram" -> currentValue = userData.getInstagram();
            case "github" -> currentValue = userData.getGithub();
            case "tiktok" -> currentValue = userData.getTiktok();
            case "twitch" -> currentValue = userData.getTwitch();
            case "kick" -> currentValue = userData.getKick();
            case "youtube" -> currentValue = userData.getYoutube();
        }

        if (currentValue == null || currentValue.isEmpty()) {
            player.sendMessage(ChatColor.RED + "❌ No tienes " + getNetworkDisplayName(network) + " configurado.");
            return;
        }

        // Eliminar
        switch (network) {
            case "discord" -> userData.setDiscord(null);
            case "instagram" -> userData.setInstagram(null);
            case "github" -> userData.setGithub(null);
            case "tiktok" -> userData.setTiktok(null);
            case "twitch" -> userData.setTwitch(null);
            case "kick" -> userData.setKick(null);
            case "youtube" -> userData.setYoutube(null);
        }

        // Guardar asíncronamente
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().saveUserData(userData);
        });

        player.sendMessage(ChatColor.RED + "✗ " + getNetworkDisplayName(network) + " eliminado de tu perfil.");
    }

    /**
     * Muestra las redes sociales propias
     */
    private void showOwnSocials(Player player) {
        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "       TUS REDES SOCIALES");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage("");

        int configured = 0;

        if (userData.getDiscord() != null && !userData.getDiscord().isEmpty()) {
            player.sendMessage(ChatColor.BLUE + "  Discord: " + ChatColor.WHITE + userData.getDiscord());
            configured++;
        }

        if (userData.getInstagram() != null && !userData.getInstagram().isEmpty()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "  Instagram: " + ChatColor.WHITE + userData.getInstagram());
            configured++;
        }

        if (userData.getGithub() != null && !userData.getGithub().isEmpty()) {
            player.sendMessage(ChatColor.DARK_GRAY + "  GitHub: " + ChatColor.WHITE + userData.getGithub());
            configured++;
        }

        if (userData.getTiktok() != null && !userData.getTiktok().isEmpty()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "  TikTok: " + ChatColor.WHITE + userData.getTiktok());
            configured++;
        }

        if (userData.getTwitch() != null && !userData.getTwitch().isEmpty()) {
            player.sendMessage(ChatColor.DARK_PURPLE + "  Twitch: " + ChatColor.WHITE + userData.getTwitch());
            configured++;
        }

        if (userData.getKick() != null && !userData.getKick().isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "  Kick: " + ChatColor.WHITE + userData.getKick());
            configured++;
        }

        if (userData.getYoutube() != null && !userData.getYoutube().isEmpty()) {
            player.sendMessage(ChatColor.RED + "  YouTube: " + ChatColor.WHITE + userData.getYoutube());
            configured++;
        }

        if (configured == 0) {
            player.sendMessage(ChatColor.GRAY + "  No tienes redes sociales configuradas.");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "  Usa /social <red> <valor> para agregar una.");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Redes configuradas: " + ChatColor.YELLOW + configured + "/7");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    /**
     * Muestra las redes sociales de otro jugador
     */
    private void showPlayerSocials(Player viewer, Player target) {
        if (viewer.equals(target)) {
            showOwnSocials(viewer);
            return;
        }

        UserData userData = plugin.getDatabaseManager().getUserData(target.getUniqueId().toString());

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        viewer.sendMessage(ChatColor.YELLOW + "   REDES DE " + target.getName().toUpperCase());
        viewer.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        viewer.sendMessage("");

        int configured = 0;

        if (userData.getDiscord() != null && !userData.getDiscord().isEmpty()) {
            viewer.sendMessage(ChatColor.BLUE + "  Discord: " + ChatColor.WHITE + userData.getDiscord());
            configured++;
        }

        if (userData.getInstagram() != null && !userData.getInstagram().isEmpty()) {
            viewer.sendMessage(ChatColor.LIGHT_PURPLE + "  Instagram: " + ChatColor.WHITE + userData.getInstagram());
            configured++;
        }

        if (userData.getGithub() != null && !userData.getGithub().isEmpty()) {
            viewer.sendMessage(ChatColor.DARK_GRAY + "  GitHub: " + ChatColor.WHITE + userData.getGithub());
            configured++;
        }

        if (userData.getTiktok() != null && !userData.getTiktok().isEmpty()) {
            viewer.sendMessage(ChatColor.LIGHT_PURPLE + "  TikTok: " + ChatColor.WHITE + userData.getTiktok());
            configured++;
        }

        if (userData.getTwitch() != null && !userData.getTwitch().isEmpty()) {
            viewer.sendMessage(ChatColor.DARK_PURPLE + "  Twitch: " + ChatColor.WHITE + userData.getTwitch());
            configured++;
        }

        if (userData.getKick() != null && !userData.getKick().isEmpty()) {
            viewer.sendMessage(ChatColor.GREEN + "  Kick: " + ChatColor.WHITE + userData.getKick());
            configured++;
        }

        if (userData.getYoutube() != null && !userData.getYoutube().isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "  YouTube: " + ChatColor.WHITE + userData.getYoutube());
            configured++;
        }

        if (configured == 0) {
            viewer.sendMessage(ChatColor.GRAY + "  Este jugador no tiene redes sociales configuradas.");
        }

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    /**
     * Muestra la ayuda del comando
     */
    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "     COMANDOS DE REDES SOCIALES");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Comandos disponibles:");
        player.sendMessage(ChatColor.WHITE + "  /social list" + ChatColor.GRAY + " - Ver tus redes sociales");
        player.sendMessage(ChatColor.WHITE + "  /social <jugador>" + ChatColor.GRAY + " - Ver redes de otro jugador");
        player.sendMessage(ChatColor.WHITE + "  /social <red> <valor>" + ChatColor.GRAY + " - Establecer red social");
        player.sendMessage(ChatColor.WHITE + "  /social <red> remove" + ChatColor.GRAY + " - Eliminar red social");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Redes disponibles:");
        player.sendMessage(ChatColor.GRAY + "  discord, instagram, github, tiktok,");
        player.sendMessage(ChatColor.GRAY + "  twitch, kick, youtube");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Ejemplos:");
        player.sendMessage(ChatColor.GRAY + "  /social discord Usuario#1234");
        player.sendMessage(ChatColor.GRAY + "  /social instagram @mi_usuario");
        player.sendMessage(ChatColor.GRAY + "  /social youtube @MiCanal");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    /**
     * Muestra ejemplos para una red específica
     */
    private void showExamples(Player player, String network) {
        switch (network) {
            case "discord" -> {
                player.sendMessage(ChatColor.GRAY + "  • Usuario#1234");
                player.sendMessage(ChatColor.GRAY + "  • @usuario");
                player.sendMessage(ChatColor.GRAY + "  • discord.com/users/123456789");
            }
            case "instagram" -> {
                player.sendMessage(ChatColor.GRAY + "  • @usuario");
                player.sendMessage(ChatColor.GRAY + "  • instagram.com/usuario");
            }
            case "github" -> {
                player.sendMessage(ChatColor.GRAY + "  • usuario");
                player.sendMessage(ChatColor.GRAY + "  • github.com/usuario");
            }
            case "tiktok" -> {
                player.sendMessage(ChatColor.GRAY + "  • @usuario");
                player.sendMessage(ChatColor.GRAY + "  • tiktok.com/@usuario");
            }
            case "twitch" -> {
                player.sendMessage(ChatColor.GRAY + "  • usuario");
                player.sendMessage(ChatColor.GRAY + "  • twitch.tv/usuario");
            }
            case "kick" -> {
                player.sendMessage(ChatColor.GRAY + "  • usuario");
                player.sendMessage(ChatColor.GRAY + "  • kick.com/usuario");
            }
            case "youtube" -> {
                player.sendMessage(ChatColor.GRAY + "  • @usuario");
                player.sendMessage(ChatColor.GRAY + "  • youtube.com/@usuario");
                player.sendMessage(ChatColor.GRAY + "  • youtube.com/c/usuario");
            }
        }
    }

    /**
     * Obtiene el nombre display de una red
     */
    private String getNetworkDisplayName(String network) {
        return switch (network) {
            case "discord" -> "Discord";
            case "instagram" -> "Instagram";
            case "github" -> "GitHub";
            case "tiktok" -> "TikTok";
            case "twitch" -> "Twitch";
            case "kick" -> "Kick";
            case "youtube" -> "YouTube";
            default -> network;
        };
    }

    /**
     * Verifica si el jugador está en cooldown
     */
    private boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }

        long lastUse = cooldowns.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldownTime = 3000; // 3 segundos

        return (now - lastUse) < cooldownTime;
    }

    /**
     * Establece el cooldown para el jugador
     */
    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Primer argumento: redes sociales, list, help o jugadores
            completions.addAll(socialNetworks);
            completions.add("list");
            completions.add("help");

            // Agregar jugadores online
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }

            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2 && socialNetworks.contains(args[0].toLowerCase())) {
            // Segundo argumento para redes sociales
            completions.add("remove");
            completions.add("eliminar");

            // Sugerencias según la red
            switch (args[0].toLowerCase()) {
                case "discord" -> completions.add("Usuario#1234");
                case "instagram", "tiktok" -> completions.add("@usuario");
                case "github", "twitch", "kick" -> completions.add("usuario");
                case "youtube" -> {
                    completions.add("@usuario");
                    completions.add("youtube.com/@usuario");
                }
            }

            return filterCompletions(completions, args[1]);
        }

        return Collections.emptyList();
    }

    /**
     * Filtra las completaciones según el input
     */
    private List<String> filterCompletions(List<String> completions, String input) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .sorted()
                .toList();
    }
}