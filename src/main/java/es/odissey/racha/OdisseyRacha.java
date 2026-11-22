package es.odissey.racha;

import es.odissey.racha.api.OdisseyRachaAPI;
import es.odissey.racha.api.RachaPerdidaEvent;
import es.odissey.racha.api.RachaSubeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OdisseyRacha: amigos y "rachas" (estilo racha de TikTok).
 *
 * Dos amigos crean una racha; se mantiene solo si los DOS entran al servidor
 * el mismo dia. Cada dia que ambos entran la racha sube 1 y puede dar premios.
 */
public final class OdisseyRacha extends JavaPlugin {

    private Almacen store;
    private MenuRacha menu;
    private final Map<Integer, Recompensa> recompensas = new LinkedHashMap<>();
    private final Map<String, Efecto> efectos = new LinkedHashMap<>();
    private final Map<UUID, Mascota> mascotas = new HashMap<>();
    // Cache en memoria: consultar mascotaObjetivo en cada tick hacia SQL
    // bloqueaba el hilo principal y repetia una query por jugador.
    private final Map<UUID, UUID> objetivosMascota = new HashMap<>();
    private final Map<UUID, UUID> standDueno = new HashMap<>();
    private final List<Tramo> tramos = new ArrayList<>();
    private YamlConfiguration mascotaYaml = new YamlConfiguration();
    private YamlConfiguration menusYaml = new YamlConfiguration();
    private String webhookUrl = "";
    private int tickMascota;

    /** Avisos ya enviados (jugador|racha|dia) para no repetirlos al reconectar. */
    private final Set<String> avisadosRiesgo = new HashSet<>();
    private final Set<String> avisadosEntrada = new HashSet<>();
    private String diaAvisos = "";

    /** Sonidos por defecto (config.yml -> sonidos.<clave>). */
    private static final Map<String, String> SONIDOS = Map.ofEntries(
            Map.entry("abrir", "ui.button.click;0.6;1.2"),
            Map.entry("cerrar", "ui.button.click;0.5;0.9"),
            Map.entry("seleccionar", "ui.button.click;0.5;1.0"),
            Map.entry("volver", "ui.button.click;0.5;0.8"),
            Map.entry("exito", "entity.player.levelup;0.6;1.2"),
            Map.entry("error", "entity.villager.no;0.7;1.0"),
            Map.entry("aviso", "block.note_block.bell;0.6;1.4"),
            Map.entry("recuperar", "entity.player.levelup;0.8;1.4"),
            Map.entry("efecto", "entity.illusioner.cast_spell;0.7;1.2"),
            Map.entry("hito", "ui.toast.challenge_complete;0.8;1.0"),
            Map.entry("mascota", "entity.allay.ambient_with_item;0.7;1.3"));

    /** Duracion de la animacion de golpe del Allay (en frames de 2 ticks). */
    private static final int GOLPE_TICKS = 6;
    /** Distancia (bloques) que se lanza el Allay hacia el objetivo al golpear. */
    private static final double GOLPE_DIST = 1.4;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cargarMenus();
        configurarZonaHoraria();
        diaAvisos = Tiempo.hoy();
        store = crearStore();
        if (store == null) return;
        store.cargar();
        cargarRecompensas();
        cargarEfectos();
        cargarMascota();
        cargarMultiplicador();
        cargarWebhook();
        menu = new MenuRacha(this);
        OdisseyRachaAPI.inicializar(this);

        ComandoRacha comando = new ComandoRacha(this);
        if (getCommand("racha") != null) {
            getCommand("racha").setExecutor(comando);
            getCommand("racha").setTabCompleter(comando);
        } else {
            getLogger().severe("No se pudo registrar el comando /racha.");
        }

        Bukkit.getPluginManager().registerEvents(new Escucha(this), this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            cargarObjetivoMascota(online.getUniqueId());
        }

