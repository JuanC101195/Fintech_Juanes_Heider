package co.financiera.cartera.caracterizacion;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Caracterización de los 13 casos con el perfil NEGOCIO (las reglas de {@code config/reglas-negocio.properties}),
 * semanales de 104 cuotas como los sirve el API. Huellas en {@code caracterizacion/negocio.sha256}; formato en
 * {@link Huellas}. Regenerar: {@code ./mvnw -q -B test -Dtest=NegocioNoCambiaTest -Dcaracterizacion.regenerar=true}.
 */
class NegocioNoCambiaTest {

    static ReglasNegocio reglasNegocio() throws IOException {
        Map<String, String> mapa = new HashMap<>();
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of("config", "reglas-negocio.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        p.forEach((k, v) -> mapa.put(String.valueOf(k), String.valueOf(v)));
        return ReglasNegocio.desde(mapa);
    }

    @Test
    void losTrezeCasosConReglasDeNegocioNoCambianNiUnPeso() throws IOException {
        ReglasNegocio reglas = reglasNegocio();
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        Map<String, Escenario> casos = new LinkedHashMap<>();
        casos.put("caso1", EscenariosExcel.caso1(p));
        casos.put("caso1-inicial", EscenariosExcel.caso1ConInicial(p, bd(590_440)));
        casos.put("caso2", EscenariosExcel.caso2(p));
        casos.put("caso3", EscenariosExcel.caso3(p, false));
        casos.put("caso4", EscenariosExcel.caso4(p));
        casos.put("caso5", EscenariosExcel.caso5(p));
        casos.put("caso6", EscenariosExcel.caso6(p));
        casos.put("caso7", EscenariosExcel.caso7(p));
        // Como CatalogoCasos: con NEGOCIO el caso 8 toma el plazo límite del perfil (X4).
        casos.put("caso8", EscenariosExcel.caso8(p, reglas.plazoLimitePeriodos(p.periodicidad(), p.numeroCuotas())));
        casos.put("caso9", EscenariosExcel.caso9(p));
        casos.put("caso10", EscenariosExcel.caso10(p));
        casos.put("caso11", EscenariosExcel.caso11(p));
        casos.put("caso12", EscenariosExcel.caso12(p));
        casos.put("caso13", EscenariosExcel.caso13(p));

        Map<String, String> actual = new LinkedHashMap<>();
        casos.forEach((nombre, e) -> actual.put(nombre, Huellas.huella(e.conReglas(reglas))));
        Huellas.verificar("negocio.sha256", actual);
    }
}
