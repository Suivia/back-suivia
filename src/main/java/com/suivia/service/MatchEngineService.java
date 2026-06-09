package com.suivia.service;

import com.suivia.domain.entity.InvoiceMatch;
import com.suivia.domain.entity.InvoiceStaging;
import com.suivia.domain.enums.MatchStatus;
import com.suivia.repository.InvoiceMatchRepository;
import com.suivia.repository.InvoiceStagingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de Conciliação Inteligente (3-Way Match) - Regra Oficial (Seção 4 da Arquitetura)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchEngineService {

    private final InvoiceMatchRepository matchRepository;
    private final InvoiceStagingRepository stagingRepository;

    @Transactional
    public InvoiceMatch processEngine(InvoiceStaging staging, String purchaseOrderDataMock) {
        log.info("Iniciando Motor de Match para NF [{}]", staging.getInvoiceNumber());

        // Simulando a extração dos pesos:
        // CNPJ (25%) - Bloqueante
        // Valor (25%)
        // Itens/Qtd (25%) - Fuzzy Match p/ descrições
        // Impostos (15%)
        // Data (10%)

        int score = 0;
        MatchStatus status = MatchStatus.REJEITADA;

        // Regra Especial de Negócio (RN03 e Sefaz Cancelada)
        if ("CANCELLED".equalsIgnoreCase(staging.getSefazStatus())) {
            score = 0;
            status = MatchStatus.REJEITADA_CRITICA;
            return saveResult(staging, score, status, "Sefaz Status Cancelado", null);
        }

        // Mock: Vamos assumir um score perfeito ou gerado para demonstração
        boolean hasCnpjMatch = true; 
        boolean hasValueMatch = true; 

        if (hasCnpjMatch) score += 25;
        if (hasValueMatch) score += 25;
        score += 20; // Itens (divergência mínima)
        score += 10; // Impostos (com erro arredondamento, perde 5)
        score += 10; // Data Ok

        // Total Score simulado: 90

        // Classificação do Resultado (Regra da Seção 4.2)
        if (score == 100) {
            status = MatchStatus.APROVADA; // Touchless
        } else if (score >= 90) {
            status = MatchStatus.DIVERGENTE_BAIXA; // Aplica tolerância e alerta
        } else if (score >= 70) {
            status = MatchStatus.DIVERGENTE_MEDIA; // Fila de exceção manual
        } else {
            status = MatchStatus.REJEITADA; // Bloqueia pagamento
        }

        // Simulação de montagem do JSONB do cabeçalho
        String headerResult = "{\"cnpj_match\": true, \"value_match\": true}";
        String itemsResult = "[{\"item\": \"Mouse\", \"qty_divergence\": 10}]";

        log.info("Match Finalizado. Score: {} | Status: {}", score, status);
        return saveResult(staging, score, status, headerResult, itemsResult);
    }

    private InvoiceMatch saveResult(InvoiceStaging staging, int score, MatchStatus status, String header, String items) {
        InvoiceMatch match = new InvoiceMatch();
        match.setStaging(staging);
        match.setMatchScore(score);
        match.setStatus(status);
        match.setHeaderMatch(header);
        match.setItemsMatch(items);
        return matchRepository.save(match);
    }
}
