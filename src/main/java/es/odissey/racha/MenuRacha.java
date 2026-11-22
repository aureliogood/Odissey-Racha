package es.odissey.racha;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Los menus de inventario del plugin (principal, amigos, solicitudes, etc.).
 *
 * Todos los menus llevan marco decorativo (borde + relleno configurables en
 * config.yml -> menus.borde-material / menus.relleno-material).
 */
public final class MenuRacha {

    private final OdisseyRacha plugin;
    private final NamespacedKey keyTipo;
    private final NamespacedKey keyUuid;
    private final NamespacedKey keyId;

    public MenuRacha(OdisseyRacha plugin) {
        this.plugin = plugin;
        this.keyTipo = new NamespacedKey(plugin, "tipo");
        this.keyUuid = new NamespacedKey(plugin, "uuid");
        this.keyId = new NamespacedKey(plugin, "id");
    }

    // ------------------------------------------------------------------
    //  VISTAS
    // ------------------------------------------------------------------

    public void abrirPrincipal(Player p) {
        UUID yo = p.getUniqueId();
        MenuHolder h = new MenuHolder(MenuHolder.Vista.PRINCIPAL, null);
        Inventory inv = Bukkit.createInventory(h, plugin.tamanoMenu("principal", 27),
                plugin.texto(plugin.textoMenu("titulo-principal")));
        h.setInventory(inv);
        decorar(inv, inv.getSize() / 9);

        poner(inv, "principal", "amigos", 10, cabeza(yo, "abrir-amigos", tm("item-amigos"), lorem(true,
                tm("item-amigos-desc"),
                tm("item-amigos-lore", "%n%", plugin.store().amigos(yo).size()))));
        poner(inv, "principal", "solicitudes", 12, item(Material.WRITABLE_BOOK, "abrir-solicitudes", null, tm("item-solicitudes"),
                lorem(true,
                        tm("item-solicitudes-desc"),
                        tm("item-solicitudes-lore", "%n%", plugin.store().solicitudes(yo).size()))));
        poner(inv, "principal", "anadir", 14, item(Material.NAME_TAG, "abrir-anadir", null, tm("item-anadir"),
                lorem(true, tm("item-anadir-desc"))));
        poner(inv, "principal", "rachas", 16, item(Material.BLAZE_POWDER, "abrir-rachas", null, tm("item-rachas"),
                lorem(true, tm("item-rachas-desc"))));

        p.openInventory(inv);
    }

    public void abrirAmigos(Player p) {
        UUID yo = p.getUniqueId();
        List<UUID> amigos = new ArrayList<>(plugin.store().amigos(yo));
        amigos.sort(Comparator.comparing(plugin::nombre, String.CASE_INSENSITIVE_ORDER));

        MenuHolder h = new MenuHolder(MenuHolder.Vista.AMIGOS, null);
        int filas = plugin.filasMenu("amigos", amigos.size(), 7);
        Inventory inv = Bukkit.createInventory(h, filas * 9,
                plugin.texto(plugin.textoMenu("titulo-amigos")));
        h.setInventory(inv);
        decorar(inv, filas);

        List<Integer> libres = contenido(filas, "amigos");
        int i = 0;
        for (UUID a : amigos) {
            if (i >= libres.size()) break;
            Racha r = plugin.store().racha(yo, a);
            int dias = plugin.diasMostrados(r);
            String estado = r == null ? plugin.crudo("racha-estado-rota") : estado(r);
            List<String> lore = new ArrayList<>();
            lore.add(plugin.textoMenu("marca"));
            lore.add("");
            lore.add(tm("item-jugador-lore-amigo", "%dias%", String.valueOf(dias)));
            lore.add(tm("item-racha-lore-estado", "%estado%", estado));
            lore.add("");
            lore.add(tm("item-jugador-lore-solicitar"));
            inv.setItem(libres.get(i++), cabeza(a, "amigo", plugin.nombre(a), lore));
        }
        if (amigos.isEmpty()) {
            inv.setItem(libres.get(0), item(Material.BARRIER, null, null, tm("vacio"), lore()));
        }
        nav(inv, filas, "volver-principal", null);
        p.openInventory(inv);
    }

