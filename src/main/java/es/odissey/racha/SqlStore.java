package es.odissey.racha;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacenamiento en base de datos (MySQL/MariaDB o SQLite). Cada operacion lee o
 * escribe directamente en la tabla correspondiente, asi un panel web o un bot
 * pueden consultar los mismos datos.
 */
public final class SqlStore implements Almacen {

    private final OdisseyRacha plugin;
    private final Db db;
    private final Map<String, Cache<Racha>> cacheParejas = new ConcurrentHashMap<>();
    private volatile Cache<List<Racha>> cacheTodas;
    private static final long CACHE_MS = 5_000L;

    private record Cache<T>(T valor, long expira) {
        boolean vigente(long ahora) { return ahora < expira; }
    }

    private void invalidarRachas() {
        cacheParejas.clear();
        cacheTodas = null;
    }

    private static Racha copiar(Racha r) {
        return r == null ? null : new Racha(r.miembros, r.dias, r.ultimoDia, r.creada,
                r.usadas, r.perdida, r.diasPerdidos, r.perdidaEl);
    }

    public SqlStore(OdisseyRacha plugin, Db db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ------------------------------------------------------------------
    //  INFRAESTRUCTURA
    // ------------------------------------------------------------------

    @Override
    public void cargar() {
        // nada que precargar: se consulta al momento
    }

    @Override
    public void guardar() {
        // los cambios se escriben al momento
    }

    @Override
    public void cerrar() {
        db.cerrar();
    }

    private String upsert(String tabla, String cols, int n, String... actualiza) {
        StringBuilder v = new StringBuilder();
        for (int i = 0; i < n; i++) v.append(i == 0 ? "?" : ",?");
        if (db.esMysql()) {
            StringBuilder u = new StringBuilder();
            for (String c : actualiza) {
                if (u.length() > 0) u.append(", ");
                u.append(c).append("=VALUES(").append(c).append(")");
            }
            return "INSERT INTO " + tabla + " (" + cols + ") VALUES (" + v + ")"
                    + " ON DUPLICATE KEY UPDATE " + u;
        }
        return "INSERT OR REPLACE INTO " + tabla + " (" + cols + ") VALUES (" + v + ")";
    }

    private String insertIgnorar(String tabla, String cols, int n) {
        String v = n == 1 ? "?" : "?".concat(",?".repeat(n - 1));
        return (db.esMysql() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + tabla + " (" + cols + ") VALUES (" + v + ")";
    }

    // ------------------------------------------------------------------
    //  AMIGOS / SOLICITUDES (tabla "pares": tipo, a, b)
    // ------------------------------------------------------------------

    private boolean parExiste(String tipo, UUID a, UUID b) {
        String sql = "SELECT 1 FROM " + db.pares() + " WHERE tipo = ? AND a = ? AND b = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, a.toString());
            ps.setString(3, b.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            error("comprobando par", e);
            return false;
        }
    }

    private void parPoner(String tipo, UUID a, UUID b) {
        String sql = insertIgnorar(db.pares(), "tipo, a, b", 3);
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, a.toString());
            ps.setString(3, b.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando par", e);
        }
    }

    private void parBorrar(String tipo, UUID a, UUID b) {
        String sql = "DELETE FROM " + db.pares() + " WHERE tipo = ? AND a = ? AND b = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, a.toString());
            ps.setString(3, b.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("borrando par", e);
        }
    }

    private Set<UUID> paresDe(String tipo, UUID jugador) {
        Set<UUID> out = new HashSet<>();
        String sql = "SELECT b FROM " + db.pares() + " WHERE tipo = ? AND a = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, jugador.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID v = uuid(rs.getString(1));
                    if (v != null) out.add(v);
                }
            }
        } catch (SQLException e) {
            error("leyendo pares", e);
        }
        return out;
    }

    private List<UUID> paresDeLista(String tipo, UUID jugador) {
        return new ArrayList<>(paresDe(tipo, jugador));
    }

    @Override
    public boolean sonAmigos(UUID a, UUID b) {
        return parExiste("amigo", a, b);
    }

    @Override
    public Set<UUID> amigos(UUID jugador) {
        return paresDe("amigo", jugador);
    }

    @Override
    public void agregarAmigo(UUID a, UUID b) {
        String sql = insertIgnorar(db.pares(), "tipo, a, b", 3);
        try (Connection c = db.con()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, "amigo");
                ps.setString(2, a.toString());
                ps.setString(3, b.toString());
                ps.executeUpdate();
                ps.setString(2, b.toString());
                ps.setString(3, a.toString());
                ps.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            error("agregando amistad bidireccional", e);
        }
    }

    @Override
    public void eliminarAmigo(UUID a, UUID b) {
        String sql = "DELETE FROM " + db.pares() + " WHERE tipo = ? AND a = ? AND b = ?";
        try (Connection c = db.con()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, "amigo");
                ps.setString(2, a.toString());
                ps.setString(3, b.toString());
                ps.executeUpdate();
                ps.setString(2, b.toString());
                ps.setString(3, a.toString());
                ps.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            error("eliminando amistad bidireccional", e);
        }
    }

    @Override
    public boolean haySolicitud(UUID de, UUID para) {
        return parExiste("sol", de, para);
    }

    @Override
    public void crearSolicitud(UUID de, UUID para) {
        parPoner("sol", de, para);
    }

    @Override
    public void borrarSolicitud(UUID de, UUID para) {
        parBorrar("sol", de, para);
    }

    @Override
    public List<UUID> solicitudes(UUID para) {
        return paresDeLista("sol", para);
    }

    @Override
    public boolean haySolicitudRacha(UUID de, UUID para) {
        return parExiste("solracha", de, para);
    }

    @Override
    public void crearSolicitudRacha(UUID de, UUID para) {
        parPoner("solracha", de, para);
    }

    @Override
    public void borrarSolicitudRacha(UUID de, UUID para) {
        parBorrar("solracha", de, para);
    }

    @Override
    public List<UUID> solicitudesRacha(UUID para) {
        return paresDeLista("solracha", para);
    }

    // ------------------------------------------------------------------
    //  LOGINS
    // ------------------------------------------------------------------

    @Override
    public String ultimoLogin(UUID jugador) {
        String sql = "SELECT fecha FROM " + db.logins() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            error("leyendo login", e);
            return null;
        }
    }

    @Override
    public void setUltimoLogin(UUID jugador, String fecha) {
        String sql = upsert(db.logins(), "uuid, fecha", 2, "fecha");
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.setString(2, fecha);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando login", e);
        }
    }

    // ------------------------------------------------------------------
    //  RACHAS
    // ------------------------------------------------------------------

    private static final String COLS_RACHA =
            "clave, a, b, miembros, dias, ultimo, creada, usadas, perdida, dias_perdidos, perdida_el";

    private Racha leerRacha(ResultSet rs) throws SQLException {
        List<UUID> miembros = new ArrayList<>();
        String m = rs.getString("miembros");
        if (m != null && !m.isBlank()) {
            for (String p : m.split("\\|")) {
                UUID u = uuid(p);
                if (u != null && !miembros.contains(u)) miembros.add(u);
            }
        }
        if (miembros.size() < 2) {
            UUID a = uuid(rs.getString("a"));
            UUID b = uuid(rs.getString("b"));
            if (a != null && !miembros.contains(a)) miembros.add(a);
            if (b != null && !miembros.contains(b)) miembros.add(b);
        }
        if (miembros.size() < 2) return null;
        return new Racha(miembros,
                rs.getInt("dias"),
                rs.getString("ultimo"),
                rs.getLong("creada"),
                rs.getInt("usadas"),
                rs.getInt("perdida") != 0,
                rs.getInt("dias_perdidos"),
                rs.getLong("perdida_el"));
    }

    @Override
    public Racha racha(UUID a, UUID b) {
        if (a == null || b == null) return null;
        String clave = Racha.clave(a, b);
        long ahora = System.currentTimeMillis();
        Cache<Racha> cache = cacheParejas.get(clave);
        if (cache != null && cache.vigente(ahora)) return copiar(cache.valor());
        // Se consultan solo las rachas de "a" (no toda la tabla) y se filtra.
        Racha encontrada = null;
        for (Racha r : rachas(a)) {
            if (r.incluye(b)) { encontrada = r; break; }
        }
        cacheParejas.put(clave, new Cache<>(copiar(encontrada), ahora + CACHE_MS));
        return encontrada;
    }

    @Override
    public boolean existeRacha(UUID a, UUID b) {
        return racha(a, b) != null;
    }

    @Override
    public Racha rachaDeClave(String clave) {
        if (clave == null) return null;
        String sql = "SELECT " + COLS_RACHA + " FROM " + db.rachas() + " WHERE clave = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? leerRacha(rs) : null;
            }
        } catch (SQLException e) {
            error("leyendo racha por clave", e);
            return null;
        }
    }

    @Override
    public boolean existeClave(String clave) {
        return rachaDeClave(clave) != null;
    }

    @Override
    public void crearRacha(UUID a, UUID b) {
        crearRacha(java.util.List.of(a, b));
    }

    @Override
    public void crearRacha(List<UUID> miembros) {
        Racha r = new Racha(miembros, 0, null, System.currentTimeMillis(), 0, false, 0, 0);
        if (r.tamano() < 2 || existeClave(r.clave())) return;
        guardarRacha(r);
    }

    @Override
    public List<Racha> rachas(UUID jugador) {
        List<Racha> out = new ArrayList<>();
        if (jugador == null) return out;
        // Filtra en la propia consulta (a, b o la lista de miembros) en vez de
        // recorrer todas las rachas del servidor en cada llamada del menu.
        String sql = "SELECT " + COLS_RACHA + " FROM " + db.rachas()
                + " WHERE a = ? OR b = ? OR miembros LIKE ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            String id = jugador.toString();
            ps.setString(1, id);
            ps.setString(2, id);
            ps.setString(3, "%" + id + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Racha r = leerRacha(rs);
                    // El LIKE puede dar falsos positivos: se confirma el miembro.
                    if (r != null && r.incluye(jugador)) out.add(r);
                }
            }
        } catch (SQLException e) {
            error("leyendo rachas del jugador", e);
        }
        return out;
    }

    @Override
    public List<Racha> todasRachas() {
        long ahora = System.currentTimeMillis();
        Cache<List<Racha>> cache = cacheTodas;
        if (cache != null && cache.vigente(ahora)) {
            List<Racha> copia = new ArrayList<>();
            for (Racha r : cache.valor()) copia.add(copiar(r));
            return copia;
        }
        List<Racha> out = new ArrayList<>();
        String sql = "SELECT " + COLS_RACHA + " FROM " + db.rachas();
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Racha r = leerRacha(rs);
                if (r != null) out.add(r);
            }
        } catch (SQLException e) {
            error("leyendo todas las rachas", e);
        }
        List<Racha> copia = new ArrayList<>();
        for (Racha r : out) copia.add(copiar(r));
        cacheTodas = new Cache<>(List.copyOf(copia), ahora + CACHE_MS);
        return out;
    }

    @Override
    public void guardarRacha(Racha r) {
        invalidarRachas();
        List<String> ids = new ArrayList<>();
        for (UUID u : r.miembros) ids.add(u.toString());
        String sql = upsert(db.rachas(),
                "clave, a, b, miembros, dias, ultimo, creada, usadas, perdida, dias_perdidos, perdida_el", 11,
                "a", "b", "miembros", "dias", "ultimo", "creada", "usadas", "perdida",
                "dias_perdidos", "perdida_el");
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.clave());
            ps.setString(2, r.a().toString());
            ps.setString(3, r.b().toString());
            ps.setString(4, String.join("|", ids));
            ps.setInt(5, r.dias);
            ps.setString(6, r.ultimoDia);
            ps.setLong(7, r.creada);
            ps.setInt(8, r.usadas);
            ps.setInt(9, r.perdida ? 1 : 0);
            ps.setInt(10, r.diasPerdidos);
            ps.setLong(11, r.perdidaEl);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando racha", e);
        }
    }

    @Override
    public void reindexarRacha(String claveVieja, Racha r) {
        // Se guarda primero la nueva clave y despues se borra la vieja: si el
        // guardado falla, la racha no desaparece sin mas.
        guardarRacha(r);
        if (claveVieja != null && !claveVieja.equals(r.clave())) {
            // Solo se borra la clave vieja si la nueva quedo guardada de verdad.
            if (rachaDeClave(r.clave()) != null) borrarRachaPorClave(claveVieja);
        }
    }

    @Override
    public void borrarRacha(UUID a, UUID b) {
        Racha r = racha(a, b);
        if (r != null) borrarRachaPorClave(r.clave());
    }

    @Override
    public void borrarRachaPorClave(String clave) {
        if (clave == null) return;
        invalidarRachas();
        String sql = "DELETE FROM " + db.rachas() + " WHERE clave = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("borrando racha", e);
        }
    }

    // ------------------------------------------------------------------
    //  MASCOTAS
    // ------------------------------------------------------------------

    @Override
    public UUID mascotaObjetivo(UUID jugador) {
        String sql = "SELECT objetivo FROM " + db.mascotas() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? uuid(rs.getString(1)) : null;
            }
        } catch (SQLException e) {
            error("leyendo mascota", e);
            return null;
        }
    }

    @Override
    public void setMascota(UUID jugador, UUID objetivo) {
        if (jugador == null || objetivo == null) return;
        String sql = upsert(db.mascotas(), "uuid, objetivo", 2, "objetivo");
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.setString(2, objetivo.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando mascota", e);
        }
    }

    @Override
    public void quitarMascota(UUID jugador) {
        String sql = "DELETE FROM " + db.mascotas() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("borrando mascota", e);
        }
    }

    @Override
    public Set<UUID> conMascota() {
        Set<UUID> out = new HashSet<>();
        String sql = "SELECT uuid FROM " + db.mascotas();
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID v = uuid(rs.getString(1));
                if (v != null) out.add(v);
            }
        } catch (SQLException e) {
            error("leyendo mascotas", e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  MEJORA DEL ALLAY
    // ------------------------------------------------------------------

    @Override
    public boolean mejoraDesbloqueada(UUID jugador) {
        String sql = "SELECT 1 FROM " + db.mejoras() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            error("comprobando mejora del allay", e);
            return false;
        }
    }

    @Override
    public boolean mejoraActiva(UUID jugador) {
        String sql = "SELECT activa FROM " + db.mejoras() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            error("comprobando mejora activa del allay", e);
            return false;
        }
    }

    @Override
    public void setMejoraDesbloqueada(UUID jugador, boolean desbloqueada) {
        String sql = desbloqueada
                ? insertIgnorar(db.mejoras(), "uuid", 1)
                : "DELETE FROM " + db.mejoras() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando desbloqueo del allay", e);
        }
    }

    @Override
    public void setMejoraActiva(UUID jugador, boolean activa) {
        String sql = "UPDATE " + db.mejoras() + " SET activa = ? WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, activa ? 1 : 0);
            ps.setString(2, jugador.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando estado de la mejora del allay", e);
        }
    }

    // ------------------------------------------------------------------
    //  COOLDOWNS
    // ------------------------------------------------------------------

    @Override
    public long cooldown(UUID jugador, String efecto) {
        String sql = "SELECT hasta FROM " + db.cooldowns() + " WHERE uuid = ? AND efecto = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.setString(2, efecto == null ? "" : efecto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            error("leyendo cooldown", e);
            return 0;
        }
    }

    @Override
    public void setCooldown(UUID jugador, String efecto, long hasta) {
        String sql = upsert(db.cooldowns(), "uuid, efecto, hasta", 3, "hasta");
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.setString(2, efecto == null ? "" : efecto);
            ps.setLong(3, hasta);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando cooldown", e);
        }
    }

    // ------------------------------------------------------------------
    //  IPs (anti-multicuenta)
    // ------------------------------------------------------------------

    @Override
    public String ip(UUID jugador) {
        String sql = "SELECT ip FROM " + db.ips() + " WHERE uuid = ?";
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            error("leyendo ip", e);
            return null;
        }
    }

    @Override
    public void setIp(UUID jugador, String ip) {
        if (ip == null) return;
        String sql = upsert(db.ips(), "uuid, ip", 2, "ip");
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jugador.toString());
            ps.setString(2, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("guardando ip", e);
        }
    }

    // ------------------------------------------------------------------

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private void error(String que, SQLException e) {
        plugin.getLogger().severe("Error " + que + " en la base de datos; se cancela la operacion para evitar tratar el fallo como un dato ausente: " + e.getMessage());
        throw new IllegalStateException("No se pudo completar la operacion SQL: " + que, e);
    }
}
