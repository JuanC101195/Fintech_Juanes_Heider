/**
 * API REST del motor de cartera (docs/api.md). Expone el núcleo, la contabilidad y los escenarios del
 * Excel con DTOs propios: el dinero y las tasas viajan como string decimal para no perder precisión.
 * Depende de {@code nucleo}, {@code contabilidad} y {@code simulaciones}; ningún módulo depende de él.
 */
@org.springframework.modulith.ApplicationModule(displayName = "API REST",
        allowedDependencies = {"nucleo", "contabilidad", "simulaciones"})
package co.financiera.cartera.api;
