package co.financiera.cartera;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de arquitectura que {@code ModularidadTest} (Spring Modulith) no expresa: el núcleo es cálculo puro y la
 * dependencia va de arriba hacia abajo (api → simulaciones/contabilidad → nucleo).
 *
 * <p>{@code package-info} se excluye de las reglas del núcleo: ahí vive la anotación {@code @ApplicationModule} de
 * Spring Modulith que declara el módulo como OPEN; no es lógica del núcleo.
 */
@AnalyzeClasses(packages = "co.financiera.cartera", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule nucleoNoUsaSpring = noClasses().that().resideInAPackage("co.financiera.cartera.nucleo..")
            .and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because("el núcleo es cálculo puro, sin Spring ni base de datos");

    @ArchTest
    static final ArchRule nucleoNoUsaApiContabilidadNiSimulaciones = noClasses()
            .that().resideInAPackage("co.financiera.cartera.nucleo..")
            .should().dependOnClassesThat().resideInAnyPackage("co.financiera.cartera.api..",
                    "co.financiera.cartera.contabilidad..", "co.financiera.cartera.simulaciones..")
            .because("todos los demás módulos dependen del núcleo, nunca al revés");

    @ArchTest
    static final ArchRule contabilidadNoUsaApiNiSimulaciones = noClasses()
            .that().resideInAPackage("co.financiera.cartera.contabilidad..")
            .should().dependOnClassesThat().resideInAnyPackage("co.financiera.cartera.api..",
                    "co.financiera.cartera.simulaciones..")
            .because("la contabilidad solo conoce el núcleo");

    @ArchTest
    static final ArchRule simulacionesNoUsaApi = noClasses().that().resideInAPackage("co.financiera.cartera.simulaciones..")
            .should().dependOnClassesThat().resideInAPackage("co.financiera.cartera.api..")
            .because("el API usa los escenarios, no al revés");
}
