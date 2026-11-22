package es.odissey.racha;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Envio de avisos a un webhook de Discord, en segundo plano (no bloquea el
 * servidor). Si la URL esta vacia, no hace nada.
 */
public final class Webhook {

    private Webhook() {
    }

    /** Envia un texto simple al webhook. Se ejecuta en un hilo aparte. */
    public static void enviar(OdisseyRacha plugin, String url, String contenido) {
        if (url == null || url.trim().isEmpty() || contenido == null) return;
        final String destino = url.trim();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(destino);
                c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                c.setRequestProperty("User-Agent", "OdisseyRacha");
                String json = "{\"content\":" + json(contenido) + "}";
                try (OutputStream os = c.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                c.getResponseCode();
            } catch (Throwable t) {
                plugin.getLogger().warning("No se pudo enviar el webhook de Discord: " + t.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    /** Escapa un texto como cadena JSON. */
    private static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }
}