    public void abrirSolicitudes(Player p) {
        UUID yo = p.getUniqueId();
        List<UUID> sol = plugin.store().solicitudes(yo);
        sol.sort(Comparator.comparing(plugin::nombre, String.CASE_INSENSITIVE_ORDER));

        MenuHolder h = new MenuHolder(MenuHolder.Vista.SOLICITUDES, null);
        int filas = plugin.filasMenu("solicitudes", sol.size(), 7);
        Inventory inv = Bukkit.createInventory(h, filas * 9,
                plugin.texto(plugin.textoMenu("titulo-solicitudes")));
        h.setInventory(inv);
        decorar(inv, filas);

        List<Integer> libres = contenido(filas, "solicitudes");
        int i = 0;
        for (UUID de : sol) {
            if (i >= libres.size()) break;
            inv.setItem(libres.get(i++), cabeza(de, "sol", plugin.nombre(de), lorem(false,
                    tm("item-solicitud-lore"),
                    tm("item-solicitud-lore-rechazar"))));
        }
        if (sol.isEmpty()) {
            inv.setItem(libres.get(0), item(Material.BARRIER, null, null, tm("vacio"), lore()));
        }
        nav(inv, filas, "volver-principal", null);
        p.openInventory(inv);
    }

    public void abrirAnadir(Player p) {
        UUID yo = p.getUniqueId();
        List<Player> otros = new ArrayList<>();
        for (Player otro : Bukkit.getOnlinePlayers()) {
            UUID u = otro.getUniqueId();
            if (u.equals(yo)) continue;
            if (plugin.store().sonAmigos(yo, u)) continue;
            if (plugin.store().haySolicitud(yo, u)) continue;
            otros.add(otro);
        }
        otros.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        MenuHolder h = new MenuHolder(MenuHolder.Vista.ANADIR, null);
        int filas = plugin.filasMenu("anadir", otros.size(), 7);
        Inventory inv = Bukkit.createInventory(h, filas * 9,
                plugin.texto(plugin.textoMenu("titulo-anadir")));
        h.setInventory(inv);
        decorar(inv, filas);

        List<Integer> libres = contenido(filas, "anadir");
        int i = 0;
        for (Player otro : otros) {
            if (i >= libres.size()) break;
            inv.setItem(libres.get(i++), cabeza(otro.getUniqueId(), "anadir", otro.getName(),
                    lorem(true, tm("item-jugador-lore-anadir"))));
        }
        if (otros.isEmpty()) {
            inv.setItem(libres.get(0), item(Material.BARRIER, null, null, tm("vacio"), lore()));
        }
        nav(inv, filas, "volver-principal", null);
        p.openInventory(inv);
    }

