package com.suivia.invoice.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO com todos os dados extraídos do XML NFe.
 * Alinhado com o modelo de dados invoice_staging do documento SUIVIA.
 */
@Data
public class NFeExtractedData {

    private String supplierCnpj;       // CNPJ do emitente
    private String recipientCnpj;      // CNPJ do destinatário
    private String invoiceNumber;      // Número da NF (nNF)
    private String invoiceKey;         // Chave de acesso 44 dígitos (chNFe)
    private String emissionDate;       // Data de emissão (dhEmi)
    private BigDecimal totalAmount;    // Valor total (vNF)
    private String purchaseOrderRef;   // Pedido de compra (xPed) - RF07
    private List<InvoiceItem> extractedItems;

    @Data
    public static class InvoiceItem {
        private String description;        // xProd
        private BigDecimal quantity;       // qCom
        private BigDecimal unitPrice;      // vUnCom
        private String ncmCode;            // NCM
        private String purchaseOrderRef;   // xPed (item)
        private String orderItemRef;       // nItemPed
    }
}
