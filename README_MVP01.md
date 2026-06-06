# SUIVIA Backend — MVP 01: Upload de Nota Fiscal

## O que esse módulo faz

Implementa o primeiro fluxo funcional completo do SUIVIA (RF01 — Captura Multicanal):

1. Recebe arquivo (XML, PDF, JPG, PNG) via API REST
2. Faz upload no AWS S3 (bucket: suivia-invoices-raw-dev-unique123)
3. Cria registro na tabela `invoice_staging` no PostgreSQL (status: processing)
4. Publica evento na fila AWS SQS (seguia-match-queue)
5. Retorna resposta imediata ao frontend

## Endpoints disponíveis

| Método | Rota                        | Descrição                    |
|--------|-----------------------------|------------------------------|
| POST   | /api/invoices/upload        | Upload de uma nota fiscal    |
| POST   | /api/invoices/upload/batch  | Upload de múltiplas notas    |
| GET    | /api/invoices/health        | Health check do módulo       |

## Exemplo de chamada (cURL)

```bash
curl -X POST http://localhost:8080/api/invoices/upload \
  -F "file=@nota_fiscal.xml" \
  -F "batchId=LOTE-001"
```

## Resposta esperada

```json
{
  "invoiceId": "uuid-gerado",
  "batchId": "LOTE-001",
  "status": "processing",
  "fileUrl": "s3://suivia-invoices-raw-dev-unique123/invoices/...",
  "message": "Nota fiscal recebida com sucesso. Processamento iniciado."
}
```

## Configuração necessária

No `application.yml`, configure:
- `DB_HOST` → endpoint do RDS criado pelo Terraform
- `DB_USER` → usuario do banco (padrão: postgres)
- `DB_PASS` → senha do banco

## Próximo módulo

MVP 02 — Parser XML (NFe/CTe) + Extração dos dados da nota
