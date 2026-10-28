package co.financiera.cartera.api;

import static co.financiera.cartera.api.Decimales.texto;

import co.financiera.cartera.api.Dtos.AsientoDto;
import co.financiera.cartera.api.Dtos.DeudaDto;
import co.financiera.cartera.api.Dtos.FilaPlan;
import co.financiera.cartera.api.Dtos.LineaAsientoDto;
import co.financiera.cartera.api.Dtos.MovimientoDto;
import co.financiera.cartera.api.Dtos.ServiciosDto;
import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.DeudaExtinguida;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Núcleo → DTO: traduce servicios, deudas, movimientos y asientos del motor a lo que viaja por el API. */
final class MapeadorDto {

    private MapeadorDto() {
    }

    static ServiciosDto servicios(Servicios s) {
        return s == null ? null
                : new ServiciosDto(texto(s.fondoGarantias()), texto(s.asistencia()), texto(s.seguro()), texto(s.total()));
    }

    static DeudaDto deuda(DeudaExtinguida d) {
        return d == null ? null
                : new DeudaDto(texto(d.cobranza()), texto(d.mora()), texto(d.interesCorriente()), servicios(d.servicios()),
                        texto(d.capital()), texto(d.total()));
    }

    static MovimientoDto movimiento(Movimiento m) {
        return new MovimientoDto(m.periodo(), m.mes(), m.estado() == null ? null : m.estado().name(), m.evento(),
                m.evento(), texto(m.saldoInicial()), texto(m.tasaPeriodica()), texto(m.cuotaFinanciera()),
                texto(m.interesCorriente()), texto(m.interesMora()), texto(m.capitalContractual()),
                texto(m.cobranzaCausada()), servicios(m.serviciosCausados()), texto(m.pagoRecibido()),
                texto(m.pagoCobranza()), texto(m.pagoMora()), texto(m.pagoInteresCorriente()), texto(m.pagoServicios()),
                texto(m.pagoCapital()), texto(m.abonoExtra()), texto(m.prepagoCapital()), texto(m.cxcCobranza()),
                texto(m.cxcMora()), texto(m.cxcInteresCorriente()), texto(m.cxcServicios()), texto(m.capitalVencido()),
                texto(m.capitalVigente()), texto(m.saldoFinal()), m.cuotasVencidas(), texto(m.deudaAExtinguir()),
                texto(m.inventario()), texto(m.cxcFondoGarantias()), texto(m.cxpDeudor()), servicios(m.pagoTerceros()),
                servicios(m.pagosServicios()), servicios(m.cxcServiciosPorConcepto()), deuda(m.extinguido()),
                texto(m.capitalVencidoInicial()), deuda(m.pendientesPagadosExtra()), texto(m.interesPrepago()),
                texto(m.costoRecuperacion()), texto(m.costoRecuperacionAsumido()), texto(m.pagoTotalCliente()),
                texto(m.pagoExtraordinario()), m.listoParaRecuperacion(),
                m.sobranteCliente().signum() == 0 ? null : texto(m.sobranteCliente()));
    }

    /** Fila del plan de pagos de una simulación: cuota financiera más servicios del periodo. */
    static FilaPlan filaPlan(Movimiento m) {
        Servicios s = m.serviciosCausados();
        return new FilaPlan(m.periodo(), m.mes(), texto(m.saldoInicial()), texto(m.interesCorriente()),
                texto(m.capitalContractual()), texto(m.cuotaFinanciera()), texto(s.fondoGarantias()),
                texto(s.asistencia()), texto(s.seguro()), texto(Calc.sumar(m.cuotaFinanciera(), s.total())),
                texto(m.saldoFinal()));
    }

    static AsientoDto asiento(Asiento a) {
        List<LineaAsientoDto> lineas = a.lineas().stream()
                .map(l -> new LineaAsientoDto(l.cuenta().name(), l.cuenta().nombreExcel(), texto(l.debito()),
                        texto(l.credito()), l.explicacion()))
                .toList();
        BigDecimal debitos = a.totalDebitos();
        BigDecimal creditos = a.totalCreditos();
        // Débitos = créditos al centavo: con DECIMAL128 las sumas difieren en residuos de 1e-28.
        boolean cuadra = alCentavo(debitos).compareTo(alCentavo(creditos)) == 0;
        return new AsientoDto(a.periodo(), a.evento(), lineas, texto(debitos), texto(creditos), cuadra);
    }

    private static BigDecimal alCentavo(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
