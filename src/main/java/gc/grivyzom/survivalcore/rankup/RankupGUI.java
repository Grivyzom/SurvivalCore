package gc.grivyzom.survivalcore.rankup;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.RankupManager.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Interfaz gráfica para el sistema de rankup.
 * Proporciona menús interactivos para gestionar rangos y ver progreso.
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RankupGUI {

    private static final String MAIN_MENU_TITLE = ChatColor.DARK_PURPLE + "Sistema de Rangos";
    private static final String PROGRESS_MENU_TITLE = ChatColor.BLUE + "Mi Progreso";
    private static final String RANKS_LIST_TITLE = ChatColor.GREEN + "Lista de Rangos";

    /**
     * Abre el menú principal del sistema de rangos
     */
    public static void openMainMenu(Player player, Main plugin) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);

        // Llenar con vidrio decorativo
        fillBorders(inv, Material.BLACK_STAINED_GLASS_PANE);

        RankupManager rankupManager = plugin.getRankupManager();
        String currentRank = rankupManager.getCurrentRank(player);

        // Cabeza del jugador con información del rango
        ItemStack playerHead = createPlayerHead(player, currentRank, rankupManager);
        inv.setItem(13, playerHead);

        // Botón de Rankup
        ItemStack rankupButton = createRankupButton(player, rankupManager);
        inv.setItem(10, rankupButton);

        // Botón de Progreso
        ItemStack progressButton = createProgressButton();
        inv.setItem(12, progressButton);

        // Botón de Lista de Rangos
        ItemStack ranksListButton = createRanksListButton();
        inv.setItem(14, ranksListButton);

        // Botón de Prestige (si está habilitado)
        if (rankupManager.isPrestigeEnabled()) {
            ItemStack prestigeButton = createPrestigeButton();
            inv.setItem(16, prestigeButton);
        }

        // Botón de Historial
        ItemStack historyButton = createHistoryButton();
        inv.setItem(22, historyButton);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
    }

    /**
     * Abre el menú de progreso detallado
     */
    public static void openProgressMenu(Player player, Main plugin) {
        RankupManager rankupManager = plugin.getRankupManager();

        rankupManager.getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, PROGRESS_MENU_TITLE);
                fillBorders(inv, Material.BLUE_STAINED_GLASS_PANE);

                if (progress.getCurrentRank() == null) {
                    // Error obteniendo progreso
                    ItemStack errorItem = createErrorItem("No se pudo obtener tu progreso");
                    inv.setItem(22, errorItem);
                } else {
                    // Información general
                    ItemStack infoItem = createProgressInfoItem(progress, rankupManager);
                    inv.setItem(22, infoItem);

                    // Requisitos individuales
                    displayRequirements(inv, progress);
                }

                // Botón de volver
                ItemStack backButton = createBackButton();
                inv.setItem(49, backButton);

                player.openInventory(inv);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            });
        });
    }

    /**
     * Abre la lista de todos los rangos
     */
    public static void openRanksList(Player player, Main plugin) {
        RankupManager rankupManager = plugin.getRankupManager();
        Map<String, RankupData> rankups = rankupManager.getRankups();

        if (rankups.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay rangos configurados.");
            return;
        }

        // Calcular el tamaño necesario
        int size = Math.min(54, ((rankups.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(null, size, RANKS_LIST_TITLE);

        fillBorders(inv, Material.GREEN_STAINED_GLASS_PANE);

        // Ordenar rangos por orden
        List<RankupData> sortedRanks = rankups.values().stream()
                .sorted(Comparator.comparingInt(RankupData::getOrder))
                .collect(Collectors.toList());

        String currentRank = rankupManager.getCurrentRank(player);
        int slot = 10;

        for (RankupData rankData : sortedRanks) {
            if (slot % 9 == 8) slot += 2; // Saltar bordes
            if (slot >= size - 9) break; // No exceder el inventario

            ItemStack rankItem = createRankDisplayItem(rankData, currentRank);
            inv.setItem(slot, rankItem);
            slot++;
        }

        // Botón de volver
        ItemStack backButton = createBackButton();
        inv.setItem(size - 5, backButton);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f);
    }

    /**
     * Crea la cabeza del jugador con información del rango
     */
    private static ItemStack createPlayerHead(Player player, String currentRank, RankupManager rankupManager) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.YELLOW + player.getName());

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (currentRank != null) {
            RankupData rankData = rankupManager.getRankups().get(currentRank);
            String displayName = rankData != null ? rankData.getDisplayName() : currentRank;
            lore.add(ChatColor.WHITE + "Rango actual: " + displayName);

            if (rankData != null && rankData.hasNextRank()) {
                RankupData nextRankData = rankupManager.getRankups().get(rankData.getNextRank());
                String nextDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankData.getNextRank();
                lore.add(ChatColor.WHITE + "Siguiente: " + nextDisplay);
            } else {
                lore.add(ChatColor.LIGHT_PURPLE + "¡Rango máximo alcanzado!");
            }
        } else {
            lore.add(ChatColor.RED + "Rango no detectado");
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Haz clic en los botones para navegar");

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Crea el botón de rankup
     */
    private static ItemStack createRankupButton(Player player, RankupManager rankupManager) {
        Material material = Material.DIAMOND;
        String name = ChatColor.GREEN + "⬆ Hacer Rankup";
        List<String> lore = new ArrayList<>();

        // Verificar cooldown
        if (rankupManager.isOnCooldown(player.getUniqueId())) {
            material = Material.CLOCK;
            name = ChatColor.RED + "⏰ Rankup en Cooldown";
            long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
            lore.add(ChatColor.GRAY + "Espera " + (remaining / 1000) + " segundos");
        } else {
            lore.add(ChatColor.WHITE + "Haz clic para intentar rankup");
            lore.add(ChatColor.GRAY + "Verifica que cumples todos");
            lore.add(ChatColor.GRAY + "los requisitos necesarios");
        }

        return createGuiItem(material, name, lore);
    }

    /**
     * Crea el botón de progreso
     */
    private static ItemStack createProgressButton() {
        return createGuiItem(
                Material.BOOK,
                ChatColor.AQUA + "📊 Ver Mi Progreso",
                Arrays.asList(
                        ChatColor.WHITE + "Muestra tu progreso detallado",
                        ChatColor.WHITE + "hacia el siguiente rango",
                        "",
                        ChatColor.YELLOW + "¡Haz clic para ver!"
                )
        );
    }

    /**
     * Crea el botón de lista de rangos
     */
    private static ItemStack createRanksListButton() {
        return createGuiItem(
                Material.ENCHANTED_BOOK,
                ChatColor.GREEN + "📋 Lista de Rangos",
                Arrays.asList(
                        ChatColor.WHITE + "Ve todos los rangos",
                        ChatColor.WHITE + "disponibles en el servidor",
                        "",
                        ChatColor.YELLOW + "¡Haz clic para explorar!"
                )
        );
    }

    /**
     * Crea el botón de prestige
     */
    private static ItemStack createPrestigeButton() {
        return createGuiItem(
                Material.NETHER_STAR,
                ChatColor.LIGHT_PURPLE + "✨ Sistema Prestige",
                Arrays.asList(
                        ChatColor.WHITE + "¿Has alcanzado el rango máximo?",
                        ChatColor.WHITE + "¡Haz prestige para empezar",
                        ChatColor.WHITE + "de nuevo con beneficios!",
                        "",
                        ChatColor.YELLOW + "¡Haz clic para más info!"
                )
        );
    }

    /**
     * Crea el botón de historial
     */
    private static ItemStack createHistoryButton() {
        return createGuiItem(
                Material.WRITABLE_BOOK,
                ChatColor.GOLD + "📜 Mi Historial",
                Arrays.asList(
                        ChatColor.WHITE + "Ve tu historial de rankups",
                        ChatColor.WHITE + "y logros conseguidos",
                        "",
                        ChatColor.YELLOW + "¡Haz clic para ver!"
                )
        );
    }

    /**
     * Crea el botón de volver
     */
    private static ItemStack createBackButton() {
        return createGuiItem(
                Material.ARROW,
                ChatColor.YELLOW + "⬅ Volver",
                Arrays.asList(
                        ChatColor.GRAY + "Regresa al menú anterior"
                )
        );
    }

    /**
     * Crea un ítem de error
     */
    private static ItemStack createErrorItem(String message) {
        return createGuiItem(
                Material.BARRIER,
                ChatColor.RED + "❌ Error",
                Arrays.asList(
                        ChatColor.GRAY + message
                )
        );
    }

    /**
     * Crea el ítem de información de progreso
     */
    private static ItemStack createProgressInfoItem(RankupProgress progress, RankupManager rankupManager) {
        Material material = Material.EXPERIENCE_BOTTLE;
        String name = ChatColor.AQUA + "📊 Tu Progreso General";
        List<String> lore = new ArrayList<>();

        // Información básica
        RankupData currentRankData = rankupManager.getRankups().get(progress.getCurrentRank());
        String currentDisplay = currentRankData != null ? currentRankData.getDisplayName() : progress.getCurrentRank();

        lore.add("");
        lore.add(ChatColor.WHITE + "Rango actual: " + currentDisplay);

        if (progress.getNextRank() != null) {
            RankupData nextRankData = rankupManager.getRankups().get(progress.getNextRank());
            String nextDisplay = nextRankData != null ? nextRankData.getDisplayName() : progress.getNextRank();
            lore.add(ChatColor.WHITE + "Siguiente: " + nextDisplay);
            lore.add("");

            // Barra de progreso
            double percentage = progress.getOverallProgress();
            String progressBar = createProgressBar(percentage, 20);
            lore.add(ChatColor.WHITE + "Progreso: " + String.format("%.1f%%", percentage));
            lore.add(progressBar);

            // Estado
            if (percentage >= 100.0) {
                lore.add("");
                lore.add(ChatColor.GREEN + "✓ ¡Listo para rankup!");
            } else {
                lore.add("");
                lore.add(ChatColor.YELLOW + "⚡ Sigue progresando...");
            }
        } else {
            lore.add(ChatColor.LIGHT_PURPLE + "¡Rango máximo alcanzado!");
        }

        return createGuiItem(material, name, lore);
    }

    /**
     * Muestra los requisitos en el inventario
     */
    private static void displayRequirements(Inventory inv, RankupProgress progress) {
        Map<String, RequirementProgress> requirements = progress.getRequirements();
        int slot = 28; // Fila central

        for (RequirementProgress reqProgress : requirements.values()) {
            if (slot % 9 == 8) slot += 2; // Saltar bordes
            if (slot >= 45) break; // No exceder el inventario

            ItemStack reqItem = createRequirementItem(reqProgress);
            inv.setItem(slot, reqItem);
            slot++;
        }
    }

    /**
     * Crea un ítem para mostrar un requisito
     */
    private static ItemStack createRequirementItem(RequirementProgress progress) {
        Material material = getRequirementMaterial(progress.getType());
        boolean completed = progress.isCompleted();

        String name = (completed ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ") +
                ChatColor.WHITE + formatRequirementName(progress.getType());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.WHITE + "Progreso: " + formatRequirementValue(progress));

        // Barra de progreso para el requisito
        String progressBar = createProgressBar(progress.getPercentage(), 15);
        lore.add(progressBar);
        lore.add(ChatColor.GRAY + String.format("%.1f%% completado", progress.getPercentage()));

        if (completed) {
            lore.add("");
            lore.add(ChatColor.GREEN + "✓ Requisito cumplido");
        }

        return createGuiItem(material, name, lore);
    }

    /**
     * Obtiene el material apropiado para un tipo de requisito
     */
    private static Material getRequirementMaterial(String type) {
        return switch (type.toLowerCase()) {
            case "money", "eco", "economy" -> Material.GOLD_INGOT;
            case "xp", "experience" -> Material.EXPERIENCE_BOTTLE;
            case "level", "levels" -> Material.ENCHANTED_BOOK;
            case "playtime", "time_played" -> Material.CLOCK;
            case "farming_level" -> Material.WHEAT;
            case "mining_level" -> Material.DIAMOND_PICKAXE;
            case "kills", "mob_kills" -> Material.IRON_SWORD;
            case "blocks_broken" -> Material.STONE_PICKAXE;
            case "permission" -> Material.PAPER;
            default -> Material.ITEM_FRAME;
        };
    }

    /**
     * Crea un ítem para mostrar información de un rango
     */
    private static ItemStack createRankDisplayItem(RankupData rankData, String currentRank) {
        boolean isCurrent = rankData.getRankId().equals(currentRank);
        Material material = isCurrent ? Material.EMERALD : Material.DIAMOND;

        String name = (isCurrent ? ChatColor.GREEN + "► " : ChatColor.WHITE) + rankData.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (isCurrent) {
            lore.add(ChatColor.GREEN + "✓ Tu rango actual");
        } else {
            lore.add(ChatColor.GRAY + "Orden: " + rankData.getOrder());
        }

        if (rankData.hasNextRank()) {
            lore.add(ChatColor.GRAY + "Siguiente: " + rankData.getNextRank());
        } else {
            lore.add(ChatColor.LIGHT_PURPLE + "Rango máximo");
        }

        // Mostrar algunos requisitos si los hay
        Map<String, Object> requirements = rankData.getRequirements();
        if (!requirements.isEmpty()) {
            lore.add("");
            lore.add(ChatColor.YELLOW + "Requisitos:");
            int count = 0;
            for (Map.Entry<String, Object> req : requirements.entrySet()) {
                if (count >= 3) {
                    lore.add(ChatColor.GRAY + "... y más");
                    break;
                }
                String reqName = formatRequirementName(req.getKey());
                lore.add(ChatColor.GRAY + "• " + reqName + ": " + req.getValue());
                count++;
            }
        }

        return createGuiItem(material, name, lore);
    }

    /**
     * Crea una barra de progreso visual
     */
    private static String createProgressBar(double percentage, int length) {
        int filled = (int) Math.ceil(percentage / 100.0 * length);
        StringBuilder bar = new StringBuilder();

        bar.append(ChatColor.GREEN);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }

        return bar.toString();
    }

    /**
     * Formatea el nombre de un requisito para mostrar
     */
    private static String formatRequirementName(String type) {
        return switch (type.toLowerCase()) {
            case "money", "eco", "economy" -> "Dinero";
            case "xp", "experience" -> "Experiencia";
            case "level", "levels" -> "Nivel";
            case "playtime", "time_played" -> "Tiempo jugado";
            case "farming_level" -> "Nivel de granjería";
            case "mining_level" -> "Nivel de minería";
            case "kills", "mob_kills" -> "Kills";
            case "blocks_broken" -> "Bloques rotos";
            case "permission" -> "Permiso";
            default -> type;
        };
    }

    /**
     * Formatea el valor de un requisito para mostrar
     */
    private static String formatRequirementValue(RequirementProgress progress) {
        String type = progress.getType().toLowerCase();
        double current = progress.getCurrent();
        double required = progress.getRequired();

        return switch (type) {
            case "money", "eco", "economy" -> String.format("$%,.0f/$%,.0f", current, required);
            case "xp", "experience" -> String.format("%,.0f/%,.0f XP", current, required);
            case "level", "levels" -> String.format("%.0f/%.0f", current, required);
            case "playtime", "time_played" -> String.format("%.0f/%.0f horas", current, required);
            case "farming_level", "mining_level" -> String.format("%.0f/%.0f", current, required);
            case "kills", "mob_kills", "blocks_broken" -> String.format("%,.0f/%,.0f", current, required);
            default -> String.format("%.0f/%.0f", current, required);
        };
    }

    /**
     * Llena los bordes del inventario con un material
     */
    private static void fillBorders(Inventory inv, Material material) {
        ItemStack borderItem = createGuiItem(material, " ", Collections.emptyList());
        int size = inv.getSize();

        // Primera y última fila
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            if (size > 9) inv.setItem(size - 9 + i, borderItem);
        }

        // Columnas laterales
        for (int row = 1; row < (size / 9) - 1; row++) {
            inv.setItem(row * 9, borderItem);
            inv.setItem(row * 9 + 8, borderItem);
        }
    }

    /**
     * Crea un ítem de GUI con nombre y lore
     */
    private static ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (!name.equals(" ")) {
                meta.setDisplayName(name);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
}

// =====================================================================

