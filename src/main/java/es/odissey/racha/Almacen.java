package es.odissey.racha;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Almacen de amigos, solicitudes y rachas. Implementado en YAML por ahora
 * (datos.yml); se puede anadir luego sqlite/mysql igual que en OdisseyCodes.
 */
public interface Almacen {

    void cargar();

    void guardar();

    void cerrar();

    // ----- Amigos -----

    boolean sonAmigos(UUID a, UUID b);

    Set<UUID> amigos(UUID jugador);

    void agregarAmigo(UUID a, UUID b);

    void eliminarAmigo(UUID a, UUID b);

    // ----- Solicitudes de amistad -----

    boolean haySolicitud(UUID de, UUID para);

    void crearSolicitud(UUID de, UUID para);

    void borrarSolicitud(UUID de, UUID para);

    List<UUID> solicitudes(UUID para);

    // ----- Solicitudes de racha -----

    boolean haySolicitudRacha(UUID de, UUID para);

    void crearSolicitudRacha(UUID de, UUID para);

    void borrarSolicitudRacha(UUID de, UUID para);

    List<UUID> solicitudesRacha(UUID para);

    // ----- Ultimo login (regla por dia) -----

    String ultimoLogin(UUID jugador);

    void setUltimoLogin(UUID jugador, String fecha);

    // ----- Rachas -----

    Racha racha(UUID a, UUID b);

    boolean existeRacha(UUID a, UUID b);

    void crearRacha(UUID a, UUID b);

    List<Racha> rachas(UUID jugador);

    List<Racha> todasRachas();

    void guardarRacha(Racha racha);

    void borrarRacha(UUID a, UUID b);

    /** Racha por su clave canonica, o null si no existe. */
    Racha rachaDeClave(String clave);

    /** true si existe una racha con esa clave canonica. */
    boolean existeClave(String clave);

    /** Crea una racha grupal (2 o mas miembros) con 0 dias. */
    void crearRacha(List<UUID> miembros);

    /** Reindexa una racha tras cambiar sus miembros (clave vieja -> nueva). */
    void reindexarRacha(String claveVieja, Racha racha);

    /** Borra una racha por su clave canonica. */
    void borrarRachaPorClave(String clave);

    // ----- Mejora del Allay (se desbloquea al llegar a X dias) -----

    /** true si el jugador ha desbloqueado (permanente) la mejora de ataque del Allay. */
    boolean mejoraDesbloqueada(UUID jugador);

    /** true si la mejora esta activa (desbloqueada y no desactivada por el jugador). */
    boolean mejoraActiva(UUID jugador);

    /** Marca el desbloqueo permanente (true) o lo quita por completo (false). */
    void setMejoraDesbloqueada(UUID jugador, boolean desbloqueada);

    /** Activa o desactiva el toggle (solo surte efecto si esta desbloqueada). */
    void setMejoraActiva(UUID jugador, boolean activa);

    // ----- Mascota de racha (a que amigo muestra cada jugador) -----

    /** Amigo que muestra la mascota de ese jugador, o null si no la tiene activa. */
    UUID mascotaObjetivo(UUID jugador);

    /** Activa la mascota de un jugador mostrando a ese amigo. */
    void setMascota(UUID jugador, UUID objetivo);

    /** Quita la mascota del jugador. */
    void quitarMascota(UUID jugador);

    /** Jugadores que tienen la mascota activa. */
    Set<UUID> conMascota();

    // ----- Cooldowns de los efectos del menu de racha -----

    /** Instante (epoch) en que el jugador podra volver a usar ese efecto. */
    long cooldown(UUID jugador, String efecto);

    /** Guarda hasta cuando queda bloqueado un efecto para un jugador. */
    void setCooldown(UUID jugador, String efecto, long hasta);

    // ----- IPs (anti-multicuenta) -----

    /** Ultima IP conocida del jugador, o null si no se ha registrado. */
    String ip(UUID jugador);

    /** Guarda la ultima IP conocida del jugador. */
    void setIp(UUID jugador, String ip);
}
