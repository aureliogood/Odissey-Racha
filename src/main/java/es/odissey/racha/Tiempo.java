package es.odissey.racha;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Utilidades de fecha para la regla de la racha (por dia calendario).
 *
 * La zona horaria se puede fijar desde config.yml ("zona-horaria"). Si esta
 * vacia o no es valida se usa la zona del servidor. Asi el cambio de dia no
 * depende de la maquina donde este alojado el servidor.
 */
public final class Tiempo {

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LEGIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static ZoneId zona = ZoneId.systemDefault();

    private Tiempo() {
    }

    /**
     * Configura la zona horaria. Un texto vacio usa la zona del servidor.
     * Devuelve false si el identificador no es valido (se mantiene la del
     * servidor).
     */
    public static boolean configurar(String id) {
        if (id == null || id.isBlank()) {
            zona = ZoneId.systemDefault();
            return true;
        }
        try {
            zona = ZoneId.of(id.trim());
            return true;
        } catch (Exception e) {
            zona = ZoneId.systemDefault();
            return false;
        }
    }

    /** Zona horaria en uso. */
    public static ZoneId zona() {
        return zona;
    }

    /** Fecha de hoy en la zona configurada. */
    public static LocalDate fechaHoy() {
        return LocalDate.now(zona);
    }

    /** Fecha de hoy como "yyyy-MM-dd". */
    public static String hoy() {
        return fechaHoy().format(F);
    }

    /** Fecha de ayer como "yyyy-MM-dd". */
    public static String ayer() {
        return fechaHoy().minusDays(1).format(F);
    }

    /** Dias completos entre dos fechas "yyyy-MM-dd". Devuelve Long.MAX_VALUE si faltan. */
    public static long diasEntre(String desde, String hasta) {
        if (desde == null || hasta == null) return Long.MAX_VALUE;
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(desde, F), LocalDate.parse(hasta, F));
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Instante (epoch) en el que se rompio una racha cuyo ultimo dia alimentado
     * fue "ultimoDia". La racha se rompe al terminar el primer dia que no
     * entraron todos, es decir, al empezar "ultimoDia + 2".
     *
     * Sirve para contar la ventana de recuperacion desde el momento real de la
     * rotura y no desde el momento en que el servidor la detecta (que puede ser
     * mucho despues si el servidor estaba apagado).
     */
    public static long instanteRotura(String ultimoDia) {
        if (ultimoDia == null) return System.currentTimeMillis();
        try {
            return LocalDate.parse(ultimoDia, F).plusDays(2)
                    .atStartOfDay(zona).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Fecha legible para el menu: "Hoy", "Ayer" o "dd/MM/yyyy".
     * Devuelve "-" si la fecha no existe o no se puede leer.
     */
    public static String formatear(String fecha) {
        if (fecha == null || fecha.isBlank()) return "-";
        try {
            LocalDate d = LocalDate.parse(fecha, F);
            LocalDate hoy = fechaHoy();
            if (d.equals(hoy)) return "Hoy";
            if (d.equals(hoy.minusDays(1))) return "Ayer";
            return d.format(LEGIBLE);
        } catch (Exception e) {
            return fecha;
        }
    }

    /** Milisegundos que quedan hasta la proxima medianoche. */
    public static long hastaMedianocheMs() {
        LocalDateTime ahora = LocalDateTime.now(zona);
        LocalDateTime fin = fechaHoy().plusDays(1).atStartOfDay();
        return Math.max(0, Duration.between(ahora, fin).toMillis());
    }

    /** Tiempo que queda hasta la medianoche, como "3h 20m" o "45m". */
    public static String restanteHoy() {
        long minutos = Math.max(0, (hastaMedianocheMs() + 59_999) / 60_000);
        long h = minutos / 60;
        long m = minutos % 60;
        return h <= 0 ? m + "m" : h + "h " + m + "m";
    }
}
