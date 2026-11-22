package es.odissey.racha.api;

import es.odissey.racha.Racha;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Se lanza cuando una racha se pierde (alguien no entro al servidor ese dia).
 */
public final class RachaPerdidaEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Racha racha;
    private final int dias;
    private final boolean recuperable;

    public RachaPerdidaEvent(Racha racha, int dias, boolean recuperable) {
        this.racha = racha;
        this.dias = dias;
        this.recuperable = recuperable;
    }

    public Racha getRacha() {
        return racha;
    }

    /** Dias que tenia la racha justo antes de perderse. */
    public int getDias() {
        return dias;
    }

    /** true si todavia se puede recuperar desde el menu. */
    public boolean isRecuperable() {
        return recuperable;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
