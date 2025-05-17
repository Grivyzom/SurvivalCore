package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.skills.Skill;
import gc.grivyzom.survivalcore.skills.SkillFactory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class SkillsMenuListener implements Listener {

    private final Main plugin;
    private static final long DEFAULT_DURATION = 100; // Aproximadamente 5 segundos
    private static final int MAX_SKILL_LEVEL = 10;

    public SkillsMenuListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(SkillsMenuGUI.INVENTORY_TITLE_PREFIX)) return;
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        Player player = (Player) event.getWhoClicked();

        // Navegación básica
        if (displayName.equals("Regresar a Profesiones")) {
            ProfesionesMenuGUI.open(player, plugin);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }
        if (displayName.equals("Cerrar Menú")) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }
        if (displayName.equals("Página Anterior")) {
            int currentPage = extractPageFromTitle(title);
            String profession = extractProfessionFromTitle(title);
            SkillsMenuGUI.open(player, plugin, currentPage - 1, profession);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }
        if (displayName.equals("Página Siguiente")) {
            int currentPage = extractPageFromTitle(title);
            String profession = extractProfessionFromTitle(title);
            SkillsMenuGUI.open(player, plugin, currentPage + 1, profession);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }

        // Manejo de selección de habilidad
        for (SkillFactory factory : plugin.getSkillManager().getRegisteredSkills()) {
            if (displayName.equals(factory.getName())) {
                // Validación de nivel de profesión
                String uuid = player.getUniqueId().toString();
                int playerLevel = 1;
                UserData data = plugin.getDatabaseManager().getUserData(uuid);
                if (data != null) {
                    if (factory.getProfession().equalsIgnoreCase("farming")) {
                        playerLevel = data.getFarmingLevel();
                    } else if (factory.getProfession().equalsIgnoreCase("mining")) {
                        playerLevel = data.getMiningLevel();
                    }
                }
                if (playerLevel < factory.getRequiredProfessionLevel()) {
                    player.sendMessage(ChatColor.RED + "Habilidad bloqueada. Desbloquea al nivel " +
                            factory.getRequiredProfessionLevel() + " de " + factory.getProfession() + ".");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    return;
                }

                // Click izquierdo: mejora de habilidad (mantiene abierto el menú)
                if (event.getClick().isLeftClick()) {
                    int currentSkillLevel = plugin.getSkillManager().getSkillLevel(player, factory.getName());
                    if (currentSkillLevel >= MAX_SKILL_LEVEL) {
                        player.sendMessage(ChatColor.RED + "Esta habilidad ya está al máximo nivel (" + MAX_SKILL_LEVEL + ").");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                        return;
                    }
                    boolean success = plugin.getSkillManager().upgradeSkill(player, factory);
                    if (success) {
                        int newLevel = plugin.getSkillManager().getSkillLevel(player, factory.getName());
                        player.sendMessage(ChatColor.GREEN + "¡" + factory.getName() + " mejorada a nivel " + newLevel + "!");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1f);
                        if (newLevel == factory.getRequiredProfessionLevel()) {
                            player.sendTitle(ChatColor.GOLD + "Habilidad Desbloqueada",
                                    ChatColor.AQUA + factory.getName() + " usa /skills para ver",
                                    10, 70, 20);
                        }
                        // Reabrir y actualizar el menú con el nuevo nivel
                        SkillsMenuGUI.open(player, plugin,
                                extractPageFromTitle(title), extractProfessionFromTitle(title));
                    }
                    return;
                }

                // Click derecho: activación de habilidad (cierra el menú)
                if (event.getClick().isRightClick()) {
                    int currentSkillLevel = plugin.getSkillManager().getSkillLevel(player, factory.getName());
                    Skill skillInstance = factory.create(plugin, currentSkillLevel, DEFAULT_DURATION);
                    plugin.getSkillManager().activateSkill(player, skillInstance);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1f);
                    player.closeInventory();
                    return;
                }
            }
        }
    }

    private int extractPageFromTitle(String title) {
        try {
            String[] parts = title.split(" - Página ");
            return Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return 1;
        }
    }

    private String extractProfessionFromTitle(String title) {
        try {
            String plainTitle = ChatColor.stripColor(title);
            String[] parts = plainTitle.split(" - ");
            if (parts.length >= 3) {
                return parts[1].trim();
            }
        } catch (Exception e) {
        }
        return "General";
    }
}
