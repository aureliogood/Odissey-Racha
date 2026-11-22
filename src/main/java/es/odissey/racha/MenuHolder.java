package es.odissey.racha;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marca un inventario como menu del plugin y guarda que vista es.
 */
public final class MenuHolder implements InventoryHolder {

    public enum Vista { PRINCIPAL, AMIGOS, SOLICITUDES, ANADIR, RACHAS, AMIGO, RACHA, INVITAR }

    public final Vista vista;
    public final UUID objetivo;
    private Inventory inventario;

    public MenuHolder(Vista vista, UUID objetivo) {
        this.vista = vista;
        this.objetivo = objetivo;
    }

    @Override
    public Inventory getInventory() {
        return inventario;
    }

    public void setInventory(Inventory inventario) {
        this.inventario = inventario;
    }
}
