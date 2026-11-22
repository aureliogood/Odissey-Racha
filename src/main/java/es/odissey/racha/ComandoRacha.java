package es.odissey.racha;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Comando /racha (alias /amigos). Sin argumentos abre el menu.
 */
public final class ComandoRacha implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "add", "aceptar", "rechazar", "eliminar", "racha", "top", "reload", "help", "admin");

    private final OdisseyRacha plugin;

    public ComandoRacha(OdisseyRacha plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            Player p = jugador(sender);
            if (p == null) return true;
            plugin.sonido(p, "abrir");
            plugin.menu().abrirPrincipal(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top" -> top(sender);
            case "help", "ayuda" -> help(sender);
            case "admin" -> admin(sender, args);
            case "reload", "recargar" -> {
                if (!sender.hasPermission("odisseyracha.admin")) {
                    sender.sendMessage(plugin.msg("sin-permiso"));
                    return true;
                }
                plugin.recargar();
                sender.sendMessage(plugin.msg("reload"));
            }
            case "add", "agregar", "aceptar", "rechazar", "eliminar", "racha" -> {
                Player p = jugador(sender);
                if (p == null) return true;
                if (args.length < 2) {
                    sender.sendMessage(plugin.msg("usage"));
                    return true;
                }
                UUID u = buscar(args[1]);
                if (u == null) {
                    sender.sendMessage(plugin.msg("jugador-no-encontrado"));
                    return true;
                }
                switch (sub) {
                    case "add", "agregar" -> plugin.enviarSolicitud(p, u);
                    case "aceptar" -> plugin.aceptarSolicitud(p, u);
                    case "rechazar" -> plugin.rechazarSolicitud(p, u);
                    case "eliminar" -> plugin.eliminarAmigo(p, u);
                    case "racha" -> plugin.solicitarRacha(p, u);
                    default -> {
                    }
                }
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    private Player jugador(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(plugin.msg("solo-jugadores"));
        return null;
    }

    private UUID buscar(String nombre) {
        Player on = Bukkit.getPlayerExact(nombre);
        if (on != null) return on.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(nombre);
        if (off != null && (off.hasPlayedBefore() || off.isOnline())) return off.getUniqueId();
        @SuppressWarnings("deprecation")
        OfflinePlayer cualquierOff = Bukkit.getOfflinePlayer(nombre);
        return (cualquierOff != null && cualquierOff.hasPlayedBefore()) ? cualquierOff.getUniqueId() : null;
    }

    private void top(CommandSender sender) {
        List<Racha> todas = new ArrayList<>();
        for (Racha r : plugin.store().todasRachas()) {
            if (r.dias > 0) todas.add(r);
        }
        if (todas.isEmpty()) {
            sender.sendMessage(plugin.msg("top-vacio"));
            return;
        }
        todas.sort((x, y) -> Integer.compare(y.dias, x.dias));
        sender.sendMessage(plugin.texto("&6&lTop de rachas:"));
        int n = 0;
        for (Racha r : todas) {
            if (n >= 10) break;
            n++;
            sender.sendMessage(plugin.texto("&e#" + n + " &f" + plugin.nombresDe(r)
                    + " &7- &e" + r.dias + " &7dias"));
        }
    }

    private void help(CommandSender s) {
        for (String l : List.of(
                "&6&lOdisseyRacha &7- comandos",
                "&e/racha &7- abre el menu",
                "&e/racha add <jugador> &7- envia solicitud de amistad",
                "&e/racha aceptar|rechazar <jugador> &7- responde a una solicitud de amistad",
                "&e/racha racha <jugador> &7- solicita una racha",
                "&e/racha eliminar <jugador> &7- elimina amigo y racha",
                "&e/racha top &7- ranking de rachas",
                "&e/racha reload &7- recarga la config")) {
            s.sendMessage(plugin.texto(l));
        }
        if (s.hasPermission("odisseyracha.admin")) {
            s.sendMessage(plugin.msg("admin-usage"));
        }
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("odisseyracha.admin")) {
            s.sendMessage(plugin.msg("sin-permiso"));
            return;
        }
        if (a.length < 2) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "dias" -> adminDias(s, a);
            case "mejora" -> adminMejora(s, a);
            case "reset" -> adminReset(s, a);
            case "rachas" -> adminRachas(s, a);
            default -> s.sendMessage(plugin.msg("admin-usage"));
        }
    }

    private void adminDias(CommandSender s, String[] a) {
        if (a.length < 5) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        String modo = a[2].toLowerCase(Locale.ROOT);
        if (!modo.equals("add") && !modo.equals("remove") && !modo.equals("set")
                && !modo.equals("agregar") && !modo.equals("quitar") && !modo.equals("poner")) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        UUID ua = buscar(a[3]);
        if (ua == null) {
            s.sendMessage(plugin.msg("jugador-no-encontrado"));
            return;
        }
        // Forma con dos jugadores: dias <modo> <jugadorA> <jugadorB> <n>
        if (a.length >= 6) {
            UUID ub = buscar(a[4]);
            if (ub == null) {
                s.sendMessage(plugin.msg("jugador-no-encontrado"));
                return;
            }
            int n = numero(a[5]);
            if (n == Integer.MIN_VALUE) {
                s.sendMessage(plugin.msg("admin-usage"));
                return;
            }
            Racha r = plugin.adminDias(ua, ub, modo, n);
            if (r == null) {
                s.sendMessage(plugin.msg("admin-racha-no",
                        "%jugador%", plugin.nombre(ua), "%amigo%", plugin.nombre(ub)));
                return;
            }
            s.sendMessage(plugin.msg("admin-dias", "%jugador%", plugin.nombre(ua),
                    "%amigo%", plugin.nombre(ub), "%dias%", String.valueOf(r.dias)));
            return;
        }
        // Forma con un jugador: dias <modo> <jugador> <n> (todas sus rachas).
        int n = numero(a[4]);
        if (n == Integer.MIN_VALUE) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        int ok = plugin.adminDiasJugador(ua, modo, n);
        if (ok == 0) {
            s.sendMessage(plugin.msg("admin-sin-rachas", "%jugador%", plugin.nombre(ua)));
            return;
        }
        s.sendMessage(plugin.msg("admin-dias-jugador", "%jugador%", plugin.nombre(ua),
                "%n%", String.valueOf(ok), "%dias%", String.valueOf(n)));
    }

    /** Parsea un entero; Integer.MIN_VALUE si no es valido. */
    private int numero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private void adminMejora(CommandSender s, String[] a) {
        if (a.length < 4) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        UUID u = buscar(a[2]);
        if (u == null) {
            s.sendMessage(plugin.msg("jugador-no-encontrado"));
            return;
        }
        boolean on = a[3].equalsIgnoreCase("on") || a[3].equalsIgnoreCase("true")
                || a[3].equalsIgnoreCase("si");
        plugin.setMejora(u, on);
        s.sendMessage(plugin.msg(on ? "admin-mejora-on" : "admin-mejora-off",
                "%jugador%", plugin.nombre(u)));
    }

    private void adminReset(CommandSender s, String[] a) {
        if (a.length < 4) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        UUID ua = buscar(a[2]);
        UUID ub = buscar(a[3]);
        if (ua == null || ub == null) {
            s.sendMessage(plugin.msg("jugador-no-encontrado"));
            return;
        }
        if (!plugin.adminReset(ua, ub)) {
            s.sendMessage(plugin.msg("admin-racha-no",
                    "%jugador%", plugin.nombre(ua), "%amigo%", plugin.nombre(ub)));
            return;
        }
        s.sendMessage(plugin.msg("admin-reset", "%jugador%", plugin.nombre(ua),
                "%amigo%", plugin.nombre(ub)));
    }

    private void adminRachas(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(plugin.msg("admin-usage"));
            return;
        }
        UUID u = buscar(a[2]);
        if (u == null) {
            s.sendMessage(plugin.msg("jugador-no-encontrado"));
            return;
        }
        List<Racha> rs = plugin.store().rachas(u);
        s.sendMessage(plugin.msg("admin-rachas", "%jugador%", plugin.nombre(u),
                "%n%", String.valueOf(rs.size())));
        for (Racha r : rs) {
            s.sendMessage(plugin.texto("&7- &f" + plugin.nombresDe(r) + " &7- &e" + r.dias + " &7dias"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && List.of(
                "add", "agregar", "aceptar", "rechazar", "eliminar", "racha")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            jugadores(args[1], out);
        } else if (args[0].equalsIgnoreCase("admin")) {
            String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            if (args.length == 2) {
                for (String s : List.of("dias", "mejora", "reset", "rachas")) {
                    if (s.startsWith(sub)) out.add(s);
                }
            } else if (args.length == 3) {
                if (sub.equals("dias")) {
                    opciones(List.of("add", "remove", "set"), args[2], out);
                } else {
                    jugadores(args[2], out);
                }
            } else if (args.length == 4) {
                if (sub.equals("dias") || sub.equals("reset")) jugadores(args[3], out);
                else if (sub.equals("mejora")) opciones(List.of("on", "off"), args[3], out);
            } else if (args.length == 5 && sub.equals("dias")) {
                // Puede ser <jugadorB> o el <n> de la forma de un solo jugador.
                jugadores(args[4], out);
                opciones(List.of("1", "7", "30", "0"), args[4], out);
            } else if (args.length == 6 && sub.equals("dias")) {
                opciones(List.of("30", "0"), args[5], out);
            }
        }
        return out;
    }

    private void jugadores(String prefijo, List<String> out) {
        String p = prefijo.toLowerCase(Locale.ROOT);
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(on.getName());
        }
    }

    private void opciones(List<String> valores, String prefijo, List<String> out) {
        String p = prefijo.toLowerCase(Locale.ROOT);
        for (String v : valores) {
            if (v.startsWith(p)) out.add(v);
        }
    }
}
