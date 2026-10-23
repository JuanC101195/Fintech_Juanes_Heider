package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.InstruccionDto;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import java.util.Arrays;
import java.util.List;

/** Traduce entre {@link InstruccionDto} y las instrucciones del motor, en los dos sentidos. */
final class Instrucciones {

    /** Tipos de instrucción del API, uno por record de {@link Instruccion}. */
    enum Tipo {
        PAGO_CONTRACTUAL,
        PAGO_PARCIAL,
        PAGO_VALOR,
        SIN_PAGO,
        ABONO_EXTRA,
        PREPAGO_TOTAL,
        USURA_VIGENTE,
        CAMBIO_TASA,
        PAGO_AL_DIA,
        CIERRE_POR_RECUPERACION
    }

    private Instrucciones() {
    }

    static List<String> tipos() {
        return Arrays.stream(Tipo.values()).map(Enum::name).toList();
    }

    static Instruccion aMotor(InstruccionDto d) {
        Tipo tipo = Enumeraciones.leer(Tipo.class, d.tipo(), null, "tipo");
        if (tipo == null) {
            throw ApiException.datoInvalido("Falta el tipo de la instrucción del periodo " + d.periodo(), "tipo");
        }
        int dias = d.diasAtraso() == null ? 0 : d.diasAtraso();
        if (dias < 0) {
            throw ApiException.datoInvalido("Los días de atraso no pueden ser negativos", "diasAtraso");
        }
        return switch (tipo) {
            case PAGO_CONTRACTUAL -> new PagoContractual(dias);
            case PAGO_PARCIAL -> new PagoParcial(Decimales.obligatorio(d.fraccion(), "fraccion"), dias);
            case PAGO_VALOR -> new PagoValor(Decimales.obligatorio(d.valor(), "valor"), dias);
            case SIN_PAGO -> new SinPago(d.causaMoraYServicios() == null || d.causaMoraYServicios());
            case ABONO_EXTRA -> new AbonoExtra(Decimales.obligatorio(d.valor(), "valor"),
                    Enumeraciones.leer(ModalidadAbono.class, d.modalidad(), ModalidadAbono.POR_DEFECTO, "modalidad"));
            case PREPAGO_TOTAL -> new PrepagoTotal(d.diasDesdeUltimaCuota() == null ? 0 : d.diasDesdeUltimaCuota());
            case USURA_VIGENTE -> new UsuraVigente(Decimales.obligatorio(d.usuraEA(), "usuraEA"));
            case CAMBIO_TASA -> new CambioTasa(Decimales.obligatorio(d.nuevaTasaEA(), "nuevaTasaEA"),
                    Enumeraciones.leer(ModalidadCambioTasa.class, d.modalidad(), ModalidadCambioTasa.MISMA_FECHA_FINAL,
                            "modalidad"));
            case PAGO_AL_DIA -> new PagoAlDia();
            case CIERRE_POR_RECUPERACION -> new CierrePorRecuperacion(
                    obligatorio(Enumeraciones.leer(TipoRecuperacion.class, d.tipoRecuperacion(), null, "tipoRecuperacion"),
                            "tipoRecuperacion"),
                    Decimales.obligatorio(d.valorMoto(), "valorMoto"),
                    Decimales.leer(d.costoRecuperacion(), "costoRecuperacion", java.math.BigDecimal.ZERO));
        };
    }

    private static <T> T obligatorio(T valor, String campo) {
        if (valor == null) {
            throw ApiException.datoInvalido("Falta el campo " + campo, campo);
        }
        return valor;
    }

    /** La instrucción del motor en el formato del request, con todos los campos de su tipo explícitos. */
    static InstruccionDto aDto(int periodo, Instruccion i) {
        return switch (i) {
            case PagoContractual p -> dto(periodo, Tipo.PAGO_CONTRACTUAL).diasAtraso(p.diasAtraso()).fin();
            case PagoParcial p -> dto(periodo, Tipo.PAGO_PARCIAL).fraccion(p.fraccion()).diasAtraso(p.diasAtraso()).fin();
            case PagoValor p -> dto(periodo, Tipo.PAGO_VALOR).valor(p.valor()).diasAtraso(p.diasAtraso()).fin();
            case SinPago s -> dto(periodo, Tipo.SIN_PAGO).causaMora(s.causaMoraYServicios()).fin();
            case AbonoExtra a -> dto(periodo, Tipo.ABONO_EXTRA).valor(a.valor()).modalidad(a.modalidad()).fin();
            case PrepagoTotal p -> dto(periodo, Tipo.PREPAGO_TOTAL).diasDesde(p.diasDesdeUltimaCuota()).fin();
            case UsuraVigente u -> dto(periodo, Tipo.USURA_VIGENTE).usura(u.usuraEA()).fin();
            case CambioTasa c -> dto(periodo, Tipo.CAMBIO_TASA).nuevaTasa(c.nuevaTasaEA()).modalidad(c.modalidad()).fin();
            case PagoAlDia p -> dto(periodo, Tipo.PAGO_AL_DIA).fin();
            case CierrePorRecuperacion c -> dto(periodo, Tipo.CIERRE_POR_RECUPERACION).recuperacion(c.tipo(), c.valorMoto(), c.costoRecuperacion())
                    .fin();
        };
    }

    private static Constructor dto(int periodo, Tipo tipo) {
        return new Constructor(periodo, tipo);
    }

    /** Arma el DTO de salida campo a campo. */
    private static final class Constructor {
        private final int periodo;
        private final Tipo tipo;
        private Integer diasAtraso;
        private String fraccion;
        private String valor;
        private Boolean causaMora;
        private String modalidad;
        private Integer diasDesde;
        private String usura;
        private String nuevaTasa;
        private String tipoRecuperacion;
        private String valorMoto;
        private String costoRecuperacion;

        Constructor(int periodo, Tipo tipo) {
            this.periodo = periodo;
            this.tipo = tipo;
        }

        Constructor diasAtraso(int v) {
            diasAtraso = v;
            return this;
        }

        Constructor fraccion(java.math.BigDecimal v) {
            fraccion = Decimales.texto(v);
            return this;
        }

        Constructor valor(java.math.BigDecimal v) {
            valor = Decimales.texto(v);
            return this;
        }

        Constructor causaMora(boolean v) {
            causaMora = v;
            return this;
        }

        Constructor modalidad(Enum<?> v) {
            modalidad = v.name();
            return this;
        }

        Constructor diasDesde(int v) {
            diasDesde = v;
            return this;
        }

        Constructor usura(java.math.BigDecimal v) {
            usura = Decimales.texto(v);
            return this;
        }

        Constructor nuevaTasa(java.math.BigDecimal v) {
            nuevaTasa = Decimales.texto(v);
            return this;
        }

        Constructor recuperacion(TipoRecuperacion t, java.math.BigDecimal moto, java.math.BigDecimal costo) {
            tipoRecuperacion = t.name();
            valorMoto = Decimales.texto(moto);
            costoRecuperacion = Decimales.texto(costo);
            return this;
        }

        InstruccionDto fin() {
            return new InstruccionDto(periodo, tipo.name(), diasAtraso, fraccion, valor, causaMora, modalidad, diasDesde,
                    usura, nuevaTasa, tipoRecuperacion, valorMoto, costoRecuperacion);
        }
    }
}
