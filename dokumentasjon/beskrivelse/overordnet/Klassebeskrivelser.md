# Klassebeskrivelser

Oversikt over de viktigste klassene og deres ansvar i sokos-ske-krav. For detaljert dokumentasjon av hver serviceklasse, se [Serviceklasser](../detaljert/Serviceklasser.md).
## Toppnivå
### [`Application.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/Application.kt)
Startpunktet for applikasjonen. Starter en Netty-basert Ktor-server på port 8080, setter opp alle plugins og konfigurasjoner, og starter de periodiske coroutine-jobbene:
- [`handleNewKrav`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt) – kjøres med konfigurerbart intervall (typisk ~5 timer)
- [`checkForStangendeKrav`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt) – kjøres hvert 24. time
Dersom en scheduled jobb feiler med et ukjent unntak venter den halve intervallet før neste forsøk.
---
## Pakke: `service`
### [`SkeService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt)
Hoved-orkestratoren for hele behandlingsløpet. Koordinerer alle andre services og er ansvarlig for den overordnede flyten i [`handleNewKrav()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt):
1. Kaller [`StatusService.getMottaksStatus()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StatusService.kt) for å oppdatere status på tidligere sendte krav
2. Henter og resender krav som skal resendes
3. Henter validerte filer fra [`FtpService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/FtpService.kt)
4. Kaller [`LineValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidator.kt) for linjevalidering
5. Lagrer krav i databasen og flytter fil til `/outbound`
6. Henter SKE-kravidentifikator for endringer og stopp
7. Sender alle usente krav via [`OpprettKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/OpprettKravService.kt), [`EndreKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/EndreKravService.kt) og [`StoppKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StoppKravService.kt)
8. Sender feilmeldinger til Slack
Har en intern `haltRun`-bryter som aktiveres dersom en fil inneholder 1000 eller flere krav – for å hindre overbelastning.
### [`FtpService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/FtpService.kt)
Håndterer all kommunikasjon med SFTP-serveren:
- Lister filer i en mappe
- Laster ned filer som lister med tekstlinjer
- Kaller [`FileValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/FileValidator.kt) på hvert nedlastet fil
- Flytter filer mellom mappene `/inbound`, `/outbound` og `/inbound/feilfiler`
Opererer med tre mappe-enums: `INBOUND`, `OUTBOUND` og `FAILED`.
### [`OpprettKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/OpprettKravService.kt)
Sender krav av typen `NYTT_KRAV` til SKE. Itererer over listen, bygger opp [`OpprettInnkrevingsoppdragRequest`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/requests/OpprettInnkrevingsoppdragRequest.kt) via [`createOpprettKravRequest()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/util/CreateRequests.kt) og kaller [`SkeClient.opprettKrav()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt). Bryter sending dersom Circuit Breaker er åpen. Oppdaterer databasen med resultatene etter sending.
### [`EndreKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/EndreKravService.kt)
Sender krav av typen `ENDRING_RENTE` og `ENDRING_HOVEDSTOL`. Fordi applikasjonen alltid sender til begge endepunkter for hver endring, grupperer tjenesten krav etter `kravidentifikatorSKE + saksnummerNAV` og sender én forespørsel per endepunkt per gruppe. Dersom de to svarene har ulike statuser, konformeres statusen etter prioritetsrekkefølge (se [`determineNewStatus()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/EndreKravService.kt)).
### [`StoppKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StoppKravService.kt)
Sender krav av typen `STOPP_KRAV` til SKE via [`SkeClient.stoppKrav()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt). Logikken tilsvarer [`OpprettKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/OpprettKravService.kt).
### [`StatusService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StatusService.kt)
Sjekker mottaksstatus for alle krav som er i tilstandene `KRAV_SENDT` eller `MOTTATT_UNDER_BEHANDLING`. For hvert krav kalles `GET /mottaksstatus` på SKE. Dersom SKE returnerer valideringsfeil hentes detaljene via `GET /valideringsfeil` og lagres i `feilmelding`-tabellen.
### [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt)
Abstraksjonslag mellom services og repositories. Delegerer alle databaseoperasjoner til [`KravRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/KravRepository.kt), [`FeilmeldingRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FeilmeldingRepository.kt) og [`FilValideringsfeilRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FilValideringsfeilRepository.kt), og håndterer oppretting og avslutning av databaseforbindelser via HikariCP.
### [`RapportService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt)    
Produserer data til webgrensesnittet for avstemmings- og resendingsrapporter. Merket med `@Frontend`-annotasjon for å indikere at den kun skal brukes fra routing-laget. Henter krav med feilstatuser og mapper dem til [`RapportObjekt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt) med tilhørende feilmeldinger.

---
## Pakke: `validation`
### [`FileValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/FileValidator.kt)
Validerer en hel kravfil etter nedlasting fra SFTP. Parser header, kravlinjer og footer, og sjekker at antall og sum stemmer overens. Bruker [`SlackService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SlackService.kt) for å sende feilvarsel. Returnerer [`ValidationResult.Success`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/ValidationResult.kt) eller [`ValidationResult.Error`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/ValidationResult.kt).
### [`LineValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidator.kt)
Itererer over alle kravlinjer i en fil og kaller [`LineValidationRules.runValidation()`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidationRules.kt) på hver linje. Linjer som feiler validering lagres i `filvalideringsfeil`-tabellen med status `VALIDERINGSFEIL_AV_LINJE_I_FIL`. Gyldige linjer settes til status `KRAV_IKKE_SENDT`.
### [`LineValidationRules`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidationRules.kt)
Inneholder alle forretningsreglene for linjevalidering (se [detaljert dokumentasjon](../detaljert/Validering.md)). Implementert som et statisk objekt med én offentlig funksjon `runValidation()`.

---
## Pakke: `client`
### [`SkeClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt)
HTTP-klienten mot SKEs REST-API. Alle kall går gjennom [`CircuitBreakerManager`](../../../src/main/kotlin/no/nav/sokos/ske/krav/config/CircuitBreakerManager.kt) og legger ved Maskinporten-token i Authorization-headeren. Støtter:
- `opprettKrav` – POST til `/innkrevingsoppdrag`
- `endreRenter` – PUT til `/innkrevingsoppdrag/{id}/renter`
- `endreHovedstol` – PUT til `/innkrevingsoppdrag/{id}/hovedstol`
- `stoppKrav` – POST til `/innkrevingsoppdrag/avskriving`
- `getMottaksStatus` – GET `/innkrevingsoppdrag/{id}/mottaksstatus`
- `getValideringsfeil` – GET `/innkrevingsoppdrag/{id}/valideringsfeil`
- `getSkeKravidentifikator` – GET `/innkrevingsoppdrag/{ref}/avstemming`
### [`SlackService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SlackService.kt)
Samler opp feilmeldinger i minnet gruppert per fil og feiltype, og sender dem samlet til Slack ved `sendErrors()`. Dersom mer enn 5 feil av samme type oppstår for et gitt krav, konsolideres de til én oppsummerende melding fordi slack har begrensning på antall like meldinger og hvis det er over n like meldinger vil ikke slack sende dem. 
### [`SlackClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SlackClient.kt)
Teknisk HTTP-klient mot Slack Webhook-endepunktet. Bygger opp Slack-meldingsformatet og sender via POST.
### [`MaskinportenAccessTokenProvider`](../../../src/main/kotlin/no/nav/sokos/ske/krav/security/MaskinportenAccessTokenProvider.kt)
Håndterer OAuth2 token-flyten mot Maskinporten. Cacher access-token i minnet og fornyer det automatisk 60 sekunder før det utløper. Bruker Mutex for trådsikker tilgang.

---
## Pakke: `config`
### [`CircuitBreakerManager`](../../../src/main/kotlin/no/nav/sokos/ske/krav/config/CircuitBreakerManager.kt)
Singleton som konfigurerer og holder Resilience4j CircuitBreaker-instansen. Standardinnstillinger:
- Sliding window: 1 kall
- Failure rate threshold: 100 %
- Wait duration i OPEN-tilstand: konfigurerbart (default 4 timer)
- Automatisk overgang fra OPEN til HALF_OPEN
### [`SftpConfig`](../../../src/main/kotlin/no/nav/sokos/ske/krav/config/SftpConfig.kt)
Konfigurerer og håndterer JSch SFTP-sesjoner med RSA-nøkkelautentisering. Tilbyr en `channel { }` høyere-ordens funksjon som sikrer at sesjon og kanal alltid lukkes etter bruk.
### [`PostgresConfig`](../../../src/main/kotlin/no/nav/sokos/ske/krav/config/PostgresConfig.kt)
Setter opp HikariCP connection pool mot PostgreSQL og kjører Flyway-migrasjoner ved oppstart (ikke i lokalt miljø).

---
## Pakke: `repository`
### [`KravRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/KravRepository.kt)
SQL-operasjoner mot `krav`-tabellen: insert av nye krav, oppdatering av status og kravidentifikator, henting av krav for statussjekk, resending og avstemming.
### [`FeilmeldingRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FeilmeldingRepository.kt)
SQL-operasjoner mot `feilmelding`-tabellen: insert og henting av feilmeldinger knyttet til et krav-ID.
### [`FilValideringsfeilRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FilValideringsfeilRepository.kt)
SQL-operasjoner mot `filvalideringsfeil`-tabellen: insert av fil- og linjevalideringsfeil .

---
## Pakke: `util`
### [`CreateRequests.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/util/CreateRequests.kt)
Funksjoner som bygger opp request-objekter til SKE basert på [`Krav`](../../../src/main/kotlin/no/nav/sokos/ske/krav/domain/Krav.kt)-domenemodellen:
- `createOpprettKravRequest()` – bygger [`OpprettInnkrevingsoppdragRequest`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/requests/OpprettInnkrevingsoppdragRequest.kt)
- `createEndreRenteRequest()` – bygger [`EndreRenteBeloepRequest`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/requests/EndringRequest.kt)
- `createEndreHovedstolRequest()` – bygger [`NyHovedStolRequest`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/requests/EndringRequest.kt)
- `createStoppKravRequest()` – bygger [`AvskrivingRequest`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/requests/AvskrivingRequest.kt)
Inneholder også hjelpefunksjoner for å klassifisere kravlinjer: `isOpprettKrav()`, `isEndring()`, `isStopp()`.
### [`RequestResult.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/util/RequestResult.kt)
Dataklasse som holder resultatet av et enkelt kall mot SKE (respons, krav, request-payload, kravidentifikator og status). Inneholder `defineStatus()`-funksjonen som mapper HTTP-statuskoder og SKE-spesifikke feiltyper til intern [`Status`](../../../src/main/kotlin/no/nav/sokos/ske/krav/domain/Status.kt)-enum.
