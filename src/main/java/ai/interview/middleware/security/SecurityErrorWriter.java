package ai.interview.middleware.security;

import ai.interview.middleware.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Renders {@link ErrorResponse} from inside the filter chain.
 *
 * <p>Security rejections happen before any {@code @RestControllerAdvice} runs, so without this a 401
 * would come back as an HTML error page while a 404 came back as JSON. Sharing the writer keeps one
 * error shape across the whole API.
 */
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String error,
            String code,
            String message)
            throws IOException {

        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body =
                ErrorResponse.of(
                        status, error, code, message, request.getRequestURI(), MDC.get(RequestIdFilter.MDC_KEY));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
