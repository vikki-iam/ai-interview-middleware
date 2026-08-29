package ai.interview.middleware.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes a request id for correlation and puts request metadata into the MDC, which
 * {@code logback-spring.xml} promotes to top-level JSON fields.
 *
 * <p>Honours an inbound {@code X-Request-Id} so a trace started at the ingress or in the browser
 * survives the hop into this service, and echoes it back so a client can quote it in a bug report.
 * Registered at the highest precedence so security rejections are logged with an id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final String MDC_METHOD = "method";
    private static final String MDC_PATH = "path";
    private static final int MAX_INBOUND_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        MDC.put(MDC_METHOD, request.getMethod());
        MDC.put(MDC_PATH, request.getRequestURI());
        response.setHeader(HEADER_NAME, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled: leaving MDC populated would tag an unrelated later request with
            // this one's id.
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(inbound)) {
            return UUID.randomUUID().toString();
        }
        // Sanitised and length-bounded: this value reaches the logs and a response header, so it is
        // untrusted input. Stripping CR/LF prevents header and log injection.
        String sanitised = inbound.replaceAll("[^A-Za-z0-9._:-]", "");
        if (sanitised.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sanitised.length() > MAX_INBOUND_LENGTH
                ? sanitised.substring(0, MAX_INBOUND_LENGTH)
                : sanitised;
    }
}
