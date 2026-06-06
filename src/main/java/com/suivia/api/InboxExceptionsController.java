package com.suivia.api;
import com.suivia.core.repository.InvoiceMatchRepository;
import com.suivia.core.domain.InvoiceMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxExceptionsController {

    private final InvoiceMatchRepository matchRepo;

    // MVP05 - Fila de Exceções
    @GetMapping("/divergent")
    public ResponseEntity<List<InvoiceMatch>> getDivergentInvoices() {
        // Retorna pro Frontend montar a tela estilo Inbox
        return ResponseEntity.ok(matchRepo.findByStatus("DIVERGENT"));
    }

    // MVP05 - Resolução Manual (Em Lote ou Unitária)
    @PostMapping("/resolve")
    public ResponseEntity<?> resolveDivergences(@RequestBody ResolveRequest request) {
        request.getMatchIds().forEach(id -> {
            matchRepo.findById(id).ifPresent(match -> {
                match.setStatus(request.getAction()); // APPROVED ou REJECTED
                match.setApprovalNote(request.getNote());
                matchRepo.save(match);
            });
        });
        return ResponseEntity.ok(Map.of("success", true, "resolved", request.getMatchIds().size()));
    }

    @lombok.Data
    static class ResolveRequest {
        private List<String> matchIds;
        private String action;
        private String note;
    }
}
