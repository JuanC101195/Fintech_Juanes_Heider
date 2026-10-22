package co.financiera.cartera.nucleo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** ADR 0002 · Calendario de vencimientos por periodicidad (PPER-1, PPER-5, CAL-1 a CAL-3). */
class CalendarioPagosTest {

    private static LocalDate f(String texto) {
        return LocalDate.parse(texto);
    }

    @Test
    void semanalVenceTodosLosMartesSinFestivos() {
        // 2026-10-20 es martes. El 2026-12-08 y el 2026-12-22 tampoco se corren (sin festivos, CAL-2).
        CalendarioPagos c = CalendarioPagos.semanal(f("2026-10-15"), f("2026-10-20"));
        assertThat(c.vencimiento(1)).isEqualTo(f("2026-10-20"));
        assertThat(c.vencimiento(8)).isEqualTo(f("2026-12-08"));
        assertThat(c.vencimiento(104).getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(c.diasDelPeriodo(1)).isEqualTo(5);
        assertThat(c.diasDelPeriodo(2)).isEqualTo(7);
    }

    @Test
    void semanalRechazaUnaPrimeraFechaQueNoSeaMartes() {
        assertThatThrownBy(() -> CalendarioPagos.semanal(f("2026-10-15"), f("2026-10-21")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("martes");
    }

    @Test
    void aTiempoHastaElFinDelMartesYElMiercolesEsElDiaUno() {
        CalendarioPagos c = CalendarioPagos.semanal(f("2026-10-15"), f("2026-10-20"));
        assertThat(c.diasAtraso(1, f("2026-10-19"))).isZero(); // pago anticipado
        assertThat(c.diasAtraso(1, f("2026-10-20"))).isZero();
        assertThat(c.diasAtraso(1, f("2026-10-21"))).isEqualTo(1);
        assertThat(c.diasAtraso(1, f("2026-11-03"))).isEqualTo(14);
    }

    @Test
    void quincenalVenceEl5YEl20CruzandoMesesYAnios() {
        CalendarioPagos c = CalendarioPagos.quincenal(f("2026-11-25"), f("2026-12-05"));
        assertThat(c.vencimiento(1)).isEqualTo(f("2026-12-05"));
        assertThat(c.vencimiento(2)).isEqualTo(f("2026-12-20"));
        assertThat(c.vencimiento(3)).isEqualTo(f("2027-01-05"));
        assertThat(c.vencimiento(48)).isEqualTo(f("2028-11-20"));
        assertThat(c.diasDelPeriodo(2)).isEqualTo(15);
        assertThat(c.diasDelPeriodo(3)).isEqualTo(16); // 20-dic → 5-ene

        CalendarioPagos desde20 = CalendarioPagos.quincenal(f("2027-02-01"), f("2027-02-20"));
        assertThat(desde20.vencimiento(2)).isEqualTo(f("2027-03-05"));
        assertThat(desde20.diasDelPeriodo(2)).isEqualTo(13); // febrero de 28 días
    }

    @Test
    void quincenalRechazaOtrosDias() {
        assertThatThrownBy(() -> CalendarioPagos.quincenal(f("2026-11-01"), f("2026-11-15")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 y el 20");
    }

    /** R4-MENSUAL-DIA (Diego, ronda 4, 2026-10-21): la mensual vence el día 5, fijo para todos. */
    @Test
    void mensualVenceElDia5DeCadaMes() {
        CalendarioPagos dia5 = CalendarioPagos.mensual(f("2026-10-20"), f("2026-11-05"));
        assertThat(CalendarioPagos.DIA_PAGO_MENSUAL).isEqualTo(5);
        assertThat(dia5.vencimiento(24)).isEqualTo(f("2028-10-05"));
        assertThat(dia5.vencimiento(4)).isEqualTo(f("2027-02-05"));
    }

    @Test
    void mensualCuentaLosDiasRealesDelMes() {
        // PPER-5: un mes sin pago son 28, 29, 30 o 31 días según el mes.
        CalendarioPagos c = CalendarioPagos.mensual(f("2026-12-15"), f("2027-01-05"));
        assertThat(c.diasDelPeriodo(2)).isEqualTo(31); // enero
        assertThat(c.diasDelPeriodo(3)).isEqualTo(28); // febrero 2027
        assertThat(c.diasDelPeriodo(4)).isEqualTo(31); // marzo
        assertThat(c.diasDelPeriodo(5)).isEqualTo(30); // abril
        assertThat(c.diasDelPeriodo(15)).isEqualTo(29); // febrero 2028, bisiesto
        assertThat(c.diasAtraso(2, f("2027-02-06"))).isEqualTo(1);
    }

    @Test
    void mensualRechazaUnaPrimeraFechaQueNoSeaUn5() {
        // La opción del día 1 se retiró (R4-MENSUAL-DIA).
        assertThatThrownBy(() -> CalendarioPagos.mensual(f("2026-10-20"), f("2026-11-01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("día 5");
        assertThatThrownBy(() -> CalendarioPagos.mensual(f("2026-10-20"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void laPrimeraFechaEsPosteriorAlDesembolsoYLosPeriodosEmpiezanEnUno() {
        assertThatThrownBy(() -> CalendarioPagos.semanal(f("2026-10-20"), f("2026-10-20")))
                .isInstanceOf(IllegalArgumentException.class);
        CalendarioPagos c = CalendarioPagos.semanal(f("2026-10-15"), f("2026-10-20"));
        assertThatThrownBy(() -> c.vencimiento(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(c.periodicidad()).isEqualTo(Periodicidad.SEMANAL);
    }
}
