package co.financiera.cartera;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * HT-01: verifica las fronteras del monolito modular. Falla si un módulo usa clases internas de
 * otro o si hay ciclos entre módulos.
 */
class ModularidadTest {

    @Test
    void lasFronterasEntreModulosSeRespetan() {
        ApplicationModules.of(MotorCarteraApplication.class).verify();
    }
}
