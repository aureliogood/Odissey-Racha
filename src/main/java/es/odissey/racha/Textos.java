package es.odissey.racha;

import java.util.HashMap;
import java.util.Map;

/**
 * Textos por defecto embebidos en el plugin. Se usan SOLO cuando la clave no
 * existe en el config.yml del servidor (asi nunca se ve una clave cruda en el
 * menu aunque la config sea antigua o falte).
 *
 * Este archivo se genera desde config.yml.
 */
public final class Textos {

    private Textos() {
    }

    private static final Map<String, String> MENUS = new HashMap<>();
    private static final Map<String, String> MENSAJES = new HashMap<>();

    static {
        MENUS.put("marca", "&8&o@OdisseyMC");
        MENUS.put("item-click", "&e➤ &fHaz click");
        MENUS.put("borde-material", "ORANGE_STAINED_GLASS_PANE");
        MENUS.put("relleno-material", "GRAY_STAINED_GLASS_PANE");
        MENUS.put("relleno-nombre", " ");
        MENUS.put("titulo-principal", "&8»» &6&lAmigos y Rachas &8««");
        MENUS.put("titulo-amigos", "&8»» &6&lMis Amigos &8««");
        MENUS.put("titulo-solicitudes", "&8»» &6&lSolicitudes &8««");
        MENUS.put("titulo-anadir", "&8»» &6&lAñadir Amigo &8««");
        MENUS.put("titulo-rachas", "&8»» &6&lMis Rachas &8««");
        MENUS.put("titulo-amigo", "&8»» &6&lAmigo: &e%jugador% &8««");
        MENUS.put("titulo-racha", "&8»» &6&lRacha con &e%jugador% &8««");
        MENUS.put("titulo-invitar", "&8»» &6&lInvitar a la Racha &8««");
        MENUS.put("item-amigos", "&e&l✦ Mis Amigos");
        MENUS.put("item-amigos-desc", "&7Gestiona tu lista de amigos y");
        MENUS.put("item-amigos-lore", "&7Amigos: &f%n%");
        MENUS.put("item-solicitudes", "&b&l✦ Solicitudes");
        MENUS.put("item-solicitudes-desc", "&7Acepta o rechaza las peticiones");
        MENUS.put("item-solicitudes-lore", "&7Pendientes: &f%n%");
        MENUS.put("item-anadir", "&a&l✦ Añadir Amigo");
        MENUS.put("item-anadir-desc", "&7Envía una solicitud a un jugador");
        MENUS.put("item-rachas", "&6&l✦ Mis Rachas");
        MENUS.put("item-rachas-desc", "&7Consulta tus rachas y su progreso");
        MENUS.put("item-volver", "&c&l← Volver");
        MENUS.put("item-cerrar", "&c&l✖ Cerrar");
        MENUS.put("vacio", "&8(vacío)");
        MENUS.put("item-jugador-lore-amigo", "&7Racha: &f%dias% &7días");
        MENUS.put("item-jugador-lore-solicitar", "&e➤ &fHaz click para ver la racha");
        MENUS.put("item-jugador-lore-anadir", "&e➤ &fHaz click para enviar solicitud");
        MENUS.put("item-solicitud-lore", "&a➤ Izquierda: aceptar");
        MENUS.put("item-solicitud-lore-rechazar", "&c➤ Derecha: rechazar");
        MENUS.put("item-solicitar-racha", "&a&lSolicitar Racha");
        MENUS.put("item-solicitar-racha-lore", "&7Crea una racha con este amigo");
        MENUS.put("item-eliminar-amigo", "&c&lEliminar Amigo");
        MENUS.put("item-eliminar-amigo-lore", "&7Elimina la amistad y la racha");
        MENUS.put("item-racha", "&6&lRacha con &e%jugador%");
        MENUS.put("item-racha-lore", "&7Días: &e%dias%");
        MENUS.put("item-racha-lore-miembros", "&7Miembros: &f%miembros%");
        MENUS.put("item-racha-lore-estado", "&7Estado: %estado%");
        MENUS.put("item-racha-lore-ultimo", "&7Último día juntos: &f%fecha%");
        MENUS.put("item-racha-lore-riesgo", "&7Alimentadla antes de medianoche: &f%tiempo%");
        MENUS.put("item-racha-lore-recuperable", "&7Puedes recuperarla durante: &f%tiempo%");
        MENUS.put("item-racha-lore-recuperaciones", "&7Recuperaciones: &f%restantes%&7/&f%max%");
        MENUS.put("item-racha-pendiente", "&e&l✦ Solicitud de Racha");
        MENUS.put("item-racha-pendiente-lore", "&7De: &f%jugador%");
        MENUS.put("item-efecto-lore-dias", "&7Necesitas &e%dias% &7días de racha");
        MENUS.put("item-efecto-lore-bloqueado", "&cNecesitas una racha activa para usar este efecto");
        MENUS.put("item-efecto-lore-usar", "&e➤ &aHaz click para activar");
        MENUS.put("item-efecto-lore-cooldown", "&cEn espera: &f%tiempo%");
        MENUS.put("item-recuperar", "&6&l✦ Recuperar Racha");
        MENUS.put("item-recuperar-lore", "&7Recuperaciones: &f%restantes%&7/&f%max%");
        MENUS.put("item-recuperar-lore2", "&e➤ &aHaz click para recuperarla");
        MENUS.put("item-mascota-poner", "&d&l✦ Activar Mascota");
        MENUS.put("item-mascota-quitar", "&d&l✦ Quitar Mascota");
        MENUS.put("item-mascota-lore", "&7Un Allay que te sigue con vuestra racha");
        MENUS.put("item-mejora-poner", "&6&l✦ Activar ataque del Allay");
        MENUS.put("item-mejora-quitar", "&6&l✦ Desactivar ataque del Allay");
        MENUS.put("item-mejora-lore", "&7Tu Allay golpea lo mismo que tú (mitad de daño)");
        MENUS.put("item-invitar", "&a&l✦ Invitar a la Racha");
        MENUS.put("item-invitar-lore", "&7Añade a un amigo a esta racha (máx &f%max%&7)");
        MENUS.put("item-invitar-amigo-lore", "&e➤ &fHaz click para invitar");
        MENUS.put("item-salir-grupo", "&c&l✖ Salir de la Racha");
        MENUS.put("item-salir-grupo-lore", "&7Te saca de esta racha");
        MENSAJES.put("prefix", "&8[&6Odissey &eRacha&8] &r");
        MENSAJES.put("solo-jugadores", "&cSolo un jugador puede hacer eso.");
        MENSAJES.put("sin-permiso", "&cNo tienes permiso.");
        MENSAJES.put("jugador-no-encontrado", "&cNo encuentro a ese jugador.");
        MENSAJES.put("no-te-agregues", "&cNo puedes agregarte a ti mismo.");
        MENSAJES.put("ya-amigos", "&cYa eres amigo de %jugador%.");
        MENSAJES.put("ya-solicitud", "&eYa enviaste una solicitud a %jugador%.");
        MENSAJES.put("limite-amigos", "&cHas alcanzado el limite de %max% amigos.");
        MENSAJES.put("limite-rachas", "&cHas alcanzado el limite de %max% rachas.");
        MENSAJES.put("limite-rachas-amigo", "&c%jugador% ya tiene el maximo de %max% rachas.");
        MENSAJES.put("solicitud-enviada", "&aSolicitud enviada a &e%jugador%&a.");
        MENSAJES.put("solicitud-recibida", "&e%jugador% &ate ha enviado una solicitud de amistad.");
        MENSAJES.put("solicitud-aceptada", "&aAhora eres amigo de &e%jugador%&a.");
        MENSAJES.put("solicitud-rechazada", "&7Rechazaste la solicitud de &e%jugador%&7.");
        MENSAJES.put("sin-solicitud", "&c%jugador% no te ha enviado una solicitud.");
        MENSAJES.put("no-son-amigos", "&cNo eres amigo de %jugador%.");
        MENSAJES.put("amigo-eliminado", "&7Eliminaste a &e%jugador% &7de tus amigos.");
        MENSAJES.put("racha-solicitud-enviada", "&aEnviaste una solicitud de racha a &e%jugador%&a.");
        MENSAJES.put("racha-solicitud-recibida", "&e%jugador% &aquiere crear una racha contigo. Acepta en el menu (&e/racha&a).");
        MENSAJES.put("racha-ya-existe", "&eYa tienes una racha con %jugador%.");
        MENSAJES.put("racha-creada", "&aRacha creada con &e%jugador%&a. Entren los dos cada dia para mantenerla.");
        MENSAJES.put("racha-rechazada", "&7Rechazaste la racha con &e%jugador%&7.");
        MENSAJES.put("racha-alimentada", "&aRacha con &e%jugador%&a: &e%dias% &adias. Mantenla manana.");
        MENSAJES.put("racha-perdida", "&cHas perdido la racha con &e%jugador% &c(%dias% dias).");
        MENSAJES.put("racha-perdible", "&eHas perdido la racha con &6%jugador% &e(%dias% dias), pero aun puedes recuperarla desde el menu (&6/racha&e).");
        MENSAJES.put("racha-recuperada", "&a&lRacha recuperada&a con &e%jugador%&a: &e%dias% &adias de vuelta.");
        MENSAJES.put("recuperar-no", "&cEsa racha no se puede recuperar (o ya no te quedan recuperaciones).");
        MENSAJES.put("efecto-desconocido", "&cEse efecto no existe.");
        MENSAJES.put("efecto-sin-racha", "&cNo tienes una racha activa para usar ese efecto.");
        MENSAJES.put("efecto-bloqueado", "&cNecesitas &e%dias% &cdias de racha (tienes &e%actual%&c).");
        MENSAJES.put("efecto-aplicado", "&aEfecto &e%efecto% &aaplicado.");
        MENSAJES.put("efecto-cooldown", "&cDebes esperar &e%tiempo% &cpara volver a usar &e%efecto%&c.");
        MENSAJES.put("economia-recompensa", "&6Recompensa de racha: &e+%cantidad% &6(&e%dias% &6dias, x&e%multiplicador%&6).");
        MENSAJES.put("mascota-desactivada", "&cLa mascota esta desactivada en este servidor.");
        MENSAJES.put("mascota-sin-racha", "&cNo tienes una racha con ese jugador.");
        MENSAJES.put("mascota-on", "&aMascota activada para tu racha con &e%jugador%&a.");
        MENSAJES.put("mascota-off", "&7Mascota desactivada.");
        MENSAJES.put("grupo-ya-miembro", "&e%jugador% &cya esta en esa racha.");
        MENSAJES.put("grupo-lleno", "&cEsa racha ya tiene el maximo de miembros (&e%max%&c).");
        MENSAJES.put("grupo-conflicto", "&c%jugador% &cya comparte otra racha con uno de los miembros.");
        MENSAJES.put("grupo-unido", "&e%jugador% &ase ha unido a la racha.");
        MENSAJES.put("grupo-salida", "&e%jugador% &7ha salido de la racha.");
        MENSAJES.put("grupo-salido", "&aHas salido de la racha.");
        MENSAJES.put("multicuenta", "&cNo puedes hacer una racha con %jugador% (misma conexion).");
        MENSAJES.put("aviso-entrada-encabezado", "&6&lTus rachas de hoy:");
        MENSAJES.put("aviso-entrada-linea", "&8- &e%nombres% &7- &e%dias% &7dias &8(&a%entraron%&7/&f%total% &7hoy)");
        MENSAJES.put("aviso-entrada-vacio", "&7No tienes rachas activas. Crea una con &e/racha&7.");
        MENSAJES.put("mejora-allay-desbloqueada", "&6&lMejora desbloqueada: &etu Allay ya ataca contigo&6 (racha de &e%dias% &6dias)!");
        MENSAJES.put("mejora-activada", "&aAtaque del Allay activado.");
        MENSAJES.put("mejora-desactivada", "&7Ataque del Allay desactivado.");
        MENSAJES.put("admin-usage", "&eUso: /racha admin dias add|remove|set <jugador> [jugadorB] <n> | /racha admin mejora <jugador> on|off | /racha admin reset <jugadorA> <jugadorB> | /racha admin rachas <jugador>");
        MENSAJES.put("admin-dias", "&aRacha de &e%jugador% &ay &e%amigo% &aahora tiene &e%dias% &adias.");
        MENSAJES.put("admin-dias-jugador", "&aOperacion &e%dias% &aaplicada a &e%n% &aracha(s) de &e%jugador%&a.");
        MENSAJES.put("admin-sin-rachas", "&c%jugador% no tiene ninguna racha.");
        MENSAJES.put("admin-mejora-on", "&aMejora del Allay activada para &e%jugador%&a.");
        MENSAJES.put("admin-mejora-off", "&cMejora del Allay desactivada para &e%jugador%&a.");
        MENSAJES.put("admin-reset", "&aRacha de &e%jugador% &ay &e%amigo% &aeliminada.");
        MENSAJES.put("admin-racha-no", "&cNo existe racha entre &e%jugador% &cy &e%amigo%&c.");
        MENSAJES.put("admin-rachas", "&e%jugador% &atiene &e%n% &aracha(s).");
        MENSAJES.put("racha-riesgo", "&eRacha de &6%dias% &edias: falta la entrada de &6%jugador% &ehoy. Tienen hasta medianoche (&6%tiempo%&e) para no perderla.");
        MENSAJES.put("racha-recompensa", "&6&lRecompensa &6por &e%dias% &6dias de racha con &e%jugador%&6!");
        MENSAJES.put("racha-estado-viva", "&aActiva");
        MENSAJES.put("racha-estado-riesgo", "&e&lEn riesgo");
        MENSAJES.put("racha-estado-perdida", "&6&lRecuperable");
        MENSAJES.put("racha-estado-rota", "&cPerdida");
        MENSAJES.put("top-vacio", "&7Todavia no hay rachas.");
        MENSAJES.put("usage", "&eUso: /racha | /racha add <jugador> | /racha aceptar <jugador> | /racha rechazar <jugador> | /racha eliminar <jugador> | /racha racha <jugador> | /racha top | /racha help");
        MENSAJES.put("reload", "&aConfig recargada.");
    }

    /** Texto de un item/titulo de menu (menus.*). */
    public static String menu(String clave) {
        if (clave == null) return "";
        String v = MENUS.get(clave);
        return v != null ? v : humanizar(clave);
    }

    /** Texto de un mensaje de chat (mensajes.*). */
    public static String mensaje(String clave) {
        if (clave == null) return "";
        String v = MENSAJES.get(clave);
        return v != null ? v : humanizar(clave);
    }

    /** Convierte "item-salir-grupo" en "Salir grupo" (ultimo recurso). */
    private static String humanizar(String clave) {
        if (clave == null || clave.isEmpty()) return "";
        String s = clave;
        for (String pre : new String[] { "item-", "titulo-", "admin-", "grupo-" }) {
            if (s.startsWith(pre)) {
                s = s.substring(pre.length());
                break;
            }
        }
        s = s.replace('-', ' ').trim();
        if (s.isEmpty()) return clave;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
