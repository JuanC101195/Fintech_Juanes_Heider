package co.financiera.cartera.nucleo;

import java.time.LocalDateTime;

/**
 * Un registro de la bitácora de operaciones sensibles: quién, cuándo y por qué (D26, D33, D45); el qué lo dice cada
 * tipo. La hora la da siempre quien llama (el núcleo no lee el reloj). Los registros son inmutables y solo se agregan.
 */
public sealed interface EventoBitacora permits CambioParametro, AltaVersionReglas, Exoneracion {

    /** Quién hizo la operación. */
    String quien();

    /** Cuándo se registró (lo da quien llama). */
    LocalDateTime registradoEn();

    /** Por qué. Nunca vacía. */
    String justificacion();

    /** Exige un texto con contenido; lo comparten los registros de la bitácora. */
    static String exigirTexto(String texto, String mensaje) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return texto;
    }
}
