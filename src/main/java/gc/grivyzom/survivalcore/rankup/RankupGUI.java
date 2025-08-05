package gc.grivyzom.survivalcore.rankup;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interfaz gráfica para el sistema de rankup 2.0 - CON INVENTARIOS BÁSICOS
 * Restaura funcionalidad de GUI para el comando /ranks
 *
 * @author Brocolitx
 * @version 2.1 - Con inventarios básicos funcionales
 */
public class RankupGUI {

    /**
     * Abre el menú principal del sistema de rangos (GUI real)
     * ESTE MÉTODO ES LLAMADO POR /ranks
     */
    public static void openMainMenu(Player player, Main plugin) {
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        try {
            Inventory gui = createMainMenuInventory(player, plugin);
            player.openInventory(gui);

            // Sonido de apertura
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo GUI principal: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo menú. Usa /rankup para comandos básicos.");
        }
    }

    /**
     * Abre el menú de progreso (GUI real)
     */
    public static void openProgressMenu(Player player, Main plugin) {
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        try {
            // Obtener progreso del jugador
            plugin.getRankupManager().getPlayerProgress(player).thenAccept(progress -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Inventory gui = createProgressMenuInventory(player, plugin, progress);
                        player.openInventory(gui);
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error creando GUI de progreso: " + e.getMessage());
                        player.sendMessage(ChatColor.RED + "❌ Error abriendo progreso. Usa /rankup progress");
                    }
                });
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú de progreso: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo progreso. Usa /rankup progress");
        }
    }

    /**
     * Abre la lista de rangos (GUI real)
     */
    public static void openRanksList(Player player, Main plugin) {
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        try {
            Inventory gui = createRanksListInventory(player, plugin);
            player.openInventory(gui);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo lista de rangos: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error abriendo lista. Usa /rankup list");
        }
    }

    // =================== CREACIÓN DE INVENTARIOS ===================

    /**
     * Crea el inventario del menú principal
     */
    private static Inventory createMainMenuInventory(Player player, Main plugin) {
        RankupManager rankupManager = plugin.getRankupManager();

        // Crear inventario
        Inventory gui = Bukkit.createInventory(null, 45, ChatColor.DARK_PURPLE + "⭐ Sistema de Rangos ⭐");

        // Llenar bordes con cristal negro
        fillBorders(gui, Material.BLACK_STAINED_GLASS_PANE, " ");

        try {
            // Información del jugador (centro-arriba)
            ItemStack playerInfo = createPlayerInfoItem(player, rankupManager);
            gui.setItem(13, playerInfo);

            // Botón de Rankup (centro-izquierda)
            ItemStack rankupButton = createRankupButton(player, rankupManager);
            gui.setItem(20, rankupButton);

            // Ver Progreso (centro)
            ItemStack progressButton = createProgressButton(player);
            gui.setItem(22, progressButton);

            // Lista de Rangos (centro-derecha)
            ItemStack ranksButton = createRanksListButton(player, rankupManager);
            gui.setItem(24, ranksButton);

            // Información del Sistema (abajo-centro)
            ItemStack infoButton = createSystemInfoButton();
            gui.setItem(40, infoButton);

        } catch (Exception e) {
            plugin.getLogger().warning("Error creando items del menú principal: " + e.getMessage());
        }

        return gui;
    }

    /**
     * Crea el inventario del menú de progreso
     */
    private static Inventory createProgressMenuInventory(Player player, Main plugin,
                                                         RankupManager.RankupProgress progress) {
        // Crear inventario
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.BLUE + "📊 Mi Progreso Detallado");

        // Llenar bordes
        fillBorders(gui, Material.BLUE_STAINED_GLASS_PANE, " ");

        try {
            // Información general de progreso (centro-arriba)
            ItemStack progressInfo = createProgressInfoItem(player, progress);
            gui.setItem(13, progressInfo);

            // Mostrar requisitos
            if (progress.getRequirements() != null && !progress.getRequirements().isEmpty()) {
                List<RankupManager.RequirementProgress> requirements = new ArrayList<>(progress.getRequirements().values());

                // Posiciones para requisitos (centro del inventario)
                int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

                for (int i = 0; i < Math.min(requirements.size(), slots.length); i++) {
                    ItemStack reqItem = createRequirementItem(requirements.get(i));
                    gui.setItem(slots[i], reqItem);
                }
            }

            // Botón volver
            ItemStack backButton = createBackButton("Volver al menú principal");
            gui.setItem(49, backButton);

        } catch (Exception e) {
            plugin.getLogger().warning("Error creando menú de progreso: " + e.getMessage());
        }

        return gui;
    }

    /**
     * Crea el inventario de lista de rangos
     */
    private static Inventory createRanksListInventory(Player player, Main plugin) {
        RankupManager rankupManager = plugin.getRankupManager();

        // Crear inventario
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GREEN + "📋 Lista de Rangos");

        // Llenar bordes
        fillBorders(gui, Material.GREEN_STAINED_GLASS_PANE, " ");

        try {
            String currentRank = rankupManager.getCurrentRank(player);
            Map<String, RankupManager.SimpleRankData> ranks = rankupManager.getRanks();

            // Ordenar rangos por orden
            List<RankupManager.SimpleRankData> sortedRanks = ranks.values().stream()
                    .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                    .toList();

            // Posiciones para rangos
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

            for (int i = 0; i < Math.min(sortedRanks.size(), slots.length); i++) {
                RankupManager.SimpleRankData rankData = sortedRanks.get(i);
                ItemStack rankItem = createRankItem(rankData, currentRank);
                gui.setItem(slots[i], rankItem);
            }

            // Botón volver
            ItemStack backButton = createBackButton("Volver al menú principal");
            gui.setItem(49, backButton);

        } catch (Exception e) {
            plugin.getLogger().warning("Error creando lista de rangos: " + e.getMessage());
        }

        return gui;
    }

    // =================== CREACIÓN DE ITEMS ===================

    /**
     * Crea el item de información del jugador
     */
    private static ItemStack createPlayerInfoItem(Player player, RankupManager rankupManager) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.YELLOW + "👤 " + player.getName());

            List<String> lore = new ArrayList<>();
            lore.add("");

            try {
                String currentRank = rankupManager.getCurrentRank(player);
                if (currentRank != null) {
                    var rankData = rankupManager.getRanks().get(currentRank);
                    String displayName = rankData != null ? rankData.getDisplayName() : currentRank;

                    lore.add(ChatColor.GRAY + "🏆 Rango actual: " + displayName);

                    if (rankData != null && rankData.hasNextRank()) {
                        var nextRankData = rankupManager.getRanks().get(rankData.getNextRank());
                        String nextDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankData.getNextRank();
                        lore.add(ChatColor.GRAY + "⬆️ Siguiente: " + nextDisplay);
                    } else {
                        lore.add(ChatColor.LIGHT_PURPLE + "⭐ ¡Rango máximo alcanzado!");
                    }
                } else {
                    lore.add(ChatColor.RED + "⚠️ Error detectando rango");
                }
            } catch (Exception e) {
                lore.add(ChatColor.RED + "⚠️ Error obteniendo información");
            }

            lore.add("");
            lore.add(ChatColor.GOLD + "Esta es tu información actual");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea el botón de rankup
     */
    private static ItemStack createRankupButton(Player player, RankupManager rankupManager) {
        ItemStack item;
        ItemMeta meta;

        // Verificar estado del rankup
        if (rankupManager.isOnCooldown(player.getUniqueId())) {
            // En cooldown
            item = new ItemStack(Material.CLOCK);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "⏰ RANKUP EN COOLDOWN");

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.RED + "Debes esperar para hacer rankup");
                long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
                lore.add(ChatColor.YELLOW + "Tiempo restante: " + (remaining / 1000) + " segundos");
                lore.add("");
                lore.add(ChatColor.GRAY + "Intenta de nuevo en unos momentos");

                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        } else {
            // Disponible para rankup
            item = new ItemStack(Material.DIAMOND);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + "⬆️ HACER RANKUP");

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.WHITE + "Haz clic para intentar subir de rango");
                lore.add("");
                lore.add(ChatColor.GRAY + "✓ Sistema verificará automáticamente");
                lore.add(ChatColor.GRAY + "  todos los requisitos necesarios");
                lore.add("");
                lore.add(ChatColor.GREEN + "▶ Clic para rankup");

                meta.setLore(lore);

                // Añadir brillo
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

                item.setItemMeta(meta);
            }
        }

        return item;
    }

    /**
     * Crea el botón de progreso
     */
    private static ItemStack createProgressButton(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + "📊 VER MI PROGRESO");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.WHITE + "Muestra tu progreso detallado");
            lore.add(ChatColor.WHITE + "hacia el siguiente rango");
            lore.add("");
            lore.add(ChatColor.GRAY + "• Requisitos completados");
            lore.add(ChatColor.GRAY + "• Requisitos pendientes");
            lore.add(ChatColor.GRAY + "• Porcentaje de progreso");
            lore.add("");
            lore.add(ChatColor.BLUE + "▶ Clic para ver detalles");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea el botón de lista de rangos
     */
    private static ItemStack createRanksListButton(Player player, RankupManager rankupManager) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "📋 LISTA DE RANGOS");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.WHITE + "Ve todos los rangos disponibles");
            lore.add(ChatColor.WHITE + "en el servidor");
            lore.add("");

            try {
                int totalRanks = rankupManager.getRanks().size();
                lore.add(ChatColor.GRAY + "Total de rangos: " + ChatColor.GREEN + totalRanks);

                String currentRank = rankupManager.getCurrentRank(player);
                if (currentRank != null) {
                    var rankData = rankupManager.getRanks().get(currentRank);
                    if (rankData != null) {
                        lore.add(ChatColor.GRAY + "Tu posición: " + ChatColor.YELLOW + "#" + rankData.getOrder());
                    }
                }
            } catch (Exception e) {
                lore.add(ChatColor.GRAY + "Información no disponible");
            }

            lore.add("");
            lore.add(ChatColor.GREEN + "▶ Clic para explorar");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea el item de información del sistema
     */
    private static ItemStack createSystemInfoButton() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + "ℹ️ INFORMACIÓN DEL SISTEMA");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.WHITE + "Sistema de rangos SurvivalCore");
            lore.add(ChatColor.GRAY + "• Integración con LuckPerms");
            lore.add(ChatColor.GRAY + "• Menús interactivos");
            lore.add(ChatColor.GRAY + "• Progreso en tiempo real");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Comandos adicionales:");
            lore.add(ChatColor.GRAY + "• /rankup - Rankup directo");
            lore.add(ChatColor.GRAY + "• /rankup progress - Progreso en chat");
            lore.add(ChatColor.GRAY + "• /rankup help - Ayuda");
            lore.add("");
            lore.add(ChatColor.GOLD + "▶ Clic para más información");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea item de información de progreso
     */
    private static ItemStack createProgressInfoItem(Player player, RankupManager.RankupProgress progress) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + "📊 Tu Progreso General");

            List<String> lore = new ArrayList<>();
            lore.add("");

            if (progress.getCurrentRank() != null) {
                lore.add(ChatColor.GRAY + "Rango actual: " + ChatColor.YELLOW + progress.getCurrentRank());
            }

            if (progress.getNextRank() != null) {
                lore.add(ChatColor.GRAY + "Siguiente rango: " + ChatColor.GREEN + progress.getNextRank());
                lore.add("");

                double percentage = progress.getOverallProgress();
                lore.add(ChatColor.GRAY + "Progreso total: " + ChatColor.YELLOW + String.format("%.1f%%", percentage));

                // Barra de progreso simple
                String progressBar = createSimpleProgressBar(percentage, 20);
                lore.add(progressBar);

            } else {
                lore.add(ChatColor.LIGHT_PURPLE + "¡Rango máximo alcanzado!");
            }

            lore.add("");
            lore.add(ChatColor.GOLD + "Información detallada de tu progreso");

            meta.setLore(lore);

            // Añadir brillo
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea item de requisito
     */
    private static ItemStack createRequirementItem(RankupManager.RequirementProgress reqProgress) {
        Material material = reqProgress.isCompleted() ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String status = reqProgress.isCompleted() ?
                    ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ";
            String reqName = formatRequirementName(reqProgress.getType());

            meta.setDisplayName(status + reqName);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Progreso: " + formatRequirementValue(reqProgress.getCurrent(), reqProgress.getType()) +
                    "/" + formatRequirementValue(reqProgress.getRequired(), reqProgress.getType()));

            if (reqProgress.isCompleted()) {
                lore.add(ChatColor.GREEN + "✓ Requisito completado");
            } else {
                double missing = reqProgress.getRequired() - reqProgress.getCurrent();
                lore.add(ChatColor.RED + "Te faltan: " + formatRequirementValue(missing, reqProgress.getType()));
            }

            lore.add("");
            double percentage = (reqProgress.getCurrent() / reqProgress.getRequired()) * 100;
            lore.add(ChatColor.GRAY + "Porcentaje: " + ChatColor.YELLOW + String.format("%.1f%%", Math.min(percentage, 100)));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea item de rango
     */
    private static ItemStack createRankItem(RankupManager.SimpleRankData rankData, String currentRank) {
        Material material;
        String prefix;

        if (rankData.getId().equals(currentRank)) {
            material = Material.EMERALD;
            prefix = ChatColor.GREEN + "► ";
        } else {
            material = Material.IRON_INGOT;
            prefix = ChatColor.GRAY + "";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(prefix + rankData.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + rankData.getId());
            lore.add(ChatColor.GRAY + "Orden: " + ChatColor.YELLOW + "#" + rankData.getOrder());

            if (rankData.hasNextRank()) {
                lore.add(ChatColor.GRAY + "Siguiente: " + ChatColor.GREEN + rankData.getNextRank());
            } else {
                lore.add(ChatColor.LIGHT_PURPLE + "¡Rango máximo!");
            }

            lore.add("");

            if (rankData.getId().equals(currentRank)) {
                lore.add(ChatColor.GREEN + "★ Tu rango actual");
                // Añadir brillo
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(ChatColor.GRAY + "Rango del servidor");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea botón de volver
     */
    private static ItemStack createBackButton(String tooltip) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "⬅ VOLVER");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + tooltip);
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Clic para volver");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Llena los bordes del inventario
     */
    private static void fillBorders(Inventory inv, Material material, String name) {
        ItemStack borderItem = new ItemStack(material);
        ItemMeta meta = borderItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            borderItem.setItemMeta(meta);
        }

        int size = inv.getSize();

        // Primera y última fila
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            if (size > 9) {
                inv.setItem(size - 9 + i, borderItem);
            }
        }

        // Columnas laterales
        for (int row = 1; row < (size / 9) - 1; row++) {
            inv.setItem(row * 9, borderItem);
            inv.setItem(row * 9 + 8, borderItem);
        }
    }

    /**
     * Crea barra de progreso simple
     */
    private static String createSimpleProgressBar(double percentage, int length) {
        int filled = (int) Math.round(percentage / 100.0 * length);
        filled = Math.max(0, Math.min(filled, length));

        StringBuilder bar = new StringBuilder();
        String color = getProgressColor(percentage);

        bar.append(color);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("▓");
        }

        return bar.toString();
    }

    /**
     * Obtiene color de progreso
     */
    private static String getProgressColor(double percentage) {
        if (percentage >= 100.0) return ChatColor.GREEN.toString();
        if (percentage >= 75.0) return ChatColor.YELLOW.toString();
        if (percentage >= 50.0) return ChatColor.GOLD.toString();
        if (percentage >= 25.0) return ChatColor.RED.toString();
        return ChatColor.DARK_RED.toString();
    }

    /**
     * Formatea nombre de requisito
     */
    private static String formatRequirementName(String type) {
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
     * Formatea valor de requisito
     */
    private static String formatRequirementValue(double value, String type) {
        return switch (type.toLowerCase()) {
            case "money" -> String.format("$%,.0f", value);
            case "playtime_hours" -> String.format("%.1fh", value);
            case "farming_level", "mining_level" -> String.format("Lv.%.0f", value);
            default -> String.format("%,.0f", value);
        };
    }

    // =================== MÉTODOS DE INFORMACIÓN (COMPATIBILIDAD) ===================

    /**
     * Muestra información del sistema como texto
     */
    public static void showClientInfo(Player player, Main plugin) {
        player.sendMessage(ChatColor.AQUA + "═══ INFORMACIÓN DEL SISTEMA ═══");
        player.sendMessage(ChatColor.WHITE + "🖥️ Sistema: " + ChatColor.YELLOW + "Menús GUI + Comandos");
        player.sendMessage(ChatColor.WHITE + "📋 Tipo: " + ChatColor.GREEN + "Inventarios interactivos");
        player.sendMessage(ChatColor.WHITE + "🔧 Estado: " + ChatColor.GREEN + "Funcional");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Comandos principales:");
        player.sendMessage(ChatColor.GRAY + "  • /ranks - Abrir menú GUI");
        player.sendMessage(ChatColor.GRAY + "  • /rankup - Hacer rankup directo");
        player.sendMessage(ChatColor.GRAY + "  • /rankup progress - Ver progreso detallado");
        player.sendMessage(ChatColor.GRAY + "  • /rankup list - Lista de rangos en chat");
    }

    public static boolean isMenuSystemAvailable(Main plugin) {
        return plugin.isRankupSystemEnabled();
    }

    public static boolean isHybridMenuSystemAvailable(Main plugin) {
        return false; // Sin sistema híbrido
    }

    public static String getSystemStatus(Main plugin) {
        if (!plugin.isRankupSystemEnabled()) {
            return ChatColor.RED + "❌ Sistema deshabilitado";
        }
        return ChatColor.GREEN + "✅ Sistema GUI básico funcionando";
    }

    public static String getHybridMenuSystemStatus(Main plugin) {
        return getSystemStatus(plugin);
    }

    public static Map<String, Object> getSystemStats(Main plugin) {
        Map<String, Object> stats = new HashMap<>();

        if (!plugin.isRankupSystemEnabled()) {
            stats.put("status", "DISABLED");
            return stats;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            stats.put("status", "GUI_BASIC");
            stats.put("ranksAvailable", rankupManager.getRanks().size());
            stats.put("systemType", "INVENTORY_BASED");
            stats.put("hybridSystemAvailable", false);
            stats.put("menuSystemAvailable", true);
            stats.put("placeholderAPIEnabled", rankupManager.isPlaceholderAPIEnabled());
            stats.put("cooldownTime", rankupManager.getCooldownTime());
            stats.put("effectsEnabled", rankupManager.areEffectsEnabled());
            stats.put("broadcastEnabled", rankupManager.isBroadcastEnabled());

        } catch (Exception e) {
            stats.put("status", "ERROR");
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    public static Map<String, Object> getHybridSystemStats(Main plugin) {
        Map<String, Object> stats = new HashMap<>();

        if (!plugin.isRankupSystemEnabled()) {
            stats.put("status", "RANKUP_DISABLED");
            return stats;
        }

        stats.put("status", "BASIC_GUI_SYSTEM");
        stats.put("hybridSystemAvailable", false);
        stats.put("menuSystemAvailable", true);
        stats.put("bedrockGuiDetected", false);
        stats.put("javaMenusAvailable", true);
        stats.put("systemType", "BASIC_INVENTORY_MENUS");

        try {
            RankupManager rankupManager = plugin.getRankupManager();
            stats.put("ranksCount", rankupManager.getRanks().size());
            stats.put("placeholderAPI", rankupManager.isPlaceholderAPIEnabled());
        } catch (Exception e) {
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}