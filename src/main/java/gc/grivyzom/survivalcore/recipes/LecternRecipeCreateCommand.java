package gc.grivyzom.survivalcore.recipes;

import gc.grivyzom.survivalcore.recipes.*;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class LecternRecipeCreateCommand implements CommandExecutor, Listener {

    private final Plugin plugin;
    private final LecternRecipeManager manager;
    private final Map<UUID, LecternRecipeCreationSession> sessions = new HashMap<>();
    private final Map<UUID, ReplaceSession> replaceSessions = new HashMap<>();

    private static class ReplaceSession {
        final LecternRecipe newRecipe;
        final LecternRecipe oldRecipe;
        ReplaceSession(LecternRecipe newRecipe, LecternRecipe oldRecipe) {
            this.newRecipe = newRecipe;
            this.oldRecipe = oldRecipe;
        }
    }

    public LecternRecipeCreateCommand(Plugin plugin, LecternRecipeManager mgr) {
        this.plugin  = plugin;
        this.manager = mgr;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ========== flujo principal del comando ========== */

    @Override
    public boolean onCommand(CommandSender sd, Command cmd,String lbl,String[] args) {
        if (!(sd instanceof Player p)) { sd.sendMessage("Sólo jugadores."); return true; }
        if (!p.hasPermission("survivalcore.lectern.admin")) { p.sendMessage(ChatColor.RED+"Sin permiso."); return true; }

        if (args.length < 4 || !args[0].equalsIgnoreCase("recipecreate")) {
            p.sendMessage(ChatColor.RED+"Uso: /lectern recipecreate <nombre> <nivel> <costoXP>");
            return true;
        }

        String name = args[1];
        int level, cost;
        try {
            level = Integer.parseInt(args[2]);
            cost  = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            p.sendMessage(ChatColor.RED+"Nivel o costo inválido."); return true;
        }

        sessions.put(p.getUniqueId(), new LecternRecipeCreationSession(name, level, cost));
        p.sendMessage(ChatColor.GOLD+"Pon el **primer ingrediente** en tu mano principal y escribe "+ChatColor.AQUA+"done");
        return true;
    }

    /* ========== captura de “done” por chat ========== */

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p   = e.getPlayer();
        UUID uuid  = p.getUniqueId();

        // 1️⃣ Confirmación de reemplazo de receta
        if (replaceSessions.containsKey(uuid)) {
            e.setCancelled(true);
            ReplaceSession rs = replaceSessions.remove(uuid);

            if (e.getMessage().equalsIgnoreCase("confirm")) {
                // Usa la instancia manager en lugar de plugin.getLecternRecipeManager()
                manager.replaceRecipe(rs.oldRecipe, rs.newRecipe);
                p.sendMessage(ChatColor.GREEN + "Receta reemplazada correctamente.");
            } else {
                p.sendMessage(ChatColor.RED + "Creación de receta cancelada.");
            }
            return;
        }

        // 2️⃣ Flujo de creación normal con "done"
        LecternRecipeCreationSession session = sessions.get(uuid);
        if (session == null) return;               // no hay sesión abierta
        if (!e.getMessage().equalsIgnoreCase("done")) return;

        e.setCancelled(true);                       // no mostramos el "done"
        plugin.getServer().getScheduler().runTask(plugin, () ->
                advance(p, session)
        );
    }


    private void advance(Player p, LecternRecipeCreationSession s) {
        ItemStack inHand = Optional.ofNullable(p.getInventory().getItemInMainHand()).orElse(null);
        if (inHand==null || inHand.getType().isAir()) {
            p.sendMessage(ChatColor.RED+"Necesitas sujetar un objeto.");
            return;
        }
        switch (s.advance(inHand)) {
            case ASK_SECOND -> p.sendMessage(ChatColor.GREEN+"¡Primer ingrediente guardado! Ahora sujeta el **segundo** ítem y escribe "+ChatColor.AQUA+"done");
            case ASK_RESULT -> p.sendMessage(ChatColor.GREEN+"Bien. Ahora sujeta el **resultado** y escribe "+ChatColor.AQUA+"done");
            case COMPLETE -> {
                LecternRecipe r = s.toRecipe();

                // Buscamos una receta existente con mismos ítems y nivel
                LecternRecipe existing = manager.findRecipe(r.getLevel(),
                        r.getInputs()[0], r.getInputs()[1]);

                if (existing != null && existing.getLevel() == r.getLevel()) {
                    // Hay conflicto: pedimos confirmación
                    p.sendMessage(ChatColor.YELLOW +
                            "Ya existe una receta con esos ítems en nivel " + r.getLevel() + ". " +
                            "Escribe " + ChatColor.AQUA + "confirm" + ChatColor.YELLOW +
                            " para reemplazar o cualquier otra cosa para cancelar.");
                    replaceSessions.put(p.getUniqueId(),
                            new ReplaceSession(r, existing));
                } else {
                    // No existía: la guardamos directamente
                    manager.addAndSave(r);
                    p.sendMessage(ChatColor.GOLD +
                            "Receta creada para nivel " + r.getLevel() + " ➜ " +
                            ChatColor.YELLOW + r.getResult().getType());
                }

                // siempre limpiamos la sesión de creación
                sessions.remove(p.getUniqueId());
            }

        }
    }

}
