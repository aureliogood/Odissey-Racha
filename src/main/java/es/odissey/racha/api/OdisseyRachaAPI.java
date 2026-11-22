package es.odissey.racha.api;

import es.odissey.racha.OdisseyRacha;
import es.odissey.racha.Racha;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API publica de OdisseyRacha para otros plugins.
 *
 * Otros plugins pueden hacer:
 *   OdisseyRachaAPI api = OdisseyRachaAPI.get();
 *   if (api != null) { int dias = api.dias(jugador, amigo); }
 *
 * Para enterarse de los cambios, registrar un Listener de {@link RachaSubeEvent}
 * o {@link RachaPerdidaEvent}.
 */
public final class OdisseyRachaAPI {

    private static OdisseyRachaAPI instancia;

    private final OdisseyRacha plugin;

    private OdisseyRachaAPI(OdisseyRacha plugin) {
        this.plugin = plugin;
    }

    /** Registra la API. Lo llama el propio plugin al activarse. */
    public static void inicializar(OdisseyRacha plugin) {
        instancia = new OdisseyRachaAPI(plugin);
    }

    /** Desregistra la API. Lo llama el propio plugin al desactivarse. */
    public static void detener() {
        instancia = null;
    }

    /** Instancia actual, o null si el plugin no esta activo. */
    public static OdisseyRachaAPI get() {
        return instancia;
    }

    /** true si la API esta disponible. */
    public static boolean disponible() {
        return instancia != null;
    }

    /** La racha entre dos jugadores, o null si no existe. */
    public Racha racha(UUID a, UUID b) {
        return plugin.store().racha(a, b);
    }

    /** true si existe una racha entre dos jugadores. */
    public boolean existeRacha(UUID a, UUID b) {
        return plugin.store().existeRacha(a, b);
    }

    /** Todas las rachas de un jugador. */
    public List<Racha> rachas(UUID jugador) {
        return plugin.store().rachas(jugador);
    }

    /** Todas las rachas del servidor. */
    public List<Racha> todasRachas() {
        return plugin.store().todasRachas();
    }

    /** Dias de la racha entre dos jugadores (0 si no existe). */
    public int dias(UUID a, UUID b) {
        Racha r = racha(a, b);
        return r == null ? 0 : r.dias;
    }

    /** La racha con mas dias de un jugador, o null si no tiene ninguna. */
    public Racha mejorRacha(UUID jugador) {
        Racha mejor = null;
        for (Racha r : rachas(jugador)) {
            if (mejor == null || r.dias > mejor.dias) mejor = r;
        }
        return mejor;
    }

    /** Las N mejores rachas del servidor (ordenadas de mas a menos dias). */
    public List<Racha> top(int n) {
        List<Racha> todas = new ArrayList<>(todasRachas());
        todas.sort((x, y) -> Integer.compare(y.dias, x.dias));
        return n > 0 && todas.size() > n ? new ArrayList<>(todas.subList(0, n)) : todas;
    }

    /** Posicion (1-based) de una racha en el top del servidor, o -1 si no esta. */
    public int posicion(Racha racha) {
        if (racha == null) return -1;
        List<Racha> top = top(0);
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).clave().equals(racha.clave())) return i + 1;
        }
        return -1;
    }

    /** Multiplicador de recompensa segun los dias de racha. */
    public double multiplicador(int dias) {
        return plugin.multiplicador(dias);
    }

    /** Recuperaciones que le quedan a una racha. */
    public int recuperacionesRestantes(Racha racha) {
        return plugin.recuperacionesRestantes(racha);
    }

    /** Recuperaciones totales que le tocan a una racha ahora. */
    public int recuperacionesTotales(Racha racha) {
        return plugin.recuperacionesTotales(racha);
    }

    /** true si hay una economia de Vault disponible. */
    public boolean economiaDisponible() {
        return es.odissey.racha.Economia.disponible();
    }

    /** Saldo del jugador (0 si no hay economia). */
    public double saldo(UUID jugador) {
        return es.odissey.racha.Economia.saldo(Bukkit.getOfflinePlayer(jugador));
    }
}