    public void abrirRachas(Player p) {
        UUID yo = p.getUniqueId();
        List<UUID> pend = plugin.store().solicitudesRacha(yo);
        pend.sort(Comparator.comparing(plugin::nombre, String.CASE_INSENSITIVE_ORDER));
        List<Racha> rachas = plugin.store().rachas(yo);
        rachas.sort(Comparator
                .comparingInt((Racha r) -> Math.max(r.dias, r.diasPerdidos)).reversed()
                .thenComparing(r -> plugin.nombresOtros(r, yo), String.CASE_INSENSITIVE_ORDER));

        MenuHolder h = new MenuHolder(MenuHolder.Vista.RACHAS, null);
        int filas = plugin.filasMenu("rachas", pend.size() + rachas.size(), 7);
        Inventory inv = Bukkit.createInventory(h, filas * 9,
                plugin.texto(plugin.textoMenu("titulo-rachas")));
        h.setInventory(inv);
        decorar(inv, filas);

        List<Integer> libres = contenido(filas, "rachas");
        int i = 0;
        for (UUID de : pend) {
            if (i >= libres.size()) break;
            inv.setItem(libres.get(i++), cabeza(de, "racha-sol", tm("item-racha-pendiente"), lorem(false,
                    tm("item-racha-pendiente-lore", "%jugador%", plugin.nombre(de)),
                    tm("item-solicitud-lore"),
                    tm("item-solicitud-lore-rechazar"))));
        }
        for (Racha r : rachas) {
            if (i >= libres.size()) break;
            UUID otro = r.otro(yo);
            List<String> loreRacha = new ArrayList<>();
            loreRacha.add(tm("item-racha-lore", "%dias%", String.valueOf(plugin.diasMostrados(r))));
            loreRacha.add(tm("item-racha-lore-miembros", "%miembros%", plugin.nombresDe(r)));
            loreRacha.addAll(loreEstado(r));
            inv.setItem(libres.get(i++), cabeza(otro, "racha",
                    tm("item-racha", "%jugador%", plugin.nombresOtros(r, yo)),
                    lorem(true, loreRacha.toArray(new String[0]))));
        }
        if (pend.isEmpty() && rachas.isEmpty()) {
            inv.setItem(libres.get(0), item(Material.BARRIER, null, null, tm("vacio"), lore()));
        }
        nav(inv, filas, "volver-principal", null);
        p.openInventory(inv);
    }

    public void abrirAmigo(Player p, UUID amigo) {
        if (!plugin.store().sonAmigos(p.getUniqueId(), amigo)) {
            abrirAmigos(p);
            return;
        }
        String titulo = plugin.textoMenu("titulo-amigo").replace("%jugador%", plugin.nombre(amigo));
        MenuHolder h = new MenuHolder(MenuHolder.Vista.AMIGO, amigo);
        Inventory inv = Bukkit.createInventory(h, plugin.tamanoMenu("amigo", 27), plugin.texto(titulo));
        h.setInventory(inv);
        decorar(inv, inv.getSize() / 9);

        Racha r = plugin.store().racha(p.getUniqueId(), amigo);
        if (r == null) {
            poner(inv, "amigo", "solicitar", 11, item(Material.BLAZE_POWDER, "solicitar", amigo, tm("item-solicitar-racha"),
                    lorem(true, tm("item-solicitar-racha-lore"))));
        } else {
            List<String> loreAmigo = new ArrayList<>();
            loreAmigo.add(tm("item-racha-lore", "%dias%", String.valueOf(plugin.diasMostrados(r))));
            loreAmigo.addAll(loreEstado(r));
            poner(inv, "amigo", "racha", 11, cabeza(amigo, "racha",
                    tm("item-racha", "%jugador%", plugin.nombre(amigo)),
                    lorem(true, loreAmigo.toArray(new String[0]))));
        }
        poner(inv, "amigo", "eliminar", 15, item(Material.BARRIER, "eliminar", amigo, tm("item-eliminar-amigo"),
                lorem(false, tm("item-eliminar-amigo-lore"))));
        nav(inv, inv.getSize() / 9, "volver-amigos", null);
        p.openInventory(inv);
    }

    private String estado(Racha r) {
        return plugin.crudo(plugin.estadoClave(r));
    }

    /** Lineas de estado reales de una racha (ultimo dia, riesgo o recuperacion). */
    private List<String> loreEstado(Racha r) {
        List<String> out = new ArrayList<>();
        out.add(tm("item-racha-lore-estado", "%estado%", estado(r)));
        out.add(tm("item-racha-lore-ultimo", "%fecha%", Tiempo.formatear(r.ultimoDia)));
        if (plugin.puedeRecuperar(r)) {
            long resta = plugin.recuperacionRestante(r);
            out.add(tm("item-racha-lore-recuperable", "%tiempo%",
                    resta < 0 ? "sin limite" : OdisseyRacha.tiempoLargo(resta)));
        } else if (r.dias > 0 && !Tiempo.hoy().equals(r.ultimoDia)) {
            out.add(tm("item-racha-lore-riesgo", "%tiempo%", Tiempo.restanteHoy()));
        }
        return out;
    }

