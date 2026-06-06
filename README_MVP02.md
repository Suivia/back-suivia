# SUIVIA Backend — MVP 02: Parser XML NFe + Worker SQS

## O que esse módulo implementa

Completa o ciclo assíncrono iniciado no MVP01:

```
SQS → Worker → Lê S3 → Parser XML NFe → Atualiza Staging
```

## Requisitos atendidos

- RF02 — Processamento em Lote (Worker SQS)
- RF04 — Parser XML Nativo (NFe/CTe)
- RF07 — Identificação Automática do Pedido (xPed)

## Arquivos novos/alterados

| Arquivo | O que faz |
|---------|-----------|
| S3FileReaderService | Lê arquivo real do S3 usando GetObjectRequest |
| NFeXmlParserService | Parser robusto das tags NFe/CTe |
| NFeExtractedData    | DTO com todos os campos extraídos |
| InvoiceParserService | Orquestra S3 + Parser + Banco |
| InvoiceProcessingListener | Worker SQS corrigido |
| InvoiceStaging | Entidade atualizada com extracted_items_json |

## Campos extraídos do XML

| Tag XML | Campo no banco |
|---------|----------------|
| emit/CNPJ | supplier_cnpj |
| nNF | invoice_number |
| chNFe | invoice_key |
| dhEmi | emission_date |
| vNF | total_amount |
| xPed | purchase_order_ref |
| det | extracted_items_json |

## Como aplicar

```bash
unzip suivia-mvp02-correto.zip -d .
git add .
git commit -m "feat: MVP02 - parser XML NFe + worker SQS corrigido (RF02, RF04, RF07)"
git push origin main
```

## Fluxo completo após MVP01 + MVP02

1. POST /api/invoices/upload  → salva no S3, cria staging (status: processing)
2. Evento publicado no SQS
3. Worker escuta SQS
4. Lê XML do S3 (GetObjectRequest)
5. Parseia tags NFe
6. Atualiza staging (status: extracted)

## Próximo — MVP03: Validação SEFAZ (RF05)

Consulta a chave de acesso (44 dígitos) na API da SEFAZ.
Status retornado: Autorizada / Cancelada / Denegada.
