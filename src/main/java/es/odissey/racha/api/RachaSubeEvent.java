package es.odissey.racha.api;

import es.odissey.racha.Racha;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Se lanza cuando una racha sube un dia (los dos jugadores han entrado hoy).
 */
public final class RachaSubeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Racha racha;
    private final int dias;
    private final double multiplicador;

    public RachaSubeEvent(Racha racha, int dias, double multiplicador) {
        this.racha = racha;
        this.dias = dias;
        this.multiplicador = multiplicador;
    }

    public Racha getRacha() {
        return racha;
    }

    public int getDias() {
        return dias;
    }

    public double getMultiplicador() {
        return multiplicador;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
