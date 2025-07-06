package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando para obtener ánforas de experiencia (xpbank)
 * Permite a los administradores crear y distribuir bancos de experiencia portátiles
 *
 * @author Brocolitx
 * @version 1.0
 */
public class XpBankCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final NamespacedKey potKey;
    private final NamespacedKey xpKey;

    public XpBankCommand(Main plugin) {
        this.plugin = plugin;
        this.potKey = new NamespacedKey(plugin, "is_xp_pot");
        this.xpKey = new NamespacedKey(plugin, "banked_xp");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (!player.hasPermission("survivalcore.xpbank.give")) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            // Dar ánfora básica vacía
            giveXpBank(player, 1, 0);
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ayuda")) {
                showHelp(player);
                return true;
            }

            // /xpbank <cantidad>
            try {
                int amount = Integer.parseInt(args[0]);
                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
                    return true;
                }
                if (amount > 64) {
                    player.sendMessage(ChatColor.RED + "La cantidad máxima es 64 por comando.");
                    return true;
                }
                giveXpBank(player, amount, 0);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "'" + args[0] + "' no es un número válido.");
                player.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/xpbank help" + ChatColor.GRAY + " para ver la ayuda.");
                return true;
            }
        } else if (args.length == 2) {
            // /xpbank <cantidad> <xp>
            try {
                int amount = Integer.parseInt(args[0]);
                long xp = Long.parseLong(args[1]);

                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
                    return true;
                }
                if (amount > 64) {
                    player.sendMessage(ChatColor.RED + "La cantidad máxima es 64 por comando.");
                    return true;
                }

                if (xp < 0) {
                    player.sendMessage(ChatColor.RED + "La XP no puede ser negativa.");
                    return true;
                }

                // Validar XP máxima razonable (equivalente a 10,000 niveles)
                if (xp > 680000) {
                    player.sendMessage(ChatColor.RED + "La XP máxima por ánfora es 680,000 (≈10,000 niveles).");
                    return true;
                }

                giveXpBank(player, amount, xp);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Uno de los valores no es un número válido.");
                player.sendMessage(ChatColor.GRAY + "Formato: " + ChatColor.WHITE + "/xpbank <cantidad> <xp>");
                return true;
            }
        } else if (args.length == 3) {
            // /xpbank give <jugador> <cantidad>
            if (args[0].equalsIgnoreCase("give")) {
                if (!player.hasPermission("survivalcore.xpbank.give.others")) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para dar ánforas a otros jugadores.");
                    return true;
                }

                String targetName = args[1];
                Player target = Bukkit.getPlayerExact(targetName);

                if (target == null) {
                    player.sendMessage(ChatColor.RED + "El jugador '" + targetName + "' no está online.");
                    return true;
                }

                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0 || amount > 64) {
                        player.sendMessage(ChatColor.RED + "La cantidad debe estar entre 1 y 64.");
                        return true;
                    }

                    giveXpBankToPlayer(player, target, amount, 0);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "'" + args[2] + "' no es un número válido.");
                    return true;
                }
            } else {
                showHelp(player);
            }
        } else if (args.length == 4) {
            // /xpbank give <jugador> <cantidad> <xp>
            if (args[0].equalsIgnoreCase("give")) {
                if (!player.hasPermission("survivalcore.xpbank.give.others")) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para dar ánforas a otros jugadores.");
                    return true;
                }

                String targetName = args[1];
                Player target = Bukkit.getPlayerExact(targetName);

                if (target == null) {
                    player.sendMessage(ChatColor.RED + "El jugador '" + targetName + "' no está online.");
                    return true;
                }

                try {
                    int amount = Integer.parseInt(args[2]);
                    long xp = Long.parseLong(args[3]);

                    if (amount <= 0 || amount > 64) {
                        player.sendMessage(ChatColor.RED + "La cantidad debe estar entre 1 y 64.");
                        return true;
                    }

                    if (xp < 0 || xp > 680000) {
                        player.sendMessage(ChatColor.RED + "La XP debe estar entre 0 y 680,000.");
                        return true;
                    }

                    giveXpBankToPlayer(player, target, amount, xp);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Valores numéricos inválidos.");
                    return true;
                }
            } else {
                showHelp(player);
            }
        } else {
            showHelp(player);
        }

        return true;
    }

    /**
     * Crea y da ánforas de experiencia al jugador que ejecuta el comando
     */
    private void giveXpBank(Player player, int amount, long storedXp) {
        ItemStack xpPot = createXpBankItem(storedXp);
        xpPot.setAmount(amount);

        // Intentar añadir al inventario
        var leftover = player.getInventory().addItem(xpPot);

        if (leftover.isEmpty()) {
            // Todo se añadió correctamente
            showSuccessMessage(player, amount, storedXp, false);

            // Efectos visuales y sonoros
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        } else {
            // Algunas no se pudieron añadir
            int given = amount - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            player.sendMessage(ChatColor.YELLOW + "Se añadieron " + given + " ánforas al inventario.");

            if (given > 0) {
                showSuccessMessage(player, given, storedXp, false);
            }

            // Soltar las que no cupieron
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }

            player.sendMessage(ChatColor.GRAY + "Las ánforas restantes se soltaron por falta de espacio.");
        }

        // Log para administradores
        plugin.getLogger().info(String.format("XpBank: %s obtuvo %d ánforas con %d XP cada una",
                player.getName(), amount, storedXp));
    }

    /**
     * Da ánforas de experiencia a otro jugador
     */
    private void giveXpBankToPlayer(Player giver, Player target, int amount, long storedXp) {
        ItemStack xpPot = createXpBankItem(storedXp);
        xpPot.setAmount(amount);

        // Intentar añadir al inventario del objetivo
        var leftover = target.getInventory().addItem(xpPot);

        if (leftover.isEmpty()) {
            // Todo se añadió correctamente
            showSuccessMessage(giver, amount, storedXp, true, target.getName());
            showSuccessMessage(target, amount, storedXp, false);

            // Efectos para ambos jugadores
            giver.playSound(giver.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.0f);
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        } else {
            // Algunas no se pudieron añadir
            int given = amount - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();

            giver.sendMessage(ChatColor.YELLOW + "Se añadieron " + given + " ánforas al inventario de " +
                    target.getName() + ".");

            if (given > 0) {
                showSuccessMessage(target, given, storedXp, false);
            }

            // Soltar las que no cupieron cerca del jugador objetivo
            for (ItemStack item : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }

            giver.sendMessage(ChatColor.GRAY + "Las ánforas restantes se soltaron por falta de espacio.");
            target.sendMessage(ChatColor.GRAY + "Algunas ánforas se soltaron por falta de espacio en tu inventario.");
        }

        // Log para administradores
        plugin.getLogger().info(String.format("XpBank: %s dio %d ánforas con %d XP cada una a %s",
                giver.getName(), amount, storedXp, target.getName()));
    }

    /**
     * Muestra mensaje de éxito personalizado
     */
    private void showSuccessMessage(Player player, int amount, long storedXp, boolean isGiving) {
        showSuccessMessage(player, amount, storedXp, isGiving, null);
    }

    private void showSuccessMessage(Player player, int amount, long storedXp, boolean isGiving, String targetName) {
        String amountText = amount == 1 ? "ánfora" : "ánforas";

        if (storedXp > 0) {
            long levels = storedXp / 68L;
            if (isGiving && targetName != null) {
                player.sendMessage(ChatColor.GREEN + "Has dado " + ChatColor.YELLOW + amount +
                        ChatColor.GREEN + " " + amountText + " con " + ChatColor.GOLD + storedXp +
                        " XP" + ChatColor.GREEN + " (" + levels + " niveles) cada una a " +
                        ChatColor.AQUA + targetName + ChatColor.GREEN + ".");
            } else {
                player.sendMessage(ChatColor.GREEN + "Has recibido " + ChatColor.YELLOW + amount +
                        ChatColor.GREEN + " " + amountText + " con " + ChatColor.GOLD + storedXp +
                        " XP" + ChatColor.GREEN + " (" + levels + " niveles) cada una.");
            }
        } else {
            if (isGiving && targetName != null) {
                player.sendMessage(ChatColor.GREEN + "Has dado " + ChatColor.YELLOW + amount +
                        ChatColor.GREEN + " " + amountText + " vacía(s) a " + ChatColor.AQUA + targetName + ChatColor.GREEN + ".");
            } else {
                player.sendMessage(ChatColor.GREEN + "Has recibido " + ChatColor.YELLOW + amount +
                        ChatColor.GREEN + " " + amountText + " vacía(s).");
            }
        }
    }

    /**
     * Crea el ítem ánfora de experiencia
     */
    private ItemStack createXpBankItem(long storedXp) {
        ItemStack pot = new ItemStack(Material.DECORATED_POT);
        ItemMeta meta = pot.getItemMeta();

        // Nombre del ítem
        meta.setDisplayName(ChatColor.GREEN + "Ánfora de Experiencia");

        // Lore del ítem
        if (storedXp > 0) {
            long levels = storedXp / 68L;
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Una ánfora forjada por antiguos sabios,",
                    ChatColor.GRAY + "capaz de contener fragmentos de tu experiencia.",
                    "",
                    ChatColor.GOLD + "XP almacenada: " + ChatColor.YELLOW + String.format("%,d", storedXp),
                    ChatColor.GOLD + "Niveles aprox: " + ChatColor.YELLOW + String.format("%,d", levels),
                    "",
                    ChatColor.GREEN + "✓ Lista para usar",
                    ChatColor.GOLD + "Coloca para usar"
            ));
        } else {
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Una ánfora forjada por antiguos sabios,",
                    ChatColor.GRAY + "capaz de contener fragmentos de tu experiencia.",
                    "",
                    ChatColor.YELLOW + "Ánfora vacía - Lista para almacenar XP",
                    "",
                    ChatColor.GOLD + "Coloca para usar"
            ));
        }

        // Datos persistentes (NBT) - Compatibles con el sistema existente
        meta.getPersistentDataContainer().set(potKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(xpKey, PersistentDataType.LONG, storedXp);

        pot.setItemMeta(meta);
        return pot;
    }

    /**
     * Muestra la ayuda del comando
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔═══════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "🏺 Comando XpBank" + ChatColor.GOLD + "              ║");
        player.sendMessage(ChatColor.GOLD + "╠═══════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Comandos básicos:" + ChatColor.GOLD + "               ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "/xpbank" + ChatColor.GRAY + " - 1 ánfora vacía" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "/xpbank <cant>" + ChatColor.GRAY + " - Cantidad específica" + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "/xpbank <cant> <xp>" + ChatColor.GRAY + " - Con XP" + ChatColor.GOLD + "      ║");
        player.sendMessage(ChatColor.GOLD + "║                                   ║");

        if (player.hasPermission("survivalcore.xpbank.give.others")) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Comandos para otros:" + ChatColor.GOLD + "             ║");
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "/xpbank give <jugador> <cant>" + ChatColor.GOLD + "    ║");
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "/xpbank give <jugador> <cant> <xp>" + ChatColor.GOLD + " ║");
            player.sendMessage(ChatColor.GOLD + "║                                   ║");
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Ejemplos:" + ChatColor.GOLD + "                        ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "/xpbank 5" + ChatColor.WHITE + " - 5 ánforas vacías" + ChatColor.GOLD + "      ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "/xpbank 1 6800" + ChatColor.WHITE + " - 1 ánfora (100 lv)" + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "/xpbank 3 1000" + ChatColor.WHITE + " - 3 ánforas (15 lv)" + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Las ánforas funcionan igual que el sistema de banco:");
        player.sendMessage(ChatColor.GRAY + "   • Colócalas para abrir el menú");
        player.sendMessage(ChatColor.GRAY + "   • Shift+Clic izquierdo para depositar todo");
        player.sendMessage(ChatColor.GRAY + "   • Shift+Clic derecho para retirar todo");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        Player player = (Player) sender;

        // ✅ FIX: Crear lista mutable desde el inicio
        try {
            if (args.length == 1) {
                // Lista base de sugerencias
                List<String> completions = new ArrayList<>(Arrays.asList("1", "2", "3", "5", "10", "16", "32", "64", "help"));

                // ✅ Ahora podemos agregar elementos sin problemas
                if (player.hasPermission("survivalcore.xpbank.give.others")) {
                    completions.add("give");
                }

                return completions.stream()
                        .filter(completion -> completion.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());

            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("give") && player.hasPermission("survivalcore.xpbank.give.others")) {
                    // Autocompletar nombres de jugadores online
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else {
                    // Sugerencias de XP comunes
                    return Arrays.asList("0", "680", "1360", "3400", "6800", "13600", "34000", "68000").stream()
                            .filter(xp -> xp.startsWith(args[1]))
                            .collect(Collectors.toList());
                }

            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("give")) {
                    // Cantidad para give command
                    return Arrays.asList("1", "2", "3", "5", "10", "16", "32", "64").stream()
                            .filter(amount -> amount.startsWith(args[2]))
                            .collect(Collectors.toList());
                }

            } else if (args.length == 4) {
                if (args[0].equalsIgnoreCase("give")) {
                    // XP para give command
                    return Arrays.asList("0", "680", "1360", "3400", "6800", "13600", "34000", "68000").stream()
                            .filter(xp -> xp.startsWith(args[3]))
                            .collect(Collectors.toList());
                }
            }

        } catch (Exception e) {
            // ✅ En caso de cualquier error, devolver lista vacía para evitar crashes
            plugin.getLogger().warning("Error en tab-complete de XpBankCommand: " + e.getMessage());
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}