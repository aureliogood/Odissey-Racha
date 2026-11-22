package es.odissey.racha;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Conexion a la base de datos (MySQL/MariaDB o SQLite) por JDBC y creacion de
 * las tablas si no existen. Los drivers se declaran en el plugin.yml.
 */
public final class Db {

    private final OdisseyRacha plugin;
    private final String tipo;    // "mysql" | "sqlite"
    private final String prefijo;
    private String url;
    private String user = "";
    private String pass = "";

    public Db(OdisseyRacha plugin, String tipo) {
        this.plugin = plugin;
        this.tipo = tipo == null ? "mysql" : tipo.toLowerCase();
        String p = plugin.getConfig().getString("storage.prefijo", "odisseyracha_");
        this.prefijo = (p == null || p.isEmpty()) ? "odisseyracha_" : p;
    }

    public boolean esMysql() {
        return tipo.equals("mysql");
    }

    public String pares() {
        return prefijo + "pares";
    }

    public String logins() {
        return prefijo + "logins";
    }

    public String rachas() {
        return prefijo + "rachas";
    }

    public String mascotas() {
        return prefijo + "mascotas";
    }

    public String mejoras() {
        return prefijo + "mejoras";
    }

    public String cooldowns() {
        return prefijo + "cooldowns";
    }

    public String ips() {
        return prefijo + "ips";
    }

    /** Abre la conexion y crea las tablas. Devuelve false si fallo. */
    public boolean abrir() {
        try {
            if (tipo.equals("sqlite")) {
                Class.forName("org.sqlite.JDBC");
                File f = new File(plugin.getDataFolder(), "racha.db");
                url = "jdbc:sqlite:" + f.getAbsolutePath();
            } else {
                Class.forName("com.mysql.cj.jdbc.Driver");
                String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
                int puerto = plugin.getConfig().getInt("storage.mysql.port", 3306);
                String base = plugin.getConfig().getString("storage.mysql.database", "odisseyracha");
                user = plugin.getConfig().getString("storage.mysql.user", "root");
                pass = plugin.getConfig().getString("storage.mysql.password", "");
                url = "jdbc:mysql://" + host + ":" + puerto + "/" + base
                        + "?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&characterEncoding=utf8&serverTimezone=UTC"
                        + "&connectTimeout=3000&socketTimeout=3000";
            }
            try (Connection c = con()) {
                crearTablas(c);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("No se pudo conectar a la base de datos (" + tipo
                    + "): " + e.getMessage());
            return false;
        }
    }

    public Connection con() throws SQLException {
        if (tipo.equals("sqlite")) {
            Connection c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA busy_timeout = 3000");
            }
            return c;
        }
        return DriverManager.getConnection(url, user, pass);
    }

    private void crearTablas(Connection c) throws SQLException {
        boolean my = esMysql();
        String txt = my ? "VARCHAR(64)" : "TEXT";
        String uuid = my ? "VARCHAR(36)" : "TEXT";
        String big = my ? "BIGINT" : "INTEGER";
        String integer = my ? "INT" : "INTEGER";
        String lista = my ? "VARCHAR(255)" : "TEXT";
        String engine = my ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + pares() + " ("
                    + "tipo " + txt + " NOT NULL,"
                    + "a " + uuid + " NOT NULL,"
                    + "b " + uuid + " NOT NULL,"
                    + "PRIMARY KEY (tipo, a, b))" + engine);

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + logins() + " ("
                    + "uuid " + uuid + " NOT NULL,"
                    + "fecha " + txt + " NULL,"
                    + "PRIMARY KEY (uuid))" + engine);

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + rachas() + " ("
                    + "clave " + (my ? "VARCHAR(255)" : "TEXT") + " NOT NULL,"
                    + "a " + uuid + " NOT NULL,"
                    + "b " + uuid + " NOT NULL,"
                    + "miembros " + lista + " NULL,"
                    + "dias " + integer + " NOT NULL DEFAULT 0,"
                    + "ultimo " + txt + " NULL,"
                    + "creada " + big + " NOT NULL DEFAULT 0,"
                    + "usadas " + integer + " NOT NULL DEFAULT 0,"
                    + "perdida " + (my ? "TINYINT(1)" : "INTEGER") + " NOT NULL DEFAULT 0,"
                    + "dias_perdidos " + integer + " NOT NULL DEFAULT 0,"
                    + "perdida_el " + big + " NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (clave))" + engine);

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + mascotas() + " ("
                    + "uuid " + uuid + " NOT NULL,"
                    + "objetivo " + uuid + " NOT NULL,"
                    + "PRIMARY KEY (uuid))" + engine);

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + mejoras() + " ("
                    + "uuid " + uuid + " NOT NULL,"
                    + "activa " + (my ? "TINYINT(1)" : "INTEGER") + " NOT NULL DEFAULT 1,"
                    + "PRIMARY KEY (uuid))" + engine);

            // Migracion: si la tabla rachas ya existia sin la columna miembros, se anade.
            try {
                st.executeUpdate("ALTER TABLE " + rachas() + " ADD COLUMN miembros " + lista + " NULL");
            } catch (SQLException ignored) {
                // ya existe
            }

            // Migracion: tablas antiguas sin las columnas de recuperacion.
            try {
                st.executeUpdate("ALTER TABLE " + rachas()
                        + " ADD COLUMN usadas " + integer + " NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // ya existe
            }
            try {
                st.executeUpdate("ALTER TABLE " + rachas()
                        + " ADD COLUMN perdida " + (my ? "TINYINT(1)" : "INTEGER")
                        + " NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // ya existe
            }
            try {
                st.executeUpdate("ALTER TABLE " + rachas()
                        + " ADD COLUMN dias_perdidos " + integer + " NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // ya existe
            }
            try {
                st.executeUpdate("ALTER TABLE " + rachas()
                        + " ADD COLUMN perdida_el " + big + " NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // ya existe
            }

            // Migracion MySQL: las claves de grupo pueden superar los 64 caracteres.
            if (my) {
                try {
                    st.executeUpdate("ALTER TABLE " + rachas()
                            + " MODIFY COLUMN clave VARCHAR(255) NOT NULL");
                } catch (SQLException ignored) {
                    // nada que hacer
                }
            }

            // Migracion: la tabla mejoras pudo crearse sin la columna "activa".
            try {
                st.executeUpdate("ALTER TABLE " + mejoras() + " ADD COLUMN activa "
                        + (my ? "TINYINT(1)" : "INTEGER") + " NOT NULL DEFAULT 1");
            } catch (SQLException ignored) {
                // ya existe
            }

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + cooldowns() + " ("
                    + "uuid " + uuid + " NOT NULL,"
                    + "efecto " + txt + " NOT NULL,"
                    + "hasta " + big + " NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (uuid, efecto))" + engine);

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + ips() + " ("
                    + "uuid " + uuid + " NOT NULL,"
                    + "ip " + txt + " NULL,"
                    + "PRIMARY KEY (uuid))" + engine);
        }
    }

    public void cerrar() {
        // las conexiones se abren y cierran por operacion
    }
}
