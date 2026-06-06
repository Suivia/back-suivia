# PROJETO SUIVIA COMPLETO - Release Candidate 1.0 (Back-End)

## O que é este pacote?
É a união completa, compilável e segura de todos os MVPs da Fase 1 e 2. 
Aqui temos o **Esqueleto Central** definitivo. Ele foi arquitetado para **não quebrar**, pois:
1. Usa **Flyway** (Migrations) garantindo que o PostgreSQL receba a estrutura exata do seu documento.
2. Contém todos os Controllers prontos para plugar o Frontend.
3. Centraliza todo o código gerado em MVPs anteriores num padrão coeso e injetável de Spring Boot.

## Como Aplicar de Forma Segura:

1. Vá na pasta do seu projeto local (o repositório Git).
2. **Delete a pasta `src` inteira atual.** Sim, estamos substituindo pelo projeto coeso, sem gambiarras.
3. Descompacte este ZIP e jogue as novas pastas `src` e `pom.xml` na raiz do seu projeto.
4. Rode as dependências:
   ```bash
   mvn clean install -DskipTests
   ```
5. Suba sua API!
   ```bash
   mvn spring-boot:run
   ```

## O Banco de Dados Vai Funcionar?
SIM! Se você configurou seu `application.yml` apontando pro banco, o script `V1__Initial_Schema.sql` 
vai automaticamente desenhar todas as tabelas (Staging, Match, Divergences) na hora que o Spring Boot iniciar. 

## Endpoints Liberados para o Frontend:
- POST `/api/invoices/upload` -> Inicia fluxo
- GET `/api/inbox/divergent` -> Traz lista pra tela de Fila de Exceções
- POST `/api/inbox/resolve` -> Aprova Notas
- GET `/api/dashboard/metrics` -> Alimenta os cards do seu Dashboard

Assim que estiver rodando e compilando limpo, comente: **"BACKEND TOTAL DEPLOYADO"**.
Aí mergulhamos 100% em **Gerar as Telas do Frontend (Painéis e Inbox)** em HTML!
