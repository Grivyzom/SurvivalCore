package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.masteries.Mastery;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class MasteryViewListener implements Listener {

    private final Main plugin;

    public MasteryViewListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Verifica que se haga clic en la GUI de maestrías
        if (event.getClickedInventory() == null ||
                !event.getView().getTitle().startsWith(ChatColor.GOLD + "" + ChatColor.BOLD + "Maestrías:")) {
            return;
        }

        // Cancelamos la acción para que no se muevan los ítems
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Player player = (Player) event.getWhoClicked();
        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        // Manejo de botones de navegación
        if (handleNavigationButtons(player, displayName)) return;

        // Se extrae la profesión a partir del título de la GUI
        String profession = event.getView().getTitle().replace(ChatColor.GOLD + "" + ChatColor.BOLD + "Maestrías: ", "");
        String internalProfession = getInternalProfessionName(profession);
        if (internalProfession == null) return;

        // Obtener la maestría a partir del nombre
        Mastery mastery = plugin.getMasteryManager().getMasteryByName(displayName, internalProfession);
        if (mastery == null) return;

        // Si se trata de Excavación Rápida, diferenciamos el tipo de clic:
        if (mastery.getId().equalsIgnoreCase("excavacion_rapida")) {
            if (event.getClick().isLeftClick()) {
                if (plugin.getMasteryManager().upgradeMastery(player, mastery.getId())) {
                    int newLevel = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString())
                            .getMasteryLevels().getOrDefault(mastery.getId(), 0);
                    player.sendMessage(ChatColor.GREEN + "¡Has mejorado " + mastery.getName() + " al nivel " + newLevel + "!");
                    playSound(player, Sound.ENTITY_PLAYER_LEVELUP);
                    if (mastery.isMaxLevel(newLevel)) {
                        player.sendTitle(ChatColor.GOLD + "¡Maestría Máxima!",
                                ChatColor.AQUA + mastery.getName() + " Nivel " + newLevel,
                                10, 70, 20);
                        playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "No puedes mejorar esta maestría ahora.");
                    playSound(player, Sound.BLOCK_ANVIL_PLACE);
                }
            } else if (event.getClick().isRightClick()) {
                // Validar que el jugador esté bajo la capa 35 para activar la habilidad
                if (player.getLocation().getBlockY() < 35) {
                    // Activar la habilidad. Se puede aplicar un cooldown para evitar abusos (si lo deseas)
                    long activationCooldown = 60000; // Ejemplo: 60 segundos de cooldown para activaciones manuales
                    if (plugin.getMasteryManager().activateMastery(player, mastery.getId(), activationCooldown)) {
                        player.sendMessage(ChatColor.GREEN + "¡Has activado " + mastery.getName() + "!");
                        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "No puedes activar " + mastery.getName() + " sobre la capa 35.");
                    playSound(player, Sound.BLOCK_ANVIL_PLACE);
                }
            }
        } else {
            // Lógica para otras maestrías (cualquier clic intenta mejorar)
            if (plugin.getMasteryManager().upgradeMastery(player, mastery.getId())) {
                int newLevel = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString())
                        .getMasteryLevels().getOrDefault(mastery.getId(), 0);
                player.sendMessage(ChatColor.GREEN + "¡Has mejorado " + mastery.getName() + " al nivel " + newLevel + "!");
                playSound(player, Sound.ENTITY_PLAYER_LEVELUP);
                if (mastery.isMaxLevel(newLevel)) {
                    player.sendTitle(ChatColor.GOLD + "¡Maestría Máxima!",
                            ChatColor.AQUA + mastery.getName() + " Nivel " + newLevel,
                            10, 70, 20);
                    playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                }
            } else {
                player.sendMessage(ChatColor.RED + "No puedes mejorar esta maestría ahora.");
                playSound(player, Sound.BLOCK_ANVIL_PLACE);
            }
        }




        // Actualizar la GUI
        MasteryViewGUI.open(player, plugin, mastery.getProfession());
    }

    private boolean handleNavigationButtons(Player player, String displayName) {
        if (displayName.equalsIgnoreCase("Cerrar")) {
            player.closeInventory();
            playSound(player, Sound.UI_BUTTON_CLICK);
            return true;
        }
        if (displayName.equalsIgnoreCase("Volver a Profesiones")) {
            // Por ejemplo, se puede volver al menú de profesiones
            // O al menú de perfil, según tu lógica
            // Aquí usamos MasteryProfessionsMenuGUI.open(player, plugin);
            MasteryProfessionsMenuGUI.open(player, plugin);
            playSound(player, Sound.UI_BUTTON_CLICK);
            return true;
        }
        return false;
    }

    private String getInternalProfessionName(String displayName) {
        switch (displayName.toLowerCase()) {
            case "minería": return "mining";
            case "granjería": return "farming";
            case "caza": return "hunting";
            case "pesca": return "fishing";
            case "herrería": return "blacksmithing";
            default: return displayName.toLowerCase();
        }
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 0.7f, 1f);
    }
}