    /** Menu de una racha: informacion, efectos, recuperar y mascota. */
    public void abrirRacha(Player p, UUID amigo) {
        UUID yo = p.getUniqueId();
        Racha r = plugin.store().racha(yo, amigo);
        if (r == null) {
            abrirRachas(p);
            return;
        }
        String titulo = plugin.textoMenu("titulo-racha").replace("%jugador%", plugin.nombresOtros(r, yo));
        MenuHolder h = new MenuHolder(MenuHolder.Vista.RACHA, amigo);
        int filasRacha = Math.max(4, plugin.tamanoMenu("racha", 54) / 9);
        Inventory inv = Bukkit.createInventory(h, filasRacha * 9, plugin.texto(titulo));
        h.setInventory(inv);
        decorar(inv, inv.getSize() / 9);

        // Info de la racha (arriba, centrado).
        List<String> info = new ArrayList<>();
        info.add(tm("item-racha-lore", "%dias%", String.valueOf(plugin.diasMostrados(r))));
        info.add(tm("item-racha-lore-miembros", "%miembros%", plugin.nombresDe(r)));
        info.addAll(loreEstado(r));
        // Las recuperaciones solo tienen sentido si la racha existe (activa o
        // pendiente de recuperar); en una racha rota o recien creada se ocultan.
        if (r.dias > 0 || r.perdida) {
            info.add(tm("item-racha-lore-recuperaciones",
                    "%restantes%", String.valueOf(plugin.recuperacionesRestantes(r)),
                    "%max%", String.valueOf(plugin.recuperacionesTotales(r))));
        }
        poner(inv, "racha", "info", 4, cabezaId(amigo, "racha-info", amigo, null,
                tm("item-racha", "%jugador%", plugin.nombresOtros(r, yo)),
                lorem(false, info.toArray(new String[0]))));

        // Efectos, centrados en la fila del medio.
        List<OdisseyRacha.Efecto> efs = new ArrayList<>(plugin.efectos().values());
        int total = efs.size();
        int centroEfectos = (filasRacha - 3) * 9 + 4;
        int inicio = centroEfectos - Math.max(0, (total - 1) / 2);
        List<Integer> slotsEfectos = plugin.slotsContenidoMenu("racha-effects");
        for (int k = 0; k < total; k++) {
            if (r.perdida || r.dias <= 0) break;
            OdisseyRacha.Efecto e = efs.get(k);
            int slot = k < slotsEfectos.size() ? slotsEfectos.get(k) : inicio + k;
            if (slot < 0 || slot >= inv.getSize()) continue;
            List<String> lore = new ArrayList<>(e.lore);
            long queda = plugin.cooldownRestante(yo, e.id);
            if (r.perdida || r.dias <= 0) {
                // Racha perdida o rota: no hay efectos disponibles.
                lore.add(tm("item-efecto-lore-bloqueado"));
            } else if (e.diasMinimos > 0 && r.dias < e.diasMinimos) {
                lore.add(tm("item-efecto-lore-dias", "%dias%", String.valueOf(e.diasMinimos)));
            } else if (queda > 0) {
                lore.add(tm("item-efecto-lore-cooldown", "%tiempo%", OdisseyRacha.tiempoLegible(queda)));
            } else {
                lore.add("");
                lore.add(tm("item-efecto-lore-usar"));
            }
            inv.setItem(slot, itemId(material(e.material), "efecto", amigo, e.id, e.nombre, lore));
        }

        // Boton de recuperar (si la racha esta perdida pero se puede recuperar).
        if (plugin.puedeRecuperar(r)) {
            poner(inv, "racha", "recuperar", filasRacha == 6 ? 22 : (filasRacha == 5 ? 13 : 18), itemId(Material.NETHER_STAR, "recuperar", amigo, null,
                    tm("item-recuperar"), lorem(true,
                            tm("item-recuperar-lore",
                                    "%restantes%", String.valueOf(plugin.recuperacionesRestantes(r)),
                                    "%max%", String.valueOf(plugin.recuperacionesTotales(r))),
                            tm("item-recuperar-lore2"))));
        }

        // Mascota.
        boolean activa = !r.perdida && r.dias > 0;
        if (activa && plugin.mascotaActivada()) {
            boolean mascotaOn = amigo.equals(plugin.store().mascotaObjetivo(yo));
            poner(inv, "racha", "mascota", (filasRacha - 2) * 9 + 1, itemId(Material.ALLAY_SPAWN_EGG, "mascota", amigo, null,
                    tm(mascotaOn ? "item-mascota-quitar" : "item-mascota-poner"),
                    lorem(true, tm("item-mascota-lore"))));
        }

        // Mejora del Allay (ataque): activar/desactivar una vez desbloqueada.
        if (activa && plugin.mejoraDesbloqueada(yo)) {
            boolean mOn = plugin.mejoraActiva(yo);
            poner(inv, "racha", "mejora-allay", (filasRacha - 2) * 9 + 3, itemId(Material.NETHERITE_SWORD, "mejora-allay", amigo, null,
                    tm(mOn ? "item-mejora-quitar" : "item-mejora-poner"),
                    lorem(false, tm("item-mejora-lore"))));
        }

        // Rachas de grupo: invitar a un amigo y salir de la racha.
        if (activa && r.tamano() < plugin.maxMiembrosRacha()) {
            poner(inv, "racha", "invitar", (filasRacha - 2) * 9 + 5, cabeza(amigo, "invitar", tm("item-invitar"),
                    lorem(true, tm("item-invitar-lore", "%max%", String.valueOf(plugin.maxMiembrosRacha())))));
        }
        if (activa) {
            poner(inv, "racha", "salir-grupo", (filasRacha - 2) * 9 + 7, item(Material.BARRIER, "salir-grupo", amigo, tm("item-salir-grupo"),
                    lorem(false, tm("item-salir-grupo-lore"))));
        }

        nav(inv, inv.getSize() / 9, "volver-rachas", null);
        p.openInventory(inv);
    }

