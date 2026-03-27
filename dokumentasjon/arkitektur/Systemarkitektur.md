# Systemarkitektur

Oversikt over sokos-ske-krav sin plass i NAVs tekniske landskap og samspillet mellom de ulike komponentene.

## Systemoversikt

```mermaid
%%{init: {"theme": "dark", "themeVariables": {"edgeLabelBackground": "#1a1a2e", "tertiaryTextColor": "#ffffff"}}}%%
flowchart TD
    saksbehandler["👤 Utvikler / fagressurs\nBruker rapportgrensesnittet\nfor å følge opp krav og feil"]
    app["sokos-ske-krav\nLeser kravfiler fra SKE via SFTP,\nvaliderer og sender krav til SKEs\nREST-API via Maskinporten"]
    sftp["⬡ OS/Z SFTP-server\nFilbasert grensesnitt.\nLeverer kravfiler i /inbound"]
    ske_api["⬡ SKE Innkrevings-API\nREST-API for oppretting, endring,\nstopp og statussjekk"]
    maskinporten["⬡ Maskinporten\nUtsteder JWT-tokens for\nmaskin-til-maskin autentisering"]
    slack["⬡ Slack\nMottar varsler om feil og avvik"]
    postgres[("PostgreSQL\nLagrer krav, feilmeldinger\nog filvalideringsfeil")]

    saksbehandler -->|"Bruker rapportwebgrensesnitt (HTTP)"| app
    app -->|"Henter kravfiler (SFTP/JSch)"| sftp
    app -->|"Flytter filer til /outbound eller /feilfiler"| sftp
    app -->|"Sender krav og henter status (HTTPS/REST)"| ske_api
    app -->|"Henter OAuth2-token (JWT-bearer)"| maskinporten
    app -->|"Lagrer og henter krav (JDBC/HikariCP)"| postgres
    app -->|"Sender feilvarsler (Webhook)"| slack

    classDef person fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef system fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef external fill:#666,stroke:#444,color:#ffffff
    classDef db fill:#1168bd,stroke:#0b4884,color:#ffffff

    class saksbehandler person
    class app system
    class sftp,ske_api,maskinporten,slack external
    class postgres db
```

## Komponentdiagram

```mermaid
%%{init: {"theme": "dark", "themeVariables": {"edgeLabelBackground": "#1a1a2e", "tertiaryTextColor": "#ffffff"}}}%%
flowchart TD
    subgraph app["sokos-ske-krav (Ktor / Netty, port 8080)"]
        scheduler["Scheduler\nKotlin Coroutines"]
        skeService["SkeService\nKoordinerer behandlingsløpet"]
        ftpService["FtpService"]
        fileValidator["FileValidator\n(injisert i FtpService)"]
        lineValidator["LineValidator\n(brukt i SkeService.processFile)"]
        opprettKravService["OpprettKravService"]
        endreKravService["EndreKravService"]
        stoppKravService["StoppKravService"]
        statusService["StatusService"]
        databaseService["DatabaseService"]
        kravRepository["KravRepository"]
        feilmeldingRepo["FeilmeldingRepository"]
        filvalRepo["FilValideringsfeilRepository"]
        skeClient["SkeClient\nKtor HTTP Client"]
        circuitBreaker["CircuitBreakerManager\nResilience4j"]
        maskinportenComp["MaskinportenAccessTokenProvider"]
        slackClient["SlackService / SlackClient"]
        rapportService["RapportService"]
        routing["RoutingConfig\nKtor Routing"]
    end

    postgres[("PostgreSQL\nkrav, feilmelding,\nfilvalideringsfeil")]
    sftp["OS/Z SFTP\nEkstern server"]
    skeApi["SKE REST-API\nEkstern tjeneste"]
    maskinportenExt["Maskinporten\nEkstern IDP"]
    slackExt["Slack Webhook\nEkstern tjeneste"]

    scheduler -->|"Trigger periodisk"| skeService
    routing -->|"Henter rapportdata"| rapportService

    skeService -->|"getValidatedFiles()"| ftpService
    ftpService -->|"validateFile()"| fileValidator
    ftpService -->|"SFTP"| sftp

    skeService -->|"validateNewLines()\netter filvalidering"| lineValidator
    lineValidator -->|"saveLineValidationError()"| databaseService

    skeService -->|"Lagrer og henter krav"| databaseService
    skeService -->|"Delegerer NYTT_KRAV"| opprettKravService
    skeService -->|"Delegerer ENDRINGER"| endreKravService
    skeService -->|"Delegerer STOPP_KRAV"| stoppKravService
    skeService -->|"Trigger statussjekk"| statusService
    skeService -->|"Sender feilmeldinger"| slackClient


    opprettKravService -->|"HTTP POST"| skeClient
    endreKravService -->|"HTTP PUT"| skeClient
    stoppKravService -->|"HTTP POST"| skeClient
    statusService -->|"HTTP GET"| skeClient

    skeClient -->|"Beskyttes"| circuitBreaker
    skeClient -->|"Henter token"| maskinportenComp
    skeClient -->|"HTTPS"| skeApi
    maskinportenComp -->|"HTTPS"| maskinportenExt

    databaseService --> kravRepository
    databaseService --> feilmeldingRepo
    databaseService --> filvalRepo
    kravRepository -->|"JDBC"| postgres
    feilmeldingRepo -->|"JDBC"| postgres
    filvalRepo -->|"JDBC"| postgres

    slackClient -->|"HTTPS Webhook"| slackExt

    classDef internal fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef external fill:#666,stroke:#444,color:#ffffff
    classDef db fill:#1168bd,stroke:#0b4884,color:#ffffff

    class scheduler,skeService,ftpService,fileValidator,lineValidator,opprettKravService,endreKravService,stoppKravService,statusService,databaseService,kravRepository,feilmeldingRepo,filvalRepo,skeClient,circuitBreaker,maskinportenComp,slackClient,rapportService,routing internal
    class sftp,skeApi,maskinportenExt,slackExt external
    class postgres db
```

## Infrastruktur og deploymentmiljø

| Egenskap           | Verdi                                                                    |
|--------------------|--------------------------------------------------------------------------|
| Plattform          | NAIS (Kubernetes) på `fss`-sonen                                         |
| Miljøer            | `dev-fss`, `prod-fss`                                                    |
| Applikasjonsserver | Ktor med Netty, port `8080`                                              |
| Autentisering inn  | Basic Auth (webgrensesnitt /rapporter/*)                                 |
| Autentisering ut   | Maskinporten (JWT-bearer)                                                |
| Database           | PostgreSQL (HikariCP connection pool, Flyway migrasjoner)                |
| Filoverføring      | SFTP via JSch med RSA-nøkkelautentisering                                |
| Feilhåndtering     | Resilience4j Circuit Breaker + Slack-varsler                             |
| Observability      | Prometheus-metrikker, Logback (JSON-logging), team-logs-marker           |

## Teknologistack

| Kategori           | Teknologi                    |
|--------------------|------------------------------|
| Språk              | Kotlin (JVM)                 |
| Rammeverk          | Ktor (server og HTTP-klient) |
| Database-migrering | Flyway                       |
| Connection pool    | HikariCP                     |
| SFTP-klient        | JSch                         |
| Autentisering      | Maskinporten (JWT)           |
| Resilience         | Resilience4j CircuitBreaker  |
| Metrikker          | Micrometer / Prometheus      |
| Logging            | KotlinLogging / Logback      |
| Konfigurasjon      | Konfig (natpryce)            |
| Serialisering      | kotlinx.serialization        |
