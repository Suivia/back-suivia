-- Base Migration (Fase 1 e Fase 2 Completa)

CREATE TABLE IF NOT EXISTS invoice_staging (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(100) NOT NULL,
    source VARCHAR(50),
    file_url VARCHAR(500),
    file_type VARCHAR(20),
    supplier_cnpj VARCHAR(18),
    invoice_number VARCHAR(100),
    invoice_key VARCHAR(44) UNIQUE,
    total_amount DECIMAL(15,2),
    extracted_items_json TEXT,
    sefaz_status VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoice_match (
    id VARCHAR(36) PRIMARY KEY,
    staging_id VARCHAR(36) REFERENCES invoice_staging(id),
    purchase_order_id VARCHAR(100),
    match_score INTEGER,
    header_match JSONB,
    items_match JSONB,
    tax_match JSONB,
    status VARCHAR(50) NOT NULL,
    approved_by VARCHAR(100),
    approval_note TEXT,
    approved_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoice_divergences (
    id VARCHAR(36) PRIMARY KEY,
    match_id VARCHAR(36) REFERENCES invoice_match(id),
    divergence_type VARCHAR(50),
    severity VARCHAR(50),
    field_name VARCHAR(100),
    expected_value VARCHAR(255),
    actual_value VARCHAR(255),
    difference_value DECIMAL(15,4),
    tolerance_applied BOOLEAN DEFAULT FALSE,
    resolution VARCHAR(50)
);
