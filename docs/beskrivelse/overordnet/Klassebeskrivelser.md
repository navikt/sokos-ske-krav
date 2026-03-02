# Klassebeskrivelser
Oversikt over de viktigste klassene og deres ansvar i sokos-ske-krav.
## Toppnivå
### `Application.kt`
Startpunktet for applikasjonen. Starter en Netty-basert Ktor-server på port 8080, setter opp alle plugins og konfigurasjoner, og starter de periodiske coroutine-jobbene:
- `handleNewKrav` – kjøres med konfigurerbart intervall (typisk ~5 timer)
- `checkKravDateForAlert` – kjøres hvert 24. time
Dersom en scheduled jobb feiler med et ukjent unntak venter den halve intervallet før neste forsøk.
---
## Pakke: `service`
### `SkeService`
Hoved-orkestratoren for hele behandlingsløpet. Koordinerer alle andre services og er ansvarlig for den overordnede flyten i `handleNewKrav()`:
1. Kaller `StatusService.getMottaksStatus()` for å oppdatere status på tidligere sendte krav
2. Henter og resender krav som skal resendes
3. Henter validerte filer fra `FtpService`
4. Kaller `LineValidator` for linjevalidering
5. Lagrer krav i databasen og flytter fil til `/outbound`
6. Henter SKE-kravidentifikator for endringer og stopp
7. Sender alle usente krav via `OpprettKravService`, `EndreKravService` og `StoppKravService`
8. Sender feilmeldinger til Slack
Har en intern `haltRun`-bryter som aktiveres dersom en fil inneholder 1000 eller flere krav – for å hindre overbelastning.
### `FtpService`
Håndterer all kommunikasjon med SFTP-serveren:
- Lister filer i en mappe
- Laster ned filer som lister med tekstlinjer
- Kaller `FileValidator` på hvert nedlastet fil
- Flytter filer mellom mappene `/inbound`, `/outbound` og `/inbound/feilfiler`
Opererer med tre mappe-enums: `INBOUND`, `OUTBOUND` og `FAILED`.
### `OpprettKravService`
Sender krav av typen `NYTT_KRAV` til SKE. Itererer over listen, bygger opp `OpprettInnkrevingsoppdragRequest` via `createOpprettKravRequest()` og kaller `SkeClient.opprettKrav()`. Bryter sending dersom Circuit Breaker er åpen. Oppdaterer databasen med resultatene etter sending.
### `EndreKravService`
Sender krav av typen `ENDRING_RENTE` og `ENDRING_HOVEDSTOL`. Fordi applikasjonen alltid sender til begge endepunkter for hver endring, grupperer tjenesten krav etter `kravidentifikatorSKE + saksnummerNAV` og sender én forespørsel per endepunkt per gruppe. Dersom de to svarene har ulike statuser, konformeres statusen etter prioritetsrekkefølge (se `determineNewStatus()`).
### `StoppKravService`
Sender krav av typen `STOPP_KRAV` til SKE via `SkeClient.stoppKrav()`. Logikken tilsvarer `OpprettKravService`.
### `StatusService`
Sjekker mottaksstatus for alle krav som er i tilstandene `KRAV_SENDT` eller `MOTTATT_UNDER_BEHANDLING`. For hvert krav kalles `GET /mottaksstatus` på SKE. Dersom SKE returnerer valideringsfeil hentes detaljene via `GET /valideringsfeil` og lagres i `feilmelding`-tabellen.
### `DatabaseService`
Abstraksjonssjikt mellom services og repositories. Delegerer alle databaseoperasjoner til `KravRepository`, `FeilmeldingRepository` og `FilValideringsfeilRepository`, og håndterer oppretting og avslutning av databaseforbindelser via HikariCP.
### `RapportService`    
Produserer data til webgrensesnittet for avstemmings- og resendingsrapporter. Merket med `@Frontend`-annotasjon for å indikere at den kun skal brukes fra routing-laget. Henter krav med feilstatuser og mapper dem til `RapportObjekt` med tilhørende feilmeldinger.

