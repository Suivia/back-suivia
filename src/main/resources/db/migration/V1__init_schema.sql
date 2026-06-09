
-- Tabela de Staging Temporária (Reflete DynamoDB Staging)
CREATE TABLE invoice_staging (
    id UUID PRIMARY KEY,
    batch_id VARCHAR(100),
    source VARCHAR(50),
    file_url TEXT,
    file_type VARCHAR(10),
    raw_textract_json JSONB,
    supplier_cnpj VARCHAR(18),
    invoice_number VARCHAR(50),
    invoice_key VARCHAR(44) UNIQUE,
    total_amount NUMERIC(15,2),
    extracted_items JSONB,
    sefaz_status VARCHAR(50),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Tabela Core de Conciliação
CREATE TABLE invoice_match (
    id UUID PRIMARY KEY,
    staging_id UUID REFERENCES invoice_staging(id),
    purchase_order_id VARCHAR(100),
    match_score INT,
    header_match JSONB,
    items_match JSONB,
    tax_match JSONB,
    status VARCHAR(50),
    approved_by VARCHAR(100),
    approval_note TEXT,
    approved_at TIMESTAMP
);

-- Tabela de Divergências Encontradas no Motor
CREATE TABLE invoice_divergences (
    id UUID PRIMARY KEY,
    invoice_id UUID REFERENCES invoice_match(id),
    divergence_type VARCHAR(50),
    severity VARCHAR(20),
    field_name VARCHAR(100),
    expected_value TEXT,
    actual_value TEXT,
    difference NUMERIC(15,2),
    tolerance_applied BOOLEAN DEFAULT FALSE,
    tolerance_rule_id UUID,
    resolution VARCHAR(50) DEFAULT 'pending'
);

CREATE INDEX idx_staging_cnpj ON invoice_staging(supplier_cnpj);
CREATE INDEX idx_staging_batch ON invoice_staging(batch_id);
