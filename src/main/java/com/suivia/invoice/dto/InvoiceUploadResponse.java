package com.suivia.invoice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceUploadResponse {
    private String invoiceId;
    private String batchId;
    private String status;
    private String fileUrl;
    private String message;
}
