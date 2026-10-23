package co.financiera.cartera.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticación OPCIONAL por clave (cabecera {@code X-API-Key}). Si {@code cartera.api.claves} está vacía o ausente
 * no hace nada. Con claves, cada una se configura como {@code sha256hex:ROL} (separadas por coma): nunca se guarda
 * ni se registra la clave en claro. Roles: {@code CONSUMIDOR} (todo {@code /api/v1/**}) y {@code LECTURA} (solo GET
 * de catálogo, reglas y casos). Sin clave válida responde 401; con rol insuficiente, 403. Exentos: OPTIONS (CORS) y
 * {@code /actuator/health}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroApiKey extends OncePerRequestFilter {

    enum Rol { CONSUMIDOR, LECTURA }

    private record Clave(byte[] hash, Rol rol) {

        @Override
        public boolean equals(Object otra) {
            return otra instanceof Clave c && Arrays.equals(hash, c.hash) && rol == c.rol;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(hash) + rol.hashCode();
        }

        /** Sin el hash: aunque no sea la clave en claro, no tiene por qué salir en un log. */
        @Override
        public String toString() {
            return "Clave[rol=" + rol + "]";
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(FiltroApiKey.class);
    private static final Pattern RUTA_LECTURA = Pattern.compile("^/api/v1/(catalogo|reglas|casos(/\\d{1,4})?)/?$");
    private static final int MAX_LARGO_CLAVE = 256;
    private static final int LARGO_HEX_SHA256 = 64;

    private final List<Clave> claves;

    public FiltroApiKey(@Value("${cartera.api.claves:}") String configuracion) {
        this.claves = interpretar(configuracion);
        if (!claves.isEmpty()) {
            LOG.info("Autenticación por X-API-Key activa con {} clave(s)", claves.size());
        }
    }

    private static List<Clave> interpretar(String configuracion) {
        List<Clave> lista = new ArrayList<>();
        if (configuracion == null || configuracion.isBlank()) {
            return lista;
        }
        String[] partes = configuracion.split(",");
        for (int i = 0; i < partes.length; i++) {
            String parte = partes[i].trim();
            if (parte.isEmpty()) {
                continue;
            }
            int separador = parte.indexOf(':');
            // Los mensajes no incluyen el valor configurado: podría ser una clave en claro.
            if (separador != LARGO_HEX_SHA256) {
                throw new IllegalStateException("cartera.api.claves: la entrada " + (i + 1)
                        + " debe ser 'sha256hex(64 caracteres):ROL'");
            }
            Rol rol;
            try {
                rol = Rol.valueOf(parte.substring(separador + 1).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("cartera.api.claves: la entrada " + (i + 1)
                        + " tiene un rol desconocido (use CONSUMIDOR o LECTURA)");
            }
            try {
                lista.add(new Clave(HexFormat.of().parseHex(parte.substring(0, separador)), rol));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("cartera.api.claves: la entrada " + (i + 1)
                        + " no tiene un hash SHA-256 hexadecimal válido");
            }
        }
        return lista;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (claves.isEmpty() || "OPTIONS".equalsIgnoreCase(request.getMethod()) || esSalud(request)) {
            chain.doFilter(request, response);
            return;
        }
        Rol rol = rolDe(request.getHeader("X-API-Key"));
        if (rol == null) {
            RespuestaFiltro.error(response, HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTORIZADO",
                    "Falta una clave válida en la cabecera X-API-Key");
            return;
        }
        if (rol == Rol.LECTURA && !permiteLectura(request)) {
            RespuestaFiltro.error(response, HttpServletResponse.SC_FORBIDDEN, "PROHIBIDO",
                    "La clave no tiene permiso para este recurso");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean esSalud(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return "/actuator/health".equals(ruta) || ruta.startsWith("/actuator/health/");
    }

    private static boolean permiteLectura(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && RUTA_LECTURA.matcher(request.getRequestURI()).matches();
    }

    /** El rol de la clave presentada, o {@code null}. Compara en tiempo constante contra todas las configuradas. */
    private Rol rolDe(String presentada) {
        if (presentada == null || presentada.isEmpty() || presentada.length() > MAX_LARGO_CLAVE) {
            return null;
        }
        byte[] hash = sha256(presentada);
        Rol encontrado = null;
        for (Clave c : claves) {
            if (MessageDigest.isEqual(c.hash(), hash) && (encontrado == null || c.rol() == Rol.CONSUMIDOR)) {
                encontrado = c.rol();
            }
        }
        return encontrado;
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
