# SUIVIA — Backend (AWS Lambda, Java 21)

Backend serverless do SUIVIA: 14 funções Lambda em **Java 21 + AWS SDK v2**, empacotadas
num único fat-jar (multi-handler). A infra que as hospeda fica em `../suivia-infra` (Terraform).

> Convertido do protótipo Python (SAM). Hoje o Java é a única implementação;
> a infra é provisionada por Terraform, não por SAM.

## Estrutura
```
src/main/java/com/suivia/lambda/
├── shared/     Json, Aws (clients SDK v2), Dynamo, Api, Audit, Http, Similarity, Val
└── handler/    14 handlers (1 classe por Lambda)
```

| Handler | Gatilho | Função |
|---|---|---|
| `ClassifierHandler` | EventBridge (S3) | RF01 cria staging e roteia (XML→parser, resto→Textract) |
| `XmlParserHandler` | invoke | RF04 parser NF-e nativo, RN02 dedup por chave |
| `TextractHandler` | invoke | RF03 OCR PDF/imagem (AnalyzeExpense + bounding boxes) |
| `SefazValidatorHandler` | invoke | RF05 consulta SEFAZ (cache 24h) → fila |
| `MatchEngineHandler` | SQS | RF08 match 3-way (CNPJ 25/valor 25/itens 25/imposto 15/data 10) |
| `InboxHandler` | API GET | `/invoices/inbox`, `/invoices/{id}` |
| `ApprovalHandler` | API POST | `/invoices/{id}/approve`, `/reject` (RN06) |
| `UploadPresignHandler` | API POST | `/invoices/presign` (upload direto S3) |
| `ExceptionsHandler` | API | `/exceptions`, `/exceptions/bulk-approve` |
| `BatchesHandler` | API GET | `/batches`, `/batches/{id}` |
| `DashboardHandler` | API GET | `/dashboard` (RF12) |
| `ToleranceHandler` | API GET/POST | `/settings/tolerances` (RF09/RF15) |
| `AuditHandler` | API GET | `/audit` (RF14) |
| `WsConnectHandler` | WebSocket | `$connect` |

## Build
```bash
mvn package        # gera target/suivia-lambdas.jar (fat-jar usado pelo Terraform)
mvn test           # testes
```

Runtime das Lambdas: `java21`. O Terraform aponta cada função para a classe via
handler `com.suivia.lambda.handler.<Classe>`, todas no mesmo jar.

## Configuração (env vars, injetadas pelo Terraform)
`STAGING_TABLE`, `MATCH_TABLE`, `DIVERG_TABLE`, `TOLERANCE_TABLE`, `AUDIT_TABLE`,
`MATCH_QUEUE_URL`, `RAW_BUCKET`, `ERP_API_URL`, `WS_API_ENDPOINT`,
`XML_PARSER_FUNCTION`, `TEXTRACT_FUNCTION`, `SEFAZ_FUNCTION`, `SEFAZ_CACHE_TTL`.

## Limitações conhecidas (herdadas do protótipo)
- `SefazValidatorHandler`: sem certificado A1/A3 o handshake SEFAZ falha e cai em `pending` (instale o cert via Secrets Manager para produção).
- `WsConnectHandler`: stub (não persiste conexões).
- Aprendizado de tolerância (RN07): no-op (planejado via DynamoDB Streams).
- `nota.xml` é uma NF-e de exemplo para testar o parser.