    /** Submenu para invitar a un amigo a la racha (grupo). */
    public void abrirInvitar(Player p, UUID amigo) {
        UUID yo = p.getUniqueId();
        Racha r = plugin.store().racha(yo, amigo);
        if (r == null || r.perdida || r.dias <= 0) {
            abrirRacha(p, amigo);
            return;
        }
        List<UUID> candidatos = new ArrayList<>();
        for (UUID a : plugin.store().amigos(yo)) {
            if (r.incluye(a)) continue;
            if (yaComparteRacha(r, a)) continue;
            candidatos.add(a);
        }
        candidatos.sort(Comparator.comparing(plugin::nombre, String.CASE_INSENSITIVE_ORDER));

        MenuHolder h = new MenuHolder(MenuHolder.Vista.INVITAR, amigo);
        int filas = plugin.filasMenu("invitar", candidatos.size(), 7);
        Inventory inv = Bukkit.createInventory(h, filas * 9,
                plugin.texto(plugin.textoMenu("titulo-invitar")));
        h.setInventory(inv);
        decorar(inv, filas);

        List<Integer> libres = contenido(filas, "invitar");
        int i = 0;
        for (UUID a : candidatos) {
            if (i >= libres.size()) break;
            inv.setItem(libres.get(i++), cabezaId(a, "invitar-amigo", amigo, a.toString(), plugin.nombre(a),
                    lorem(true, tm("item-invitar-amigo-lore"))));
        }
        if (candidatos.isEmpty()) {
            inv.setItem(libres.get(0), item(Material.BARRIER, null, null, tm("vacio"), lore()));
        }
        nav(inv, filas, "volver-racha", amigo);
        p.openInventory(inv);
    }

