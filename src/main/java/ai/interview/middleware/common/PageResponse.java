package ai.interview.middleware.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope.
 *
 * <p>Spring's {@code PageImpl} is deliberately not serialised directly: its JSON shape is an
 * implementation detail that has changed between Spring Data versions, and leaking it would make an
 * upgrade a breaking API change for every client.
 */
@Schema(name = "PageResponse", description = "A page of results with pagination metadata")
public record PageResponse<T>(
        @Schema(description = "Items on the current page") List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Requested page size", example = "20") int size,
        @Schema(description = "Total matching elements", example = "137") long totalElements,
        @Schema(description = "Total pages available", example = "7") int totalPages,
        @Schema(description = "True when this is the first page") boolean first,
        @Schema(description = "True when this is the last page") boolean last,
        @Schema(description = "Number of items on this page", example = "20") int numberOfElements) {

    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements());
    }
}
