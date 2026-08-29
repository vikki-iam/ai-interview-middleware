package ai.interview.middleware.web.controller;

import ai.interview.middleware.dto.dashboard.DashboardSummaryResponse;
import ai.interview.middleware.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Aggregated platform metrics")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Platform summary",
            description =
                    "Totals, per-status breakdowns, the mean interview score and the next few "
                            + "interviews. `averageOverallScore` is absent until at least one result exists.")
    @ApiResponse(responseCode = "200", description = "The summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        // A short private max-age lets a browser tab refresh cheaply without ever letting a shared
        // cache serve one user's figures to another.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(15)).cachePrivate())
                .body(dashboardService.summary());
    }
}
