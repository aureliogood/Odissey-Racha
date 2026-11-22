package es.odissey.racha;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Escucha la entrada de jugadores (para mantener la racha) y los clics en los menus.
 */
public final class Escucha implements Listener {

    private final OdisseyRacha plugin;
    /** Ultimo clic sobre una mascota, para no abrir el menu dos veces. */
    private final Map<UUID, Long> ultimoClic = new HashMap<>();
    /** Ultimo clic en un menu, para no procesar la misma accion dos veces. */
    private final Map<UUID, Long> ultimoMenu = new HashMap<>();

    public Escucha(OdisseyRacha plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.alEntrar(p);
        });
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        ultimoClic.remove(u);
        ultimoMenu.remove(u);
        plugin.mascotaSalir(e.getPlayer());
    }

    @EventHandler
    public void alClicMascota(PlayerInteractEntityEvent e) {
        clicMascota(e.getPlayer(), e.getRightClicked(), e);
    }

    @EventHandler
    public void alClicMascotaAt(PlayerInteractAtEntityEvent e) {
        clicMascota(e.getPlayer(), e.getRightClicked(), e);
    }

    @EventHandler
    public void alDanoMascota(EntityDamageEvent e) {
        if (plugin.duenoDeMascota(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alDanoEntidad(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        // No hacer que el Allay ataque a otra mascota.
        if (plugin.duenoDeMascota(e.getEntity()) != null) return;
        plugin.golpeMascota(p, e.getEntity(), e.getFinalDamage());
    }

    private void clicMascota(Player p, Entity ent, Cancellable ev) {
        UUID dueno = plugin.duenoDeMascota(ent);
        if (dueno == null) return;
        ev.setCancelled(true);
        // Tanto PlayerInteractEntityEvent como PlayerInteractAtEntityEvent se
        // disparan al clicar; se ignora el segundo seguido para no abrir 2 veces.
        long ahora = System.currentTimeMillis();
        Long antes = ultimoClic.get(p.getUniqueId());
        if (antes != null && ahora - antes < 250) return;
        ultimoClic.put(p.getUniqueId(), ahora);
        plugin.clicMascota(p, dueno);
    }

    @EventHandler
    public void alClic(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryView vista = e.getView();
        if (!(vista.getTopInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        // Solo los clics dentro del menu (slot crudo dentro del inventario de
        // arriba). Comparar el inventario por identidad con getClickedInventory()
        // puede fallar si otro plugin envuelve la vista; por numero de slot no.
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= vista.getTopInventory().getSize()) return;
        ItemStack it = e.getCurrentItem();
        if (it == null) return;
        // Anti doble clic: varios eventos seguidos no deben ejecutar la accion
        // dos veces (p. ej. gastar dos recuperaciones de racha).
        long ahora = System.currentTimeMillis();
        Long antes = ultimoMenu.get(p.getUniqueId());
        if (antes != null && ahora - antes < 120) return;
        ultimoMenu.put(p.getUniqueId(), ahora);
        boolean derecha = e.isRightClick();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.menu().manejar(p, it, derecha));
    }
}
