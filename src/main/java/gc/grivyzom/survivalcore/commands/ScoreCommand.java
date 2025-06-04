package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;
import java.util.Arrays;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ScoreCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final List<HelpEntry> helpEntries;
    private final int messagesPerPage = 5;
    private final NamespacedKey lecternKey;
    private final NamespacedKey levelKey;

    private static class HelpEntry {
        String command, message, permission;
        HelpEntry(String cmd, String msg, String perm) {
            this.command = cmd;
            this.message = msg;
            this.permission = perm;
        }
    }

    public ScoreCommand(Main plugin) {
        this.plugin = plugin;
        this.helpEntries = new ArrayList<>();
        loadHelpMessages();

        this.lecternKey = new NamespacedKey(plugin, "is_magic_lectern");
        this.levelKey   = new NamespacedKey(plugin, "lectern_level");
    }

    private void loadHelpMessages() {
        var list = plugin.getConfig().getMapList("help.commands");
        if (list != null) {
            for (Map<?, ?> map : list) {
                String cmd = map.get("command").toString();
                String msg = ChatColor.translateAlternateColorCodes('&', map.get("message").toString());
                String perm = map.get("permission").toString();
                helpEntries.add(new HelpEntry(cmd, msg, perm));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("xpbank")) {
            return handleXpbank(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return handleAdmin(sender, args);
        }
        if (args.length == 0) {
            sendHelp(sender, 1);
            return true;
        }
// /score lectern give <jugador>
//  ─── antes de la comprobación de cooldown ───
        if (args[0].equalsIgnoreCase("lectern")) {
            // /score lectern give <jugador>
            if (args.length == 3 && args[1].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("survivalcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "No tienes permisos.");
                    return true;
                }
                String targetName = args[2];
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Jugador no encontrado o no está online.");
                    return true;
                }
                ItemStack it  = new ItemStack(Material.LECTERN);
                ItemMeta meta = it.getItemMeta();
                meta.getPersistentDataContainer().set(lecternKey, PersistentDataType.BYTE,  (byte)1);
                meta.getPersistentDataContainer().set(levelKey,   PersistentDataType.INTEGER, 1);
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Atril Mágico");
                meta.setLore(Arrays.asList(ChatColor.GRAY + "Haz clic derecho para usar"));
                it.setItemMeta(meta);

                target.getInventory().addItem(it);
                sender.sendMessage(ChatColor.GREEN + "Se ha dado un Atril Mágico a " + targetName + ".");
                return true;
            }
            // Si hay otros subcomandos de lectern ponlos aquí…
        }
        if (args.length == 3 &&
                args[1].equalsIgnoreCase("give")) {

            if (!sender.hasPermission("survivalcore.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permisos.");
                return true;
            }

            String targetName = args[2];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jugador no encontrado o no está online.");
                return true;
            }

            ItemStack it = new ItemStack(Material.LECTERN);
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(lecternKey, PersistentDataType.BYTE, (byte)1);
            meta.getPersistentDataContainer().set(levelKey,    PersistentDataType.INTEGER, 1);
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Atril Mágico");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Haz clic derecho para usar"));
            it.setItemMeta(meta);

            target.getInventory().addItem(it);
            sender.sendMessage(ChatColor.GREEN + "Se ha dado un Atril Mágico a " + targetName + ".");
            return true;
        }
        // Cooldown para operaciones de set
        if (args.length >= 2 && (args[0].equalsIgnoreCase("birthday") || args[0].equalsIgnoreCase("gender"))
                && args[1].equalsIgnoreCase("set") && sender instanceof Player p) {
            if (plugin.getCooldownManager().isOnCooldown(p.getName())) {
                sender.sendMessage(ChatColor.RED + "Espera antes de usar este comando de nuevo.");
                return true;
            }
            plugin.getCooldownManager().setCooldown(p.getName());
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "version" -> sender.sendMessage(ChatColor.WHITE + "Versión del plugin: " + ChatColor.GREEN + plugin.getDescription().getVersion());
            case "reload"  -> reloadConfig(sender);
            case "birthday"-> handleBirthday(sender, args);
            case "gender"  -> handleGender(sender, args);
            case "country" -> handleCountry(sender, args);
            case "help"    -> {
                int page = 1;
                if (args.length >= 2) {
                    try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }
                sendHelp(sender, page);
            }
            default -> sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /" + label + " help");
        }
        return true;
    }

    private boolean handleXpbank(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar este comando.");
            return true;
        }

        // /score xpbank upgrade
        if (args.length == 2 && args[1].equalsIgnoreCase("upgrade")) {
            return upgradeXpBank(p);
        }

        // /score xpbank transfer <jugador> <cantidad>
        if (args.length >= 3 && args[1].equalsIgnoreCase("transfer")) {
            // CORRECCIÓN: Pasar los argumentos correctamente
            // args[0] = "xpbank", args[1] = "transfer", args[2] = jugador, args[3] = cantidad

            if (args.length != 4) {
                p.sendMessage(ChatColor.RED + "Uso: /score xpbank transfer <jugador> <cantidad>");
                p.sendMessage(ChatColor.GRAY + "Transfiere experiencia de tu banco a otro jugador.");
                p.sendMessage(ChatColor.YELLOW + "Ejemplo: /score xpbank transfer Steve 1000");
                return true;
            }

            // Crear array con los argumentos en el formato esperado por handleBankTransfer
            String[] transferArgs = {"transfer", args[2], args[3]};
            return plugin.getXpTransferCommand().handleBankTransfer(sender, transferArgs);
        }

        // /score xpbank give <jugador>
        if (args.length == 3 && args[1].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("survivalcore.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permisos para dar ánforas.");
                return true;
            }
            String targetName = args[2];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jugador no encontrado o no está online.");
                return true;
            }
            ItemStack pot = new ItemStack(Material.DECORATED_POT);
            ItemMeta meta = pot.getItemMeta();
            NamespacedKey potKey = new NamespacedKey(plugin, "is_xp_pot");
            NamespacedKey xpKey  = new NamespacedKey(plugin, "banked_xp");

            meta.getPersistentDataContainer().set(potKey, PersistentDataType.BYTE, (byte)1);
            meta.getPersistentDataContainer().set(xpKey,  PersistentDataType.LONG,  0L);

            meta.setDisplayName(ChatColor.GREEN + "Ánfora de Experiencia");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Una ánfora forjada por antiguos sabios,",
                    ChatColor.GRAY + "capaz de contener fragmentos de tu experiencia.",
                    "",
                    ChatColor.GOLD + "Coloca para usar"
            ));

            pot.setItemMeta(meta);
            target.getInventory().addItem(pot);
            sender.sendMessage(ChatColor.GREEN + "Se ha dado una Ánfora de Experiencia a " + targetName + ".");
            return true;
        }

        // /score xpbank info - Mostrar información del banco
        if (args.length == 2 && args[1].equalsIgnoreCase("info")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String uuid = p.getUniqueId().toString();
                long bankedXp = plugin.getDatabaseManager().getBankedXp(uuid);
                long capacity = plugin.getDatabaseManager().getBankCapacity(uuid);
                long capacityLevels = capacity / 68L;
                long bankedLevels = bankedXp / 68L;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
                    p.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "🏦 Banco de Experiencia" + ChatColor.GOLD + "        ║");
                    p.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
                    p.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "XP Almacenada: " + ChatColor.YELLOW +
                            String.format("%,d", bankedXp) + " XP" + ChatColor.GOLD + " ║");
                    p.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Equivalente: " + ChatColor.AQUA +
                            String.format("%,d", bankedLevels) + " niveles" + ChatColor.GOLD + " ║");
                    p.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Capacidad: " + ChatColor.GREEN +
                            String.format("%,d", capacity) + " XP" + ChatColor.GOLD + " ║");
                    p.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Espacio libre: " + ChatColor.LIGHT_PURPLE +
                            String.format("%,d", capacity - bankedXp) + " XP" + ChatColor.GOLD + " ║");
                    p.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
                    p.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/score xpbank transfer <jugador> <cantidad>" +
                            ChatColor.GRAY + " para transferir");
                    p.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/score xpbank upgrade" +
                            ChatColor.GRAY + " para aumentar capacidad");
                });
            });
            return true;
        }

        // Ayuda por defecto
        p.sendMessage(ChatColor.RED + "Subcomandos de xpbank:");
        p.sendMessage(ChatColor.YELLOW + "/score xpbank info" + ChatColor.GRAY + " - Ver información del banco");
        p.sendMessage(ChatColor.YELLOW + "/score xpbank transfer <jugador> <cantidad>" + ChatColor.GRAY + " - Transferir XP");
        p.sendMessage(ChatColor.YELLOW + "/score xpbank upgrade" + ChatColor.GRAY + " - Mejorar capacidad del banco");
        if (sender.hasPermission("survivalcore.admin")) {
            p.sendMessage(ChatColor.YELLOW + "/score xpbank give <jugador>" + ChatColor.GRAY + " - Dar ánfora (admin)");
        }
        return true;
    }
    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /score admin <profession|abilities> reset <jugador>");
            return true;
        }
        String type = args[1].toLowerCase();
        String action = args[2].toLowerCase();
        String target = args[3];
        String uuid = Bukkit.getOfflinePlayer(target).getUniqueId().toString();
        UserData data = plugin.getDatabaseManager().getUserData(uuid);
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado.");
            return true;
        }
        if (type.equals("profession") && action.equals("reset")) {
            data.setFarmingLevel(1);
            data.setFarmingXP(0);
            data.setMiningLevel(1);
            data.setMiningXP(0);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(data));
            sender.sendMessage(ChatColor.GREEN + "Professions de " + target + " reseteadas.");
        } else if (type.equals("abilities") && action.equals("reset")) {
            data.setAbilities(new HashMap<>());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(data));
            sender.sendMessage(ChatColor.GREEN + "Habilidades de " + target + " reseteadas.");
        } else {
            sender.sendMessage(ChatColor.RED + "Uso: /score admin <profession|abilities> reset <jugador>");
        }
        return true;
    }

    private void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.reload")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos.");
            return;
        }
        sender.sendMessage(ChatColor.WHITE + "Recargando configuración...");
        plugin.reloadConfig();
        plugin.getCropExperienceConfig().reload();
        plugin.updateInternalConfig();
        plugin.getLecternRecipeManager().reloadAsync();
        sender.sendMessage(ChatColor.GREEN + "¡Recarga completa!");
    }

    private void handleBirthday(CommandSender sender, String[] args) {
        DateTimeFormatter inFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        if (args.length == 1 && sender instanceof Player p) {
            UserData d = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
            String fecha = d.getCumpleaños();
            sender.sendMessage((fecha == null)
                    ? ChatColor.RED + "No tienes cumpleaños seteado."
                    : ChatColor.GREEN + "Tu cumpleaños: " + ChatColor.WHITE + fecha);
        } else if (args.length >= 3 && args[1].equalsIgnoreCase("set") && sender instanceof Player p) {
            String raw = args[2];
            LocalDate ld;
            try { ld = LocalDate.parse(raw, inFmt); } catch (DateTimeParseException ex) {
                sender.sendMessage(ChatColor.RED + "Formato inválido. Usa MM/dd/yyyy."); return;
            }
            String iso = ld.toString();
            UserData d = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
            d.setCumpleaños(iso);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(d));
            sender.sendMessage(ChatColor.GREEN + "Cumpleaños seteado: " + ChatColor.WHITE + iso);
        } else if (args.length == 4 && args[1].equalsIgnoreCase("set") && sender.hasPermission("survivalcore.admin")) {
            String raw = args[2]; String target = args[3];
            LocalDate ld;
            try { ld = LocalDate.parse(raw, inFmt); } catch (DateTimeParseException ex) {
                sender.sendMessage(ChatColor.RED + "Formato inválido. Usa MM/dd/yyyy."); return;
            }
            String iso = ld.toString();
            String uuid = Bukkit.getOfflinePlayer(target).getUniqueId().toString();
            UserData d = plugin.getDatabaseManager().getUserData(uuid);
            d.setCumpleaños(iso);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(d));
            sender.sendMessage(ChatColor.GREEN + "Cumple de " + target + ": " + ChatColor.WHITE + iso);
        } else {
            sender.sendMessage(ChatColor.RED + "Uso: /score birthday [set] <MM/dd/yyyy> [jugador]");
        }
    }

    private void handleGender(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player p) {
            UserData d = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
            String g = d.getGenero();
            sender.sendMessage((g == null)
                    ? ChatColor.RED + "Género no asignado."
                    : ChatColor.GREEN + "Tu género: " + ChatColor.WHITE + g);
        } else if (args.length == 3 && args[1].equalsIgnoreCase("set") && sender instanceof Player p) {
            String gen = args[2];
            if (!isValidGender(gen)) {
                sender.sendMessage(ChatColor.RED + "Opciones: Masculino|Femenino"); return;
            }
            String capital = gen.substring(0,1).toUpperCase() + gen.substring(1).toLowerCase();
            UserData d = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
            d.setGenero(capital);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(d));
            sender.sendMessage(ChatColor.GREEN + "Género seteado: " + ChatColor.WHITE + capital);
        } else if (args.length == 4 && args[1].equalsIgnoreCase("set") && sender.hasPermission("survivalcore.admin")) {
            String target = args[2]; String gen = args[3];
            if (!isValidGender(gen)) {
                sender.sendMessage(ChatColor.RED + "Opciones: Masculino|Femenino"); return;
            }
            String capital = gen.substring(0,1).toUpperCase() + gen.substring(1).toLowerCase();
            String uuid = Bukkit.getOfflinePlayer(target).getUniqueId().toString();
            UserData d = plugin.getDatabaseManager().getUserData(uuid);
            d.setGenero(capital);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(d));
            sender.sendMessage(ChatColor.GREEN + "Género de " + target + ": " + ChatColor.WHITE + capital);
        } else {
            sender.sendMessage(ChatColor.RED + "Uso: /score gender [set] <jugador?> <Masculino|Femenino>");
        }
    }

    private boolean isValidGender(String gender) {
        String g = gender.toLowerCase();
        return g.equals("masculino") || g.equals("femenino");
    }

    private void handleCountry(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player p) {
            UserData d = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
            String c = d.getPais();
            if (c == null || c.isEmpty()) {
                c = autoDetectCountry(p); d.setPais(c);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().saveUserData(d));
                sender.sendMessage(ChatColor.GREEN + "País detectado: " + ChatColor.WHITE + c);
            } else {
                sender.sendMessage(ChatColor.GREEN + "Tu país: " + ChatColor.WHITE + c);
            }
        } else if (args.length == 2) {
            String target = args[1];
            String uuid = Bukkit.getOfflinePlayer(target).getUniqueId().toString();
            UserData d = plugin.getDatabaseManager().getUserData(uuid);
            String c = d.getPais();
            sender.sendMessage((c == null || c.isEmpty())
                    ? ChatColor.RED + "No definido para " + target
                    : ChatColor.GREEN + "País de " + target + ": " + ChatColor.WHITE + c);
        }
    }

    private String autoDetectCountry(Player player) {
        return "PaísDetectado";
    }

    private boolean upgradeXpBank(Player p) {
        String uuid = p.getUniqueId().toString();
        var db = plugin.getDatabaseManager();

        long currCapXp = db.getBankCapacity(uuid);        // capacidad en puntos XP
        long currCapLv = currCapXp / 68L;                 // capacidad en niveles
        int  bankLvl   = (int)(currCapLv / 2500);         // cada nivel = 2 500 niveles

        if (bankLvl >= 20) {
            p.sendMessage(ChatColor.GREEN + "¡Tu banco ya está al nivel máximo (20)!");
            return true;
        }

        int  costLv   = 500 + 250 * bankLvl;              // coste progresivo
        long costXp   = costLv * 68L;

        if (p.getLevel() < costLv) {
            p.sendMessage(ChatColor.RED + "Necesitas " + costLv + " niveles para mejorar tu banco.");
            return true;
        }

        int  newBankLvl = bankLvl + 1;
        long newCapLv   = newBankLvl * 2500L;
        long newCapXp   = newCapLv * 68L;

        p.setLevel(p.getLevel() - costLv);
        boolean ok = db.upgradeBankCapacity(uuid, newCapXp);

        if (!ok) {
            p.sendMessage(ChatColor.RED + "Error al mejorar el banco. Inténtalo luego.");
        } else {
            p.sendMessage(ChatColor.GOLD + "¡Banco mejorado a nivel " + newBankLvl +
                    " (" + newCapLv + " niveles)!  Coste: " + costLv + " lv (≈ " + costXp + " XP)");
        }
        return true;
    }

    private void sendHelp(CommandSender sender, int page) {
        var msgs = new ArrayList<String>();
        for (HelpEntry e : helpEntries) {
            if (!(sender instanceof Player) || e.permission.isEmpty() || sender.hasPermission(e.permission)) {
                msgs.add(e.message);
            }
        }
        int total = msgs.size();
        int pages = (int)Math.ceil((double)total / messagesPerPage);
        page = Math.max(1, Math.min(page, pages == 0 ? 1 : pages));
        sender.sendMessage(ChatColor.GREEN + "SurvivalCore Help " + ChatColor.WHITE + "(" + page + "/" + (pages == 0 ? 1 : pages) + ")");
        int start = (page - 1) * messagesPerPage;
        for (int i = start; i < Math.min(start + messagesPerPage, total); i++) {
            sender.sendMessage(msgs.get(i));
        }
        if (sender instanceof Player pl) {
            TextComponent nav = new TextComponent();
            if (page > 1) nav.addExtra(previousButton(page - 1)); else nav.addExtra(disabledPrev());
            nav.addExtra(new TextComponent("   "));
            if (page < pages) nav.addExtra(nextButton(page + 1)); else nav.addExtra(disabledNext());
            pl.spigot().sendMessage(nav);
        }
    }

    private TextComponent previousButton(int page) {
        TextComponent comp = new TextComponent(ChatColor.AQUA + "« Anterior");
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/score help " + page));
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Ir a la página " + page).create()));
        return comp;
    }

    private TextComponent nextButton(int page) {
        TextComponent comp = new TextComponent(ChatColor.AQUA + "Siguiente »");
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/score help " + page));
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Ir a la página " + page).create()));
        return comp;
    }

    private TextComponent disabledPrev() {
        return new TextComponent(ChatColor.GRAY + "« Anterior");
    }

    private TextComponent disabledNext() {
        return new TextComponent(ChatColor.GRAY + "Siguiente »");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Autocompletar para el primer argumento (/score <subcomando>)
            for (String s : List.of("xpbank", "lectern", "version", "reload",
                    "birthday", "gender", "country", "help", "admin")) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("xpbank")) {
            // Autocompletar para /score xpbank <subcomando>
            List<String> xpbankSubcommands = Arrays.asList("give", "transfer", "upgrade");
            for (String sub : xpbankSubcommands) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    completions.add(sub);
                }
            }
        }
        else if (args.length == 2) {String sub = args[0].toLowerCase();
            if (sub.equals("xpbank")) {
                if ("give".startsWith(args[1].toLowerCase())) completions.add("give");
            } else if (List.of("birthday","gender","country").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                }
            } else if (sub.equals("help")) {
                int pages = (int)Math.ceil((double)helpEntries.size()/messagesPerPage);
                for (int i = 1; i <= pages; i++) completions.add(String.valueOf(i));
            } else if (sub.equals("admin")) {
                for (String o : List.of("profession","abilities")) {
                    if (o.startsWith(args[1].toLowerCase())) completions.add(o);
                }
            }
        }
        else if (args.length == 3 && args[0].equalsIgnoreCase("xpbank")) {
            // Autocompletar nombres de jugadores para los subcomandos que lo requieran
            if (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("transfer")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("xpbank")
                && args[1].equalsIgnoreCase("transfer")) {
            // Autocompletar cantidad para transfer (opcional)
            return Arrays.asList("100", "500", "1000");
        }

        else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (List.of("profession","abilities").contains(args[1].toLowerCase())) completions.add("reset");
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[2].equalsIgnoreCase("reset")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[3].toLowerCase())) completions.add(p.getName());
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("xpbank")) {
            for (String s : List.of("upgrade","give")) {
                if (s.startsWith(args[1].toLowerCase())) completions.add(s);
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("lectern")) {
            if ("give".startsWith(args[1].toLowerCase())) completions.add("give");
        }
        else if (args.length == 3 && args[0].equalsIgnoreCase("xpbank") && args[1].equalsIgnoreCase("transfer")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) completions.add(p.getName());
            }
        }
        else if (args.length == 3 &&
                args[0].equalsIgnoreCase("lectern") &&
                args[1].equalsIgnoreCase("give")) {

            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase().startsWith(args[2].toLowerCase()))
                    completions.add(pl.getName());
            }
        }

        return completions;
    }
}
