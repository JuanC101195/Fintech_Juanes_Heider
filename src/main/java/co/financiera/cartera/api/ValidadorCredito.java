package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.CreditoRequest;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.util.Arrays;
import java.util.Map;
import java.util.OptionalInt;
import org.springframework.http.HttpStatus;

/** Request → núcleo: valida el crédito del request (línea, plazo, dinero) y arma sus parámetros y su operación. */
final class ValidadorCredito {

    /** Nombres de campo del API que se repiten en errores y en el catálogo. */
    static final String CAMPO_VALOR_MOTO = "valorMoto";
    static final String CAMPO_PERIODICIDAD = "periodicidad";
    static final String CAMPO_CUOTAS = "cuotas";
    private static final String CAMPO_MESES = "meses";
    private static final String CAMPO_PLAZO_LIMITE = "plazoLimitePeriodos";

    /** El doble de las cuotas máximas del catálogo (hoy 2 × 104 semanas). */
    static final int MAX_PLAZO_LIMITE_PERIODOS = 2 * Arrays.stream(Periodicidad.values())
            .mapToInt(per -> per.cuotasEnMeses(plazoMaximoCatalogo())).max().orElseThrow();

    private ValidadorCredito() {
    }

    /** El mayor plazo máximo del catálogo de líneas (R4-PLAZO-LINEA: hoy 24 en todas). */
    static int plazoMaximoCatalogo() {
        return Arrays.stream(LineaCredito.values()).mapToInt(LineaCredito::plazoMaximoMeses).max().orElseThrow();
    }

    static LineaCredito linea(CreditoRequest c) {
        return Enumeraciones.leer(LineaCredito.class, c.linea(), LineaCredito.CPPU, "linea");
    }

    /**
     * Parámetros del crédito del request. Si el request no trae {@code plazoLimitePeriodos} y el perfil fija
     * {@code plazoLimiteMeses} (R4-PLAZO-LIMITE: 24 meses en NEGOCIO), el plazo límite son esos meses en
     * periodos del crédito; con EXCEL queda el del producto (120 semanas convertidas).
     */
    static ParametrosProducto parametros(CreditoRequest c, ReglasNegocio reglas) {
        LineaCredito linea = linea(c);
        Periodicidad per = Enumeraciones.leer(Periodicidad.class, c.periodicidad(), Periodicidad.SEMANAL,
                CAMPO_PERIODICIDAD);
        int cuotas = resolverCuotas(c, linea, per);
        ParametrosProducto p = ParametrosProducto.de(linea, per, cuotas);
        if (reglas.cobranzaDiaria() != null) {
            // COB-6: el valor diario vigente de la cobranza queda fijo en los parámetros del crédito nuevo.
            p = p.conCobranzaDiaria(reglas.cobranzaDiaria());
        }
        return resolverPlazoLimite(p, c, reglas, per, cuotas);
    }

    /** Las cuotas del crédito a partir de {@code cuotas}, {@code meses} o ambos (que deben coincidir). */
    private static int resolverCuotas(CreditoRequest c, LineaCredito linea, Periodicidad per) {
        int maxMeses = linea.plazoMaximoMeses();
        int maxCuotas = per.cuotasEnMeses(maxMeses);
        if (c.cuotas() == null && c.meses() == null) {
            throw ApiException.datoInvalido("Mande cuotas o meses", CAMPO_CUOTAS);
        }
        Integer cuotasMeses = c.meses() == null ? null : cuotasDeMeses(c.meses(), per, maxMeses, maxCuotas);
        if (c.cuotas() == null) {
            return cuotasMeses;
        }
        if (c.cuotas() < 1) {
            throw ApiException.datoInvalido("Las cuotas deben ser al menos 1", CAMPO_CUOTAS);
        }
        if (c.cuotas() > maxCuotas) {
            throw plazoMaximo(maxMeses, maxCuotas, per, c.cuotas() + " " + per.unidadCuota());
        }
        if (cuotasMeses != null && !cuotasMeses.equals(c.cuotas())) {
            throw ApiException.datoInvalido(c.meses() + " meses son " + cuotasMeses + " " + per.unidadCuota()
                    + ", no " + c.cuotas() + "; mande solo cuotas o solo meses", CAMPO_CUOTAS);
        }
        return c.cuotas();
    }

    private static int cuotasDeMeses(int meses, Periodicidad per, int maxMeses, int maxCuotas) {
        if (meses < 1) {
            throw ApiException.datoInvalido("Los meses deben ser al menos 1", CAMPO_MESES);
        }
        if (meses > maxMeses) {
            throw plazoMaximo(maxMeses, maxCuotas, per, "Plazo de " + meses + " meses");
        }
        int cuotas = per.cuotasEnMeses(meses);
        if (cuotas < 1) {
            throw ApiException.datoInvalido(meses + " meses no alcanzan para una cuota " + per.adjetivo(), CAMPO_MESES);
        }
        return cuotas;
    }

    /** El plazo límite de recuperación: el del request, o el que fija el perfil, o el del producto. */
    private static ParametrosProducto resolverPlazoLimite(ParametrosProducto p, CreditoRequest c, ReglasNegocio reglas,
            Periodicidad per, int cuotas) {
        if (c.plazoLimitePeriodos() != null) {
            // Puede ser menor que las cuotas: el caso 8 proyecta 100 semanas de un crédito de 104.
            if (c.plazoLimitePeriodos() < 1) {
                throw ApiException.datoInvalido("El plazo límite debe ser al menos 1 periodo", CAMPO_PLAZO_LIMITE);
            }
            if (c.plazoLimitePeriodos() > MAX_PLAZO_LIMITE_PERIODOS) {
                throw ApiException.datoInvalido("El plazo límite no puede superar " + MAX_PLAZO_LIMITE_PERIODOS
                        + " periodos (el doble de las cuotas máximas del catálogo)", CAMPO_PLAZO_LIMITE);
            }
            return p.conPlazoLimite(c.plazoLimitePeriodos());
        }
        OptionalInt limite = reglas.plazoLimitePeriodos(per, cuotas);
        return limite.isPresent() ? p.conPlazoLimite(limite.getAsInt()) : p;
    }

    private static ApiException plazoMaximo(int maxMeses, int maxCuotas, Periodicidad per, String pedido) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiException.PLAZO_MAXIMO,
                pedido + " supera el máximo de " + maxMeses + " meses (" + maxCuotas + " " + per.unidadCuota() + ")",
                Map.of("maximoMeses", maxMeses, "maximoCuotas", maxCuotas, "periodicidad", per.name()));
    }

    /** COB-6: la cobranza del crédito está activa salvo que el request diga {@code false}. */
    static boolean cobranzaActiva(CreditoRequest c) {
        return c.cobranzaActiva() == null || c.cobranzaActiva();
    }

    static Operacion operacion(CreditoRequest c) {
        Operacion base = Operacion.ejemploExcel();
        return new Operacion(Decimales.dinero(c.valorMoto(), CAMPO_VALOR_MOTO, base.valorMoto(), true),
                Decimales.dinero(c.matriculaRunt(), "matriculaRunt", base.matriculaRunt(), false),
                Decimales.dinero(c.soat(), "soat", base.soat(), false),
                Decimales.dinero(c.prenda(), "prenda", base.prenda(), false),
                Decimales.dinero(c.gpsPlataforma(), "gpsPlataforma", base.gpsPlataforma(), false),
                Decimales.dinero(c.gastosComerciales(), "gastosComerciales", base.gastosComerciales(), false),
                Decimales.dinero(c.pagoInicial(), "pagoInicial", Calc.CERO, false));
    }
}
