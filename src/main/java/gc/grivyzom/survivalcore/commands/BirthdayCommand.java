package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * /birthday  - pide la fecha por chat (dd-MM-yyyy)
 * /birthday set <dd-MM-yyyy>
 * /birthday info
 * /birthday announce
 */
public class BirthdayCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final Set<UUID> waitingInput = new HashSet<>();

    // Aceptaremos guiones o barras
    private static final DateTimeFormatter DASH_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter SLASH_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public BirthdayCommand(Main plugin) {
        this.plugin = plugin;
    }

    /* =================== COMANDO =================== */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (args.length == 0) {
            waitingInput.add(p.getUniqueId());
            p.sendMessage(ChatColor.AQUA + "Escribe tu fecha de cumpleaños en el chat (formato dd-MM-yyyy).");
            p.sendMessage(ChatColor.GRAY  + "Ejemplo: 05-09-2002");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> sendInfo(p);
            case "set"  -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Uso: /birthday set <dd-MM-yyyy>"); return true; }
                setBirthday(p, args[1]);
            }
            case "announce" -> announce(p);
            default -> p.sendMessage(ChatColor.RED + "Subcomandos: set | info | announce");
        }
        return true;
    }

    /* =================== LÓGICA =================== */

    public void processChat(Player p, String message) {
        if (!waitingInput.remove(p.getUniqueId())) return; // sale si no estaba esperando
        setBirthday(p, message.trim());
    }

    private void setBirthday(Player p, String raw) {
        LocalDate date = tryParse(raw);
        if (date == null) {
            p.sendMessage(ChatColor.RED + "Formato inválido. Usa dd-MM-yyyy o dd/MM/yyyy.");
            return;
        }

        UserData d = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
        d.setCumpleaños(date.toString());   // se guarda como ISO yyyy-MM-dd
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().saveUserData(d));

        p.sendMessage(ChatColor.GREEN + "¡Cumpleaños establecido para el "
                + ChatColor.WHITE + date.format(DASH_FMT) + ChatColor.GREEN + "!");
    }

    /** Intenta parsear con guiones o con barras */
    private LocalDate tryParse(String raw) {
        try { return LocalDate.parse(raw, DASH_FMT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(raw, SLASH_FMT); } catch (DateTimeParseException ignored) {}
        return null;
    }

    private void sendInfo(Player p) {
        String stored = plugin.getDatabaseManager()
                .getUserData(p.getUniqueId().toString())
                .getCumpleaños();

        if (stored == null) {
            p.sendMessage(ChatColor.RED + "Aún no has configurado tu cumpleaños.");
            return;
        }

        LocalDate date = LocalDate.parse(stored);   // ISO yyyy-MM-dd
        p.sendMessage(ChatColor.YELLOW + "Tu cumpleaños es el " +
                ChatColor.WHITE + date.format(DASH_FMT));
    }


    private void announce(Player p) {
        LocalDate today = LocalDate.now();
        UserData data = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
        if (data.getCumpleaños() == null) {
            p.sendMessage(ChatColor.RED + "Primero configura tu cumpleaños.");
            return;
        }
        LocalDate stored = LocalDate.parse(data.getCumpleaños());
        if (stored.getMonth() != today.getMonth() || stored.getDayOfMonth() != today.getDayOfMonth()) {
            p.sendMessage(ChatColor.RED + "¡Hoy no es tu cumpleaños!");
            return;
        }
        String msg = ChatColor.LIGHT_PURPLE + "¡" + p.getName() + " está de cumpleaños hoy! 🎉";
        Bukkit.broadcastMessage(msg);
    }

    /* =================== TAB COMPLETER =================== */
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1)
            return Arrays.asList("set","info","announce").stream()
                    .filter(s2 -> s2.startsWith(args[0].toLowerCase())).toList();
        return Collections.emptyList();
    }
}
