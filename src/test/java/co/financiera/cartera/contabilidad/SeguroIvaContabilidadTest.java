package co.financiera.cartera.contabilidad;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.nucleo.Servicios;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R11-CONTABILIDAD-SEGURO-IVA (Diego, 2026-10-31): si el seguro da 10.000, el cliente paga 11.900 y la contabilidad
 * segmenta CxP seguros 10.000 / IVA por pagar por seguros 1.900 / CxP aseguradoras 11.900. El IVA nunca es de la
 * financiera frente a la DIAN.
 */
class SeguroIvaContabilidadTest {

    @Test
    void elEjemploDiezMilMilNovecientosOnceMilNovecientosCuadraEnPartidaDoble() {
        Servicios detalle = new Servicios(bd(0), bd(0), bd(0), bd(10_000), bd(1_900));
        List<Asiento.Linea> lineas = new ArrayList<>();
        lineas.add(Asiento.Linea.debito(Cuenta.CAJA, bd(11_900), "Recaudo del seguro con IVA"));
        lineas.addAll(ReglaContable.seguroTodoRiesgo(detalle.total(), detalle));
        Asiento asiento = new Asiento(1, "ejemplo", lineas);

        assertThat(asiento.cuadra()).isTrue();
        assertThat(asiento.totalDebitos()).isEqualByComparingTo(bd(11_900));
        assertThat(asiento.lineas()).filteredOn(l -> l.cuenta() == Cuenta.CXP_SEGUROS).singleElement()
                .satisfies(l -> assertThat(l.credito()).isEqualByComparingTo(bd(10_000)));
        assertThat(asiento.lineas()).filteredOn(l -> l.cuenta() == Cuenta.IVA_SEGUROS_POR_PAGAR).singleElement()
                .satisfies(l -> assertThat(l.credito()).isEqualByComparingTo(bd(1_900)));
        assertThat(ReglaContable.cxpAseguradoras(asiento)).isEqualByComparingTo(bd(11_900));
        // Ninguna línea manda el IVA a una cuenta de la DIAN de la financiera ni al seguro de terceros genérico.
        assertThat(asiento.lineas()).noneMatch(l -> l.cuenta() == Cuenta.CXP_SERVICIOS_TERCEROS);
    }

    @Test
    void sinSeguroTodoRiesgoNoHayLineasDeSeguroNiDeIva() {
        Servicios sin = new Servicios(bd(100), bd(50), bd(30), bd(0), bd(0));
        assertThat(ReglaContable.seguroTodoRiesgo(sin.total(), sin)).allMatch(l -> l.credito().signum() == 0);
        assertThat(ReglaContable.cxpAseguradoras(new Asiento(1, "x", List.of()))).isEqualByComparingTo(bd(0));
    }
}
