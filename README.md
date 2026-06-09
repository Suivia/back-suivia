# SUIVIA - Backend Enterprise (Spring Boot)

Baseado nos documentos de arquitetura e requisitos funcionais.
Este é o core para subir toda a engine de processamento, match e persistência.

## Tecnologias Usadas:
- Java 17 + Spring Boot 3
- PostgreSQL (Dados e JSONB para staging)
- Flyway (Migrations)
- Lombok
- Estrutura pronta pra mensageria assíncrona (EventDriven)

## Como rodar local:

1. **Suba o banco de dados via Docker:**
```bash
docker-compose up -d
```

2. **Inicie a aplicação Spring Boot:**
```bash
./mvnw spring-boot:run
```

A aplicação fará as migrations no PostgreSQL automaticamente e ficará na porta `8080`.
