package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GUI mejorado para selección de género con cooldown
 * Configurable desde guis.yml
 *
 * @author Brocolitx
 * @version 2.0
 */
public class GeneroGUI {

    private static Main plugin;
    private static ConfigurationSection config;

    // Cache de configuración
    private static String inventoryTitle;
    private static int inventorySize;
    private static boolean enabled;

    public static void initialize(Main mainPlugin) {
        plugin = mainPlugin;
        reloadConfig();
    }

    public static void reloadConfig() {
        // Cargar configuración desde guis.yml
        if (plugin.getGuisConfig() != null) {
            config = plugin.getGuisConfig().getConfigurationSection("gender_gui");
            if (config != null) {
                enabled = config.getBoolean("enabled", true);
                inventoryTitle = ChatColor.translateAlternateColorCodes('&',
                        config.getString("title", "&d&lSelecciona tu Género"));
                inventorySize = config.getInt("size", 27);
            }
        }
    }

    public static void open(Player player, Main plugin) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "El sistema de género está deshabilitado.");
            return;
        }

        // Verificar cooldown
        if (!plugin.getDatabaseManager().canChangeGender(player.getUniqueId().toString())) {
            if (!player.hasPermission("survivalcore.gender.bypass")) {
                String remaining = plugin.getDatabaseManager().getGenderCooldownRemaining(player.getUniqueId().toString());
                player.sendMessage(ChatColor.RED + "❌ Debes esperar " + ChatColor.YELLOW + remaining +
                        ChatColor.RED + " antes de cambiar tu género nuevamente.");
                playSound(player, "cooldown");
                return;
            }
        }

        Inventory inv = Bukkit.createInventory(null, inventorySize, inventoryTitle);

        // Obtener datos actuales del jugador
        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        String currentGender = userData.getGenero() != null ? userData.getGenero() : "No establecido";

        // Cargar items desde configuración
        loadItems(inv, player, currentGender);

        player.openInventory(inv);
        playSound(player, "open");
    }

    private static void loadItems(Inventory inv, Player player, String currentGender) {
        if (config == null) return;

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return;

        // Cargar bordes
        ConfigurationSection border = items.getConfigurationSection("border");
        if (border != null) {
            ItemStack borderItem = createItemFromConfig(border, player);
            List<Integer> slots = border.getIntegerList("slots");
            for (int slot : slots) {
                if (slot < inv.getSize()) {
                    inv.setItem(slot, borderItem);
                }
            }
        }

        // Cargar opción Masculino
        loadGenderOption(inv, items.getConfigurationSection("masculino"), player, currentGender.equals("Masculino"));

        // Cargar opción Femenino
        loadGenderOption(inv, items.getConfigurationSection("femenino"), player, currentGender.equals("Femenino"));

        // Cargar opción Otro
        loadGenderOption(inv, items.getConfigurationSection("otro"), player, currentGender.equals("Otro"));

        // Cargar información
        ConfigurationSection info = items.getConfigurationSection("info");
        if (info != null) {
            ItemStack infoItem = createInfoItem(info, player, currentGender);
            int slot = info.getInt("slot", 22);
            if (slot < inv.getSize()) {
                inv.setItem(slot, infoItem);
            }
        }
    }

    private static void loadGenderOption(Inventory inv, ConfigurationSection section, Player player, boolean isCurrent) {
        if (section == null) return;

        int slot = section.getInt("slot");
        if (slot >= inv.getSize()) return;

        ItemStack item = createItemFromConfig(section, player);

        // Añadir brillo si es la opción actual
        if (isCurrent) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GREEN + "✓ Género actual");
                meta.setLore(lore);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }

        inv.setItem(slot, item);
    }

    private static ItemStack createItemFromConfig(ConfigurationSection section, Player player) {
        Material material = Material.valueOf(section.getString("material", "STONE").toUpperCase());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nombre
            String name = section.getString("name", "");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(name, player)));

            // Lore
            List<String> lore = section.getStringList("lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&',
                        replacePlaceholders(line, player)));
            }
            meta.setLore(coloredLore);

            // Brillo
            if (section.getBoolean("glow", false)) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createInfoItem(ConfigurationSection section, Player player, String currentGender) {
        Material material = Material.valueOf(section.getString("material", "BOOK").toUpperCase());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = section.getString("name", "&6&lInformación");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = section.getStringList("lore");
            List<String> coloredLore = new ArrayList<>();

            for (String line : lore) {
                // Reemplazar placeholders
                line = line.replace("{gender}", currentGender);

                // Calcular último cambio
                UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
                if (userData.getUltimoCambioGenero() > 0) {
                    long lastChange = userData.getUltimoCambioGenero();
                    long now = System.currentTimeMillis();
                    long diff = now - lastChange;

                    long days = diff / (24 * 60 * 60 * 1000);
                    if (days == 0) {
                        line = line.replace("{last_change}", "Hoy");
                    } else if (days == 1) {
                        line = line.replace("{last_change}", "Ayer");
                    } else {
                        line = line.replace("{last_change}", "Hace " + days + " días");
                    }
                } else {
                    line = line.replace("{last_change}", "Nunca");
                }

                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            meta.setLore(coloredLore);

            if (section.getBoolean("glow", false)) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private static String replacePlaceholders(String text, Player player) {
        text = text.replace("{player}", player.getName());
        text = text.replace("{displayname}", player.getDisplayName());
        return text;
    }

    /**
     * Maneja el click en un item del inventario
     */
    public static void handleClick(Player player, ItemStack item, int slot, Main plugin) {
        if (item == null || !item.hasItemMeta()) return;

        // Buscar qué item fue clickeado basándose en el slot
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return;

        // Verificar cada opción de género
        handleGenderSelection(player, items.getConfigurationSection("masculino"), slot, "Masculino");
        handleGenderSelection(player, items.getConfigurationSection("femenino"), slot, "Femenino");
        handleGenderSelection(player, items.getConfigurationSection("otro"), slot, "Otro");
    }

    private static void handleGenderSelection(Player player, ConfigurationSection section, int clickedSlot, String gender) {
        if (section == null) return;

        int slot = section.getInt("slot");
        if (slot != clickedSlot) return;

        // Verificar cooldown nuevamente
        if (!plugin.getDatabaseManager().canChangeGender(player.getUniqueId().toString())) {
            if (!player.hasPermission("survivalcore.gender.bypass")) {
                String remaining = plugin.getDatabaseManager().getGenderCooldownRemaining(player.getUniqueId().toString());
                player.sendMessage(ChatColor.RED + "❌ Debes esperar " + ChatColor.YELLOW + remaining +
                        ChatColor.RED + " antes de cambiar tu género nuevamente.");
                playSound(player, "cooldown");
                player.closeInventory();
                return;
            }
        }

        // Actualizar género
        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        userData.setGenero(gender);
        userData.setUltimoCambioGenero(System.currentTimeMillis());

        // Guardar asíncronamente
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().saveUserData(userData);
        });

        // Ejecutar acciones configuradas
        executeActions(player, section.getConfigurationSection("actions.left_click"), gender);

        player.closeInventory();
        playSound(player, "select");

        // Mensaje de confirmación
        player.sendMessage(ChatColor.GREEN + "✅ Has seleccionado " +
                ChatColor.YELLOW + gender + ChatColor.GREEN + " como tu género.");

        // Disparar evento personalizado
        plugin.firePlayerGenderChangeEvent(player, userData.getGenero(), gender);
    }

    private static void executeActions(Player player, ConfigurationSection actions, String gender) {
        if (actions == null) return;

        List<String> actionList = actions.getStringList("");
        if (actionList.isEmpty()) {
            // Si no es una lista, intentar obtener las acciones directamente
            actionList = new ArrayList<>(actions.getKeys(false));
        }

        for (String action : actionList) {
            executeAction(player, action, gender);
        }
    }

    private static void executeAction(Player player, String action, String gender) {
        if (action.startsWith("[MESSAGE]")) {
            String message = action.substring(9).trim();
            message = message.replace("{gender}", gender);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else if (action.startsWith("[SOUND]")) {
            String[] parts = action.substring(7).trim().split(":");
            if (parts.length >= 1) {
                try {
                    Sound sound = Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception ignored) {}
            }
        } else if (action.startsWith("[CLOSE]")) {
            player.closeInventory();
        }
    }

    private static void playSound(Player player, String soundType) {
        if (config == null) return;

        ConfigurationSection sounds = config.getConfigurationSection("sounds");
        if (sounds == null) return;

        String soundName = sounds.getString(soundType);
        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 0.8f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public static String getInventoryTitle() {
        return inventoryTitle != null ? inventoryTitle : ChatColor.LIGHT_PURPLE + "Selecciona tu Género";
    }
}