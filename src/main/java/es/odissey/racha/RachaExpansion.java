package es.odissey.racha;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Expansion de PlaceholderAPI. Se registra solo si PlaceholderAPI esta presente.
 *
 * Placeholders (identificador "odisseyracha"):
 *   %odisseyracha_dias%             dias de su mejor racha
 *   %odisseyracha_amigo%            el amigo de su mejor racha
 *   %odisseyracha_rachas%           cuantas rachas tiene
 *   %odisseyracha_multiplicador%    multiplicador de esa racha (ej: 1.5)
 *   %odisseyracha_pos%              posicion en el top del servidor
 *   %odisseyracha_recuperaciones%   recuperaciones que le quedan
 *   %odisseyracha_top%              TOP completo (varias lineas, para holograma)
 *   %odisseyracha_<n>%              linea formateada de la racha n (1 = mejor)
 *   %odisseyracha_<n>_nombres%      nombres de la racha n
 *   %odisseyracha_<n>_dias%         dias de la racha n
 *   %odisseyracha_<n>_pos%          posicion de la racha n
 *   %odisseyracha_<n>_miembros%     cuantos miembros tiene la racha n
 *   %odisseyracha_top_<n>%          alias de %odisseyracha_<n>%
 *   %odisseyracha_dias_<jugador>%   dias de racha con ese jugador
 */
public final class RachaExpansion extends PlaceholderExpansion {

    private final OdisseyRacha plugin;

    public RachaExpansion(OdisseyRacha plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "odisseyracha";
    }

    @Override
    public String getAuthor() {
        return "ocampo05";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params == null ? "" : params.toLowerCase();

        if (p.equals("top") || p.startsWith("top_")) {
            return top(p.equals("top") ? "" : p.substring(4));
        }

        // %odisseyracha_1% .. %odisseyracha_<n>% (y _nombres/_dias/_pos/_miembros):
        // linea n del top de rachas. Vacio si no existe esa posicion.
        if (!p.isEmpty() && Character.isDigit(p.charAt(0))) {
            return top(p);
        }

        if (player == null) return "";

        UUID yo = player.getUniqueId();
        Racha mejor = mejor(yo);

        switch (p) {
            case "dias":
                return mejor == null ? "0" : String.valueOf(mejor.dias);
            case "amigo":
                return mejor == null ? "" : plugin.nombre(mejor.otro(yo));
            case "rachas":
                return String.valueOf(plugin.store().rachas(yo).size());
            case "multiplicador":
                return mejor == null ? "1" : numero(plugin.multiplicador(mejor.dias));
            case "pos": {
                if (mejor == null) return "0";
                int pos = posicion(mejor);
                return String.valueOf(Math.max(0, pos));
            }
            case "recuperaciones":
                return mejor == null ? "0" : String.valueOf(plugin.recuperacionesRestantes(mejor));
            default:
                break;
        }

        if (p.startsWith("dias_")) {
            UUID amigo = porNombre(p.substring(5));
            if (amigo == null) return "0";
            Racha r = plugin.store().racha(yo, amigo);
            return String.valueOf(r == null ? 0 : r.dias);
        }
        return null;
    }

    private Racha mejor(UUID yo) {
        Racha mejor = null;
        for (Racha r : plugin.store().rachas(yo)) {
            if (mejor == null || r.dias > mejor.dias) mejor = r;
        }
        return mejor;
    }

    private int posicion(Racha racha) {
        List<Racha> todas = ordenadas();
        for (int i = 0; i < todas.size(); i++) {
            if (todas.get(i).clave().equals(racha.clave())) return i + 1;
        }
        return -1;
    }

    private String top(String resto) {
        List<Racha> todas = ordenadas();
        String fmt = plugin.getConfig().getString("holograma.formato",
                "&e%pos%. &f%nombres% &7- &e%dias% &7dias");

        // %odisseyracha_top% -> top completo en varias lineas.
        if (resto.isEmpty()) {
            if (todas.isEmpty()) {
                return plugin.getConfig().getString("holograma.vacio", "&7Todavia no hay rachas.");
            }
            int cant = plugin.getConfig().getInt("holograma.cantidad", 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(cant, todas.size()); i++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(linea(fmt, i + 1, todas.get(i)));
            }
            return sb.toString();
        }

        String[] parts = resto.split("_");
        int n;
        try {
            n = Integer.parseInt(parts[0].trim());
        } catch (Exception e) {
            return "";
        }
        if (n < 1 || n > todas.size()) return "";
        Racha r = todas.get(n - 1);
        String campo = parts.length > 1 ? parts[1] : "";
        switch (campo) {
            case "nombres":
            case "nombre":
            case "jugador":
                return plugin.nombresDe(r);
            case "dias":
                return String.valueOf(r.dias);
            case "pos":
            case "posicion":
                return String.valueOf(n);
            case "miembros":
                return String.valueOf(r.tamano());
            default:
                return linea(fmt, n, r);
        }
    }

    private String linea(String fmt, int pos, Racha r) {
        return fmt.replace("%pos%", String.valueOf(pos))
                .replace("%nombres%", plugin.nombresDe(r))
                .replace("%dias%", String.valueOf(r.dias))
                .replace("%miembros%", String.valueOf(r.tamano()));
    }

    private List<Racha> ordenadas() {
        List<Racha> todas = new ArrayList<>();
        for (Racha r : plugin.store().todasRachas()) {
            if (r.dias > 0) todas.add(r);
        }
        todas.sort((x, y) -> Integer.compare(y.dias, x.dias));
        return todas;
    }

    private UUID porNombre(String nombre) {
        org.bukkit.entity.Player on = org.bukkit.Bukkit.getPlayerExact(nombre);
        if (on != null) return on.getUniqueId();
        OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayerIfCached(nombre);
        return off != null ? off.getUniqueId() : null;
    }

    private static String numero(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
