package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Las claves de {@link ReglasNegocio}: las que lee la configuración, las que muestra el API y las del importador. */
class ReglasNegocioClavesTest {

    @Test
    void lasClavesSonLosComponentesDelRecordEnSuOrden() {
        // Las mismas 19 que estaban escritas a mano (y en CLAVES_MOTOR de herramientas/importar_respuestas.py).
        assertThat(ReglasNegocio.CLAVES).containsExactly("prelacion", "formulaMora", "baseInteres",
                "topeCobranzaPorPago", "baseServicios", "serviciosDespuesDelPlazo", "moraAntesDeRecuperacion",
                "aplicacionAbono", "interesPrepagoPorDias", "margenUsura", "modalidadUsura",
                "costoRecuperacionAlFondo", "cobranzaIngresoNoOperacional", "plazoLimiteMeses",
                "cambioTasaSobreCapitalVigente", "cobranzaDiaria", "cuotasVencidasParaRecuperacion",
                "prorrateoPrimeraCuota", "diasMora");
    }
}