---
## Pakke: `validation`
### `FileValidator`
Validerer en hel kravfil etter nedlasting fra SFTP. Parser header, kravlinjer og footer, og sjekker at antall og sum stemmer overens. Bruker `SlackService` for å sende feilvarsel. Returnerer `ValidationResult.Success` eller `ValidationResult.Error`.
### `LineValidator`
Itererer over alle kravlinjer i en fil og kaller `LineValidationRules.runValidation()` på hver linje. Linjer som feiler validering lagres i `filvalideringsfeil`-tabellen med status `VALIDERINGSFEIL_AV_LINJE_I_FIL`. Gyldige linjer settes til status `KRAV_IKKE_SENDT`.
### `LineValidationRules`
Inneholder alle forretningsreglene for linjevalidering (se detaljert dokumentasjon). Implementert som et statisk objekt med én offentlig funksjon `runValidation()`.
---
## Pakke: `client`
### `SkeClient`
HTTP-klienten mot SKEs REST-API. Alle kall går gjennom `CircuitBreakerManager` og legger ved Maskinporten-token i Authorization-headeren. Støtter:
- `opprettKrav` – POST til `/innkrevingsoppdrag`
- `endreRenter` – PUT til `/innkrevingsoppdrag/{id}/renter`
- `endreHovedstol` – PUT til `/innkrevingsoppdrag/{id}/hovedstol`
- `stoppKrav` – POST til `/innkrevingsoppdrag/avskriving`
- `getMottaksStatus` – GET `/innkrevingsoppdrag/{id}/mottaksstatus`
- `getValideringsfeil` – GET `/innkrevingsoppdrag/{id}/valideringsfeil`
- `getSkeKravidentifikator` – GET `/innkrevingsoppdrag/{ref}/avstemming`
### `SlackService`
Samler opp feilmeldinger i minnet gruppert per fil og feiltype, og sender dem samlet til Slack ved `sendErrors()`. Dersom mer enn 5 feil av samme type oppstår for et gitt krav, konsolideres de til én oppsummerende melding for å unngå støy.
### `SlackClient`
Teknisk HTTP-klient mot Slack Webhook-endepunktet. Bygger opp Slack-meldingsformatet og sender via POST.
### `MaskinportenAccessTokenProvider`
Håndterer OAuth2 token-flyten mot Maskinporten. Cacher access-token i minnet og fornyer det automatisk 60 sekunder før det utløper. Bruker Mutex for trådsikker tilgang.
---
## Pakke: `config`
### `CircuitBreakerManager`
Singleton som konfigurerer og holder Resilience4j CircuitBreaker-instansen. Standardinnstillinger:
- Sliding window: 1 kall
- Failure rate threshold: 100 %
- Wait duration i OPEN-tilstand: konfigurerbart (default 4 timer)
- Automatisk overgang fra OPEN til HALF_OPEN
### `SftpConfig`
Konfigurerer og håndterer JSch SFTP-sesjoner med RSA-nøkkelautentisering. Tilbyr en `channel { }` høyere-ordens funksjon som sikrer at sesjon og kanal alltid lukkes etter bruk.
### `PostgresConfig`
Setter opp HikariCP connection pool mot PostgreSQL og kjører Flyway-migrasjoner ved oppstart (ikke i lokalt miljø).
---
## Pakke: `repository`
### `KravRepository`
SQL-operasjoner mot `krav`-tabellen: innsetting av nye krav, oppdatering av status og kravidentifikator, henting av krav for statussjekk, resending og avstemming.
### `FeilmeldingRepository`
SQL-operasjoner mot `feilmelding`-tabellen: innsetting og henting av feilmeldinger knyttet til et krav-ID.
### `FilValideringsfeilRepository`
SQL-operasjoner mot `filvalideringsfeil`-tabellen: innsetting av fil- og linjevalideringsfeil.
---
## Pakke: `util`
### `CreateRequests.kt`
Funksjoner som bygger opp request-objekter til SKE basert på `Krav`-domenemodellen:
- `createOpprettKravRequest()` – bygger `OpprettInnkrevingsoppdragRequest`
- `createEndreRenteRequest()` – bygger `EndreRenteBeloepRequest`
- `createEndreHovedstolRequest()` – bygger `NyHovedStolRequest`
- `createStoppKravRequest()` – bygger `AvskrivingRequest`
Inneholder også hjelpefunksjoner for å klassifisere kravlinjer: `isOpprettKrav()`, `isEndring()`, `isStopp()`.
### `RequestResult.kt`
Dataklasse som holder resultatet av et enkelt kall mot SKE (respons, krav, request-payload, kravidentifikator og status). Inneholder `defineStatus()`-funksjonen som mapper HTTP-statuskoder og SKE-spesifikke feiltyper til intern `Status`-enum.
