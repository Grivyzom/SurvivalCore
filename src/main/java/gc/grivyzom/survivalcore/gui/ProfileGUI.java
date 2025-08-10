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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI de perfil configurable desde guis.yml
 * Actualizado para ser completamente configurable
 *
 * @author Brocolitx
 * @version 3.0
 */
public class ProfileGUI {

    private static Main plugin;
    private static ConfigurationSection config;

    // Cache de configuración
    private static String inventoryTitle;
    private static int inventorySize;
    private static boolean enabled;

    // Cache de páginas activas por jugador
    private static final Map<Player, String> playerPages = new ConcurrentHashMap<>();

    public static void initialize(Main mainPlugin) {
        plugin = mainPlugin;
        reloadConfig();
    }

    public static void reloadConfig() {
        // Cargar configuración desde guis.yml
        if (plugin.getGuisConfig() != null) {
            config = plugin.getGuisConfig().getConfigurationSection("profile_gui");
            if (config != null) {
                enabled = config.getBoolean("enabled", true);
                inventoryTitle = ChatColor.translateAlternateColorCodes('&',
                        config.getString("title", "&6&lPerfil de {player}"));
                inventorySize = config.getInt("size", 54);
            }
        }
    }

    /**
     * Abre el GUI de perfil para un jugador
     */
    public static void open(Player player, Main plugin) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "El sistema de perfil está deshabilitado.");
            return;
        }

        openPage(player, "main");
        playSound(player, "open_menu");
    }

    /**
     * Abre una página específica del perfil
     */
    public static void openPage(Player player, String pageName) {
        if (!enabled || config == null) {
            player.sendMessage(ChatColor.RED + "El sistema de perfil no está configurado correctamente.");
            return;
        }

        ConfigurationSection pages = config.getConfigurationSection("pages");
        if (pages == null) {
            player.sendMessage(ChatColor.RED + "No hay páginas configuradas en el perfil.");
            return;
        }

        ConfigurationSection page = pages.getConfigurationSection(pageName);
        if (page == null) {
            player.sendMessage(ChatColor.RED + "La página '" + pageName + "' no existe.");
            return;
        }

        // Crear inventario
        String pageTitle = page.getString("title", inventoryTitle);
        pageTitle = ChatColor.translateAlternateColorCodes('&', replacePlaceholders(pageTitle, player));

        Inventory inv = Bukkit.createInventory(null, inventorySize, pageTitle);

        // Cargar items de la página
        loadPageItems(inv, page, player);

        // Guardar página actual del jugador
        playerPages.put(player, pageName);

        player.openInventory(inv);
    }

    /**
     * Carga los items de una página específica
     */
    private static void loadPageItems(Inventory inv, ConfigurationSection page, Player player) {
        ConfigurationSection items = page.getConfigurationSection("items");
        if (items == null) return;

        for (String itemKey : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(itemKey);
            if (itemSection == null) continue;

            // Verificar si es un item con múltiples slots (decoración)
            if (itemSection.contains("slots")) {
                List<Integer> slots = itemSection.getIntegerList("slots");
                ItemStack item = createItemFromConfig(itemSection, player);
                for (int slot : slots) {
                    if (slot < inv.getSize()) {
                        inv.setItem(slot, item);
                    }
                }
            } else {
                // Item individual
                int slot = itemSection.getInt("slot", -1);
                if (slot >= 0 && slot < inv.getSize()) {
                    ItemStack item = createItemFromConfig(itemSection, player);
                    inv.setItem(slot, item);
                }
            }
        }
    }

    /**
     * Crea un item desde la configuración
     */
    private static ItemStack createItemFromConfig(ConfigurationSection section, Player player) {
        String materialName = section.getString("material", "STONE").toUpperCase();
        Material material;

        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
            plugin.getLogger().warning("Material inválido en profile_gui: " + materialName);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nombre del item
            String name = section.getString("name", "");
            if (!name.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        replacePlaceholders(name, player)));
            }

            // Lore del item
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&',
                            replacePlaceholders(line, player)));
                }
                meta.setLore(coloredLore);
            }

            // Brillo
            if (section.getBoolean("glow", false)) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        // Manejar cabeza de jugador
        if (material == Material.PLAYER_HEAD && section.contains("owner")) {
            String owner = replacePlaceholders(section.getString("owner"), player);
            if (owner.equals("{player}")) {
                owner = player.getName();
            }

            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                item.setItemMeta(skullMeta);
            }
        }

        return item;
    }

    /**
     * Reemplaza placeholders en el texto
     */
    private static String replacePlaceholders(String text, Player player) {
        if (text == null) return "";

        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        // Placeholders básicos del jugador
        text = text.replace("{player}", player.getName());
        text = text.replace("{displayname}", player.getDisplayName());
        text = text.replace("{level}", String.valueOf(player.getLevel()));
        text = text.replace("{exp}", String.valueOf(player.getTotalExperience()));

        if (userData != null) {
            // Información personal
            text = text.replace("{gender}", userData.getGenero() != null ? userData.getGenero() : "No establecido");
            text = text.replace("{birthday}", userData.getCumpleaños() != null ? userData.getCumpleaños() : "No establecido");
            text = text.replace("{country}", userData.getPais() != null ? userData.getPais() : "Desconocido");

            // Niveles y experiencia
            text = text.replace("{farming_level}", String.valueOf(userData.getFarmingLevel()));
            text = text.replace("{farming_xp}", String.valueOf(userData.getFarmingXP()));
            text = text.replace("{mining_level}", String.valueOf(userData.getMiningLevel()));
            text = text.replace("{mining_xp}", String.valueOf(userData.getMiningXP()));

            // Banco de XP
            text = text.replace("{banked_xp}", String.valueOf(userData.getBankedXp()));
            text = text.replace("{bank_capacity}", String.valueOf(userData.getBankCapacity()));

            // Redes sociales
            text = text.replace("{social_count}", String.valueOf(userData.getSocialMediaCount()));
            text = text.replace("{discord_status}", getSocialStatus(userData.getDiscord()));
            text = text.replace("{instagram_status}", getSocialStatus(userData.getInstagram()));
            text = text.replace("{github_status}", getSocialStatus(userData.getGithub()));
            text = text.replace("{tiktok_status}", getSocialStatus(userData.getTiktok()));
            text = text.replace("{twitch_status}", getSocialStatus(userData.getTwitch()));
            text = text.replace("{kick_status}", getSocialStatus(userData.getKick()));
            text = text.replace("{youtube_status}", getSocialStatus(userData.getYoutube()));

            // Redes sociales para display
            text = text.replace("{discord_display}", getSocialDisplay(userData.getDiscord()));
            text = text.replace("{instagram_display}", getSocialDisplay(userData.getInstagram()));
            text = text.replace("{github_display}", getSocialDisplay(userData.getGithub()));
            text = text.replace("{tiktok_display}", getSocialDisplay(userData.getTiktok()));
            text = text.replace("{twitch_display}", getSocialDisplay(userData.getTwitch()));
            text = text.replace("{kick_display}", getSocialDisplay(userData.getKick()));
            text = text.replace("{youtube_display}", getSocialDisplay(userData.getYoutube()));

            // Estados booleanos para redes sociales (para glow y otras características)
            text = text.replace("{discord_set}", String.valueOf(userData.getDiscord() != null && !userData.getDiscord().isEmpty()));
            text = text.replace("{instagram_set}", String.valueOf(userData.getInstagram() != null && !userData.getInstagram().isEmpty()));
            text = text.replace("{github_set}", String.valueOf(userData.getGithub() != null && !userData.getGithub().isEmpty()));
            text = text.replace("{tiktok_set}", String.valueOf(userData.getTiktok() != null && !userData.getTiktok().isEmpty()));
            text = text.replace("{twitch_set}", String.valueOf(userData.getTwitch() != null && !userData.getTwitch().isEmpty()));
            text = text.replace("{kick_set}", String.valueOf(userData.getKick() != null && !userData.getKick().isEmpty()));
            text = text.replace("{youtube_set}", String.valueOf(userData.getYoutube() != null && !userData.getYoutube().isEmpty()));

            // Cooldown de género
            text = text.replace("{gender_cooldown}", plugin.getDatabaseManager().canChangeGender(player.getUniqueId().toString()) ?
                    "Disponible" : plugin.getDatabaseManager().getGenderCooldownRemaining(player.getUniqueId().toString()));
        }

        // Placeholders de rankup si está disponible
        if (plugin.isRankupSystemEnabled()) {
            try {
                String currentRank = plugin.getRankupManager().getCurrentRank(player);
                text = text.replace("{rank}", currentRank != null ? currentRank : "default");

                if (currentRank != null) {
                    var rankData = plugin.getRankupManager().getRanks().get(currentRank);
                    if (rankData != null) {
                        text = text.replace("{rank_display}", ChatColor.stripColor(rankData.getDisplayName()));
                    } else {
                        text = text.replace("{rank_display}", currentRank);
                    }
                } else {
                    text = text.replace("{rank_display}", "Sin rango");
                }
            } catch (Exception e) {
                text = text.replace("{rank}", "error");
                text = text.replace("{rank_display}", "Error");
            }
        } else {
            text = text.replace("{rank}", "default");
            text = text.replace("{rank_display}", "Sin sistema de rankup");
        }

        // Placeholders de estadísticas (valores por defecto para evitar errores)
        text = text.replace("{playtime}", "N/A");
        text = text.replace("{first_join}", "Desconocido");
        text = text.replace("{last_seen}", "Ahora");
        text = text.replace("{mob_kills}", "0");
        text = text.replace("{blocks_mined}", "0");
        text = text.replace("{distance}", "0");
        text = text.replace("{achievements_count}", "0");
        text = text.replace("{achievements_total}", "0");
        text = text.replace("{achievement_points}", "0");
        text = text.replace("{achievement_rank}", "Novato");

        return text;
    }

    /**
     * Obtiene el estado de una red social para mostrar en lore
     */
    private static String getSocialStatus(String socialValue) {
        if (socialValue != null && !socialValue.isEmpty()) {
            return "✓ Configurado";
        } else {
            return "✗ No configurado";
        }
    }

    /**
     * Obtiene el display de una red social (truncado si es muy largo)
     */
    private static String getSocialDisplay(String socialValue) {
        if (socialValue != null && !socialValue.isEmpty()) {
            return socialValue.length() > 20 ? socialValue.substring(0, 17) + "..." : socialValue;
        } else {
            return "No configurado";
        }
    }

    /**
     * Maneja el click en un item del inventario
     */
    public static void handleClick(Player player, ItemStack item, int slot, Main plugin) {
        if (item == null || !item.hasItemMeta()) return;

        String currentPage = playerPages.get(player);
        if (currentPage == null) currentPage = "main";

        // Buscar la acción correspondiente al slot clickeado
        ConfigurationSection pages = config.getConfigurationSection("pages");
        if (pages == null) return;

        ConfigurationSection page = pages.getConfigurationSection(currentPage);
        if (page == null) return;

        ConfigurationSection items = page.getConfigurationSection("items");
        if (items == null) return;

        // Buscar el item clickeado
        for (String itemKey : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(itemKey);
            if (itemSection == null) continue;

            // Verificar si este item coincide con el slot clickeado
            if (itemSection.contains("slots")) {
                List<Integer> slots = itemSection.getIntegerList("slots");
                if (slots.contains(slot)) {
                    // Este es un item decorativo, no hacer nada
                    return;
                }
            } else {
                int itemSlot = itemSection.getInt("slot", -1);
                if (itemSlot == slot) {
                    // Ejecutar acciones para este item
                    executeItemActions(player, itemSection.getConfigurationSection("actions.left_click"), itemKey);
                    return;
                }
            }
        }
    }

    /**
     * Ejecuta las acciones de un item
     */
    private static void executeItemActions(Player player, ConfigurationSection actions, String itemKey) {
        if (actions == null) return;

        List<String> actionList = new ArrayList<>();

        // Intentar obtener las acciones como lista
        if (actions.isList("")) {
            actionList = actions.getStringList("");
        } else {
            // Si no es una lista, obtener las claves
            for (String key : actions.getKeys(false)) {
                String actionValue = actions.getString(key);
                if (actionValue != null) {
                    actionList.add(actionValue);
                }
            }
        }

        for (String action : actionList) {
            executeAction(player, action, itemKey);
        }
    }

    /**
     * Ejecuta una acción específica
     */
    private static void executeAction(Player player, String action, String itemKey) {
        if (action.startsWith("[OPEN_PAGE]")) {
            String pageName = action.substring(11).trim();
            openPage(player, pageName);
            playSound(player, "change_page");

        } else if (action.startsWith("[CLOSE]")) {
            player.closeInventory();
            playSound(player, "close_menu");

        } else if (action.startsWith("[COMMAND]")) {
            String command = action.substring(9).trim();
            player.performCommand(command);

        } else if (action.startsWith("[MESSAGE]")) {
            String message = action.substring(9).trim();
            message = replacePlaceholders(message, player);
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

        } else if (action.startsWith("[SOCIAL_REMOVE]")) {
            String network = action.substring(15).trim();
            removeSocialNetwork(player, network);

        } else if (action.startsWith("[AWAIT_INPUT]")) {
            String inputType = action.substring(13).trim();
            handleAwaitInput(player, inputType);
        }
    }

    /**
     * Elimina una red social
     */
    private static void removeSocialNetwork(Player player, String network) {
        UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        switch (network.toLowerCase()) {
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

        // Reabrir la página para reflejar los cambios
        openPage(player, playerPages.get(player));
    }

    /**
     * Maneja la espera de input del jugador
     */
    private static void handleAwaitInput(Player player, String inputType) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Esta funcionalidad estará disponible próximamente.");
        player.sendMessage(ChatColor.GRAY + "Por ahora, usa /social <red> <valor> para configurar tus redes sociales.");
    }

    /**
     * Reproduce un sonido
     */
    private static void playSound(Player player, String soundType) {
        if (config == null) return;

        ConfigurationSection globalSounds = plugin.getGuisConfig().getConfigurationSection("global.default_sounds");
        if (globalSounds == null) return;

        String soundName = globalSounds.getString(soundType);
        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                float volume = (float) plugin.getGuisConfig().getDouble("global.sound_volume", 0.8);
                float pitch = (float) plugin.getGuisConfig().getDouble("global.sound_pitch", 1.0);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Obtiene el título del inventario
     */
    public static String getInventoryTitle() {
        return inventoryTitle != null ? inventoryTitle : ChatColor.GREEN + "Perfil de Jugador";
    }

    /**
     * Verifica si un título pertenece al sistema de perfil
     */
    public static boolean isProfileInventory(String title) {
        if (config == null) return false;

        ConfigurationSection pages = config.getConfigurationSection("pages");
        if (pages == null) return false;

        for (String pageKey : pages.getKeys(false)) {
            ConfigurationSection page = pages.getConfigurationSection(pageKey);
            if (page != null) {
                String pageTitle = page.getString("title", inventoryTitle);
                // Comparar sin códigos de color y sin placeholders específicos
                String cleanTitle = ChatColor.stripColor(pageTitle).replace("{player}", "");
                String cleanInventoryTitle = ChatColor.stripColor(title);

                if (cleanInventoryTitle.contains(cleanTitle.trim()) || cleanTitle.trim().isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Limpia el cache del jugador al cerrar
     */
    public static void cleanupPlayer(Player player) {
        playerPages.remove(player);
    }
}