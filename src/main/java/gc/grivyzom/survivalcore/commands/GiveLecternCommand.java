package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando para dar Lectern Magic a jugadores
 * Uso: /givelectern <jugador> [nivel] [cantidad] [-s]
 *
 * @author Mojang Team (Brocolitx)
 * @version 1.0
 */
public class GiveLecternCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final NamespacedKey keyLectern;
    private final NamespacedKey keyLevel;

    public GiveLecternCommand(Main plugin) {
        this.plugin = plugin;
        this.keyLectern = new NamespacedKey(plugin, "is_magic_lectern");
        this.keyLevel = new NamespacedKey(plugin, "lectern_level");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survivalcore.give.lectern")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        // Parsear argumentos
        String targetName = args[0];
        int level = 1;
        int amount = 1;
        boolean silent = false;

        // Verificar flag silencioso
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        if (argsList.contains("-s")) {
            silent = true;
            argsList.remove("-s");
            args = argsList.toArray(new String[0]);
        }

        // Parsear nivel
        if (args.length > 1) {
            try {
                level = Integer.parseInt(args[1]);
                if (level < 1 || level > 10) {
                    sender.sendMessage(ChatColor.RED + "El nivel debe estar entre 1 y 10.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Nivel inválido: " + args[1]);
                return true;
            }
        }

        // Parsear cantidad
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(ChatColor.RED + "La cantidad debe estar entre 1 y 64.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Cantidad inválida: " + args[2]);
                return true;
            }
        }

        // Buscar jugador objetivo
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + targetName);
            return true;
        }

        // Crear el Lectern Magic
        ItemStack lectern = createMagicLectern(level, amount);

        // Dar el item al jugador
        if (target.getInventory().firstEmpty() == -1) {
            // Inventario lleno - soltar al suelo
            target.getWorld().dropItemNaturally(target.getLocation(), lectern);
            if (!silent) {
                target.sendMessage(ChatColor.GOLD + "📜 Has recibido un " + ChatColor.YELLOW +
                        "Atril Mágico Nivel " + level + ChatColor.GOLD + " (soltado al suelo)");
            }
        } else {
            // Agregar al inventario
            target.getInventory().addItem(lectern);
            if (!silent) {
                target.sendMessage(ChatColor.GOLD + "📜 Has recibido un " + ChatColor.YELLOW +
                        "Atril Mágico Nivel " + level);
            }
        }

        // Mensaje de confirmación al emisor
        if (!silent) {
            sender.sendMessage(ChatColor.GREEN + "✓ Entregado " + amount + "x Atril Mágico Nivel " +
                    level + " a " + target.getName());
        }

        // Log en consola (siempre visible para admins)
        plugin.getLogger().info("[LecternMagic] " + sender.getName() + " dio " + amount +
                "x Atril Mágico Nivel " + level + " a " + target.getName() +
                (silent ? " (silencioso)" : ""));

        return true;
    }

    /**
     * Crea un Atril Mágico con el nivel especificado
     */
    private ItemStack createMagicLectern(int level, int amount) {
        ItemStack lectern = new ItemStack(Material.LECTERN, amount);
        ItemMeta meta = lectern.getItemMeta();

        // Nombre según nivel (estilo Minecraft)
        String name = switch (level) {
            case 1 -> ChatColor.WHITE + "📜 Atril Mágico";
            case 2 -> ChatColor.GREEN + "📜 Atril Mágico Mejorado";
            case 3 -> ChatColor.BLUE + "📜 Atril Mágico Avanzado";
            case 4 -> ChatColor.LIGHT_PURPLE + "📜 Atril Mágico Superior";
            case 5 -> ChatColor.GOLD + "📜 Atril Mágico Épico";
            default -> {
                if (level >= 6 && level <= 8) {
                    yield ChatColor.RED + "📜 Atril Mágico Legendario";
                } else {
                    yield ChatColor.DARK_PURPLE + "📜 Atril Mágico Mítico";
                }
            }
        };

        meta.setDisplayName(name);

        // Lore descriptivo estilo Minecraft
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "═══════════════════");
        lore.add(ChatColor.GRAY + "Fusiona objetos para crear");
        lore.add(ChatColor.GRAY + "nuevos items mágicos");
        lore.add("");
        lore.add(ChatColor.WHITE + "📊 Nivel: " + ChatColor.YELLOW + level + ChatColor.GRAY + "/10");
        lore.add(ChatColor.WHITE + "⚡ Recetas desbloqueadas: " + ChatColor.AQUA + getRecipeCountForLevel(level));
        lore.add("");

        // Descripción según nivel
        switch (level) {
            case 1 -> {
                lore.add(ChatColor.GRAY + "✦ Recetas básicas disponibles");
                lore.add(ChatColor.GRAY + "✦ Fusión simple de materiales");
            }
            case 2, 3 -> {
                lore.add(ChatColor.GREEN + "✦ Recetas mejoradas disponibles");
                lore.add(ChatColor.GREEN + "✦ Mayor eficiencia energética");
            }
            case 4, 5 -> {
                lore.add(ChatColor.BLUE + "✦ Recetas avanzadas disponibles");
                lore.add(ChatColor.BLUE + "✦ Creación de items únicos");
            }
            default -> {
                if (level >= 6) {
                    lore.add(ChatColor.LIGHT_PURPLE + "✦ Recetas legendarias disponibles");
                    lore.add(ChatColor.LIGHT_PURPLE + "✦ Poder arcano supremo");
                }
            }
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "Clic derecho para abrir menú");
        lore.add(ChatColor.YELLOW + "Clic izquierdo para fusionar items");
        lore.add(ChatColor.DARK_PURPLE + "═══════════════════");

        meta.setLore(lore);

        // Datos persistentes
        meta.getPersistentDataContainer().set(keyLectern, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(keyLevel, PersistentDataType.INTEGER, level);

        lectern.setItemMeta(meta);
        return lectern;
    }

    /**
     * Obtiene el número aproximado de recetas para un nivel
     */
    private int getRecipeCountForLevel(int level) {
        // Basado en la progresión típica de Minecraft
        return switch (level) {
            case 1 -> 3;
            case 2 -> 7;
            case 3 -> 12;
            case 4 -> 18;
            case 5 -> 25;
            case 6 -> 33;
            case 7 -> 42;
            case 8 -> 52;
            case 9 -> 63;
            case 10 -> 75;
            default -> level * 8;
        };
    }

    /**
     * Muestra el uso del comando
     */
    private void showUsage(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══ " + ChatColor.WHITE + "COMANDO LECTERN MAGIC" + ChatColor.GOLD + " ═══");
        sender.sendMessage(ChatColor.WHITE + "Uso: " + ChatColor.YELLOW + "/givelectern <jugador> [nivel] [cantidad] [-s]");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "Parámetros:");
        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "jugador" + ChatColor.GRAY + " - Nombre del jugador objetivo");
        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "nivel" + ChatColor.GRAY + " - Nivel del atril (1-10, por defecto: 1)");
        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "cantidad" + ChatColor.GRAY + " - Cantidad a dar (1-64, por defecto: 1)");
        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + "-s" + ChatColor.GRAY + " - Modo silencioso (sin mensajes al jugador)");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "Ejemplos:");
        sender.sendMessage(ChatColor.YELLOW + "  /givelectern Steve" + ChatColor.GRAY + " - Da un Atril Mágico nivel 1");
        sender.sendMessage(ChatColor.YELLOW + "  /givelectern Alex 5" + ChatColor.GRAY + " - Da un Atril Mágico nivel 5");
        sender.sendMessage(ChatColor.YELLOW + "  /givelectern Bob 3 2 -s" + ChatColor.GRAY + " - Da 2 Atriles nivel 3 silenciosamente");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("survivalcore.give.lectern")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Nombres de jugadores online
            String partial = args[0].toLowerCase();
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList()));

        } else if (args.length == 2) {
            // Niveles disponibles
            String partial = args[1];
            for (int i = 1; i <= 10; i++) {
                String level = String.valueOf(i);
                if (level.startsWith(partial)) {
                    completions.add(level);
                }
            }

        } else if (args.length == 3) {
            // Cantidades comunes
            String partial = args[2];
            String[] amounts = {"1", "2", "4", "8", "16", "32", "64"};
            for (String amount : amounts) {
                if (amount.startsWith(partial)) {
                    completions.add(amount);
                }
            }

        } else if (args.length == 4) {
            // Flag silencioso
            if ("-s".startsWith(args[3].toLowerCase())) {
                completions.add("-s");
            }
        }

        return completions;
    }
}