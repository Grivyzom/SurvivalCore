package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.DatabaseManager;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.recipes.LecternRecipe;
import gc.grivyzom.survivalcore.recipes.LecternRecipeManager;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class LecternRecipeUseListener implements Listener {

    private final JavaPlugin plugin;
    private final LecternRecipeManager recipeManager;

    public LecternRecipeUseListener(Main plugin) {
        this.plugin = plugin;
        this.recipeManager = plugin.getLecternRecipeManager();
    }

    @EventHandler
    public void onPlayerClickLecternInventory(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        InventoryView view = e.getView();
        String title = view.getTitle();
        if (!title.equalsIgnoreCase("Atril Mágico")) {
            return;
        }

        e.setCancelled(true);

        int clickedSlot = e.getRawSlot();
        if (clickedSlot != 2) {
            return;
        }

        ItemStack input1 = view.getItem(0);
        ItemStack input2 = view.getItem(1);
        if (input1 == null || input2 == null) {
            p.sendMessage(ChatColor.RED + "Necesitas colocar dos ingredientes válidos.");
            return;
        }

        LecternRecipe matched = recipeManager.findRecipe(p.getLevel(), input1, input2);
        if (matched == null) {
            p.sendMessage(ChatColor.YELLOW + "No existe ninguna receta para esos ingredientes.");
            return;
        }

        int xpCost = matched.getXpCost();
        DatabaseManager db = ((Main) plugin).getDatabaseManager();
        UserData userData = db.getUserData(p.getUniqueId().toString());

        // Verificar si tiene suficiente XP (niveles o banco)
        if (p.getLevel() < xpCost) {
            // Verificar si tiene suficiente XP en el banco (consultando directamente la BD)
            long bankedXp = db.getBankedXp(p.getUniqueId().toString());
            if (bankedXp < xpCost) {
                p.sendMessage(ChatColor.RED + "Necesitas al menos nivel " + xpCost +
                        " o " + xpCost + " XP en tu banco para crear \"" + matched.getId() + "\".");
                return;
            }

            // Descontar del banco
            if (!db.withdrawBankedXp(p.getUniqueId().toString(), xpCost)) {
                p.sendMessage(ChatColor.RED + "Error al descontar XP del banco.");
                return;
            }
            p.sendMessage(ChatColor.GREEN + "Has creado \"" + matched.getId() +
                    "\" por " + xpCost + " XP del banco.");
        } else {
            // Descontar de los niveles
            p.setLevel(p.getLevel() - xpCost);
            p.sendMessage(ChatColor.GREEN + "Has creado \"" + matched.getId() +
                    "\" por " + xpCost + " niveles.");
        }

        // Consumir los ítems de entrada
        view.setItem(0, null);
        view.setItem(1, null);

        // Dar el resultado
        ItemStack result = matched.getResult().clone();
        p.getInventory().addItem(result);

        p.sendMessage(ChatColor.GREEN + "Has creado \"" + matched.getId() +
                "\" por " + xpCost + (p.getLevel() >= xpCost ? " niveles." : " XP del banco."));
        p.closeInventory();
    }

    // En LecternRecipeUseListener.java
    private void playSuccessEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.5);
        player.sendTitle("", ChatColor.GREEN + "¡Receta completada!", 10, 40, 10);
    }
}
