package es.odissey.racha;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Guarda amigos, solicitudes y rachas en datos.yml.
 */
public final class YamlStore implements Almacen {

    private final OdisseyRacha plugin;
    private final File archivo;
    private final YamlConfiguration yaml = new YamlConfiguration();

    private final Map<UUID, Set<UUID>> amigos = new HashMap<>();
    private final Map<UUID, Set<UUID>> solicitudes = new HashMap<>();
    private final Map<UUID, Set<UUID>> solicitudesRacha = new HashMap<>();
    private final Map<UUID, String> logins = new HashMap<>();
    private final Map<String, Racha> rachas = new LinkedHashMap<>();
    private final Map<UUID, UUID> mascotas = new HashMap<>();
    private final Map<UUID, Boolean> mejoras = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, String> ips = new HashMap<>();

    private boolean sucio;
    private boolean cargaFallida;

    public YamlStore(OdisseyRacha plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "datos.yml");
    }

    @Override
    public void cargar() {
        amigos.clear();
        solicitudes.clear();
        solicitudesRacha.clear();
        logins.clear();
        rachas.clear();
        mascotas.clear();
        mejoras.clear();
        cooldowns.clear();
        ips.clear();
        sucio = false; // lo cargado no necesita volver a guardarse
        cargaFallida = false;
        if (!archivo.exists()) return;
        try {
            yaml.load(archivo);
        } catch (Exception e) {
            cargaFallida = true;
            plugin.getLogger().log(Level.SEVERE, "No se pudo leer datos.yml; se bloquearan escrituras para conservar el archivo: " + e.getMessage(), e);
            return;
        }
        leerPares(yaml.getConfigurationSection("amigos"), amigos);
        leerPares(yaml.getConfigurationSection("solicitudes"), solicitudes);
        leerPares(yaml.getConfigurationSection("solicitudes-racha"), solicitudesRacha);

        ConfigurationSection sl = yaml.getConfigurationSection("logins");
        if (sl != null) {
            for (String k : sl.getKeys(false)) {
                UUID u = uuid(k);
                if (u != null) logins.put(u, sl.getString(k));
            }
        }
        ConfigurationSection sr = yaml.getConfigurationSection("rachas");
        if (sr != null) {
            for (String k : sr.getKeys(false)) {
                ConfigurationSection r = sr.getConfigurationSection(k);
                if (r == null) continue;
                Racha ra = rachaDeClave(k, r);
                if (ra != null) rachas.put(ra.clave(), ra);
            }
        }
        ConfigurationSection sm = yaml.getConfigurationSection("mascotas");
        if (sm != null) {
            for (String k : sm.getKeys(false)) {
                UUID u = uuid(k);
                UUID obj = uuid(sm.getString(k));
                if (u != null && obj != null) mascotas.put(u, obj);
            }
        }
        ConfigurationSection smj = yaml.getConfigurationSection("mejoras");
        if (smj != null) {
            for (String k : smj.getKeys(false)) {
                UUID u = uuid(k);
                if (u != null) mejoras.put(u, smj.getBoolean(k, true));
            }
        }
        ConfigurationSection sc = yaml.getConfigurationSection("cooldowns");
        if (sc != null) {
            for (String k : sc.getKeys(false)) {
                UUID u = uuid(k);
                ConfigurationSection ef = sc.getConfigurationSection(k);
                if (u == null || ef == null) continue;
                Map<String, Long> m = new HashMap<>();
                for (String e : ef.getKeys(false)) m.put(e, ef.getLong(e));
                cooldowns.put(u, m);
            }
        }
        ConfigurationSection sip = yaml.getConfigurationSection("ips");
        if (sip != null) {
            for (String k : sip.getKeys(false)) {
                UUID u = uuid(k);
                String v = sip.getString(k);
                if (u != null && v != null) ips.put(u, v);
            }
        }
    }

    private void leerPares(ConfigurationSection sec, Map<UUID, Set<UUID>> destino) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            UUID u = uuid(k);
            if (u == null) continue;
            Set<UUID> set = new HashSet<>();
            for (String s : sec.getStringList(k)) {
                UUID v = uuid(s);
                if (v != null) set.add(v);
            }
            destino.put(u, set);
        }
    }

    private Racha rachaDeClave(String clave, ConfigurationSection r) {
        String[] partes = clave.split("\\|");
        if (partes.length < 2) return null;
        List<UUID> miembros = new ArrayList<>();
        for (String p : partes) {
            UUID u = uuid(p);
            if (u == null) return null;
            if (!miembros.contains(u)) miembros.add(u);
        }
        return new Racha(miembros, r.getInt("dias", 0), r.getString("ultimo"),
                r.getLong("creada", System.currentTimeMillis()),
                r.getInt("recuperaciones-usadas", 0),
                r.getBoolean("perdida", false),
                r.getInt("dias-perdidos", 0),
                r.getLong("perdida-el", 0));
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void guardar() {
        if (!sucio || cargaFallida) return;
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            YamlConfiguration out = new YamlConfiguration();
            volcarPares("amigos", amigos, out);
            volcarPares("solicitudes", solicitudes, out);
            volcarPares("solicitudes-racha", solicitudesRacha, out);
            for (Map.Entry<UUID, String> e : logins.entrySet()) {
                out.set("logins." + e.getKey(), e.getValue());
            }
            for (Racha r : rachas.values()) {
                String base = "rachas." + r.clave();
                out.set(base + ".dias", r.dias);
                out.set(base + ".ultimo", r.ultimoDia);
                out.set(base + ".creada", r.creada);
                out.set(base + ".recuperaciones-usadas", r.usadas);
                out.set(base + ".perdida", r.perdida);
                out.set(base + ".dias-perdidos", r.diasPerdidos);
                out.set(base + ".perdida-el", r.perdidaEl);
            }
            for (Map.Entry<UUID, UUID> e : mascotas.entrySet()) {
                out.set("mascotas." + e.getKey(), e.getValue().toString());
            }
            for (Map.Entry<UUID, Boolean> e : mejoras.entrySet()) {
                out.set("mejoras." + e.getKey(), e.getValue());
            }
            for (Map.Entry<UUID, Map<String, Long>> e : cooldowns.entrySet()) {
                for (Map.Entry<String, Long> c : e.getValue().entrySet()) {
                    if (c.getValue() != null && c.getValue() > System.currentTimeMillis()) {
                        out.set("cooldowns." + e.getKey() + "." + c.getKey(), c.getValue());
                    }
                }
            }
            for (Map.Entry<UUID, String> e : ips.entrySet()) {
                out.set("ips." + e.getKey(), e.getValue());
            }
            out.save(archivo);
            sucio = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar datos.yml: " + e.getMessage());
        }
    }

    private void volcarPares(String raiz, Map<UUID, Set<UUID>> mapa, YamlConfiguration out) {
        for (Map.Entry<UUID, Set<UUID>> e : mapa.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<String> l = new ArrayList<>();
            for (UUID v : e.getValue()) l.add(v.toString());
            out.set(raiz + "." + e.getKey(), l);
        }
    }

    @Override
    public void cerrar() {
        guardar();
    }

    // ----- Amigos -----

    @Override
    public boolean sonAmigos(UUID a, UUID b) {
        Set<UUID> s = amigos.get(a);
        return s != null && s.contains(b);
    }

    @Override
    public Set<UUID> amigos(UUID jugador) {
        return new HashSet<>(amigos.getOrDefault(jugador, Set.of()));
    }

    @Override
    public void agregarAmigo(UUID a, UUID b) {
        amigos.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        amigos.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        sucio = true;
    }

    @Override
    public void eliminarAmigo(UUID a, UUID b) {
        Set<UUID> sa = amigos.get(a);
        if (sa != null) sa.remove(b);
        Set<UUID> sb = amigos.get(b);
        if (sb != null) sb.remove(a);
        sucio = true;
    }

    // ----- Solicitudes de amistad -----

    @Override
    public boolean haySolicitud(UUID de, UUID para) {
        Set<UUID> s = solicitudes.get(para);
        return s != null && s.contains(de);
    }

    @Override
    public void crearSolicitud(UUID de, UUID para) {
        solicitudes.computeIfAbsent(para, k -> new HashSet<>()).add(de);
        sucio = true;
    }

    @Override
    public void borrarSolicitud(UUID de, UUID para) {
        Set<UUID> s = solicitudes.get(para);
        if (s != null) s.remove(de);
        sucio = true;
    }

    @Override
    public List<UUID> solicitudes(UUID para) {
        return new ArrayList<>(solicitudes.getOrDefault(para, Set.of()));
    }

    // ----- Solicitudes de racha -----

    @Override
    public boolean haySolicitudRacha(UUID de, UUID para) {
        Set<UUID> s = solicitudesRacha.get(para);
        return s != null && s.contains(de);
    }

    @Override
    public void crearSolicitudRacha(UUID de, UUID para) {
        solicitudesRacha.computeIfAbsent(para, k -> new HashSet<>()).add(de);
        sucio = true;
    }

    @Override
    public void borrarSolicitudRacha(UUID de, UUID para) {
        Set<UUID> s = solicitudesRacha.get(para);
        if (s != null) s.remove(de);
        sucio = true;
    }

    @Override
    public List<UUID> solicitudesRacha(UUID para) {
        return new ArrayList<>(solicitudesRacha.getOrDefault(para, Set.of()));
    }

    // ----- Logins -----

    @Override
    public String ultimoLogin(UUID jugador) {
        return logins.get(jugador);
    }

    @Override
    public void setUltimoLogin(UUID jugador, String fecha) {
        // Solo marca para guardar si el valor cambia (evita escrituras inutiles).
        if (!java.util.Objects.equals(logins.put(jugador, fecha), fecha)) {
            sucio = true;
        }
    }

    // ----- Rachas -----

    @Override
    public Racha racha(UUID a, UUID b) {
        for (Racha r : rachas.values()) {
            if (r.incluye(a) && r.incluye(b)) return r;
        }
        return null;
    }

    @Override
    public boolean existeRacha(UUID a, UUID b) {
        return racha(a, b) != null;
    }

    @Override
    public Racha rachaDeClave(String clave) {
        return clave == null ? null : rachas.get(clave);
    }

    @Override
    public boolean existeClave(String clave) {
        return clave != null && rachas.containsKey(clave);
    }

    @Override
    public void crearRacha(UUID a, UUID b) {
        crearRacha(java.util.List.of(a, b));
    }

    @Override
    public void crearRacha(List<UUID> miembros) {
        Racha r = new Racha(miembros, 0, null, System.currentTimeMillis(), 0, false, 0, 0);
        if (r.tamano() < 2 || rachas.containsKey(r.clave())) return;
        rachas.put(r.clave(), r);
        sucio = true;
    }

    @Override
    public List<Racha> rachas(UUID jugador) {
        List<Racha> out = new ArrayList<>();
        for (Racha r : rachas.values()) {
            if (r.incluye(jugador)) out.add(r);
        }
        return out;
    }

    @Override
    public List<Racha> todasRachas() {
        return new ArrayList<>(rachas.values());
    }

    @Override
    public void guardarRacha(Racha racha) {
        rachas.put(racha.clave(), racha);
        sucio = true;
    }

    @Override
    public void reindexarRacha(String claveVieja, Racha racha) {
        if (claveVieja != null) rachas.remove(claveVieja);
        rachas.put(racha.clave(), racha);
        sucio = true;
    }

    @Override
    public void borrarRacha(UUID a, UUID b) {
        Racha r = racha(a, b);
        if (r != null && rachas.remove(r.clave()) != null) sucio = true;
    }

    @Override
    public void borrarRachaPorClave(String clave) {
        if (clave != null && rachas.remove(clave) != null) sucio = true;
    }

    // ----- Mascota de racha -----

    @Override
    public UUID mascotaObjetivo(UUID jugador) {
        return mascotas.get(jugador);
    }

    @Override
    public void setMascota(UUID jugador, UUID objetivo) {
        mascotas.put(jugador, objetivo);
        sucio = true;
    }

    @Override
    public void quitarMascota(UUID jugador) {
        if (mascotas.remove(jugador) != null) sucio = true;
    }

    @Override
    public Set<UUID> conMascota() {
        return new HashSet<>(mascotas.keySet());
    }

    // ----- Mejora del Allay -----

    @Override
    public boolean mejoraDesbloqueada(UUID jugador) {
        return mejoras.containsKey(jugador);
    }

    @Override
    public boolean mejoraActiva(UUID jugador) {
        return Boolean.TRUE.equals(mejoras.get(jugador));
    }

    @Override
    public void setMejoraDesbloqueada(UUID jugador, boolean desbloqueada) {
        if (desbloqueada) mejoras.putIfAbsent(jugador, Boolean.TRUE);
        else mejoras.remove(jugador);
        sucio = true;
    }

    @Override
    public void setMejoraActiva(UUID jugador, boolean activa) {
        if (mejoras.containsKey(jugador)) {
            mejoras.put(jugador, activa);
            sucio = true;
        }
    }

    // ----- Cooldowns de efectos -----

    @Override
    public long cooldown(UUID jugador, String efecto) {
        Map<String, Long> m = cooldowns.get(jugador);
        if (m == null) return 0;
        Long v = m.get(efecto);
        return v == null ? 0 : v;
    }

    @Override
    public void setCooldown(UUID jugador, String efecto, long hasta) {
        cooldowns.computeIfAbsent(jugador, k -> new HashMap<>()).put(efecto, hasta);
        sucio = true;
    }

    // ----- IPs (anti-multicuenta) -----

    @Override
    public String ip(UUID jugador) {
        return ips.get(jugador);
    }

    @Override
    public void setIp(UUID jugador, String ip) {
        if (ip == null) return;
        if (!ip.equals(ips.put(jugador, ip))) {
            sucio = true;
        }
    }
}