        // PlaceholderAPI: registra la expansion solo si esta instalado.
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new RachaExpansion(this).register();
                getLogger().info("PlaceholderAPI detectado: placeholders %odisseyracha_...% activados.");
            } catch (Throwable t) {
                getLogger().warning("No se pudo registrar la expansion de PlaceholderAPI: " + t.getMessage());
            }
        }

        // Revisa las rachas cada 5 minutos (para romper las que se pasen de dia).
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                revisarRachas();
            } catch (RuntimeException e) {
                // Un corte SQL no debe cancelar la tarea repetida para siempre.
                getLogger().log(java.util.logging.Level.SEVERE,
                        "No se pudo revisar las rachas; se reintentara en el siguiente ciclo.", e);
            }
        }, 20L * 60, 20L * 60 * 5);

        // La mascota sigue al jugador: se actualiza cada 2 ticks.
        Bukkit.getScheduler().runTaskTimer(this, this::actualizarMascotas, 20L, 2L);

        getLogger().info("OdisseyRacha activado. " + store.todasRachas().size() + " racha(s).");
    }

    @Override
    public void onDisable() {
        for (UUID u : new ArrayList<>(mascotas.keySet())) borrarEntidades(u);
        if (store != null) store.cerrar();
        OdisseyRachaAPI.detener();
    }

    /** Crea el almacen segun la config (yaml, mysql o sqlite). */
    private Almacen crearStore() {
        String tipo = getConfig().getString("storage.tipo", "yaml");
        tipo = tipo == null ? "yaml" : tipo.toLowerCase(Locale.ROOT);
        if (tipo.equals("mysql") || tipo.equals("sqlite")) {
            Db db = new Db(this, tipo);
            if (db.abrir()) {
                getLogger().info("Usando base de datos (" + tipo + ").");
                return new SqlStore(this, db);
            }
            getLogger().severe("No se pudo abrir la base SQL configurada. Se cancela la activacion para evitar usar un almacen YAML distinto y aparentar que los datos desaparecieron.");
            Bukkit.getPluginManager().disablePlugin(this);
            return null;
        }
        return new YamlStore(this);
    }

    /** Recarga el config (mensajes, recompensas, efectos y mascota). */
    public void recargar() {
        reloadConfig();
        cargarMenus();
        configurarZonaHoraria();
        avisadosRiesgo.clear();
        avisadosEntrada.clear();
        diaAvisos = Tiempo.hoy();
        cargarRecompensas();
        cargarEfectos();
        cargarMascota();
        cargarMultiplicador();
        cargarWebhook();
        // Recrea las mascotas activas para aplicar los cambios.
        for (UUID u : new ArrayList<>(mascotas.keySet())) borrarEntidades(u);
    }

    /** Lee "zona-horaria" del config y la aplica a Tiempo. */
    private void configurarZonaHoraria() {
        String id = getConfig().getString("zona-horaria", "");
        if (!Tiempo.configurar(id)) {
            getLogger().warning("Zona horaria '" + id + "' no valida; se usara la del servidor.");
        }
    }

    /**
     * Reproduce un sonido configurado. Formato en config.yml:
     * "namespace:clave" o "namespace:clave;volumen;pitch".
     * Un sonido mal escrito nunca debe romper el menu.
     */
    public void sonido(Player p, String clave) {
        if (p == null || clave == null) return;
        if (!getConfig().getBoolean("sonidos.activado", true)) return;
        String def = SONIDOS.getOrDefault(clave, "ui.button.click");
        String raw = getConfig().getString("sonidos." + clave, def);
        if (raw == null || raw.isBlank()) return;
        String[] partes = raw.split(";");
        String key = partes[0].trim();
        if (key.isEmpty()) return;
        float volumen = partes.length > 1 ? parseFloat(partes[1], 0.6f) : 0.6f;
        float pitch = partes.length > 2 ? parseFloat(partes[2], 1.0f) : 1.0f;
        try {
            p.playSound(p.getLocation(), key, volumen, pitch);
        } catch (Throwable ignored) {
            // sonido no disponible en esta version: se ignora
        }
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void sonarMiembros(Racha r, String clave) {
        if (r == null) return;
        for (UUID u : r.miembros) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) sonido(p, clave);
        }
    }

    /**
     * Marca el cambio de dia y limpia los avisos ya enviados. Devuelve true si
     * el dia ha cambiado desde la ultima comprobacion.
     */
    private boolean asegurarDia() {
        String hoy = Tiempo.hoy();
        if (hoy.equals(diaAvisos)) return false;
        diaAvisos = hoy;
        avisadosRiesgo.clear();
        avisadosEntrada.clear();
        return true;
    }

    /**
     * Los jugadores conectados cuentan como que han entrado hoy. Asi una racha
     * no se pierde (ni se queda sin subir) solo porque ambos estaban online al
     * pasar la medianoche y no reconectaron.
     */
    private void marcarOnline() {
        String hoy = Tiempo.hoy();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (!hoy.equals(store.ultimoLogin(u))) {
                store.setUltimoLogin(u, hoy);
            }
        }
    }

    private void cargarRecompensas() {
        recompensas.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("recompensas");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            int dia;
            try {
                dia = Integer.parseInt(k.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection r = sec.getConfigurationSection(k);
            if (r == null) continue;
            Recompensa rec = new Recompensa(dia);
            rec.efectos.addAll(r.getStringList("efectos"));
            rec.objetos.addAll(r.getStringList("objetos"));
            rec.comandos.addAll(r.getStringList("comandos"));
            recompensas.put(dia, rec);
        }
    }

    /** Efectos del menu de racha (lo que se abre al clicar la mascota). */
    private void cargarEfectos() {
        efectos.clear();
        ConfigurationSection sec = menusYaml.getConfigurationSection("efectos-racha");
        if (sec == null) sec = getConfig().getConfigurationSection("efectos-racha");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection e = sec.getConfigurationSection(id);
            if (e == null) continue;
            Efecto ef = new Efecto(id);
            ef.nombre = e.getString("nombre", id);
            ef.material = e.getString("material", "BLAZE_POWDER");
            ef.efecto = e.getString("efecto", "");
            ef.diasMinimos = Math.max(0, e.getInt("dias-minimos", 0));
            ef.cooldown = Math.max(0, e.getInt("cooldown", 300));
            ef.lore.addAll(e.getStringList("lore"));
            efectos.put(id.toLowerCase(Locale.ROOT), ef);
        }
    }

    /** Carga mascota.yml (texto del holograma y posicion). */
    private void cargarMascota() {
        File f = new File(getDataFolder(), "mascota.yml");
        if (!f.exists()) saveResource("mascota.yml", false);
        mascotaYaml = YamlConfiguration.loadConfiguration(f);
    }

    /** Carga los tramos del multiplicador de racha (multiplicador.tramos). */
    private void cargarMultiplicador() {
        tramos.clear();
        for (Map<?, ?> m : getConfig().getMapList("multiplicador.tramos")) {
            Object d = m.get("dias");
            Object x = m.get("multiplicador");
            int dias = d instanceof Number ? ((Number) d).intValue() : parseInt(String.valueOf(d), 0);
            double mult = x instanceof Number ? ((Number) x).doubleValue() : 1.0;
            tramos.add(new Tramo(Math.max(0, dias), Math.max(0, mult)));
        }
        tramos.sort(Comparator.comparingInt((Tramo t) -> t.dias).reversed());
    }

    private void cargarWebhook() {
        String u = getConfig().getString("discord.webhook", "");
        webhookUrl = u == null ? "" : u.trim();
    }

    /** Multiplicador de recompensa segun los dias de racha. */
    public double multiplicador(int dias) {
        if (!getConfig().getBoolean("multiplicador.activado", true)) return 1.0;
        for (Tramo t : tramos) {
            if (dias >= t.dias) return t.mult;
        }
        return 1.0;
    }

    public boolean economiaActivada() {
        return getConfig().getBoolean("economia.activado", true);
    }

    // ------------------------------------------------------------------
    //  RACHAS
    // ------------------------------------------------------------------

    /** Se llama cuando un jugador entra: actualiza su login y evalúa sus rachas. */
    public void alEntrar(Player jugador) {
        if (jugador == null || !jugador.isOnline()) return;
        asegurarDia();
        // Si el dia cambio con jugadores ya conectados, se marcan antes de
        // evaluar para que la entrada de este jugador cuente de inmediato.
        marcarOnline();
        UUID u = jugador.getUniqueId();
        store.setIp(u, ipDe(jugador));
        // marcarOnline() ya lo dejo en "hoy"; solo se escribe si hiciera falta.
        if (!Tiempo.hoy().equals(store.ultimoLogin(u))) {
            store.setUltimoLogin(u, Tiempo.hoy());
        }
        cargarObjetivoMascota(u);
        for (Racha r : store.rachas(u)) evaluar(r);
        if (getConfig().getBoolean("avisar-al-entrar", true)) {
            avisarRiesgo(jugador);
            avisarEntrada(jugador);
        }
        store.guardar();
    }

    /** IP actual del jugador, o null si no se puede obtener. */
    private String ipDe(Player p) {
        try {
            java.net.InetSocketAddress a = p.getAddress();
            return a == null ? null : a.getAddress().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private void cargarObjetivoMascota(UUID jugador) {
        UUID objetivo = store.mascotaObjetivo(jugador);
        if (objetivo != null) {
            Racha r = store.racha(jugador, objetivo);
            if (r == null || r.perdida || r.dias <= 0) {
                store.quitarMascota(jugador);
                store.guardar();
                objetivo = null;
            }
        }
        if (objetivo == null) objetivosMascota.remove(jugador);
        else objetivosMascota.put(jugador, objetivo);
    }

    public boolean antiMulticuenta() {
        return getConfig().getBoolean("anti-multicuenta", true);
    }

    /** true si ambos comparten la misma IP conocida (posible multicuenta). */
    public boolean mismaIp(UUID a, UUID b) {
        if (!antiMulticuenta() || a == null || b == null) return false;
        String ia = store.ip(a);
        String ib = store.ip(b);
        return ia != null && ia.equals(ib);
    }

    /** Resumen de las rachas del jugador al entrar (quien ha entrado hoy). */
    private void avisarEntrada(Player jugador) {
        UUID yo = jugador.getUniqueId();
        String hoy = Tiempo.hoy();
        // Una sola vez por jugador y dia (evita spam al reconectar).
        if (!avisadosEntrada.add(yo + "|" + hoy)) return;
        List<Racha> rs = store.rachas(yo);
        rs.removeIf(r -> r.perdida || r.dias <= 0);
        if (rs.isEmpty()) {
            jugador.sendMessage(msg("aviso-entrada-vacio"));
            return;
        }
        jugador.sendMessage(msg("aviso-entrada-encabezado"));
        for (Racha r : rs) {
            int entraron = 0;
            for (UUID m : r.miembros) {
                if (hoy.equals(store.ultimoLogin(m))) entraron++;
            }
            jugador.sendMessage(msg("aviso-entrada-linea",
                    "%nombres%", nombresOtros(r, yo),
                    "%dias%", String.valueOf(Math.max(r.dias, r.diasPerdidos)),
                    "%entraron%", String.valueOf(entraron),
                    "%total%", String.valueOf(r.tamano())));
        }
    }

    /**
     * Al entrar, avisa al jugador de las rachas que siguen vivas pero todavia no
     * se han alimentado hoy porque el otro compañero no ha entrado.
     */
    private void avisarRiesgo(Player jugador) {
        String hoy = Tiempo.hoy();
        String tiempo = Tiempo.restanteHoy();
        UUID yo = jugador.getUniqueId();
        boolean avisado = false;
        for (Racha r : store.rachas(yo)) {
            if (r.dias <= 0) continue;
            if (hoy.equals(r.ultimoDia)) continue;
            // Quienes aun no han entrado hoy (distintos de mi).
            List<UUID> faltan = new ArrayList<>();
            for (UUID m : r.miembros) {
                if (m.equals(yo)) continue;
                if (!hoy.equals(store.ultimoLogin(m))) faltan.add(m);
            }
            if (faltan.isEmpty()) continue;
            // Una sola vez por racha y dia (evita repetirlo al reconectar).
            if (!avisadosRiesgo.add(yo + "|" + r.clave() + "|" + hoy)) continue;
            jugador.sendMessage(msg("racha-riesgo",
                    "%jugador%", nombres(faltan),
                    "%dias%", String.valueOf(r.dias),
                    "%tiempo%", tiempo));
            avisado = true;
        }
        if (avisado) sonido(jugador, "aviso");
    }

    /** Nombres de una lista de jugadores, separados por comas. */
    public String nombres(List<UUID> jugadores) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : jugadores) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(nombre(u));
        }
        return sb.toString();
    }

    /** Evalúa una racha segun los ultimos logins de los dos jugadores. */
    private void evaluar(Racha r) {
        asegurarDia();
        String hoy = Tiempo.hoy();

        // Si esta perdida pero aun recuperable, se queda congelada hasta que la
        // recuperen o se pase la ventana (entonces muere del todo).
        if (r.perdida) {
            long horas = horasRecuperacion();
            if (horas > 0 && System.currentTimeMillis() - r.perdidaEl > horas * 3600_000L) {
                r.perdida = false;
                r.diasPerdidos = 0;
                r.perdidaEl = 0;
                store.guardarRacha(r);
            }
            return;
        }

        // La rotura se comprueba ANTES de mirar si han vuelto hoy. Si no, dos
        // jugadores que vuelven el mismo dia despues de dias sin entrar hacian
        // que la racha se reiniciase a 1 en silencio, como si no hubiese pasado
        // nada, en vez de marcarse como perdida.
        if (r.ultimoDia != null && r.dias > 0 && Tiempo.diasEntre(r.ultimoDia, hoy) > 1) {
            romperRacha(r);
            return;
        }

        boolean todos = true;
        for (UUID m : r.miembros) {
            if (!hoy.equals(store.ultimoLogin(m))) {
                todos = false;
                break;
            }
        }
        if (todos && !hoy.equals(r.ultimoDia)) {
            long gap = r.ultimoDia == null ? Long.MAX_VALUE : Tiempo.diasEntre(r.ultimoDia, hoy);
            r.dias = gap > 1 ? 1 : r.dias + 1;
            r.ultimoDia = hoy;
            store.guardarRacha(r);
            // Un fallo en una recompensa/evento externo no debe romper la racha.
            try {
                desbloquearMejora(r);
                Bukkit.getPluginManager().callEvent(
                        new RachaSubeEvent(r, r.dias, multiplicador(r.dias)));
                Recompensa rec = recompensas.get(r.dias);
                if (rec != null) darRecompensa(r, rec);
                darRecompensaDinero(r);
                webhookSubida(r);
            } catch (Throwable t) {
                getLogger().log(java.util.logging.Level.WARNING,
                        "Error al procesar la subida de la racha " + r.clave()
                                + ": " + t.getMessage(), t);
            }
            if (getConfig().getBoolean("avisar-al-subir", true)) {
                avisar(r, "racha-alimentada", "%dias%", String.valueOf(r.dias));
            }
            sonarMiembros(r, "exito");
        }
    }

    /**
     * Marca una racha como perdida porque ha pasado al menos un dia completo
     * sin que entrasen todos sus miembros. Si la ventana de recuperacion sigue
     * abierta (contada desde la rotura real, no desde que el servidor la
     * detecta) queda como "recuperable"; si no, muere del todo.
     */
    private void romperRacha(Racha r) {
        int antes = r.dias;
        long rotura = Tiempo.instanteRotura(r.ultimoDia);
        long horas = horasRecuperacion();
        boolean enVentana = horas <= 0 || System.currentTimeMillis() - rotura <= horas * 3600_000L;
        boolean recuperable = recuperacionesActivadas()
                && recuperacionesRestantes(antes, r.usadas) > 0 && enVentana;

        r.dias = 0;
        r.ultimoDia = null;
        if (recuperable) {
            r.perdida = true;
            r.diasPerdidos = antes;
            r.perdidaEl = rotura;
        } else {
            r.perdida = false;
            r.diasPerdidos = 0;
            r.perdidaEl = 0;
        }
        store.guardarRacha(r);
        limpiarMascotasRacha(r);
        try {
            Bukkit.getPluginManager().callEvent(new RachaPerdidaEvent(r, antes, recuperable));
            webhookPerdida(r, antes, recuperable);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Error al procesar la perdida de la racha " + r.clave()
                            + ": " + t.getMessage(), t);
        }
        if (getConfig().getBoolean("avisar-al-perder", true)) {
            avisar(r, recuperable ? "racha-perdible" : "racha-perdida",
                    "%dias%", String.valueOf(antes));
        }
        sonarMiembros(r, recuperable ? "aviso" : "error");
    }

    /** Da la recompensa diaria en dinero (Vault), escalada por el multiplicador. */
    private void darRecompensaDinero(Racha r) {
        if (!economiaActivada()) return;
        double base = getConfig().getDouble("economia.recompensa-diaria", 0);
        if (base <= 0) return;
        if (!Economia.disponible()) return;
        double mult = multiplicador(r.dias);
        double total = base * mult;
        for (UUID u : r.miembros) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            if (!Economia.depositar(op, total)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(msg("economia-recompensa",
                        "%cantidad%", Economia.dinero(total),
                        "%dias%", String.valueOf(r.dias),
                        "%multiplicador%", Economia.dinero(mult)));
            }
        }
    }

    private void webhookSubida(Racha r) {
        if (webhookUrl.isEmpty() || !getConfig().getBoolean("discord.al-subir", true)) return;
        String t = getConfig().getString("discord.mensaje-subida",
                "%jugador% y %amigo% llevan %dias% dias de racha!");
        Webhook.enviar(this, webhookUrl, t
                .replace("%jugador%", nombre(r.a()))
                .replace("%amigo%", nombresOtros(r, r.a()))
                .replace("%dias%", String.valueOf(r.dias))
                .replace("%multiplicador%", Economia.dinero(multiplicador(r.dias))));
    }

    private void webhookPerdida(Racha r, int dias, boolean recuperable) {
        if (webhookUrl.isEmpty() || !getConfig().getBoolean("discord.al-perder", true)) return;
        String t = getConfig().getString("discord.mensaje-perdida",
                "%jugador% y %amigo% han perdido su racha de %dias% dias.");
        Webhook.enviar(this, webhookUrl, t
                .replace("%jugador%", nombre(r.a()))
                .replace("%amigo%", nombresOtros(r, r.a()))
                .replace("%dias%", String.valueOf(dias))
                .replace("%estado%", recuperable ? "recuperable" : "perdida"));
    }

    // ------------------------------------------------------------------
    //  RECUPERACION DE RACHA (estilo TikTok)
    // ------------------------------------------------------------------

    private boolean recuperacionesActivadas() {
        return getConfig().getBoolean("recuperaciones.activado", true);
    }

    private long horasRecuperacion() {
        return Math.max(0, getConfig().getLong("recuperaciones.horas", 48));
    }

    /** Recuperaciones maximas segun los dias: base + dias/porDias, con tope. */
    private int recuperacionesMax(int dias) {
        int base = Math.max(0, getConfig().getInt("recuperaciones.base", 3));
        int max = Math.max(base, getConfig().getInt("recuperaciones.maximo", 6));
        int por = Math.max(1, getConfig().getInt("recuperaciones.por-dias", 10));
        int n = base + Math.max(0, dias) / por;
        return Math.min(max, n);
    }

    /** Recuperaciones que le quedan a la racha (del total que le toca). */
    public int recuperacionesRestantes(Racha r) {
        if (r == null) return 0;
        return recuperacionesRestantes(Math.max(r.dias, r.diasPerdidos), r.usadas);
    }

    private int recuperacionesRestantes(int dias, int usadas) {
        return Math.max(0, recuperacionesMax(dias) - usadas);
    }

    /** Recuperaciones totales que le tocan a la racha ahora mismo. */
    public int recuperacionesTotales(Racha r) {
        if (r == null) return 0;
        return recuperacionesMax(Math.max(r.dias, r.diasPerdidos));
    }

    /** true si la racha se puede recuperar ahora mismo. */
    public boolean puedeRecuperar(Racha r) {
        if (!recuperacionesActivadas() || r == null || !r.perdida) return false;
        long horas = horasRecuperacion();
        if (horas > 0 && System.currentTimeMillis() - r.perdidaEl > horas * 3600_000L) return false;
        return recuperacionesRestantes(r) > 0;
    }

    /**
     * Milisegundos que quedan para recuperar la racha. Devuelve -1 si no hay
     * limite de tiempo (recuperaciones.horas <= 0) y 0 si no es recuperable.
     */
    public long recuperacionRestante(Racha r) {
        if (r == null || !r.perdida || !recuperacionesActivadas()) return 0;
        long horas = horasRecuperacion();
        if (horas <= 0) return -1;
        return Math.max(0, r.perdidaEl + horas * 3600_000L - System.currentTimeMillis());
    }

    /** Recupera la racha perdida, gastando una recuperacion. */
    public void recuperar(Player p, Racha r) {
        if (p == null) return;
        // Defensa en profundidad: la racha debe existir y el jugador pertenecer.
        if (r == null || !r.incluye(p.getUniqueId())) {
            p.sendMessage(msg("recuperar-no"));
            sonido(p, "error");
            return;
        }
        if (!puedeRecuperar(r)) {
            p.sendMessage(msg("recuperar-no"));
            sonido(p, "error");
            return;
        }
        r.usadas++;
        r.dias = Math.max(1, r.diasPerdidos);
        r.diasPerdidos = 0;
        r.perdida = false;
        r.perdidaEl = 0;
        r.ultimoDia = Tiempo.ayer(); // asi hoy la racha sigue contando
        store.guardarRacha(r);
        avisar(r, "racha-recuperada", "%dias%", String.valueOf(r.dias));
        sonarMiembros(r, "recuperar");
        revisarRacha(r);
    }

    /** Dias que se muestran en los menus (incluye los perdidos recuperables). */
    public int diasMostrados(Racha r) {
        return r == null ? 0 : Math.max(r.dias, r.diasPerdidos);
    }

    /** Clave del mensaje de estado real de la racha (activa, riesgo, perdida...). */
    public String estadoClave(Racha r) {
        if (r == null) return "racha-estado-rota";
        if (r.perdida) {
            // Si ya no se puede recuperar (ventana expirada o sin usos) se
            // muestra como perdida aunque la evaluacion no la haya limpiado aun.
            return puedeRecuperar(r) ? "racha-estado-perdida" : "racha-estado-rota";
        }
        if (r.dias <= 0) return "racha-estado-rota";
        return Tiempo.hoy().equals(r.ultimoDia) ? "racha-estado-viva" : "racha-estado-riesgo";
    }

    // ------------------------------------------------------------------
    //  EFECTOS DEL MENU DE RACHA
    // ------------------------------------------------------------------

    /** Aplica al jugador uno de los efectos del menu de racha. */
    public void aplicarEfecto(Player p, String id, Racha r) {
        if (p == null) return;
        Efecto e = efectos.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (e == null) {
            p.sendMessage(msg("efecto-desconocido"));
            sonido(p, "error");
            return;
        }
        if (r == null || !r.incluye(p.getUniqueId()) || r.perdida || r.dias <= 0) {
            p.sendMessage(msg("efecto-sin-racha"));
            sonido(p, "error");
            return;
        }
        if (r.dias < e.diasMinimos) {
            p.sendMessage(msg("efecto-bloqueado",
                    "%dias%", String.valueOf(e.diasMinimos),
                    "%actual%", String.valueOf(r.dias)));
            sonido(p, "error");
            return;
        }
        long ahora = System.currentTimeMillis();
        long hasta = store.cooldown(p.getUniqueId(), e.id.toLowerCase(Locale.ROOT));
        if (e.cooldown > 0 && hasta > ahora) {
            p.sendMessage(msg("efecto-cooldown",
                    "%efecto%", e.nombre,
                    "%tiempo%", tiempoLegible(hasta - ahora)));
            sonido(p, "error");
            return;
        }
        darEfecto(p, e.efecto);
        if (e.cooldown > 0) {
            store.setCooldown(p.getUniqueId(), e.id.toLowerCase(Locale.ROOT),
                    ahora + e.cooldown * 1000L);
            store.guardar();
        }
        p.sendMessage(msg("efecto-aplicado", "%efecto%", e.nombre));
        sonido(p, "efecto");
    }

    /** Cuanto le queda de cooldown a un efecto para un jugador (0 si puede usarlo). */
    public long cooldownRestante(UUID jugador, String id) {
        long hasta = store.cooldown(jugador, id == null ? "" : id.toLowerCase(Locale.ROOT));
        long queda = hasta - System.currentTimeMillis();
        return Math.max(0, queda);
    }

    /** Formatea milisegundos como "1m 30s" / "45s". */
    public static String tiempoLegible(long ms) {
        long seg = Math.max(0, (ms + 999) / 1000);
        long min = seg / 60;
        long s = seg % 60;
        return min > 0 ? min + "m " + s + "s" : s + "s";
    }

    /** Formatea milisegundos como "2d 3h" / "5h 20m" / "45m". -1 = sin limite. */
    public static String tiempoLargo(long ms) {
        if (ms < 0) return "sin limite";
        long seg = Math.max(0, (ms + 999) / 1000);
        long d = seg / 86_400;
        seg %= 86_400;
        long h = seg / 3_600;
        seg %= 3_600;
        long m = seg / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    // ------------------------------------------------------------------
    //  MASCOTA DE RACHA
    // ------------------------------------------------------------------

    // ----- Ajustes (mascota.yml) -----

    public boolean mascotaActivada() {
        return mascotaYaml.getBoolean("mascota.activado", true);
    }

    /** Separacion a la derecha del jugador (bloques). */
    private double mascotaOffset() {
        return mascotaYaml.getDouble("mascota.offset-derecha", 0.8);
    }

    /** Altura sobre los pies del jugador (bloques). */
    private double mascotaAltura() {
        return mascotaYaml.getDouble("mascota.altura", 0.9);
    }

    /** Altura del holograma por encima de la cabeza. */
    private double mascotaHoloAltura() {
        return Math.max(0.2, mascotaYaml.getDouble("mascota.holo-altura", 1.2));
    }

    /** Color de fondo del holograma (alpha 0 = transparente). */
    private org.bukkit.Color mascotaFondo() {
        String s = mascotaYaml.getString("mascota.holo-fondo", "transparente");
        if (s == null) return org.bukkit.Color.fromARGB(0);
        s = s.trim().replace("#", "").replace("0x", "").replace("0X", "");
        if (s.isEmpty() || s.equalsIgnoreCase("transparente")
                || s.equalsIgnoreCase("transparent") || s.equalsIgnoreCase("none")
                || s.equalsIgnoreCase("ninguno")) {
            return org.bukkit.Color.fromARGB(0);
        }
        try {
            long v = Long.parseLong(s, 16);
            return s.length() <= 6
                    ? org.bukkit.Color.fromRGB((int) v)
                    : org.bukkit.Color.fromARGB((int) v);
        } catch (Exception e) {
            return org.bukkit.Color.fromARGB(0);
        }
    }

    /** Cuanto sube y baja flotando (0 = no flota). */
    private double mascotaFlotar() {
        return Math.max(0, mascotaYaml.getDouble("mascota.flotar", 0.12));
    }

    private String mascotaFuego() {
        return mascotaYaml.getString("mascota.fuego", "\uD83D\uDD25");
    }

    // ----- Mejora del Allay (ataque) -----

    /** Dias de racha necesarios para desbloquear el ataque del Allay. */
    public int mejoraDiasDesbloqueo() {
        return Math.max(0, mascotaYaml.getInt("mascota.mejora.dias-desbloqueo", 30));
    }

    /** Fraccion del dano del jugador que replica el Allay (0.5 = mitad). */
    public double mejoraFraccion() {
        return Math.max(0, mascotaYaml.getDouble("mascota.mejora.fraccion-dano", 0.5));
    }

    /** true si el jugador desbloqueo (permanente) la mejora de ataque del Allay. */
    public boolean mejoraDesbloqueada(UUID jugador) {
        return store.mejoraDesbloqueada(jugador);
    }

    /** true si la mejora esta activa (desbloqueada y no desactivada). */
    public boolean mejoraActiva(UUID jugador) {
        return store.mejoraActiva(jugador);
    }

    /** Da o quita el desbloqueo permanente de la mejora (admin). */
    public void setMejora(UUID jugador, boolean desbloqueada) {
        store.setMejoraDesbloqueada(jugador, desbloqueada);
        store.guardar();
    }

    /** Activa o desactiva el toggle de la mejora (menu). */
    public void setMejoraActiva(UUID jugador, boolean activa) {
        store.setMejoraActiva(jugador, activa);
        store.guardar();
    }

    /** Desbloquea la mejora para los miembros si la racha llega al umbral. */
    private void desbloquearMejora(Racha r) {
        int umbral = mejoraDiasDesbloqueo();
        if (umbral <= 0 || r.dias < umbral) return;
        for (UUID u : r.miembros) {
            if (store.mejoraDesbloqueada(u)) continue;
            store.setMejoraDesbloqueada(u, true);
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(msg("mejora-allay-desbloqueada",
                    "%dias%", String.valueOf(umbral)));
        }
    }

    /**
     * El Allay golpea a lo mismo que golpea el jugador (si tiene la mejora).
     * Se llama desde Escucha al dañar una entidad.
     */
    public void golpeMascota(Player atacante, org.bukkit.entity.Entity victima, double dano) {
        if (!(victima instanceof org.bukkit.entity.LivingEntity le)) return;
        if (!store.mejoraActiva(atacante.getUniqueId())) return;
        Mascota m = mascotas.get(atacante.getUniqueId());
        if (m == null || !m.entidad.isValid()) return;
        if (victima.getUniqueId().equals(m.entidad.getUniqueId())) return;
        if (victima.getUniqueId().equals(atacante.getUniqueId())) return;
        double d = dano * mejoraFraccion();
        if (d <= 0) return;

        final Allay allay = m.entidad;
        org.bukkit.Location vloc = victima.getLocation().add(0, 1, 0);

        // Orientar el Allay hacia el objetivo.
        org.bukkit.util.Vector dir = vloc.toVector().subtract(allay.getLocation().toVector());
        if (dir.lengthSquared() > 1.0E-4) {
            org.bukkit.Location rot = allay.getLocation();
            rot.setDirection(dir);
            allay.setRotation(rot.getYaw(), rot.getPitch());
        }

        // Animacion: dash hacia el objetivo (horizontal) + brazo + efecto.
        org.bukkit.util.Vector plano = vloc.toVector().subtract(allay.getLocation().toVector());
        plano.setY(0);
        if (plano.lengthSquared() > 1.0E-4) {
            plano.normalize();
            m.golpeDx = plano.getX();
            m.golpeDz = plano.getZ();
        } else {
            m.golpeDx = 0;
            m.golpeDz = 0;
        }
        m.golpeTicks = GOLPE_TICKS;
        allay.swingMainHand();
        victima.getWorld().spawnParticle(org.bukkit.Particle.CRIT, vloc, 10, 0.25, 0.25, 0.25, 0.15);
        victima.getWorld().playSound(vloc, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.3f);

        // El daño entra en el pico del golpe (no instantaneo), sin ticks de
        // invulnerabilidad para que cuente tras el del jugador, y con atacante jugador
        // para respetar las reglas de daño, PvP y protecciones del servidor.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!allay.isValid() || !le.isValid() || le.isDead() || !atacante.isOnline()) return;
            le.setNoDamageTicks(0);
            le.damage(d, atacante);
        }, GOLPE_TICKS);
    }

    // ----- Rachas de grupo -----

    /** Maximo de miembros por racha. */
    public int maxMiembrosRacha() {
        return Math.max(2, getConfig().getInt("max-miembros-racha", 6));
    }

    /** Anade un amigo a la racha que comparte con "ref" (grupo). */
    public void invitarAGrupo(Player p, UUID ref, UUID nuevo) {
        if (p == null || ref == null || nuevo == null) return;
        Racha r = store.racha(p.getUniqueId(), ref);
        if (r == null || !r.incluye(p.getUniqueId()) || r.perdida || r.dias <= 0) return;
        if (r.incluye(nuevo)) {
            p.sendMessage(msg("grupo-ya-miembro", "%jugador%", nombre(nuevo)));
            sonido(p, "error");
            return;
        }
        if (!store.sonAmigos(p.getUniqueId(), nuevo)) {
            p.sendMessage(msg("no-son-amigos", "%jugador%", nombre(nuevo)));
            sonido(p, "error");
            return;
        }
        for (UUID m : r.miembros) {
            if (mismaIp(m, nuevo)) {
                p.sendMessage(msg("multicuenta", "%jugador%", nombre(nuevo)));
                sonido(p, "error");
                return;
            }
        }
        if (r.tamano() >= maxMiembrosRacha()) {
            p.sendMessage(msg("grupo-lleno", "%max%", String.valueOf(maxMiembrosRacha())));
            sonido(p, "error");
            return;
        }
        for (UUID m : r.miembros) {
            if (store.existeRacha(m, nuevo)) {
                p.sendMessage(msg("grupo-conflicto", "%jugador%", nombre(nuevo)));
                sonido(p, "error");
                return;
            }
        }
        int max = getConfig().getInt("max-rachas", 3);
        if (max > 0 && store.rachas(nuevo).size() >= max) {
            p.sendMessage(msg("limite-rachas-amigo",
                    "%jugador%", nombre(nuevo), "%max%", String.valueOf(max)));
            sonido(p, "error");
            return;
        }
        String vieja = r.clave();
        r.miembros.add(nuevo);
        store.reindexarRacha(vieja, r);
        store.guardar();
        avisarTodos(r, "grupo-unido", "%jugador%", nombre(nuevo));
        sonarMiembros(r, "exito");
        evaluar(r);
        store.guardar();
    }

    /** Saca al jugador de una racha; si quedan menos de dos, la racha se borra. */
    public void salirRacha(Player p, UUID ref) {
        if (p == null || ref == null) return;
        Racha r = store.racha(p.getUniqueId(), ref);
        if (r == null || !r.incluye(p.getUniqueId()) || r.perdida || r.dias <= 0) return;
        String vieja = r.clave();
        r.miembros.remove(p.getUniqueId());
        if (r.tamano() < 2) {
            store.borrarRachaPorClave(vieja);
        } else {
            store.reindexarRacha(vieja, r);
        }
        store.guardar();
        avisarTodos(r, "grupo-salida", "%jugador%", p.getName());
        p.sendMessage(msg("grupo-salido"));
        sonido(p, "seleccionar");
    }

    /** Avisa a todos los miembros online. */
    public void avisarTodos(Racha r, String clave, String... extra) {
        for (UUID u : r.miembros) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.sendMessage(msg(clave, extra));
        }
    }

    // ----- Activar / quitar -----

    /** Activa o quita la mascota del jugador mostrando a ese amigo. */
    public void toggleMascota(Player p, UUID amigo) {
        if (p == null || amigo == null) return;
        if (!mascotaActivada()) {
            p.sendMessage(msg("mascota-desactivada"));
            sonido(p, "error");
            return;
        }
        Racha r = store.racha(p.getUniqueId(), amigo);
        if (r == null || !r.incluye(p.getUniqueId()) || r.perdida || r.dias <= 0) {
            p.sendMessage(msg("mascota-sin-racha"));
            sonido(p, "error");
            return;
        }
        if (amigo.equals(store.mascotaObjetivo(p.getUniqueId()))) {
            store.quitarMascota(p.getUniqueId());
            objetivosMascota.remove(p.getUniqueId());
            store.guardar();
            borrarEntidades(p.getUniqueId());
            p.sendMessage(msg("mascota-off"));
            sonido(p, "mascota");
        } else {
            store.setMascota(p.getUniqueId(), amigo);
            objetivosMascota.put(p.getUniqueId(), amigo);
            store.guardar();
            borrarEntidades(p.getUniqueId());
            Mascota m = spawnMascota(p, amigo);
            if (m != null) actualizarPosicion(p, m, amigo);
            p.sendMessage(msg("mascota-on", "%jugador%", nombre(amigo)));
            sonido(p, "mascota");
        }
    }

    /** Desactiva las mascotas que dependian de una racha que acaba de perderse. */
    private void limpiarMascotasRacha(Racha r) {
        for (UUID miembro : r.miembros) {
            UUID objetivo = store.mascotaObjetivo(miembro);
            if (objetivo != null && r.incluye(objetivo)) {
                store.quitarMascota(miembro);
                objetivosMascota.remove(miembro);
                borrarEntidades(miembro);
                Player jugador = Bukkit.getPlayer(miembro);
                if (jugador != null) jugador.sendMessage(msg("mascota-off"));
            }
        }
    }

    /** Al salir se quitan las entidades (se recrean al volver a entrar). */
    public void mascotaSalir(Player p) {
        objetivosMascota.remove(p.getUniqueId());
        borrarEntidades(p.getUniqueId());
    }

    private void actualizarMascotas() {
        tickMascota++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID obj = objetivosMascota.get(p.getUniqueId());
            if (obj == null || !mascotaActivada()) {
                if (mascotas.containsKey(p.getUniqueId())) borrarEntidades(p.getUniqueId());
                continue;
            }
            Mascota m = mascotas.get(p.getUniqueId());
            if (m == null || !m.entidad.isValid()) {
                m = spawnMascota(p, obj);
                if (m == null) continue;
            }
            actualizarPosicion(p, m, obj);
        }
        for (UUID u : new ArrayList<>(mascotas.keySet())) {
            if (Bukkit.getPlayer(u) == null) borrarEntidades(u);
        }
    }

    private Mascota spawnMascota(Player p, UUID amigo) {
        try {
            Location loc = posicionMascota(p, 0);

            Allay allay = p.getWorld().spawn(loc, Allay.class);
            allay.setAI(false);
            allay.setGravity(false);
            allay.setInvulnerable(true);
            allay.setSilent(true);
            allay.setPersistent(false);
            allay.setCollidable(false);
            allay.setCanPickupItems(false);
            allay.setCanDuplicate(false);
            allay.setRemoveWhenFarAway(false);

            TextDisplay td = p.getWorld().spawn(loc.clone().add(0, mascotaHoloAltura(), 0), TextDisplay.class);
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(mascotaFondo());
            td.setPersistent(false);
            td.setInterpolationDuration(2);

            Mascota m = new Mascota(allay, td);
            m.fondo = mascotaFondo().asARGB();
            mascotas.put(p.getUniqueId(), m);
            standDueno.put(allay.getUniqueId(), p.getUniqueId());
            standDueno.put(td.getUniqueId(), p.getUniqueId());
            return m;
        } catch (Throwable t) {
            getLogger().warning("No se pudo crear la mascota: " + t.getMessage());
            return null;
        }
    }

    /** Posicion de la mascota: a la derecha del jugador, con flotado opcional. */
    private Location posicionMascota(Player p, double bobExtra) {
        Location base = p.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double off = mascotaOffset();
        double bob = mascotaFlotar() * Math.sin(tickMascota * 0.35) + bobExtra;
        return base.clone().add(rx * off, mascotaAltura() + bob, rz * off);
    }

    private void actualizarPosicion(Player p, Mascota m, UUID amigo) {
        Location loc = posicionMascota(p, 0);

        // Animacion de golpe: el Allay se lanza hacia el objetivo y vuelve.
        if (m.golpeTicks > 0) {
            double t = 1.0 - (m.golpeTicks / (double) GOLPE_TICKS); // 0 -> 1
            double curva = Math.sin(Math.PI * t);                   // sube y baja
            loc.add(m.golpeDx * GOLPE_DIST * curva,
                    0.2 * curva,
                    m.golpeDz * GOLPE_DIST * curva);
            m.golpeTicks--;
        }

        m.entidad.teleport(loc);
        m.holo.teleport(loc.clone().add(0, mascotaHoloAltura(), 0));

        // Si cambio el color de fondo, refresca el holograma.
        int fondo = mascotaFondo().asARGB();
        if (fondo != m.fondo) {
            m.holo.setDefaultBackground(false);
            m.holo.setBackgroundColor(org.bukkit.Color.fromARGB(fondo));
            m.fondo = fondo;
        }

        Component texto = holoMascota(p, amigo);
        if (!texto.equals(m.texto)) {
            m.holo.text(texto);
            m.texto = texto;
        }
    }

    private Component holoMascota(Player p, UUID amigo) {
        Racha r = store.racha(p.getUniqueId(), amigo);
        int dias = r == null ? 0 : Math.max(r.dias, r.diasPerdidos);
        String l1 = mascotaYaml.getString("mascota.holo-titulo", "&6&lMascota de racha");
        String l2 = mascotaYaml.getString("mascota.holo-linea",
                "&e%jugador% &7y &e%amigo% &7%fuego% &6%dias%");
        l2 = l2.replace("%jugador%", p.getName())
                .replace("%amigo%", nombre(amigo))
                .replace("%fuego%", mascotaFuego())
                .replace("%dias%", String.valueOf(dias));
        return Component.join(Component.newline(), List.of(texto(l1), texto(l2)));
    }

    // ----- Clic sobre la mascota -----

    /** Dueno de esa entidad (Allay u holograma) si es una mascota, o null. */
    public UUID duenoDeMascota(org.bukkit.entity.Entity e) {
        return e == null ? null : standDueno.get(e.getUniqueId());
    }

    /**
     * Clic sobre una mascota. Solo el dueño y su amigo de racha pueden abrir el
     * menu; cualquier otro no hace nada.
     */
    public void clicMascota(Player p, UUID dueno) {
        if (p == null || dueno == null) return;
        UUID objetivo = store.mascotaObjetivo(dueno);
        if (objetivo == null) return;
        if (p.getUniqueId().equals(dueno)) {
            sonido(p, "abrir");
            menu.abrirRacha(p, objetivo);
        } else if (p.getUniqueId().equals(objetivo)) {
            sonido(p, "abrir");
            menu.abrirRacha(p, dueno);
        }
    }

    private void borrarEntidades(UUID uuid) {
        Mascota m = mascotas.remove(uuid);
        if (m == null) return;
        standDueno.remove(m.entidad.getUniqueId());
        standDueno.remove(m.holo.getUniqueId());
        if (m.entidad.isValid()) m.entidad.remove();
        if (m.holo.isValid()) m.holo.remove();
    }

    /**
     * Tarea periodica: marca como "hoy" a los jugadores conectados (para que el
     * cambio de dia no rompa una racha que sus miembros mantienen en linea) y
     * evalua todas las rachas.
     */
    private void revisarRachas() {
        boolean nuevoDia = asegurarDia();
        marcarOnline();
        for (Racha r : store.todasRachas()) evaluar(r);
        if (nuevoDia) {
            for (Player p : Bukkit.getOnlinePlayers()) avisarRiesgo(p);
        }
        store.guardar();
    }

    // ------------------------------------------------------------------
    //  AMIGOS / SOLICITUDES (acciones usadas por el menu y el comando)
    // ------------------------------------------------------------------

    public void enviarSolicitud(Player de, UUID para) {
        if (de == null || para == null) return;
        UUID yo = de.getUniqueId();
        if (yo.equals(para)) {
            de.sendMessage(msg("no-te-agregues"));
            sonido(de, "error");
            return;
        }
        if (store.sonAmigos(yo, para)) {
            de.sendMessage(msg("ya-amigos", "%jugador%", nombre(para)));
            sonido(de, "error");
            return;
        }
        if (store.haySolicitud(yo, para)) {
            de.sendMessage(msg("ya-solicitud", "%jugador%", nombre(para)));
            sonido(de, "error");
            return;
        }
        // Si el otro ya me habia enviado una, la acepto directamente.
        if (store.haySolicitud(para, yo)) {
            aceptarSolicitud(de, para);
            return;
        }
        int max = Math.max(1, getConfig().getInt("max-amigos", 20));
        if (store.amigos(yo).size() >= max || store.amigos(para).size() >= max) {
            de.sendMessage(msg("limite-amigos", "%max%", String.valueOf(max)));
            sonido(de, "error");
            return;
        }
        store.crearSolicitud(yo, para);
        store.guardar();
        de.sendMessage(msg("solicitud-enviada", "%jugador%", nombre(para)));
        sonido(de, "exito");
        Player otro = Bukkit.getPlayer(para);
        if (otro != null) {
            otro.sendMessage(msg("solicitud-recibida", "%jugador%", de.getName()));
            sonido(otro, "aviso");
        }
    }

    public void aceptarSolicitud(Player p, UUID de) {
        if (p == null || de == null) return;
        if (!store.haySolicitud(de, p.getUniqueId())) {
            p.sendMessage(msg("sin-solicitud", "%jugador%", nombre(de)));
            sonido(p, "error");
            return;
        }
        // Se vuelve a comprobar el limite: la solicitud pudo enviarse antes de
        // que cualquiera de los dos llegara al maximo de amigos.
        int max = Math.max(1, getConfig().getInt("max-amigos", 20));
        if (store.amigos(p.getUniqueId()).size() >= max
                || store.amigos(de).size() >= max) {
            p.sendMessage(msg("limite-amigos", "%max%", String.valueOf(max)));
            sonido(p, "error");
            return;
        }
        store.borrarSolicitud(de, p.getUniqueId());
        store.agregarAmigo(p.getUniqueId(), de);
        store.guardar();
        p.sendMessage(msg("solicitud-aceptada", "%jugador%", nombre(de)));
        sonido(p, "exito");
        Player otro = Bukkit.getPlayer(de);
        if (otro != null) otro.sendMessage(msg("solicitud-aceptada", "%jugador%", p.getName()));
    }

    public void rechazarSolicitud(Player p, UUID de) {
        if (p == null || de == null) return;
        if (!store.haySolicitud(de, p.getUniqueId())) {
            p.sendMessage(msg("sin-solicitud", "%jugador%", nombre(de)));
            sonido(p, "error");
            return;
        }
        store.borrarSolicitud(de, p.getUniqueId());
        store.guardar();
        p.sendMessage(msg("solicitud-rechazada", "%jugador%", nombre(de)));
        sonido(p, "seleccionar");
    }

    public void eliminarAmigo(Player p, UUID otro) {
        if (p == null || otro == null) return;
        if (!store.sonAmigos(p.getUniqueId(), otro)) {
            p.sendMessage(msg("no-son-amigos", "%jugador%", nombre(otro)));
            sonido(p, "error");
            return;
        }
        store.eliminarAmigo(p.getUniqueId(), otro);
        // Solo borra la racha si era de dos; en grupo, solo te saca a ti.
        Racha r = store.racha(p.getUniqueId(), otro);
        if (r != null) {
            if (r.tamano() <= 2) {
                store.borrarRachaPorClave(r.clave());
            } else {
                String vieja = r.clave();
                r.miembros.remove(p.getUniqueId());
                store.reindexarRacha(vieja, r);
            }
        }
        store.borrarSolicitudRacha(p.getUniqueId(), otro);
        store.borrarSolicitudRacha(otro, p.getUniqueId());
        store.guardar();
        p.sendMessage(msg("amigo-eliminado", "%jugador%", nombre(otro)));
        sonido(p, "seleccionar");
    }

    public void solicitarRacha(Player p, UUID amigo) {
        if (p == null || amigo == null) return;
        if (!store.sonAmigos(p.getUniqueId(), amigo)) {
            p.sendMessage(msg("no-son-amigos", "%jugador%", nombre(amigo)));
            sonido(p, "error");
            return;
        }
        if (mismaIp(p.getUniqueId(), amigo)) {
            p.sendMessage(msg("multicuenta", "%jugador%", nombre(amigo)));
            sonido(p, "error");
            return;
        }
        if (store.existeRacha(p.getUniqueId(), amigo)) {
            p.sendMessage(msg("racha-ya-existe", "%jugador%", nombre(amigo)));
            sonido(p, "error");
            return;
        }
        int max = getConfig().getInt("max-rachas", 3);
        if (max > 0 && (store.rachas(p.getUniqueId()).size() >= max
                || store.rachas(amigo).size() >= max)) {
            p.sendMessage(msg("limite-rachas", "%max%", String.valueOf(max)));
            sonido(p, "error");
            return;
        }
        if (store.haySolicitudRacha(amigo, p.getUniqueId())) {
            aceptarRacha(p, amigo);
            return;
        }
        if (store.haySolicitudRacha(p.getUniqueId(), amigo)) return;
        store.crearSolicitudRacha(p.getUniqueId(), amigo);
        store.guardar();
        p.sendMessage(msg("racha-solicitud-enviada", "%jugador%", nombre(amigo)));
        sonido(p, "exito");
        Player otro = Bukkit.getPlayer(amigo);
        if (otro != null) {
            otro.sendMessage(msg("racha-solicitud-recibida", "%jugador%", p.getName()));
            sonido(otro, "aviso");
        }
    }

    public void aceptarRacha(Player p, UUID de) {
        if (p == null || de == null) return;
        if (!store.haySolicitudRacha(de, p.getUniqueId())) return;
        if (mismaIp(p.getUniqueId(), de)) {
            store.borrarSolicitudRacha(de, p.getUniqueId());
            store.guardar();
            p.sendMessage(msg("multicuenta", "%jugador%", nombre(de)));
            sonido(p, "error");
            return;
        }
        int max = getConfig().getInt("max-rachas", 3);
        if (max > 0 && (store.rachas(p.getUniqueId()).size() >= max
                || store.rachas(de).size() >= max)) {
            p.sendMessage(msg("limite-rachas", "%max%", String.valueOf(max)));
            sonido(p, "error");
            return;
        }
        store.borrarSolicitudRacha(de, p.getUniqueId());
        store.crearRacha(p.getUniqueId(), de);
        store.guardar();
        revisarRacha(p.getUniqueId(), de);
        p.sendMessage(msg("racha-creada", "%jugador%", nombre(de)));
        sonido(p, "exito");
        Player otro = Bukkit.getPlayer(de);
        if (otro != null) {
            otro.sendMessage(msg("racha-creada", "%jugador%", p.getName()));
            sonido(otro, "exito");
        }
    }

    public void rechazarRacha(Player p, UUID de) {
        if (p == null || de == null) return;
        if (!store.haySolicitudRacha(de, p.getUniqueId())) return;
        store.borrarSolicitudRacha(de, p.getUniqueId());
        store.guardar();
        p.sendMessage(msg("racha-rechazada", "%jugador%", nombre(de)));
        sonido(p, "seleccionar");
    }

    /** Re-evalua una racha concreta (tras crearla, aceptar o cambiar miembros). */
    public void revisarRacha(Racha r) {
        if (r != null) {
            evaluar(r);
            store.guardar();
        }
    }

    public void revisarRacha(UUID a, UUID b) {
        if (a == null || b == null) return;
        revisarRacha(store.racha(a, b));
    }

    // ------------------------------------------------------------------
    //  ADMIN (comandos /racha admin)
    // ------------------------------------------------------------------

    /** Suma, quita o fija los dias de una racha. Devuelve la racha o null. */
    public Racha adminDias(UUID a, UUID b, String modo, int n) {
        if (a == null || b == null) return null;
        Racha r = store.racha(a, b);
        if (r == null) return null;
        if (!aplicarDias(r, modo, n)) return null;
        store.guardarRacha(r);
        store.guardar();
        desbloquearMejora(r);
        store.guardar();
        return r;
    }

    /**
     * Suma, quita o fija los dias a TODAS las rachas de un jugador. Devuelve
     * cuantas rachas se actualizaron (0 si no tiene ninguna).
     */
    public int adminDiasJugador(UUID jugador, String modo, int n) {
        if (jugador == null) return 0;
        int ok = 0;
        for (Racha r : store.rachas(jugador)) {
            if (!aplicarDias(r, modo, n)) continue;
            store.guardarRacha(r);
            desbloquearMejora(r);
            ok++;
        }
        store.guardar();
        return ok;
    }

    /** Aplica "add"/"remove"/"set" a una racha. false si el modo es invalido. */
    private boolean aplicarDias(Racha r, String modo, int n) {
        switch (modo == null ? "" : modo.toLowerCase(Locale.ROOT)) {
            case "add", "agregar", "sumar" -> r.dias = Math.max(0, r.dias + n);
            case "remove", "quitar", "restar" -> r.dias = Math.max(0, r.dias - n);
            case "set", "poner", "fijar" -> r.dias = Math.max(0, n);
            default -> {
                return false;
            }
        }
        if (r.dias > 0) {
            r.perdida = false;
            r.diasPerdidos = 0;
            r.perdidaEl = 0;
            // Se marca como alimentada hoy para que la evaluacion periodica no
            // sume un dia extra ni la rompa justo despues del cambio del admin.
            r.ultimoDia = Tiempo.hoy();
        }
        return true;
    }

    /** Borra una racha por la pareja (admin). */
    public boolean adminReset(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (!store.existeRacha(a, b)) return false;
        store.borrarRacha(a, b);
        store.guardar();
        return true;
    }

    private void darRecompensa(Racha r, Recompensa rec) {
        for (UUID u : r.miembros) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            for (String ef : rec.efectos) darEfecto(p, ef);
            for (String ob : rec.objetos) darObjeto(p, ob);
        }
        for (String cmd : rec.comandos) {
            for (UUID u : r.miembros) {
                String c = cmd
                        .replace("%player%", nombre(u))
                        .replace("%jugador%", nombre(u))
                        .replace("%amigo%", nombresOtros(r, u))
                        .replace("%dias%", String.valueOf(rec.dia));
                despachar(c);
            }
        }
        for (UUID u : r.miembros) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(msg("racha-recompensa",
                        "%dias%", String.valueOf(rec.dia),
                        "%jugador%", nombresOtros(r, u)));
                sonido(p, "hito");
            }
        }
    }

    private void darEfecto(Player p, String def) {
        if (def == null || def.trim().isEmpty()) return;
        String[] partes = def.split(":");
        PotionEffectType tipo = efectoDe(partes[0]);
        if (tipo == null) {
            getLogger().warning("Efecto desconocido: " + def);
            return;
        }
        // El nivel se escribe 1-based (SPEED:1 = Velocidad I); el amplificador es 0-based.
        int nivel = partes.length >= 2 ? parseInt(partes[1], 1) : 1;
        int segundos = partes.length >= 3 ? parseInt(partes[2], 300) : 300;
        p.addPotionEffect(new PotionEffect(tipo, Math.max(1, segundos) * 20, Math.max(0, nivel - 1)));
    }

    @SuppressWarnings("removal")
    private PotionEffectType efectoDe(String nombre) {
        String n = nombre.trim();
        if (n.isEmpty()) return null;
        NamespacedKey key = NamespacedKey.fromString(n.toLowerCase(Locale.ROOT));
        if (key != null) {
            PotionEffectType t = Registry.EFFECT.get(key);
            if (t != null) return t;
        }
        try {
            return PotionEffectType.getByName(n.toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return null;
        }
    }

    private void darObjeto(Player p, String def) {
        if (def == null || def.trim().isEmpty()) return;
        String[] partes = def.split(":");
        Material m = Material.matchMaterial(partes[0].trim());
        if (m == null) {
            getLogger().warning("Material desconocido: " + def);
            return;
        }
        int cant = partes.length >= 2 ? Math.max(1, parseInt(partes[1], 1)) : 1;
        p.getInventory().addItem(new ItemStack(m, cant));
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void despachar(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        } else {
            Bukkit.getScheduler().runTask(this,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c));
        }
    }

    // ------------------------------------------------------------------
    //  UTILIDADES
    // ------------------------------------------------------------------

    private void avisar(Racha r, String clave, String... extra) {
        for (UUID u : r.miembros) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            String[] pares = new String[extra.length + 2];
            pares[0] = "%jugador%";
            pares[1] = nombresOtros(r, u);
            System.arraycopy(extra, 0, pares, 2, extra.length);
            p.sendMessage(msg(clave, pares));
        }
    }

    /** Nombres de los miembros distintos de "excluir", separados por comas. */
    public String nombresOtros(Racha r, UUID excluir) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : r.miembros) {
            if (u.equals(excluir)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(nombre(u));
        }
        return sb.toString();
    }

    /** Nombres de todos los miembros, separados por comas. */
    public String nombresDe(Racha r) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : r.miembros) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(nombre(u));
        }
        return sb.toString();
    }

    /** Nombre de un jugador (online u offline). */
    public String nombre(UUID u) {
        if (u == null) return "?";
        Player p = Bukkit.getPlayer(u);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n != null ? n : u.toString();
    }

    public String crudo(String clave) {
        String v = getConfig().getString("mensajes." + clave);
        return v != null ? v : Textos.mensaje(clave);
    }

    public Component texto(String conColor) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(conColor == null ? "" : conColor);
    }

    /** Mensaje de chat (con prefijo) como Component, con reemplazos opcionales. */
    public Component msg(String clave, String... reemplazos) {
        String s = crudo(clave);
        for (int i = 0; i + 1 < reemplazos.length; i += 2) {
            s = s.replace(reemplazos[i], reemplazos[i + 1]);
        }
        return texto(crudo("prefix") + s);
    }

    public String textoMenu(String clave) {
        String externo = menusYaml.getString("menus." + clave);
        if (externo != null) return externo;
        String v = getConfig().getString("menus." + clave);
        return v != null ? v : Textos.menu(clave);
    }

    /** Tamaño de inventario definido en plugins/OdisseyRacha/menus/gui.yml. */
    public int tamanoMenu(String id, int defecto) {
        int n = menusYaml.getInt("guis." + id + ".size", defecto);
        if (n == 0) return defecto;
        int minimo = id.equals("racha") ? 36 : 27;
        if (n < minimo || n > 54 || n % 9 != 0) return defecto;
        return n;
    }

    public int filasMenu(String id, int cantidad, int maxContenido) {
        int fijo = menusYaml.getInt("guis." + id + ".size", 0);
        if (fijo >= 27 && fijo <= 54 && fijo % 9 == 0) return fijo / 9;
        int filas = Math.max(3, Math.min(6, Math.max(1, (cantidad + maxContenido - 1) / maxContenido) + 2));
        int min = Math.max(3, Math.min(6, menusYaml.getInt("guis." + id + ".min-rows", 3)));
        int max = Math.max(min, Math.min(6, menusYaml.getInt("guis." + id + ".max-rows", 6)));
        return Math.max(min, Math.min(max, filas));
    }

    /** Slot de un elemento definido en el archivo externo de las GUIs. */
    public int slotMenu(String gui, String elemento, int defecto) {
        int slot = menusYaml.getInt("guis." + gui + ".slots." + elemento, defecto);
        int size = tamanoMenu(gui, 54);
        if (slot >= 0 && slot < size) return slot;
        return defecto >= 0 && defecto < size ? defecto : -1;
    }

    public List<Integer> slotsContenidoMenu(String gui) {
        List<Integer> slots = menusYaml.getIntegerList("guis." + gui + ".content-slots");
        return new ArrayList<>(slots);
    }

    public Material materialMenu(String accion, Material defecto) {
        String nombre = accion == null ? null : menusYaml.getString("guis.materials." + accion);
        if (nombre == null && accion != null && accion.startsWith("volver-")) {
            nombre = menusYaml.getString("guis.materials.volver");
        }
        Material material = nombre == null ? null : Material.matchMaterial(nombre);
        return material == null ? defecto : material;
    }

    private void cargarMenus() {
        File dir = new File(getDataFolder(), "menus");
        if (!dir.exists() && !dir.mkdirs()) {
            getLogger().warning("No se pudo crear la carpeta menus; se usara la configuracion integrada.");
            menusYaml = new YamlConfiguration();
            return;
        }
        crearAyudaMenus(dir);
        File archivo = new File(dir, "gui.yml");
        if (!archivo.exists()) {
            YamlConfiguration inicial = new YamlConfiguration();
            ConfigurationSection textos = getConfig().getConfigurationSection("menus");
            if (textos != null) copiarSeccion(textos, "menus", inicial);
            ConfigurationSection efectosGui = getConfig().getConfigurationSection("efectos-racha");
            if (efectosGui != null) copiarSeccion(efectosGui, "efectos-racha", inicial);
            inicial.set("guis.principal.size", 27);
            inicial.set("guis.amigo.size", 27);
            inicial.set("guis.racha.size", 54);
            inicial.set("guis.principal.slots.amigos", 10);
            inicial.set("guis.principal.slots.solicitudes", 12);
            inicial.set("guis.principal.slots.anadir", 14);
            inicial.set("guis.principal.slots.rachas", 16);
            inicial.set("guis.amigo.slots.solicitar", 11);
            inicial.set("guis.amigo.slots.racha", 11);
            inicial.set("guis.amigo.slots.eliminar", 15);
            inicial.set("guis.racha.slots.info", 4);
            inicial.set("guis.materials.abrir-amigos", "PLAYER_HEAD");
            inicial.set("guis.materials.abrir-solicitudes", "WRITABLE_BOOK");
            inicial.set("guis.materials.abrir-anadir", "NAME_TAG");
            inicial.set("guis.materials.abrir-rachas", "BLAZE_POWDER");
            inicial.set("guis.materials.amigo", "PLAYER_HEAD");
            inicial.set("guis.materials.sol", "PLAYER_HEAD");
            inicial.set("guis.materials.anadir", "PLAYER_HEAD");
            inicial.set("guis.materials.racha", "PLAYER_HEAD");
            inicial.set("guis.materials.racha-info", "PLAYER_HEAD");
            inicial.set("guis.materials.racha-sol", "PLAYER_HEAD");
            inicial.set("guis.materials.solicitar", "BLAZE_POWDER");
            inicial.set("guis.materials.eliminar", "BARRIER");
            inicial.set("guis.materials.recuperar", "NETHER_STAR");
            inicial.set("guis.materials.mascota", "ALLAY_SPAWN_EGG");
            inicial.set("guis.materials.mejora-allay", "NETHERITE_SWORD");
            inicial.set("guis.materials.invitar", "PLAYER_HEAD");
            inicial.set("guis.materials.invitar-amigo", "PLAYER_HEAD");
            inicial.set("guis.materials.salir-grupo", "BARRIER");
            inicial.set("guis.materials.cerrar", "BARRIER");
            inicial.set("guis.materials.volver", "ARROW");
            inicial.set("guis.amigos.min-rows", 3);
            inicial.set("guis.amigos.max-rows", 6);
            inicial.set("guis.solicitudes.min-rows", 3);
            inicial.set("guis.solicitudes.max-rows", 6);
            inicial.set("guis.anadir.min-rows", 3);
            inicial.set("guis.anadir.max-rows", 6);
            inicial.set("guis.rachas.min-rows", 3);
            inicial.set("guis.rachas.max-rows", 6);
            inicial.set("guis.invitar.min-rows", 3);
            inicial.set("guis.invitar.max-rows", 6);
            try {
                inicial.save(archivo);
            } catch (java.io.IOException e) {
                getLogger().warning("No se pudo crear menus/gui.yml: " + e.getMessage());
            }
        }
        menusYaml = YamlConfiguration.loadConfiguration(archivo);
    }

    private void copiarSeccion(ConfigurationSection desde, String ruta, YamlConfiguration hasta) {
        for (String clave : desde.getKeys(false)) {
            String destino = ruta + "." + clave;
            ConfigurationSection hijo = desde.getConfigurationSection(clave);
            if (hijo != null) copiarSeccion(hijo, destino, hasta);
            else hasta.set(destino, desde.get(clave));
        }
    }

    private void crearAyudaMenus(File dir) {
        File ayuda = new File(dir, "LEEME.txt");
        if (ayuda.exists()) return;
        String contenido = """
                OdisseyRacha - configuracion de menus

                Edita gui.yml y usa /racha reload para aplicar los cambios.
                La seccion menus contiene todos los titulos, nombres, lore,
                textos de estado, botones, vacios y materiales del marco.
                La seccion efectos-racha contiene nombre y lore de cada efecto.

                En guis.<id>.size define el tamano fijo en slots (multiplo de 9,
                entre 9 y 54). Para listas usa min-rows y max-rows; tambien puedes
                fijar size. IDs: principal, amigos, solicitudes, anadir, rachas,
                amigo, racha e invitar.

                En guis.<id>.slots.<elemento> cambia la posicion de botones fijos.
                En guis.<id>.content-slots define una lista de slots para los
                elementos repetidos de una lista. Para los efectos usa
                guis.racha-effects.content-slots.

                En guis.materials.<accion> cambia el material de una accion.
                Para los efectos, el material individual esta en
                efectos-racha.<id>.material. El material de vacio se configura
                con guis.materials.vacio; el borde y relleno estan en menus.

                Los slots empiezan en cero. Evita poner dos elementos en el mismo
                slot y conserva espacio para el marco y la navegacion.
                """;
        try {
            java.nio.file.Files.writeString(ayuda.toPath(), contenido, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            getLogger().warning("No se pudo crear menus/LEEME.txt: " + e.getMessage());
        }
    }

    public Almacen store() {
        return store;
    }

    public MenuRacha menu() {
        return menu;
    }

    public Map<String, Efecto> efectos() {
        return efectos;
    }

    /** Une el estado de una mascota activa (Allay + holograma). */
    private static final class Mascota {
        final Allay entidad;
        final TextDisplay holo;
        Component texto;
        int fondo = -1;
        int golpeTicks;          // frames restantes de la animacion de golpe
        double golpeDx, golpeDz; // direccion (horizontal) hacia la que se lanza

        Mascota(Allay entidad, TextDisplay holo) {
            this.entidad = entidad;
            this.holo = holo;
        }
    }

    /** Un tramo del multiplicador de racha: a partir de "dias", vale "mult". */
    private static final class Tramo {
        final int dias;
        final double mult;

        Tramo(int dias, double mult) {
            this.dias = dias;
            this.mult = mult;
        }
    }

    /** Un efecto del menu de racha (se activa al clicar su item). */
    public static final class Efecto {
        public final String id;
        public String nombre;
        public String material;
        public String efecto;
        public int diasMinimos;
        public int cooldown;
        public final List<String> lore = new ArrayList<>();

        public Efecto(String id) {
            this.id = id;
        }
    }

    /** Una recompensa por hito de dias. */
    public static final class Recompensa {
        public final int dia;
        public final List<String> efectos = new ArrayList<>();
        public final List<String> objetos = new ArrayList<>();
        public final List<String> comandos = new ArrayList<>();

        public Recompensa(int dia) {
            this.dia = dia;
        }
    }
}