    private boolean yaComparteRacha(Racha r, UUID jugador) {
        for (UUID m : r.miembros) {
            if (plugin.store().existeRacha(m, jugador)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  DECORACION
    // ------------------------------------------------------------------

    /** Rellena el marco (borde + fondo) del menu. */
    private void decorar(Inventory inv, int filas) {
        ItemStack borde = relleno("borde-material");
        ItemStack fondo = relleno("relleno-material");
        int size = filas * 9;
        for (int i = 0; i < size; i++) {
            int fila = i / 9;
            int col = i % 9;
            boolean esBorde = fila == 0 || fila == filas - 1 || col == 0 || col == 8;
            inv.setItem(i, esBorde ? borde : fondo);
        }
    }

    /** Slots interiores (sin marco) de un menu de "filas" filas. */
    private List<Integer> contenido(int filas, String gui) {
        List<Integer> out = new ArrayList<>();
        List<Integer> personalizados = plugin.slotsContenidoMenu(gui);
        if (!personalizados.isEmpty()) {
            for (int slot : personalizados) {
                if (slot < 0 || slot >= filas * 9) continue;
                int fila = slot / 9;
                int col = slot % 9;
                if (fila == 0 || fila == filas - 1 || col == 0 || col == 8) continue;
                if (!out.contains(slot)) out.add(slot);
            }
            if (!out.isEmpty()) return out;
        }
        for (int i = 0; i < filas * 9; i++) {
            int fila = i / 9;
            int col = i % 9;
            if (fila == 0 || fila == filas - 1 || col == 0 || col == 8) continue;
            out.add(i);
        }
        return out;
    }

    /** Fila de navegacion abajo (Volver + Cerrar). */
    private void nav(Inventory inv, int filas, String volverTipo, UUID volverUuid) {
        int base = filas * 9 - 9;
        String gui = switch (((MenuHolder) inv.getHolder()).vista) {
            case PRINCIPAL -> "principal";
            case AMIGOS -> "amigos";
            case SOLICITUDES -> "solicitudes";
            case ANADIR -> "anadir";
            case RACHAS -> "rachas";
            case AMIGO -> "amigo";
            case RACHA -> "racha";
            case INVITAR -> "invitar";
        };
        poner(inv, gui, "volver", base + 3,
                item(Material.ARROW, volverTipo, volverUuid, tm("item-volver"), lore()));
        poner(inv, gui, "cerrar", base + 5,
                item(Material.BARRIER, "cerrar", null, tm("item-cerrar"), lore()));
    }

    /** Filas necesarias (3 a 6) para "n" items en slots interiores (7 por fila). */
    private int filasPara(int n) {
        int filasContenido = Math.max(1, (n + 6) / 7);
        // Devuelve FILAS, no slots: los menus se crean con "filas * 9" y
        // decorar/contenido/nav tambien cuentan filas. Antes devolvia slots
        // (27..54) y al multiplicar por 9 en el llamador salia 243, que Bukkit
        // rechaza ("size must be a multiple of 9 between 9 and 54").
        return Math.max(3, Math.min(6, filasContenido + 2));
    }

    private ItemStack relleno(String materialClave) {
        ItemStack it = new ItemStack(material(plugin.textoMenu(materialClave)));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.texto(plugin.textoMenu("relleno-nombre")));
            flags(meta);
            it.setItemMeta(meta);
        }
        return it;
    }

    // ------------------------------------------------------------------
    //  CLICS
    // ------------------------------------------------------------------

    public void manejar(Player p, ItemStack it, boolean derecha) {
        String tipo = tipoDe(it);
        if (tipo == null) return;
        UUID u = uuidDe(it);
        // Sonido de navegacion. Las acciones (solicitar, aceptar, recuperar...)
        // ponen su propio sonido en el plugin para no repetirlo.
        switch (tipo) {
            case "abrir-amigos", "abrir-solicitudes", "abrir-anadir", "abrir-rachas",
                 "amigo", "racha", "invitar" -> plugin.sonido(p, "seleccionar");
            case "volver-principal", "volver-amigos", "volver-rachas", "volver-racha" ->
                    plugin.sonido(p, "volver");
            case "cerrar" -> plugin.sonido(p, "cerrar");
            default -> {
            }
        }
        switch (tipo) {
            case "abrir-amigos" -> abrirAmigos(p);
            case "abrir-solicitudes" -> abrirSolicitudes(p);
            case "abrir-anadir" -> abrirAnadir(p);
            case "abrir-rachas" -> abrirRachas(p);
            case "volver-principal" -> abrirPrincipal(p);
            case "volver-amigos" -> abrirAmigos(p);
            case "cerrar" -> p.closeInventory();
            case "amigo" -> {
                if (u != null) abrirAmigo(p, u);
            }
            case "racha" -> {
                if (u != null) abrirRacha(p, u);
            }
            case "racha-info" -> {
            }
            case "volver-rachas" -> abrirRachas(p);
            case "efecto" -> {
                if (u != null) {
                    plugin.aplicarEfecto(p, idDe(it), plugin.store().racha(p.getUniqueId(), u));
                    abrirRacha(p, u);
                }
            }
            case "recuperar" -> {
                if (u != null) {
                    plugin.recuperar(p, plugin.store().racha(p.getUniqueId(), u));
                    abrirRacha(p, u);
                }
            }
            case "mascota" -> {
                if (u != null) {
                    plugin.toggleMascota(p, u);
                    abrirRacha(p, u);
                }
            }
            case "mejora-allay" -> {
                Racha actual = u == null ? null : plugin.store().racha(p.getUniqueId(), u);
                if (actual == null || actual.perdida || actual.dias <= 0) {
                    abrirRacha(p, u);
                    return;
                }
                boolean on = !plugin.mejoraActiva(p.getUniqueId());
                plugin.setMejoraActiva(p.getUniqueId(), on);
                p.sendMessage(plugin.msg(on ? "mejora-activada" : "mejora-desactivada"));
                plugin.sonido(p, "seleccionar");
                if (u != null) abrirRacha(p, u);
            }
            case "invitar" -> {
                if (u != null) abrirInvitar(p, u);
            }
            case "invitar-amigo" -> {
                if (u != null) {
                    UUID nuevo = null;
                    String id = idDe(it);
                    if (id != null) {
                        try {
                            nuevo = UUID.fromString(id);
                        } catch (Exception ex) {
                            nuevo = null;
                        }
                    }
                    if (nuevo != null) plugin.invitarAGrupo(p, u, nuevo);
                    abrirRacha(p, u);
                }
            }
            case "salir-grupo" -> {
                if (u != null) plugin.salirRacha(p, u);
                abrirRachas(p);
            }
            case "volver-racha" -> {
                if (u != null) abrirRacha(p, u);
                else abrirRachas(p);
            }
            case "anadir" -> {
                if (u != null) {
                    plugin.enviarSolicitud(p, u);
                    abrirAnadir(p);
                }
            }
            case "sol" -> {
                if (u != null) {
                    if (derecha) plugin.rechazarSolicitud(p, u);
                    else plugin.aceptarSolicitud(p, u);
                    abrirSolicitudes(p);
                }
            }
            case "racha-sol" -> {
                if (u != null) {
                    if (derecha) plugin.rechazarRacha(p, u);
                    else plugin.aceptarRacha(p, u);
                    abrirRachas(p);
                }
            }
            case "solicitar" -> {
                if (u != null) {
                    plugin.solicitarRacha(p, u);
                    abrirAmigo(p, u);
                }
            }
            case "eliminar" -> {
                if (u != null) {
                    plugin.eliminarAmigo(p, u);
                    abrirAmigos(p);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    //  UTILIDADES
    // ------------------------------------------------------------------

    private String tm(String clave, Object... pares) {
        String s = plugin.textoMenu(clave);
        for (int i = 0; i + 1 < pares.length; i += 2) {
            s = s.replace(String.valueOf(pares[i]), String.valueOf(pares[i + 1]));
        }
        return s;
    }

    private List<String> lore(String... lineas) {
        return new ArrayList<>(List.of(lineas));
    }

    /** Lore estandar: marca, descripcion(es) y, si "click", la pista de click. */
    private List<String> lorem(boolean click, String... lineas) {
        List<String> l = new ArrayList<>();
        l.add(plugin.textoMenu("marca"));
        l.add("");
        for (String s : lineas) l.add(s);
        if (click) {
            l.add("");
            l.add(plugin.textoMenu("item-click"));
        }
        return l;
    }

    /** Aplica flags para ocultar el "ruido" (atributos, encantamientos, etc.). */
    private void flags(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    private ItemStack item(Material mat, String tipo, UUID u, String nombre, List<String> lore) {
        ItemStack it = new ItemStack(plugin.materialMenu(tipo == null ? "vacio" : tipo, mat));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(plugin.texto(nombre == null ? "" : nombre));
        if (lore != null && !lore.isEmpty()) meta.lore(loreDe(lore));
        flags(meta);
        if (tipo != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyTipo, PersistentDataType.STRING, tipo);
            if (u != null) pdc.set(keyUuid, PersistentDataType.STRING, u.toString());
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack itemId(Material mat, String tipo, UUID u, String id, String nombre, List<String> lore) {
        ItemStack it = new ItemStack(plugin.materialMenu(tipo, mat));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(plugin.texto(nombre == null ? "" : nombre));
        if (lore != null && !lore.isEmpty()) meta.lore(loreDe(lore));
        flags(meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTipo, PersistentDataType.STRING, tipo);
        if (u != null) pdc.set(keyUuid, PersistentDataType.STRING, u.toString());
        if (id != null) pdc.set(keyId, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private Material material(String nombre) {
        Material m = nombre == null ? null : Material.matchMaterial(nombre.trim());
        return m == null ? Material.STONE : m;
    }

    private String idDe(ItemStack it) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
    }

    private ItemStack cabeza(UUID u, String tipo, String nombre, List<String> lore) {
        Material mat = plugin.materialMenu(tipo, Material.PLAYER_HEAD);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(Bukkit.getOfflinePlayer(u));
        if (meta == null) return it;
        meta.displayName(plugin.texto(nombre == null ? "" : nombre));
        if (lore != null && !lore.isEmpty()) meta.lore(loreDe(lore));
        flags(meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTipo, PersistentDataType.STRING, tipo);
        pdc.set(keyUuid, PersistentDataType.STRING, u.toString());
        it.setItemMeta(meta);
        return it;
    }

    /** Cabeza con uuid (dato "u") y un id extra (dato "id"). */
    private ItemStack cabezaId(UUID skin, String tipo, UUID u, String id, String nombre, List<String> lore) {
        Material mat = plugin.materialMenu(tipo, Material.PLAYER_HEAD);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(Bukkit.getOfflinePlayer(skin));
        if (meta == null) return it;
        meta.displayName(plugin.texto(nombre == null ? "" : nombre));
        if (lore != null && !lore.isEmpty()) meta.lore(loreDe(lore));
        flags(meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTipo, PersistentDataType.STRING, tipo);
        if (u != null) pdc.set(keyUuid, PersistentDataType.STRING, u.toString());
        if (id != null) pdc.set(keyId, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** Convierte lineas de lore en Componentes (respeta las lineas vacias). */
    private List<Component> loreDe(List<String> lore) {
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(plugin.texto(s == null ? "" : s));
        }
        return l;
    }

    private String tipoDe(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(keyTipo, PersistentDataType.STRING);
    }

    private UUID uuidDe(ItemStack it) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        String s = meta.getPersistentDataContainer().get(keyUuid, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private void poner(Inventory inv, String gui, String elemento, int defecto, ItemStack item) {
        int slot = plugin.slotMenu(gui, elemento, defecto);
        if (slot < 0 || slot >= inv.getSize()) slot = defecto;
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }
}
