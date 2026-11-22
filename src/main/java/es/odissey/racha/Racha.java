package es.odissey.racha;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Una racha entre dos o mas jugadores (grupo). La racha sube un dia cada vez que
 * TODOS los miembros entran al servidor el mismo dia. Si un dia no entran todos,
 * se pierde. La pareja minimo tiene 2 miembros; se pueden anadir mas (grupo).
 */
public final class Racha {

    /** Miembros de la racha (2 o mas). El primero es el de referencia. */
    public final List<UUID> miembros = new ArrayList<>();
    public int dias;
    public String ultimoDia;   // ultimo dia (yyyy-MM-dd) en que entraron todos
    public long creada;

    // Recuperacion de racha (estilo TikTok): si se pierde, se puede recuperar
    // durante una ventana de tiempo, gastando una de las recuperaciones.
    public int usadas;         // recuperaciones ya gastadas
    public boolean perdida;    // true mientras es recuperable (dias=0)
    public int diasPerdidos;   // dias que tenia justo antes de perderse
    public long perdidaEl;     // instante (epoch) en que se perdio

    public Racha(UUID a, UUID b, int dias, String ultimoDia, long creada) {
        this(List.of(a, b), dias, ultimoDia, creada, 0, false, 0, 0);
    }

    public Racha(UUID a, UUID b, int dias, String ultimoDia, long creada,
                 int usadas, boolean perdida, int diasPerdidos, long perdidaEl) {
        this(List.of(a, b), dias, ultimoDia, creada, usadas, perdida, diasPerdidos, perdidaEl);
    }

    public Racha(Collection<UUID> miembros, int dias, String ultimoDia, long creada,
                 int usadas, boolean perdida, int diasPerdidos, long perdidaEl) {
        for (UUID u : miembros) {
            if (u != null && !this.miembros.contains(u)) this.miembros.add(u);
        }
        this.dias = dias;
        this.ultimoDia = ultimoDia;
        this.creada = creada;
        this.usadas = usadas;
        this.perdida = perdida;
        this.diasPerdidos = diasPerdidos;
        this.perdidaEl = perdidaEl;
    }

    /** Primer miembro (referencia). */
    public UUID a() {
        return miembros.get(0);
    }

    /** Segundo miembro (siempre existe: minimo 2). */
    public UUID b() {
        return miembros.size() > 1 ? miembros.get(1) : miembros.get(0);
    }

    /** El primer miembro distinto de u (para mostrar "el otro"). */
    public UUID otro(UUID u) {
        for (UUID m : miembros) {
            if (!m.equals(u)) return m;
        }
        return miembros.get(0);
    }

    /** Los miembros distintos de u. */
    public List<UUID> otros(UUID u) {
        List<UUID> out = new ArrayList<>();
        for (UUID m : miembros) {
            if (!m.equals(u)) out.add(m);
        }
        return out;
    }

    public boolean incluye(UUID u) {
        return miembros.contains(u);
    }

    public int tamano() {
        return miembros.size();
    }

    /** Clave canonica (miembros ordenados, independiente del orden). */
    public String clave() {
        return clave(miembros);
    }

    public static String clave(Collection<UUID> miembros) {
        List<String> s = new ArrayList<>();
        for (UUID u : miembros) s.add(u.toString());
        Collections.sort(s);
        return String.join("|", s);
    }

    public static String clave(UUID x, UUID y) {
        return x.toString().compareTo(y.toString()) <= 0
                ? x + "|" + y
                : y + "|" + x;
    }
}
