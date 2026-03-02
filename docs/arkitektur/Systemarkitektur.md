# Systemarkitektur

Oversikt over sokos-ske-krav sin plass i NAVs tekniske landskap og samspillet mellom de ulike komponentene.

## Systemoversikt

```mermaid
C4Context
    title Systemkontekst – sokos-ske-krav

    Person(saksbehandler, "Saksbehandler / Drift", "Bruker rapportgrensesnittet for å følge opp krav og feil")

    System(app, "sokos-ske-krav", "Leser kravfiler fra SKE via SFTP, validerer og sender krav til SKEs REST-API via Maskinporten")

    System_Ext(sftp, "SKE SFTP-server", "Filbasert grensesnitt. Leverer kravfiler i /inbound og mottar behandlede filer i /outbound")
    System_Ext(ske_api, "SKE Innkrevings-API", "REST-API for oppretting, endring, stopp og statussjekk av innkrevingsoppdrag")
    System_Ext(maskinporten, "Maskinporten", "Utsteder JWT-tokens for maskin-til-maskin autentisering mot SKE")
    System_Ext(slack, "Slack", "Mottar varsler om feil og avvik")

    SystemDb(postgres, "PostgreSQL", "Lagrer krav, feilmeldinger og filvalideringsfeil")

    Rel(saksbehandler, app, "Bruker rapportwebgrensesnitt (HTTP)")
    Rel(app, sftp, "Henter kravfiler (SFTP/JSch)")
    Rel(app, sftp, "Flytter filer til /outbound eller /feilfiler")
    Rel(app, ske_api, "Sender krav og henter status (HTTPS/REST)")
    Rel(app, maskinporten, "Henter OAuth2-token (JWT-bearer)")
    Rel(app, postgres, "Lagrer og henter krav (JDBC/HikariCP)")
    Rel(app, slack, "Sender feilvarsler (Webhook)")
```

## Komponentdiagram

```mermaid
C4Component
    title Komponentoversikt – sokos-ske-krav

    Container_Boundary(app, "sokos-ske-krav (Ktor / Netty, port 8080)") {

        Component(scheduler, "Scheduler", "Kotlin Coroutines", "Kjører handleNewKrav periodisk (konfigurerbart intervall) og checkKravDateForAlert hver 24. time")

        Component(skeService, "SkeService", "Service", "Koordinerer hele behandlingsløpet: henting av filer, sending av krav og resending")

        Component(ftpService, "FtpService", "Service", "Kobler til SFTP-server, laster ned, validerer og flytter filer")
        Component(fileValidator, "FileValidator", "Validation", "Validerer filstruktur: header/footer-kontroll, antall og sum")
        Component(lineValidator, "LineValidator", "Validation", "Validerer enkeltlinjer mot forretningsregler og duplikatsjekk")

        Component(opprettKravService, "OpprettKravService", "Service", "Sender NYTT_KRAV til SKE (POST innkrevingsoppdrag)")
        Component(endreKravService, "EndreKravService", "Service", "Sender ENDRING_RENTE og ENDRING_HOVEDSTOL til SKE (PUT)")
        Component(stoppKravService, "StoppKravService", "Service", "Sender STOPP_KRAV til SKE (POST avskriving)")
        Component(statusService, "StatusService", "Service", "Henter mottaksstatus og valideringsfeil fra SKE")

        Component(databaseService, "DatabaseService", "Service", "Abstraksjonssjikt mot databasen")
        Component(kravRepository, "KravRepository", "Repository", "CRUD for krav-tabellen")
        Component(feilmeldingRepo, "FeilmeldingRepository", "Repository", "CRUD for feilmelding-tabellen")
        Component(filvalRepo, "FilValideringsfeilRepository", "Repository", "CRUD for filvalideringsfeil-tabellen")

        Component(skeClient, "SkeClient", "HTTP Client (Ktor)", "Kaller SKEs REST-endepunkter med Maskinporten-token og circuit breaker")
        Component(circuitBreaker, "CircuitBreakerManager", "Resilience4j", "Beskytter mot kaskadefeil ved SKE-nedetid")
        Component(maskinporten, "MaskinportenAccessTokenProvider", "Security", "Cacher og fornyer JWT-tokens fra Maskinporten")

        Component(slackClient, "SlackService / SlackClient", "Client", "Samler opp og sender feilmeldinger til Slack")

        Component(rapportService, "RapportService", "Service (Frontend)", "Henter data for avstemming- og resendingsrapporter")
        Component(routing, "RoutingConfig", "Ktor Routing", "Eksponerer /api/hentNye, /api/hentStatus og /rapporter/*")
    }

    ContainerDb(postgres, "PostgreSQL", "Database", "krav, feilmelding, filvalideringsfeil")
    Container_Ext(sftp, "SKE SFTP", "Ekstern server")
    Container_Ext(skeApi, "SKE REST-API", "Ekstern tjeneste")
    Container_Ext(maskinportenExt, "Maskinporten", "Ekstern IDP")
    Container_Ext(slackExt, "Slack Webhook", "Ekstern tjeneste")

    Rel(scheduler, skeService, "Trigger periodisk")
    Rel(routing, skeService, "Trigger manuelt via HTTP")
    Rel(routing, statusService, "Trigger manuelt via HTTP")
    Rel(routing, rapportService, "Henter rapportdata")

    Rel(skeService, ftpService, "Henter validerte filer")
    Rel(skeService, databaseService, "Lagrer og henter krav")
    Rel(skeService, opprettKravService, "Delegerer NYTT_KRAV")
    Rel(skeService, endreKravService, "Delegerer ENDRINGER")
    Rel(skeService, stoppKravService, "Delegerer STOPP_KRAV")
    Rel(skeService, statusService, "Trigger statussjekk")
    Rel(skeService, slackClient, "Sender feilmeldinger")

    Rel(ftpService, fileValidator, "Validerer fil")
    Rel(ftpService, sftp, "SFTP")

    Rel(opprettKravService, skeClient, "HTTP POST")
    Rel(endreKravService, skeClient, "HTTP PUT")
    Rel(stoppKravService, skeClient, "HTTP POST")
    Rel(statusService, skeClient, "HTTP GET")

    Rel(skeClient, circuitBreaker, "Alle kall beskyttes")
    Rel(skeClient, maskinporten, "Henter token")
    Rel(skeClient, skeApi, "HTTPS")
    Rel(maskinporten, maskinportenExt, "HTTPS")

    Rel(databaseService, kravRepository, "")
    Rel(databaseService, feilmeldingRepo, "")
    Rel(databaseService, filvalRepo, "")
    Rel(kravRepository, postgres, "JDBC")
    Rel(feilmeldingRepo, postgres, "JDBC")
    Rel(filvalRepo, postgres, "JDBC")

    Rel(slackClient, slackExt, "HTTPS Webhook")
```

## Infrastruktur og deploymentmiljø

| Egenskap           | Verdi                                                                    |
|--------------------|--------------------------------------------------------------------------|
| Plattform          | NAIS (Kubernetes) på `fss`-sonen                                         |
| Miljøer            | `dev-fss`, `prod-fss`                                                    |
| Applikasjonsserver | Ktor med Netty, port `8080`                                              |
| Autentisering inn  | AzureAD JWT (API-endepunkter) + Basic Auth (webgrensesnitt /rapporter/*) |
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

