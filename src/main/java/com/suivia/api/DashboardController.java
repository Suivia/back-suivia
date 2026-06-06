package com.suivia.api;
import com.suivia.core.repository.InvoiceStagingRepository;
import com.suivia.core.repository.InvoiceMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final InvoiceStagingRepository stagingRepo;
    private final InvoiceMatchRepository matchRepo;

    // MVP06 - Visão Real-time
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        long totalStaging = stagingRepo.count();
        long approved = matchRepo.countByStatus("APPROVED");
        long divergent = matchRepo.countByStatus("DIVERGENT");
        long rejected = matchRepo.countByStatus("REJECTED");

        // Touchless Rate
        long totalMatch = approved + divergent + rejected;
        double touchlessRate = totalMatch > 0 ? ((double) approved / totalMatch) * 100 : 0.0;

        return ResponseEntity.ok(Map.of(
            "totalProcessed", totalStaging,
            "approved", approved,
            "divergent", divergent,
            "rejected", rejected,
            "touchlessRate", Math.round(touchlessRate) + "%"
        ));
    }
}
