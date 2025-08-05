package gc.grivyzom.survivalcore.rankup.menu;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de menús configurables para Rankup 2.0
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MenuManager {

    private final Main plugin;
    private final RankupManager rankupManager;

    private File menuConfigFile;
    private FileConfiguration menuConfig;

    // Caché de menús por jugador
    private final Map<UUID, CachedMenu> menuCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

    // Configuración
    private boolean menuEnabled;
    private boolean cachingEnabled;
    private boolean soundsEnabled;
    private boolean particlesEnabled;
    private int cacheTimeout;
    private int autoRefreshInterval;

    public MenuManager(Main plugin, RankupManager rankupManager) {
        this.plugin = plugin;
        this.rankupManager = rankupManager;

        initializeConfig();
        loadConfiguration();

        // Programar actualización automática si está habilitada
        if (autoRefreshInterval > 0) {
            startAutoRefreshTask();
        }
    }

    /**
     * Inicializa el archivo de configuración - CORREGIDO
     */
    private void initializeConfig() {
        try {
            // Crear directorio menus si no existe
            File menusDir = new File(plugin.getDataFolder(), "menus");
            if (!menusDir.exists()) {
                if (menusDir.mkdirs()) {
                    plugin.getLogger().info("✓ Directorio menus/ creado");
                } else {
                    plugin.getLogger().warning("⚠ No se pudo crear directorio menus/");
                }
            }

            menuConfigFile = new File(menusDir, "rankup_menu.yml");

            // Crear archivo por defecto si no existe
            if (!menuConfigFile.exists()) {
                plugin.getLogger().info("📄 Archivo rankup_menu.yml no existe, creando...");

                try {
                    // Intentar usar recurso del plugin
                    plugin.saveResource("menus/rankup_menu.yml", false);
                    plugin.getLogger().info("✓ Archivo rankup_menu.yml creado desde resources");
                } catch (Exception e) {
                    plugin.getLogger().warning("⚠ No se pudo crear desde resources: " + e.getMessage());
                    plugin.getLogger().info("🔧 Creando archivo básico manualmente...");

                    if (createBasicMenuConfig()) {
                        plugin.getLogger().info("✓ Archivo básico de menú creado exitosamente");
                    } else {
                        throw new RuntimeException("No se pudo crear archivo de configuración de menú");
                    }
                }
            }

            // Verificar que el archivo sea legible
            if (!menuConfigFile.canRead()) {
                throw new RuntimeException("No se puede leer el archivo de configuración de menú");
            }

            // Cargar configuración
            menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);

            // Verificar que la configuración sea válida
            if (!menuConfig.contains("menu_settings")) {
                plugin.getLogger().warning("⚠ Configuración de menú inválida, regenerando...");
                if (createBasicMenuConfig()) {
                    menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
                }
            }

            plugin.getLogger().info("✓ Configuración de menú cargada correctamente");

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error crítico inicializando configuración de menú: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error inicializando MenuManager", e);
        }
    }

    /**
     * Carga la configuración del menú
     */
    private void loadConfiguration() {
        try {
            // Configuración general
            ConfigurationSection settings = menuConfig.getConfigurationSection("menu_settings");
            if (settings != null) {
                menuEnabled = settings.getBoolean("enabled", true);
                autoRefreshInterval = settings.getInt("auto_refresh_interval", -1);
                soundsEnabled = settings.getBoolean("enable_sounds", true);
                particlesEnabled = settings.getBoolean("enable_particles", true);
            }

            // Configuración avanzada
            ConfigurationSection advanced = menuConfig.getConfigurationSection("advanced");
            if (advanced != null) {
                cachingEnabled = advanced.getBoolean("cache_menus", true);
                cacheTimeout = advanced.getInt("cache_duration_seconds", 60);
            }

            plugin.getLogger().info("✓ Configuración de menús cargada");

        } catch (Exception e) {
            plugin.getLogger().severe("Error cargando configuración de menús: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Abre el menú principal para un jugador
     */
    public void openMainMenu(Player player) {
        if (!menuEnabled) {
            player.sendMessage(ChatColor.RED + "❌ El sistema de menús está deshabilitado");
            return;
        }

        try {
            // Verificar caché
            if (cachingEnabled) {
                CachedMenu cached = menuCache.get(player.getUniqueId());
                if (cached != null && cached.isValid()) {
                    player.openInventory(cached.getInventory());
                    playSound(player, "menu_open");
                    return;
                }
            }

            // Crear menú principal
            Inventory menu = createMainMenu(player);
            player.openInventory(menu);

            // Guardar en caché
            if (cachingEnabled) {
                menuCache.put(player.getUniqueId(), new CachedMenu(menu, System.currentTimeMillis()));
            }

            playSound(player, "menu_open");
            spawnParticles(player, "menu_open");

        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú principal para " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo menú. Contacta a un administrador.");
        }
    }

    /**
     * Crea el menú principal basado en la configuración
     */
    private Inventory createMainMenu(Player player) {
        ConfigurationSection mainMenu = menuConfig.getConfigurationSection("menus.main");
        if (mainMenu == null) {
            throw new RuntimeException("Configuración de menú principal no encontrada");
        }

        // Título y tamaño
        String title = ChatColor.translateAlternateColorCodes('&',
                replacePlaceholders(player, mainMenu.getString("title", "&5Sistema de Rangos")));
        int size = mainMenu.getInt("size", 45);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Fondo
        ConfigurationSection background = mainMenu.getConfigurationSection("background");
        if (background != null && background.getBoolean("enabled", false)) {
            fillBackground(inv, background);
        }

        // Items
        ConfigurationSection items = mainMenu.getConfigurationSection("items");
        if (items != null) {
            for (String itemKey : items.getKeys(false)) {
                ConfigurationSection itemConfig = items.getConfigurationSection(itemKey);
                if (itemConfig != null) {
                    ItemStack item = createMenuItem(player, itemKey, itemConfig);
                    if (item != null) {
                        int slot = itemConfig.getInt("slot", -1);
                        if (slot >= 0 && slot < size) {
                            inv.setItem(slot, item);
                        }
                    }
                }
            }
        }

        return inv;
    }

    /**
     * Crea un ítem de menú basado en la configuración
     */
    private ItemStack createMenuItem(Player player, String itemKey, ConfigurationSection config) {
        try {
            // Verificar condición habilitada
            String enabledCondition = config.getString("enabled_condition");
            if (enabledCondition != null && !evaluateCondition(player, enabledCondition)) {
                // Crear ítem deshabilitado si está configurado
                return createDisabledMenuItem(player, config);
            }

            // Determinar material
            Material material = getMaterialForItem(player, itemKey, config);
            if (material == null) {
                plugin.getLogger().warning("Material no válido para ítem: " + itemKey);
                return null;
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;

            // Nombre
            String name = config.getString("name", itemKey);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(player, name)));

            // Lore
            List<String> lore = config.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> processedLore = new ArrayList<>();
                for (String line : lore) {
                    processedLore.add(ChatColor.translateAlternateColorCodes('&',
                            replacePlaceholders(player, line)));
                }
                meta.setLore(processedLore);
            }

            // Cabeza de jugador
            if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
                SkullMeta skullMeta = (SkullMeta) meta;
                String skullOwner = config.getString("skull_owner");
                if (skullOwner != null) {
                    if (skullOwner.equals("%player%")) {
                        skullMeta.setOwningPlayer(player);
                    } else {
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skullOwner));
                    }
                }
            }

            item.setItemMeta(meta);

            // Encantado
            if (config.getBoolean("enchanted", false)) {
                item = addGlow(item);
            }

            return item;

        } catch (Exception e) {
            plugin.getLogger().warning("Error creando ítem de menú '" + itemKey + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Determina el material apropiado para un ítem
     */
    private Material getMaterialForItem(Player player, String itemKey, ConfigurationSection config) {
        // Verificar estados especiales (cooldown, bloqueado, etc.)
        if (itemKey.equals("rankup_button")) {
            if (rankupManager.isOnCooldown(player.getUniqueId())) {
                String cooldownMaterial = config.getString("cooldown_material", "CLOCK");
                return Material.valueOf(cooldownMaterial);
            }

            // Verificar si puede hacer rankup
            String currentRank = rankupManager.getCurrentRank(player);
            if (currentRank != null) {
                var rankData = rankupManager.getRanks().get(currentRank);
                if (rankData != null && rankData.getNextRank() == null) {
                    // Rango máximo alcanzado
                    String blockedMaterial = config.getString("blocked_material", "BARRIER");
                    return Material.valueOf(blockedMaterial);
                }
            }
        }

        // Verificar toggles de configuración
        if (itemKey.endsWith("_toggle")) {
            PlayerSettings settings = getPlayerSettings(player);
            boolean enabled = false;

            switch (itemKey) {
                case "notifications_toggle" -> enabled = settings.notificationsEnabled;
                case "effects_toggle" -> enabled = settings.effectsEnabled;
                case "sounds_toggle" -> enabled = settings.soundsEnabled;
            }

            String materialKey = enabled ? "enabled_material" : "disabled_material";
            String materialName = config.getString(materialKey, "STONE");
            return Material.valueOf(materialName);
        }

        // Material por defecto
        String materialName = config.getString("material", "STONE");
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido: " + materialName + " para ítem: " + itemKey);
            return Material.STONE;
        }
    }

    /**
     * Crea un ítem deshabilitado
     */
    private ItemStack createDisabledMenuItem(Player player, ConfigurationSection config) {
        String disabledMaterial = config.getString("disabled_material", "GRAY_DYE");
        String disabledName = config.getString("disabled_name", "&8Deshabilitado");
        List<String> disabledLore = config.getStringList("disabled_lore");

        try {
            Material material = Material.valueOf(disabledMaterial);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                        replacePlaceholders(player, disabledName)));

                if (!disabledLore.isEmpty()) {
                    List<String> processedLore = new ArrayList<>();
                    for (String line : disabledLore) {
                        processedLore.add(ChatColor.translateAlternateColorCodes('&',
                                replacePlaceholders(player, line)));
                    }
                    meta.setLore(processedLore);
                }

                item.setItemMeta(meta);
            }
            return item;
        } catch (Exception e) {
            return new ItemStack(Material.GRAY_DYE);
        }
    }

    /**
     * Abre el menú de progreso
     */
    public void openProgressMenu(Player player) {
        if (!menuEnabled) {
            player.sendMessage(ChatColor.RED + "❌ El sistema de menús está deshabilitado");
            return;
        }

        try {
            rankupManager.getPlayerProgress(player).thenAccept(progress -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Inventory menu = createProgressMenu(player, progress);
                        player.openInventory(menu);
                        playSound(player, "page_turn");
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error creando menú de progreso: " + e.getMessage());
                        player.sendMessage(ChatColor.RED + "❌ Error abriendo menú de progreso");
                    }
                });
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú de progreso: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo menú de progreso");
        }
    }

    /**
     * Crea el menú de progreso
     */
    private Inventory createProgressMenu(Player player, RankupManager.RankupProgress progress) {
        ConfigurationSection progressMenu = menuConfig.getConfigurationSection("menus.progress");
        if (progressMenu == null) {
            throw new RuntimeException("Configuración de menú de progreso no encontrada");
        }

        String title = ChatColor.translateAlternateColorCodes('&',
                replacePlaceholders(player, progressMenu.getString("title", "&bMi Progreso")));
        int size = progressMenu.getInt("size", 54);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Fondo
        ConfigurationSection background = progressMenu.getConfigurationSection("background");
        if (background != null && background.getBoolean("enabled", false)) {
            fillBackground(inv, background);
        }

        // Items estáticos
        ConfigurationSection items = progressMenu.getConfigurationSection("items");
        if (items != null) {
            // Información de progreso
            ConfigurationSection progressInfo = items.getConfigurationSection("progress_info");
            if (progressInfo != null) {
                ItemStack item = createProgressInfoItem(player, progress, progressInfo);
                int slot = progressInfo.getInt("slot", 22);
                inv.setItem(slot, item);
            }

            // Botón volver
            ConfigurationSection backButton = items.getConfigurationSection("back_button");
            if (backButton != null) {
                ItemStack item = createMenuItem(player, "back_button", backButton);
                int slot = backButton.getInt("slot", 49);
                inv.setItem(slot, item);
            }
        }

        // Requisitos dinámicos
        if (progress.getRequirements() != null) {
            addRequirementsToMenu(inv, player, progress, progressMenu);
        }

        return inv;
    }

    /**
     * Añade requisitos dinámicamente al menú
     */
    private void addRequirementsToMenu(Inventory inv, Player player,
                                       RankupManager.RankupProgress progress,
                                       ConfigurationSection menuConfig) {
        try {
            ConfigurationSection items = menuConfig.getConfigurationSection("items");
            if (items == null) return;

            int startSlot = items.getInt("requirements_start_slot", 28);
            String pattern = items.getString("requirements_pattern", "horizontal");

            ConfigurationSection template = items.getConfigurationSection("requirement_template");
            if (template == null) return;

            List<Integer> slots = calculateRequirementSlots(startSlot, pattern,
                    progress.getRequirements().size(), inv.getSize());

            int index = 0;
            for (Map.Entry<String, RankupManager.RequirementProgress> entry :
                    progress.getRequirements().entrySet()) {

                if (index >= slots.size()) break;

                ItemStack reqItem = createRequirementItem(player, entry.getValue(), template);
                inv.setItem(slots.get(index), reqItem);
                index++;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error añadiendo requisitos al menú: " + e.getMessage());
        }
    }

    /**
     * Calcula las posiciones de los requisitos según el patrón
     */
    private List<Integer> calculateRequirementSlots(int startSlot, String pattern,
                                                    int count, int invSize) {
        List<Integer> slots = new ArrayList<>();

        switch (pattern.toLowerCase()) {
            case "horizontal" -> {
                for (int i = 0; i < count && startSlot + i < invSize; i++) {
                    if ((startSlot + i) % 9 != 8) { // No en borde derecho
                        slots.add(startSlot + i);
                    }
                }
            }
            case "vertical" -> {
                for (int i = 0; i < count; i++) {
                    int slot = startSlot + (i * 9);
                    if (slot < invSize) {
                        slots.add(slot);
                    }
                }
            }
            case "grid" -> {
                int row = startSlot / 9;
                int col = startSlot % 9;

                for (int i = 0; i < count; i++) {
                    int currentSlot = (row * 9) + col;
                    if (currentSlot < invSize && col < 8) {
                        slots.add(currentSlot);
                        col++;
                        if (col >= 8) { // Cambiar de fila
                            col = 1;
                            row++;
                        }
                    }
                }
            }
        }

        return slots;
    }

    /**
     * Crea un ítem de requisito
     */
    private ItemStack createRequirementItem(Player player,
                                            RankupManager.RequirementProgress progress,
                                            ConfigurationSection template) {
        boolean completed = progress.isCompleted();

        // Material
        String materialKey = completed ? "completed_material" : "incomplete_material";
        String materialName = template.getString(materialKey, completed ? "LIME_DYE" : "RED_DYE");
        Material material = Material.valueOf(materialName);

        // Nombre
        String nameKey = completed ? "completed_name" : "incomplete_name";
        String name = template.getString(nameKey, "%requirement_name%");
        name = name.replace("%requirement_name%", formatRequirementName(progress.getType()));

        // Lore
        String loreKey = completed ? "completed_lore" : "incomplete_lore";
        List<String> lore = template.getStringList(loreKey);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(player, name)));

            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                line = line.replace("%current_value%", formatRequirementValue(progress.getCurrent(), progress.getType()));
                line = line.replace("%required_value%", formatRequirementValue(progress.getRequired(), progress.getType()));
                line = line.replace("%missing_value%", formatRequirementValue(
                        Math.max(0, progress.getRequired() - progress.getCurrent()), progress.getType()));

                processedLore.add(ChatColor.translateAlternateColorCodes('&',
                        replacePlaceholders(player, line)));
            }
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Abre el menú de lista de rangos
     */
    public void openRanksListMenu(Player player) {
        if (!menuEnabled) {
            player.sendMessage(ChatColor.RED + "❌ El sistema de menús está deshabilitado");
            return;
        }

        try {
            Inventory menu = createRanksListMenu(player);
            player.openInventory(menu);
            playSound(player, "page_turn");
        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo lista de rangos: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo lista de rangos");
        }
    }

    /**
     * Crea el menú de lista de rangos
     */
    private Inventory createRanksListMenu(Player player) {
        ConfigurationSection ranksMenu = menuConfig.getConfigurationSection("menus.ranks_list");
        if (ranksMenu == null) {
            throw new RuntimeException("Configuración de menú de rangos no encontrada");
        }

        String title = ChatColor.translateAlternateColorCodes('&',
                replacePlaceholders(player, ranksMenu.getString("title", "&2Lista de Rangos")));
        int size = ranksMenu.getInt("size", 54);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Fondo
        ConfigurationSection background = ranksMenu.getConfigurationSection("background");
        if (background != null && background.getBoolean("enabled", false)) {
            fillBackground(inv, background);
        }

        // Botón volver
        ConfigurationSection items = ranksMenu.getConfigurationSection("items");
        if (items != null) {
            ConfigurationSection backButton = items.getConfigurationSection("back_button");
            if (backButton != null) {
                ItemStack item = createMenuItem(player, "back_button", backButton);
                int slot = backButton.getInt("slot", 49);
                inv.setItem(slot, item);
            }
        }

        // Rangos dinámicos
        addRanksToMenu(inv, player, ranksMenu);

        return inv;
    }

    /**
     * Añade rangos dinámicamente al menú
     */
    private void addRanksToMenu(Inventory inv, Player player, ConfigurationSection menuConfig) {
        try {
            ConfigurationSection items = menuConfig.getConfigurationSection("items");
            if (items == null) return;

            int startSlot = items.getInt("ranks_start_slot", 10);
            String pattern = items.getString("ranks_pattern", "grid");

            ConfigurationSection template = items.getConfigurationSection("rank_template");
            if (template == null) return;

            Map<String, RankupManager.SimpleRankData> ranks = rankupManager.getRanks();
            String currentRank = rankupManager.getCurrentRank(player);

            List<RankupManager.SimpleRankData> sortedRanks = ranks.values().stream()
                    .sorted(Comparator.comparingInt(RankupManager.SimpleRankData::getOrder))
                    .toList();

            List<Integer> slots = calculateRequirementSlots(startSlot, pattern,
                    sortedRanks.size(), inv.getSize());

            for (int i = 0; i < sortedRanks.size() && i < slots.size(); i++) {
                RankupManager.SimpleRankData rankData = sortedRanks.get(i);
                ItemStack rankItem = createRankItem(player, rankData, currentRank, template);
                inv.setItem(slots.get(i), rankItem);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error añadiendo rangos al menú: " + e.getMessage());
        }
    }

    /**
     * Crea un ítem de rango
     */
    private ItemStack createRankItem(Player player, RankupManager.SimpleRankData rankData,
                                     String currentRank, ConfigurationSection template) {

        // Determinar estado del rango
        RankState state = determineRankState(rankData, currentRank);

        // Material y nombre según estado
        String materialKey = switch (state) {
            case CURRENT -> "current_material";
            case COMPLETED -> "completed_material";
            case LOCKED -> "locked_material";
        };

        String nameKey = switch (state) {
            case CURRENT -> "current_name";
            case COMPLETED -> "completed_name";
            case LOCKED -> "locked_name";
        };

        String loreKey = switch (state) {
            case CURRENT -> "current_lore";
            case COMPLETED -> "completed_lore";
            case LOCKED -> "locked_lore";
        };

        String materialName = template.getString(materialKey, "STONE");
        Material material = Material.valueOf(materialName);

        String name = template.getString(nameKey, "%rank_display_name%");
        name = name.replace("%rank_display_name%", rankData.getDisplayName());

        List<String> lore = template.getStringList(loreKey);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(player, name)));

            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                line = line.replace("%rank_order%", String.valueOf(rankData.getOrder()));
                line = line.replace("%next_rank%", rankData.hasNextRank() ?
                        rankData.getNextRank() : "Rango máximo");

                processedLore.add(ChatColor.translateAlternateColorCodes('&',
                        replacePlaceholders(player, line)));
            }
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }

        // Encantado según configuración
        String enchantedKey = switch (state) {
            case CURRENT -> "enchanted_current";
            case COMPLETED -> "enchanted_completed";
            case LOCKED -> "enchanted_locked";
        };

        if (template.getBoolean(enchantedKey, false)) {
            item = addGlow(item);
        }

        return item;
    }

    /**
     * Abre el menú de configuración personal
     */
    public void openSettingsMenu(Player player) {
        if (!menuEnabled) {
            player.sendMessage(ChatColor.RED + "❌ El sistema de menús está deshabilitado");
            return;
        }

        try {
            Inventory menu = createSettingsMenu(player);
            player.openInventory(menu);
            playSound(player, "menu_open");
        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú de configuración: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo configuración");
        }
    }

    /**
     * Crea el menú de configuración
     */
    private Inventory createSettingsMenu(Player player) {
        ConfigurationSection settingsMenu = menuConfig.getConfigurationSection("menus.settings");
        if (settingsMenu == null) {
            throw new RuntimeException("Configuración de menú de configuración no encontrada");
        }

        String title = ChatColor.translateAlternateColorCodes('&',
                replacePlaceholders(player, settingsMenu.getString("title", "&7Configuración")));
        int size = settingsMenu.getInt("size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Fondo
        ConfigurationSection background = settingsMenu.getConfigurationSection("background");
        if (background != null && background.getBoolean("enabled", false)) {
            fillBackground(inv, background);
        }

        // Items
        ConfigurationSection items = settingsMenu.getConfigurationSection("items");
        if (items != null) {
            for (String itemKey : items.getKeys(false)) {
                ConfigurationSection itemConfig = items.getConfigurationSection(itemKey);
                if (itemConfig != null) {
                    ItemStack item = createMenuItem(player, itemKey, itemConfig);
                    if (item != null) {
                        int slot = itemConfig.getInt("slot", -1);
                        if (slot >= 0 && slot < size) {
                            inv.setItem(slot, item);
                        }
                    }
                }
            }
        }

        return inv;
    }

    /**
     * Maneja el clic en un menú
     */
    public boolean handleMenuClick(Player player, String menuTitle, int slot, ItemStack clickedItem) {
        if (!menuEnabled) return false;

        try {
            // Determinar tipo de menú por título
            String menuType = determineMenuType(menuTitle);
            if (menuType == null) return false;

            // Manejar según tipo de menú
            switch (menuType) {
                case "main" -> {
                    return handleMainMenuClick(player, slot, clickedItem);
                }
                case "progress" -> {
                    return handleProgressMenuClick(player, slot, clickedItem);
                }
                case "ranks_list" -> {
                    return handleRanksListClick(player, slot, clickedItem);
                }
                case "settings" -> {
                    return handleSettingsMenuClick(player, slot, clickedItem);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error manejando clic en menú: " + e.getMessage());
        }

        return false;
    }

    /**
     * Maneja clics en el menú principal
     */
    private boolean handleMainMenuClick(Player player, int slot, ItemStack clickedItem) {
        ConfigurationSection mainMenu = menuConfig.getConfigurationSection("menus.main.items");
        if (mainMenu == null) return false;

        // Buscar el ítem que corresponde a este slot
        for (String itemKey : mainMenu.getKeys(false)) {
            ConfigurationSection itemConfig = mainMenu.getConfigurationSection(itemKey);
            if (itemConfig != null && itemConfig.getInt("slot", -1) == slot) {
                return handleMenuAction(player, itemKey, itemConfig);
            }
        }

        return false;
    }

    /**
     * Maneja acciones específicas del menú
     */
    private boolean handleMenuAction(Player player, String actionKey, ConfigurationSection config) {
        playSound(player, config.getString("sound", "button_click"));

        switch (actionKey) {
            case "rankup_button" -> {
                if (rankupManager.isOnCooldown(player.getUniqueId())) {
                    long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
                    player.sendMessage(ChatColor.RED + "⏰ Espera " + (remaining / 1000) + " segundos");
                    return true;
                }

                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "🔄 Procesando rankup...");

                rankupManager.attemptRankup(player).thenAccept(result -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.isSuccess()) {
                            player.sendMessage(result.getMessage());
                            playSound(player, "rankup_success");
                            spawnParticles(player, "rankup_success");
                        } else {
                            player.sendMessage(result.getMessage());
                            playSound(player, "rankup_fail");
                        }
                    });
                });
                return true;
            }

            case "progress_button" -> {
                openProgressMenu(player);
                return true;
            }

            case "ranks_list_button" -> {
                openRanksListMenu(player);
                return true;
            }

            case "settings_button" -> {
                openSettingsMenu(player);
                return true;
            }

            case "notifications_toggle", "effects_toggle", "sounds_toggle" -> {
                togglePlayerSetting(player, actionKey);
                // Refrescar el menú actual
                Bukkit.getScheduler().runTaskLater(plugin, () -> openSettingsMenu(player), 1L);
                return true;
            }

            case "back_button" -> {
                openMainMenu(player);
                return true;
            }

            default -> {
                // Acción no implementada
                String message = menuConfig.getString("messages.coming_soon", "&eProximamente...");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }
        }
    }

    /**
     * Alterna una configuración del jugador
     */
    private void togglePlayerSetting(Player player, String settingKey) {
        PlayerSettings settings = getPlayerSettings(player);
        String settingName = "";

        switch (settingKey) {
            case "notifications_toggle" -> {
                settings.notificationsEnabled = !settings.notificationsEnabled;
                settingName = "Notificaciones";
            }
            case "effects_toggle" -> {
                settings.effectsEnabled = !settings.effectsEnabled;
                settingName = "Efectos";
            }
            case "sounds_toggle" -> {
                settings.soundsEnabled = !settings.soundsEnabled;
                settingName = "Sonidos";
            }
        }

        savePlayerSettings(player, settings);

        String status = getSettingStatus(player, settingKey);
        String message = menuConfig.getString("messages.setting_changed",
                "&aConfiguración actualizada: &f%setting% &7→ &e%value%");
        message = message.replace("%setting%", settingName).replace("%value%", status);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        playSound(player, settings.soundsEnabled ? "toggle_on" : "toggle_off");
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Llena el fondo del inventario
     */
    private void fillBackground(Inventory inv, ConfigurationSection background) {
        try {
            String materialName = background.getString("material", "BLACK_STAINED_GLASS_PANE");
            Material material = Material.valueOf(materialName);
            String name = background.getString("name", "&r");

            ItemStack backgroundItem = new ItemStack(material);
            ItemMeta meta = backgroundItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                backgroundItem.setItemMeta(meta);
            }

            // Llenar bordes
            int size = inv.getSize();

            // Primera y última fila
            for (int i = 0; i < 9 && i < size; i++) {
                inv.setItem(i, backgroundItem);
                if (size > 9) {
                    inv.setItem(size - 9 + i, backgroundItem);
                }
            }

            // Columnas laterales
            for (int row = 1; row < (size / 9) - 1; row++) {
                inv.setItem(row * 9, backgroundItem);
                inv.setItem(row * 9 + 8, backgroundItem);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error llenando fondo: " + e.getMessage());
        }
    }

    /**
     * Reemplaza placeholders en un texto
     */
    private String replacePlaceholders(Player player, String text) {
        if (text == null) return "";

        // Placeholders básicos
        text = text.replace("%player%", player.getName());

        // PlaceholderAPI si está disponible
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }

        // Placeholders personalizados del menú
        text = replaceCustomPlaceholders(player, text);

        return text;
    }

    /**
     * Reemplaza placeholders personalizados
     */
    private String replaceCustomPlaceholders(Player player, String text) {
        try {
            // Estado de rankup
            if (text.contains("%rankup_status%")) {
                String status = getRankupStatus(player);
                text = text.replace("%rankup_status%", status);
            }

            // Tiempo de cooldown
            if (text.contains("%cooldown_time%")) {
                if (rankupManager.isOnCooldown(player.getUniqueId())) {
                    long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
                    text = text.replace("%cooldown_time%", (remaining / 1000) + "s");
                } else {
                    text = text.replace("%cooldown_time%", "0s");
                }
            }

            // Estados de configuración
            text = text.replace("%notifications_status%", getSettingStatus(player, "notifications_toggle"));
            text = text.replace("%effects_status%", getSettingStatus(player, "effects_toggle"));
            text = text.replace("%sounds_status%", getSettingStatus(player, "sounds_toggle"));

            // Estado de progreso
            if (text.contains("%progress_status%")) {
                String status = getProgressStatus(player);
                text = text.replace("%progress_status%", status);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error reemplazando placeholders personalizados: " + e.getMessage());
        }

        return text;
    }

    /**
     * Obtiene el estado de rankup del jugador
     */
    private String getRankupStatus(Player player) {
        if (rankupManager.isOnCooldown(player.getUniqueId())) {
            return ChatColor.RED + "En cooldown";
        }

        String currentRank = rankupManager.getCurrentRank(player);
        if (currentRank != null) {
            var rankData = rankupManager.getRanks().get(currentRank);
            if (rankData != null && rankData.getNextRank() == null) {
                return ChatColor.LIGHT_PURPLE + "Rango máximo";
            }
        }

        return ChatColor.GREEN + "Disponible";
    }

    /**
     * Obtiene el estado de una configuración
     */
    private String getSettingStatus(Player player, String settingKey) {
        PlayerSettings settings = getPlayerSettings(player);

        boolean enabled = switch (settingKey) {
            case "notifications_toggle" -> settings.notificationsEnabled;
            case "effects_toggle" -> settings.effectsEnabled;
            case "sounds_toggle" -> settings.soundsEnabled;
            default -> false;
        };

        return enabled ?
                menuConfig.getString("messages.status.notifications_on", "&aActivadas") :
                menuConfig.getString("messages.status.notifications_off", "&cDesactivadas");
    }

    /**
     * Obtiene el estado de progreso
     */
    private String getProgressStatus(Player player) {
        try {
            String currentRank = rankupManager.getCurrentRank(player);
            if (currentRank != null) {
                var rankData = rankupManager.getRanks().get(currentRank);
                if (rankData != null && rankData.getNextRank() == null) {
                    return menuConfig.getString("messages.status.max_rank_reached",
                            "&d¡Rango máximo alcanzado!");
                }
            }

            return menuConfig.getString("messages.status.requirements_pending",
                    "&eRequisitos pendientes");

        } catch (Exception e) {
            return ChatColor.GRAY + "Desconocido";
        }
    }

    /**
     * Determina el tipo de menú por título
     */
    private String determineMenuType(String title) {
        title = ChatColor.stripColor(title).toLowerCase();

        if (title.contains("sistema de rangos") || title.contains("rankup")) {
            return "main";
        } else if (title.contains("progreso")) {
            return "progress";
        } else if (title.contains("lista") && title.contains("rangos")) {
            return "ranks_list";
        } else if (title.contains("configuración") || title.contains("settings")) {
            return "settings";
        }

        return null;
    }

    /**
     * Determina el estado de un rango para un jugador
     */
    private RankState determineRankState(RankupManager.SimpleRankData rankData, String currentRank) {
        if (rankData.getId().equals(currentRank)) {
            return RankState.CURRENT;
        }

        // Verificar si es un rango ya completado (orden menor al actual)
        if (currentRank != null) {
            var currentRankData = rankupManager.getRanks().get(currentRank);
            if (currentRankData != null && rankData.getOrder() < currentRankData.getOrder()) {
                return RankState.COMPLETED;
            }
        }

        return RankState.LOCKED;
    }

    /**
     * Maneja clics en el menú de progreso
     */
    private boolean handleProgressMenuClick(Player player, int slot, ItemStack clickedItem) {
        ConfigurationSection progressMenu = menuConfig.getConfigurationSection("menus.progress.items");
        if (progressMenu == null) return false;

        // Verificar botón volver
        ConfigurationSection backButton = progressMenu.getConfigurationSection("back_button");
        if (backButton != null && backButton.getInt("slot", -1) == slot) {
            openMainMenu(player);
            playSound(player, "button_click");
            return true;
        }

        return false;
    }

    /**
     * Maneja clics en el menú de lista de rangos
     */
    private boolean handleRanksListClick(Player player, int slot, ItemStack clickedItem) {
        ConfigurationSection ranksMenu = menuConfig.getConfigurationSection("menus.ranks_list.items");
        if (ranksMenu == null) return false;

        // Verificar botón volver
        ConfigurationSection backButton = ranksMenu.getConfigurationSection("back_button");
        if (backButton != null && backButton.getInt("slot", -1) == slot) {
            openMainMenu(player);
            playSound(player, "button_click");
            return true;
        }

        return false;
    }

    /**
     * Maneja clics en el menú de configuración
     */
    private boolean handleSettingsMenuClick(Player player, int slot, ItemStack clickedItem) {
        ConfigurationSection settingsMenu = menuConfig.getConfigurationSection("menus.settings.items");
        if (settingsMenu == null) return false;

        // Buscar el ítem que corresponde a este slot
        for (String itemKey : settingsMenu.getKeys(false)) {
            ConfigurationSection itemConfig = settingsMenu.getConfigurationSection(itemKey);
            if (itemConfig != null && itemConfig.getInt("slot", -1) == slot) {
                return handleMenuAction(player, itemKey, itemConfig);
            }
        }

        return false;
    }

    /**
     * Crea un ítem de información de progreso
     */
    private ItemStack createProgressInfoItem(Player player, RankupManager.RankupProgress progress,
                                             ConfigurationSection config) {
        String materialName = config.getString("material", "EXPERIENCE_BOTTLE");
        Material material = Material.valueOf(materialName);

        String name = config.getString("name", "&b📊 Tu Progreso");
        List<String> lore = config.getStringList("lore");

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    replacePlaceholders(player, name)));

            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                // Reemplazar variables específicas del progreso
                if (progress.getCurrentRank() != null) {
                    var currentRankData = rankupManager.getRanks().get(progress.getCurrentRank());
                    if (currentRankData != null) {
                        line = line.replace("%current_rank_display%", currentRankData.getDisplayName());
                    }
                }

                if (progress.getNextRank() != null) {
                    var nextRankData = rankupManager.getRanks().get(progress.getNextRank());
                    if (nextRankData != null) {
                        line = line.replace("%next_rank_display%", nextRankData.getDisplayName());
                    } else {
                        line = line.replace("%next_rank_display%", progress.getNextRank());
                    }
                } else {
                    line = line.replace("%next_rank_display%", "Rango máximo alcanzado");
                }

                processedLore.add(ChatColor.translateAlternateColorCodes('&',
                        replacePlaceholders(player, line)));
            }
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }

        // Encantado si está configurado
        if (config.getBoolean("enchanted", false)) {
            item = addGlow(item);
        }

        return item;
    }

    /**
     * Reproduce un sonido para el jugador
     */
    private void playSound(Player player, String soundKey) {
        if (!soundsEnabled) return;

        PlayerSettings settings = getPlayerSettings(player);
        if (!settings.soundsEnabled) return;

        try {
            String soundConfig = menuConfig.getString("sounds." + soundKey);
            if (soundConfig == null) return;

            String[] parts = soundConfig.split(":");
            if (parts.length >= 1) {
                Sound sound = Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;

                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error reproduciendo sonido '" + soundKey + "': " + e.getMessage());
        }
    }

    /**
     * Genera partículas para el jugador
     */
    private void spawnParticles(Player player, String particleKey) {
        if (!particlesEnabled) return;

        PlayerSettings settings = getPlayerSettings(player);
        if (!settings.effectsEnabled) return;

        try {
            ConfigurationSection particleConfig = menuConfig.getConfigurationSection("particles." + particleKey);
            if (particleConfig == null) return;

            String particleType = particleConfig.getString("type", "ENCHANTMENT_TABLE");
            int count = particleConfig.getInt("count", 10);
            double offsetX = particleConfig.getDouble("offset_x", 0.5);
            double offsetY = particleConfig.getDouble("offset_y", 0.5);
            double offsetZ = particleConfig.getDouble("offset_z", 0.5);

            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleType);
            player.getWorld().spawnParticle(particle,
                    player.getLocation().add(0, 1, 0),
                    count, offsetX, offsetY, offsetZ, 0.1);

        } catch (Exception e) {
            plugin.getLogger().warning("Error generando partículas '" + particleKey + "': " + e.getMessage());
        }
    }

    /**
     * Añade efecto de brillo a un ítem
     */
    private ItemStack addGlow(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error añadiendo brillo a ítem: " + e.getMessage());
        }
        return item;
    }

    /**
     * Formatea el nombre de un requisito
     */
    private String formatRequirementName(String type) {
        return switch (type.toLowerCase()) {
            case "money" -> "Dinero";
            case "level" -> "Nivel";
            case "playtime_hours" -> "Tiempo jugado";
            case "mob_kills" -> "Mobs eliminados";
            case "blocks_mined" -> "Bloques minados";
            case "farming_level" -> "Nivel de granjería";
            case "mining_level" -> "Nivel de minería";
            case "animals_bred" -> "Animales criados";
            case "fish_caught" -> "Peces pescados";
            case "ender_dragon_kills" -> "Ender Dragons";
            case "wither_kills" -> "Withers eliminados";
            default -> type.replace("_", " ");
        };
    }

    /**
     * Formatea el valor de un requisito
     */
    private String formatRequirementValue(double value, String type) {
        return switch (type.toLowerCase()) {
            case "money" -> String.format("$%,.0f", value);
            case "playtime_hours" -> String.format("%.1fh", value);
            case "farming_level", "mining_level" -> String.format("Lv.%.0f", value);
            default -> String.format("%,.0f", value);
        };
    }

    /**
     * Evalúa una condición
     */
    private boolean evaluateCondition(Player player, String condition) {
        return switch (condition.toLowerCase()) {
            case "prestige_enabled" -> rankupManager.isPrestigeEnabled();
            case "is_max_rank" -> {
                String currentRank = rankupManager.getCurrentRank(player);
                if (currentRank != null) {
                    var rankData = rankupManager.getRanks().get(currentRank);
                    yield rankData != null && rankData.getNextRank() == null;
                }
                yield false;
            }
            default -> true;
        };
    }

    /**
     * Obtiene la configuración de un jugador
     */
    private PlayerSettings getPlayerSettings(Player player) {
        return playerSettings.computeIfAbsent(player.getUniqueId(),
                k -> loadPlayerSettings(player));
    }

    /**
     * Carga la configuración de un jugador desde archivo
     */
    private PlayerSettings loadPlayerSettings(Player player) {
        File playerFile = new File(plugin.getDataFolder(), "players/" + player.getUniqueId() + "_menu.yml");

        if (playerFile.exists()) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
                PlayerSettings settings = new PlayerSettings();
                settings.notificationsEnabled = config.getBoolean("notifications", true);
                settings.effectsEnabled = config.getBoolean("effects", true);
                settings.soundsEnabled = config.getBoolean("sounds", true);
                return settings;
            } catch (Exception e) {
                plugin.getLogger().warning("Error cargando configuración de " + player.getName() + ": " + e.getMessage());
            }
        }

        return new PlayerSettings(); // Configuración por defecto
    }

    /**
     * Guarda la configuración de un jugador
     */
    private void savePlayerSettings(Player player, PlayerSettings settings) {
        try {
            File playersDir = new File(plugin.getDataFolder(), "players");
            if (!playersDir.exists()) {
                playersDir.mkdirs();
            }

            File playerFile = new File(playersDir, player.getUniqueId() + "_menu.yml");
            FileConfiguration config = new YamlConfiguration();

            config.set("notifications", settings.notificationsEnabled);
            config.set("effects", settings.effectsEnabled);
            config.set("sounds", settings.soundsEnabled);
            config.set("lastUpdated", System.currentTimeMillis());

            config.save(playerFile);

            // Actualizar caché
            playerSettings.put(player.getUniqueId(), settings);

        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando configuración de " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Crea configuración por defecto si no existe el archivo
     */
    private void createDefaultConfig() {
        try {
            menuConfig.set("menu_settings.enabled", true);
            menuConfig.set("menu_settings.enable_sounds", true);

            // Menú principal básico
            menuConfig.set("menus.main.title", "&5&l⭐ Sistema de Rangos ⭐");
            menuConfig.set("menus.main.size", 45);

            menuConfig.save(menuConfigFile);
            plugin.getLogger().info("✓ Configuración básica de menú creada");

        } catch (IOException e) {
            plugin.getLogger().severe("Error creando configuración por defecto: " + e.getMessage());
        }
    }

    /**
     * Inicia la tarea de actualización automática
     */
    private void startAutoRefreshTask() {
        if (autoRefreshInterval <= 0) return;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Limpiar caché expirado
            long currentTime = System.currentTimeMillis();
            menuCache.entrySet().removeIf(entry ->
                    currentTime - entry.getValue().getCreationTime() > (cacheTimeout * 1000L));

            // Actualizar menús abiertos si es necesario
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory() != null) {
                    String title = player.getOpenInventory().getTitle();
                    if (determineMenuType(title) != null) {
                        // Refrescar menú si está configurado para auto-refresh
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Solo refrescar si el jugador sigue teniendo el menú abierto
                            if (player.getOpenInventory() != null &&
                                    player.getOpenInventory().getTitle().equals(title)) {
                                refreshPlayerMenu(player, title);
                            }
                        });
                    }
                }
            }
        }, autoRefreshInterval * 20L, autoRefreshInterval * 20L);
    }

    /**
     * Refresca el menú de un jugador
     */
    private void refreshPlayerMenu(Player player, String menuTitle) {
        try {
            String menuType = determineMenuType(menuTitle);
            if (menuType == null) return;

            // Limpiar caché para este jugador
            menuCache.remove(player.getUniqueId());

            // Reabrir menú correspondiente
            switch (menuType) {
                case "main" -> openMainMenu(player);
                case "progress" -> openProgressMenu(player);
                case "ranks_list" -> openRanksListMenu(player);
                case "settings" -> openSettingsMenu(player);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error refrescando menú para " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Recarga la configuración del menú
     */
    public void reloadConfig() {
        try {
            menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
            loadConfiguration();

            // Limpiar caché
            menuCache.clear();

            plugin.getLogger().info("✓ Configuración de menús recargada");

        } catch (Exception e) {
            plugin.getLogger().severe("Error recargando configuración de menús: " + e.getMessage());
        }
    }

    /**
     * Limpia los datos de un jugador cuando se desconecta
     */
    public void cleanupPlayer(Player player) {
        menuCache.remove(player.getUniqueId());
        // No remover playerSettings para mantener configuración persistente
    }

    /**
     * Obtiene estadísticas del sistema de menús
     */
    public Map<String, Object> getMenuStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("menuEnabled", menuEnabled);
        stats.put("cachingEnabled", cachingEnabled);
        stats.put("cachedMenus", menuCache.size());
        stats.put("playerSettings", playerSettings.size());
        stats.put("autoRefreshInterval", autoRefreshInterval);

        return stats;
    }

    // =================== CLASES INTERNAS ===================

    /**
     * Estados posibles de un rango
     */
    private enum RankState {
        CURRENT,    // Rango actual del jugador
        COMPLETED,  // Rango ya superado
        LOCKED      // Rango bloqueado/futuro
    }

    /**
     * Menú en caché
     */
    private static class CachedMenu {
        private final Inventory inventory;
        private final long creationTime;

        public CachedMenu(Inventory inventory, long creationTime) {
            this.inventory = inventory;
            this.creationTime = creationTime;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public boolean isValid() {
            return System.currentTimeMillis() - creationTime < 60000; // 1 minuto
        }
    }

    /**
     * Configuración personal del jugador
     */
    private static class PlayerSettings {
        public boolean notificationsEnabled = true;
        public boolean effectsEnabled = true;
        public boolean soundsEnabled = true;
    }

    /**
     * Crea un archivo de configuración básico
     */
    private boolean createBasicMenuConfig() {
        try {
            String basicConfig = """
                    # =======================================================
                    #           MENÚ INTERACTIVO DE RANKUP - CONFIGURACIÓN BÁSICA
                    # =======================================================
                                
                    menu_settings:
                      enabled: true
                      auto_refresh_interval: -1
                      enable_sounds: true
                      enable_particles: true
                      close_after_rankup: false
                                
                    menus:
                      main:
                        title: "&5&l⭐ Sistema de Rangos ⭐"
                        size: 45
                        background:
                          enabled: true
                          material: "BLACK_STAINED_GLASS_PANE"
                          name: "&r"
                        
                        items:
                          player_info:
                            slot: 13
                            material: "PLAYER_HEAD"
                            skull_owner: "%player%"
                            name: "&e&l%player%"
                            lore:
                              - ""
                              - "&7▶ Rango actual: %score_rank_display%"
                              - "&7▶ Siguiente rango: %score_next_rank%"
                              - "&7▶ Progreso: &e%score_rankup_progress%"
                              - ""
                            sound: "BLOCK_NOTE_BLOCK_BELL:0.8:1.2"
                          
                          rankup_button:
                            slot: 20
                            material: "DIAMOND"
                            name: "&a&l⬆ HACER RANKUP"
                            lore:
                              - ""
                              - "&fHaz clic para intentar subir de rango"
                              - ""
                              - "&7Estado: %rankup_status%"
                              - "&7Progreso: &e%score_rankup_progress%"
                              - ""
                              - "&a✓ Haz clic para rankup"
                            enchanted: true
                            sound: "ENTITY_PLAYER_LEVELUP:1.0:1.0"
                            cooldown_material: "CLOCK"
                            cooldown_name: "&c&l⏰ RANKUP EN COOLDOWN"
                            cooldown_lore:
                              - ""
                              - "&cDebes esperar para hacer rankup"
                              - "&cTiempo restante: &e%cooldown_time%"
                              - ""
                            blocked_material: "BARRIER"
                            blocked_name: "&c&l❌ RANKUP NO DISPONIBLE"
                            blocked_lore:
                              - ""
                              - "&cNo cumples los requisitos necesarios"
                              - "&cRevisa tu progreso para más detalles"
                              - ""
                          
                          progress_button:
                            slot: 22
                            material: "BOOK"
                            name: "&b&l📊 VER MI PROGRESO"
                            lore:
                              - ""
                              - "&fMuestra tu progreso detallado"
                              - "&fhacia el siguiente rango"
                              - ""
                              - "&7Progreso actual: &e%score_rankup_progress%"
                              - ""
                              - "&b✓ Haz clic para ver detalles"
                            sound: "BLOCK_ENCHANTMENT_TABLE_USE:0.8:1.0"
                          
                          ranks_list_button:
                            slot: 24
                            material: "ENCHANTED_BOOK"
                            name: "&2&l📋 LISTA DE RANGOS"
                            lore:
                              - ""
                              - "&fVe todos los rangos disponibles"
                              - "&fen el servidor y sus requisitos"
                              - ""
                              - "&7Total de rangos: &a%score_total_ranks%"
                              - "&7Tu posición: &e#%score_rank_order%"
                              - ""
                              - "&2✓ Haz clic para explorar"
                            sound: "BLOCK_NOTE_BLOCK_CHIME:0.8:1.0"
                                
                      progress:
                        title: "&b&l📊 Mi Progreso Detallado"
                        size: 54
                        background:
                          enabled: true
                          material: "BLUE_STAINED_GLASS_PANE"
                          name: "&r"
                        
                        items:
                          progress_info:
                            slot: 22
                            material: "EXPERIENCE_BOTTLE"
                            name: "&b&l📊 Tu Progreso General"
                            lore:
                              - ""
                              - "&7Rango actual: &e%score_rank_display%"
                              - "&7Siguiente rango: &a%score_next_rank%"
                              - ""
                              - "&7Progreso: &e%score_rankup_progress%"
                              - "%score_rankup_progress_bar%"
                              - ""
                            enchanted: true
                            sound: "ENTITY_EXP_ORB_PICKUP:1.0:1.0"
                          
                          back_button:
                            slot: 49
                            material: "ARROW"
                            name: "&e&l⬅ VOLVER"
                            lore:
                              - ""
                              - "&7Regresa al menú principal"
                            sound: "UI_BUTTON_CLICK:0.8:1.0"
                          
                          requirements_start_slot: 28
                          requirements_pattern: "horizontal"
                          
                          requirement_template:
                            completed_material: "LIME_STAINED_GLASS_PANE"
                            incomplete_material: "RED_STAINED_GLASS_PANE"
                            completed_name: "&a&l✓ %requirement_name%"
                            incomplete_name: "&c&l✗ %requirement_name%"
                            completed_lore:
                              - ""
                              - "&7Progreso: &a%current_value%/%required_value%"
                              - "&a✓ Requisito completado"
                            incomplete_lore:
                              - ""
                              - "&7Progreso: &c%current_value%/%required_value%"
                              - "&7Te faltan: &e%missing_value%"
                              - ""
                              - "&c✗ Requisito pendiente"
                                
                      ranks_list:
                        title: "&2&l📋 Lista de Rangos"
                        size: 54
                        background:
                          enabled: true
                          material: "GREEN_STAINED_GLASS_PANE"
                          name: "&r"
                        
                        items:
                          back_button:
                            slot: 49
                            material: "ARROW"
                            name: "&e&l⬅ VOLVER"
                            lore:
                              - ""
                              - "&7Regresa al menú principal"
                            sound: "UI_BUTTON_CLICK:0.8:1.0"
                          
                          ranks_start_slot: 10
                          ranks_pattern: "grid"
                          
                          rank_template:
                            current_material: "EMERALD"
                            completed_material: "DIAMOND"
                            locked_material: "IRON_INGOT"
                            current_name: "&a&l► %rank_display_name%"
                            completed_name: "&e&l✓ %rank_display_name%"
                            locked_name: "&7%rank_display_name%"
                            current_lore:
                              - ""
                              - "&a▶ Tu rango actual"
                              - "&7Orden: &e#%rank_order%"
                              - ""
                              - "&7Siguiente: %next_rank%"
                            completed_lore:
                              - ""
                              - "&e✓ Rango ya superado"
                              - "&7Orden: &e#%rank_order%"
                              - ""
                            locked_lore:
                              - ""
                              - "&7Rango bloqueado"
                              - "&7Orden: &e#%rank_order%"
                              - ""
                            enchanted_current: true
                            enchanted_completed: false
                            enchanted_locked: false
                                
                    requirement_materials:
                      money: "GOLD_INGOT"
                      level: "EXPERIENCE_BOTTLE"
                      playtime_hours: "CLOCK"
                      mob_kills: "IRON_SWORD"
                      blocks_mined: "DIAMOND_PICKAXE"
                      farming_level: "WHEAT"
                      mining_level: "STONE_PICKAXE"
                      animals_bred: "WHEAT_SEEDS"
                      fish_caught: "FISHING_ROD"
                      ender_dragon_kills: "DRAGON_HEAD"
                      wither_kills: "WITHER_SKELETON_SKULL"
                      permission: "PAPER"
                      default: "ITEM_FRAME"
                                
                    sounds:
                      menu_open: "BLOCK_ENDER_CHEST_OPEN:0.8:1.0"
                      menu_close: "BLOCK_ENDER_CHEST_CLOSE:0.5:1.0"
                      button_click: "UI_BUTTON_CLICK:0.8:1.0"
                      rankup_success: "UI_TOAST_CHALLENGE_COMPLETE:1.0:1.0"
                      rankup_fail: "BLOCK_NOTE_BLOCK_BASS:1.0:0.5"
                      page_turn: "ITEM_BOOK_PAGE_TURN:1.0:1.0"
                      toggle_on: "BLOCK_LEVER_CLICK:1.0:1.2"
                      toggle_off: "BLOCK_LEVER_CLICK:1.0:0.8"
                                
                    messages:
                      menu_opened: "&a✓ Menú de rangos abierto"
                      menu_closed: "&7Menú cerrado"
                      feature_not_available: "&c❌ Esta característica no está disponible"
                      coming_soon: "&e⚠ Próximamente disponible..."
                      
                      status:
                        ready_for_rankup: "&a¡Listo para rankup!"
                        requirements_pending: "&e%pending% requisitos pendientes"
                        max_rank_reached: "&d¡Rango máximo alcanzado!"
                        cooldown_active: "&cEn cooldown: %time%"
                                
                    advanced:
                      cache_menus: true
                      cache_duration_seconds: 60
                      auto_update_placeholders: true
                      placeholder_update_interval: 5
                      validate_config: true
                      debug_menus: false
                      max_concurrent_menus: 50
                    """;

            // Escribir archivo
            java.nio.file.Files.writeString(menuConfigFile.toPath(), basicConfig,
                    java.nio.charset.StandardCharsets.UTF_8);

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error creando configuración básica de menú: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}