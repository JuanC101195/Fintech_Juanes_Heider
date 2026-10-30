package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formato de texto explícito y estable de una {@link Instantanea}: una línea {@code clave=valor} por campo, en un orden
 * fijo, con el formato y la versión al principio. No usa serialización de Java ni reflexión: renombrar un campo del
 * record no cambia el formato, y un cambio de formato exige subir {@link #VERSION} y leer las versiones anteriores.
 *
 * <p>Reglas del formato (versión 1):
 * <ul>
 *   <li>Primera línea {@code formato=motor-cartera.instantanea}, segunda {@code version=1}.</li>
 *   <li>Dinero y tasas: {@code BigDecimal.toPlainString()} (todos los decimales, escala incluida: {@code 0.00} se lee
 *       con escala 2). Si la escala es negativa (por ejemplo {@code 1.0E+10}, que {@code toPlainString} escribiría
 *       sin su escala) se escribe con {@code toString()}. Así la lectura devuelve el mismo {@code BigDecimal}, con la
 *       misma escala, y los cálculos que siguen dan exactamente las mismas cifras.</li>
 *   <li>Enteros en decimal, booleanos {@code true}/{@code false}, enums por su nombre, fechas ISO ({@code 2026-10-23}).</li>
 *   <li>Lo opcional ({@code versionReglas}, {@code usuraPorAplicar}, el calendario) va vacío cuando no hay.</li>
 *   <li>La lectura es estricta: exige exactamente estas claves, sin repetidas ni desconocidas.</li>
 *   <li>R9-SEGURO-TODO-RIESGO: en cada grupo de servicios, {@code .seguroTodoRiesgo} y {@code .ivaSeguroTodoRiesgo}
 *       solo se escriben cuando alguno no es el 0 exacto (valor o escala distintos de {@code 0}), y al leer son
 *       opcionales (0 si faltan). Así las instantáneas sin
 *       seguro todo riesgo (todas las anteriores y las del Excel) siguen siendo de la versión 1, idénticas.</li>
 * </ul>
 */
public final class FormatoInstantanea {
    private static final String CLAVE_FORMATO = "formato";
    private static final String CLAVE_VERSION = "version";


    public static final String FORMATO = "motor-cartera.instantanea";
    public static final int VERSION = 1;

    private FormatoInstantanea() {
    }

    public static String escribir(Instantanea i) {
        Escritor w = new Escritor();
        w.linea(CLAVE_FORMATO, FORMATO);
        w.linea(CLAVE_VERSION, String.valueOf(VERSION));
        w.linea("periodo", String.valueOf(i.periodo()));
        w.linea("terminado", String.valueOf(i.terminado()));
        w.linea("versionReglas", i.versionReglas() == null ? "" : i.versionReglas());
        Operacion o = i.operacion();
        w.dinero("operacion.valorMoto", o.valorMoto());
        w.dinero("operacion.matriculaRunt", o.matriculaRunt());
        w.dinero("operacion.soat", o.soat());
        w.dinero("operacion.prenda", o.prenda());
        w.dinero("operacion.gpsPlataforma", o.gpsPlataforma());
        w.dinero("operacion.gastosComerciales", o.gastosComerciales());
        w.dinero("operacion.pagoInicial", o.pagoInicial());
        ParametrosProducto p = i.parametros();
        w.linea("parametros.numeroCuotas", String.valueOf(p.numeroCuotas()));
        w.linea("parametros.periodicidad", p.periodicidad().name());
        w.dinero("parametros.tasaEA", p.tasaEA());
        w.dinero("parametros.fondoGarantiasPorcentajeMensual", p.fondoGarantiasPorcentajeMensual());
        w.dinero("parametros.asistenciaMensual", p.asistenciaMensual());
        w.dinero("parametros.seguroPorcentajeMensual", p.seguroPorcentajeMensual());
        w.dinero("parametros.iva", p.iva());
        w.dinero("parametros.cobranzaDiaria", p.cobranzaDiaria());
        w.linea("parametros.plazoLimitePeriodos", String.valueOf(p.plazoLimitePeriodos()));
        w.linea("cobranzaActiva", String.valueOf(i.cobranzaActiva()));
        CalendarioPagos c = i.calendario();
        w.linea("calendario.desembolso", c == null ? "" : c.desembolso().toString());
        w.linea("calendario.primerVencimiento", c == null ? "" : c.primerVencimiento().toString());
        Tasas t = i.tasas();
        w.dinero("tasas.efectivaAnual", t.efectivaAnual());
        w.dinero("tasas.periodica", t.periodica());
        w.dinero("tasas.diaria", t.diaria());
        w.dinero("tasas.mensual", t.mensual());
        w.dinero("cuota", i.cuota());
        w.dinero("saldo", i.saldo());
        w.dinero("saldoPlan", i.saldoPlan());
        w.dinero("capitalVencido", i.capitalVencido());
        w.dinero("cxcInteres", i.cxcInteres());
        w.dinero("cxcMora", i.cxcMora());
        w.dinero("cxcCobranza", i.cxcCobranza());
        w.servicios("cxcServicios", i.cxcServicios());
        w.linea("cuotasVigentes", String.valueOf(i.cuotasVigentes()));
        w.linea("cuotasVencidas", String.valueOf(i.cuotasVencidas()));
        w.servicios("recaudadoDelMes", i.recaudadoDelMes());
        w.linea("usuraPorAplicar", i.usuraPorAplicar() == null ? "" : texto(i.usuraPorAplicar()));
        w.linea("marcadoParaRecuperacion", String.valueOf(i.marcadoParaRecuperacion()));
        return w.toString();
    }

    public static Instantanea leer(String texto) {
        Lector r = new Lector(texto);
        if (!FORMATO.equals(r.texto(CLAVE_FORMATO))) {
            throw new IllegalArgumentException("No es una instantánea del motor: formato=" + r.texto(CLAVE_FORMATO));
        }
        if (r.entero(CLAVE_VERSION) != VERSION) {
            throw new IllegalArgumentException("Versión de instantánea no soportada: " + r.texto(CLAVE_VERSION)
                    + " (este motor lee la " + VERSION + ")");
        }
        Operacion operacion = new Operacion(r.dinero("operacion.valorMoto"), r.dinero("operacion.matriculaRunt"),
                r.dinero("operacion.soat"), r.dinero("operacion.prenda"), r.dinero("operacion.gpsPlataforma"),
                r.dinero("operacion.gastosComerciales"), r.dinero("operacion.pagoInicial"));
        Periodicidad periodicidad = Periodicidad.valueOf(r.texto("parametros.periodicidad"));
        ParametrosProducto parametros = new ParametrosProducto(r.entero("parametros.numeroCuotas"), periodicidad,
                r.dinero("parametros.tasaEA"), r.dinero("parametros.fondoGarantiasPorcentajeMensual"),
                r.dinero("parametros.asistenciaMensual"), r.dinero("parametros.seguroPorcentajeMensual"),
                r.dinero("parametros.iva"), r.dinero("parametros.cobranzaDiaria"), r.entero("parametros.plazoLimitePeriodos"));
        String desembolso = r.texto("calendario.desembolso");
        String primerVencimiento = r.texto("calendario.primerVencimiento");
        if (desembolso.isEmpty() != primerVencimiento.isEmpty()) {
            throw new IllegalArgumentException("El calendario lleva las dos fechas o ninguna");
        }
        CalendarioPagos calendario = desembolso.isEmpty() ? null
                : CalendarioPagos.de(periodicidad, LocalDate.parse(desembolso), LocalDate.parse(primerVencimiento));
        Tasas tasas = new Tasas(r.dinero("tasas.efectivaAnual"), r.dinero("tasas.periodica"), r.dinero("tasas.diaria"),
                r.dinero("tasas.mensual"));
        String version = r.texto("versionReglas");
        String usura = r.texto("usuraPorAplicar");
        Instantanea i = new Instantanea(r.entero("periodo"), r.booleano("terminado"), version.isEmpty() ? null : version,
                operacion, parametros, r.booleano("cobranzaActiva"), calendario, tasas, r.dinero("cuota"), r.dinero("saldo"),
                r.dinero("saldoPlan"), r.dinero("capitalVencido"), r.dinero("cxcInteres"), r.dinero("cxcMora"),
                r.dinero("cxcCobranza"), r.servicios("cxcServicios"), r.entero("cuotasVigentes"), r.entero("cuotasVencidas"),
                r.servicios("recaudadoDelMes"), usura.isEmpty() ? null : new BigDecimal(usura),
                r.booleano("marcadoParaRecuperacion"));
        r.verificarQueNoSobra();
        return i;
    }

    /** {@code toPlainString}, salvo escala negativa ({@code toString}, que la conserva). */
    static String texto(BigDecimal v) {
        return v.scale() < 0 ? v.toString() : v.toPlainString();
    }

    private static final class Escritor {
        private final StringBuilder t = new StringBuilder();

        void linea(String clave, String valor) {
            t.append(clave).append('=').append(valor).append('\n');
        }

        void dinero(String clave, BigDecimal valor) {
            linea(clave, texto(valor));
        }

        void servicios(String prefijo, Servicios s) {
            dinero(prefijo + ".fondoGarantias", s.fondoGarantias());
            dinero(prefijo + ".asistencia", s.asistencia());
            dinero(prefijo + ".seguro", s.seguro());
            if (!ceroExacto(s.seguroTodoRiesgo()) || !ceroExacto(s.ivaSeguroTodoRiesgo())) {
                dinero(prefijo + ".seguroTodoRiesgo", s.seguroTodoRiesgo());
                dinero(prefijo + ".ivaSeguroTodoRiesgo", s.ivaSeguroTodoRiesgo());
            }
        }

        /** 0 con escala 0: así se lee una clave opcional ausente; cualquier otro valor (incluso 0E-30) se escribe. */
        private static boolean ceroExacto(BigDecimal valor) {
            return valor.signum() == 0 && valor.scale() == 0;
        }

        @Override
        public String toString() {
            return t.toString();
        }
    }

    private static final class Lector {
        private final Map<String, String> valores = new LinkedHashMap<>();
        private final Set<String> leidas = new HashSet<>();

        Lector(String texto) {
            for (String linea : texto.split("\r?\n")) {
                if (linea.isEmpty()) {
                    continue;
                }
                int igual = linea.indexOf('=');
                if (igual <= 0) {
                    throw new IllegalArgumentException("Línea sin clave=valor en la instantánea: " + linea);
                }
                String clave = linea.substring(0, igual);
                if (valores.put(clave, linea.substring(igual + 1)) != null) {
                    throw new IllegalArgumentException("Clave repetida en la instantánea: " + clave);
                }
            }
        }

        String texto(String clave) {
            String v = valores.get(clave);
            if (v == null) {
                throw new IllegalArgumentException("Falta la clave " + clave + " en la instantánea");
            }
            leidas.add(clave);
            return v;
        }

        BigDecimal dinero(String clave) {
            return new BigDecimal(texto(clave));
        }

        int entero(String clave) {
            return Integer.parseInt(texto(clave));
        }

        boolean booleano(String clave) {
            String v = texto(clave);
            if (!"true".equals(v) && !"false".equals(v)) {
                throw new IllegalArgumentException("Valor booleano inválido en " + clave + ": " + v);
            }
            return Boolean.parseBoolean(v);
        }

        Servicios servicios(String prefijo) {
            return new Servicios(dinero(prefijo + ".fondoGarantias"), dinero(prefijo + ".asistencia"),
                    dinero(prefijo + ".seguro"), dineroOpcional(prefijo + ".seguroTodoRiesgo"),
                    dineroOpcional(prefijo + ".ivaSeguroTodoRiesgo"));
        }

        /** Dinero de una clave opcional (R9-SEGURO-TODO-RIESGO): 0 si no está. */
        BigDecimal dineroOpcional(String clave) {
            return valores.containsKey(clave) ? dinero(clave) : BigDecimal.ZERO;
        }

        void verificarQueNoSobra() {
            List<String> sobran = valores.keySet().stream().filter(k -> !leidas.contains(k)).toList();
            if (!sobran.isEmpty()) {
                throw new IllegalArgumentException("Claves desconocidas en la instantánea: " + sobran);
            }
        }
    }
}
