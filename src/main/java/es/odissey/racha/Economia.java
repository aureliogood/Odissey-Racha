package es.odissey.racha;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;

/**
 * Acceso opcional a Vault (dinero) por reflexion, para no depender de Vault al
 * compilar. Si el servidor no tiene Vault (o ninguna economia registrada),
 * disponible() es false y el plugin sigue funcionando sin dinero.
 */
public final class Economia {

    private static boolean vaultPresente;
    private static Object eco;
    private static Method mSaldo;
    private static Method mRetirar;
    private static Method mDepositar;

    private Economia() {
    }

    /** true si hay una economia de Vault utilizable. */
    public static boolean disponible() {
        buscar();
        return eco != null;
    }

    /** true si la clase de Vault esta presente (aunque no haya proveedor). */
    public static boolean vaultPresente() {
        buscar();
        return vaultPresente;
    }

    /** Saldo del jugador, o 0 si no hay economia. */
    public static double saldo(OfflinePlayer jugador) {
        buscar();
        if (eco == null || mSaldo == null || jugador == null) return 0;
        try {
            Object r = mSaldo.invoke(eco, jugador);
            return r instanceof Number ? ((Number) r).doubleValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Quita dinero al jugador. true si se pudo. */
    public static boolean retirar(OfflinePlayer jugador, double cantidad) {
        buscar();
        if (eco == null || mRetirar == null || jugador == null || cantidad <= 0) return false;
        try {
            Object r = mRetirar.invoke(eco, jugador, cantidad);
            // Respuesta tipica de Vault: EconomyResponse con transactionSuccess().
            return exito(r);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Da dinero al jugador. true si se pudo. */
    public static boolean depositar(OfflinePlayer jugador, double cantidad) {
        buscar();
        if (eco == null || mDepositar == null || jugador == null || cantidad <= 0) return false;
        try {
            Object r = mDepositar.invoke(eco, jugador, cantidad);
            return exito(r);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Lee transactionSuccess() de una EconomyResponse (true si no se puede leer). */
    private static boolean exito(Object respuesta) {
        if (respuesta == null) return true;
        try {
            Object ok = respuesta.getClass().getMethod("transactionSuccess").invoke(respuesta);
            return !(ok instanceof Boolean) || (Boolean) ok;
        } catch (Throwable t) {
            return true;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void buscar() {
        if (eco != null) return;
        Class<?> economia;
        try {
            economia = Class.forName("net.milkbowl.vault.economy.Economy");
            vaultPresente = true;
        } catch (Throwable sinVault) {
            return;
        }
        Object servicios = Bukkit.getServicesManager();
        try {
            Method getReg = servicios.getClass().getMethod("getRegistration", Class.class);
            Object reg = getReg.invoke(servicios, economia);
            if (reg != null && usarProveedor(reg)) return;
        } catch (Throwable ignored) {
        }
        try {
            Method getRegs = servicios.getClass().getMethod("getRegistrations", Class.class);
            Object res = getRegs.invoke(servicios, economia);
            if (res instanceof Iterable<?> registros) {
                for (Object rsp : registros) {
                    if (usarProveedor(rsp)) return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean usarProveedor(Object registro) {
        try {
            Object proveedor = registro.getClass().getMethod("getProvider").invoke(registro);
            if (proveedor == null) return false;
            ecolocal(proveedor);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void ecolocal(Object proveedor) {
        eco = proveedor;
        // Se busca la sobrecarga que recibe OfflinePlayer (evita la de String).
        mSaldo = metodo(proveedor, "getBalance", OfflinePlayer.class);
        mRetirar = metodo(proveedor, "withdrawPlayer", OfflinePlayer.class, double.class);
        mDepositar = metodo(proveedor, "depositPlayer", OfflinePlayer.class, double.class);
    }

    /** Busca un metodo por nombre y tipos exactos de parametros. */
    private static Method metodo(Object obj, String nombre, Class<?>... tipos) {
        try {
            Method m = obj.getClass().getMethod(nombre, tipos);
            // El proveedor puede ser una clase no publica: hay que abrir el acceso.
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Formatea una cantidad de dinero sin decimales si es entera. */
    public static String dinero(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
