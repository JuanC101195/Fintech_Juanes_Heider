package co.financiera.cartera.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tope del cuerpo de la petición (256 KB por defecto): responde 413 en el JSON del contrato. Con
 * {@code Content-Length} decide sin leer; con {@code Transfer-Encoding: chunked} lee hasta el tope + 1 byte.
 */
public class FiltroTamanoCuerpo extends OncePerRequestFilter {

    static final String CODIGO = "CUERPO_DEMASIADO_GRANDE";

    /** Única ruta con un tope propio: el lote de escenarios trae hasta 500 créditos. */
    static final String RUTA_LOTE = "/api/v1/escenarios/ejecutar-lote";
    static final int MAXIMO_LOTE_POR_DEFECTO = 8 * 1024 * 1024;

    private final int maximo;
    private final int maximoLote;

    public FiltroTamanoCuerpo(int maximo) {
        this(maximo, Math.max(maximo, MAXIMO_LOTE_POR_DEFECTO));
    }

    public FiltroTamanoCuerpo(int maximo, int maximoLote) {
        this.maximo = maximo;
        this.maximoLote = maximoLote;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String metodo = request.getMethod();
        if ("GET".equals(metodo) || "OPTIONS".equals(metodo) || "HEAD".equals(metodo)) {
            chain.doFilter(request, response);
            return;
        }
        int maximo = RUTA_LOTE.equals(request.getRequestURI()) ? maximoLote : this.maximo;
        long largo = request.getContentLengthLong();
        if (largo > maximo) {
            rechazar(response, maximo);
            return;
        }
        if (largo >= 0) {
            chain.doFilter(request, response);
            return;
        }
        byte[] cuerpo = request.getInputStream().readNBytes(maximo + 1);
        if (cuerpo.length > maximo) {
            rechazar(response, maximo);
            return;
        }
        chain.doFilter(new CuerpoEnMemoria(request, cuerpo), response);
    }

    private void rechazar(HttpServletResponse response, int maximo) throws IOException {
        RespuestaFiltro.error(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, CODIGO,
                "El cuerpo de la petición supera el máximo de " + (maximo / 1024) + " KB");
    }

    /** La petición con el cuerpo ya leído (para el caso chunked). */
    private static final class CuerpoEnMemoria extends HttpServletRequestWrapper {
        private final byte[] cuerpo;

        CuerpoEnMemoria(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset juego = getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), juego));
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }
    }
}
