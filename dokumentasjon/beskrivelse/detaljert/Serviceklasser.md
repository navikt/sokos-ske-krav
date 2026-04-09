# Detaljert beskrivelse – serviceklasser

Dokumentasjonen beskriver ansvar, avhengigheter og viktige detaljer for alle serviceklasser i `sokos-ske-krav`. Se [Klassebeskrivelser](../overordnet/Klassebeskrivelser.md) for en kortere overordnet oversikt.

## Innholdsfortegnelse

- [SkeService](#skeservice)
  - [Avhengigheter](#avhengigheter)
  - [Viktige egenskaper](#viktige-egenskaper)
  - [Metoder](#metoder)
- [FtpService](#ftpservice)
  - [Avhengigheter](#avhengigheter-1)
  - [Mapper-enum: Directories](#mapper-enum-directories)
  - [Dataklasse: FtpFil](#dataklasse-ftpfil)
  - [Metoder](#metoder-1)
- [OpprettKravService](#opprettkravservice)
  - [Avhengigheter](#avhengigheter-2)
  - [Metoder](#metoder-2)
- [EndreKravService](#endrekravservice)
  - [Avhengigheter](#avhengigheter-3)
  - [Metoder](#metoder-3)
- [StoppKravService](#stoppkravservice)
  - [Avhengigheter](#avhengigheter-4)
  - [Metoder](#metoder-4)
- [StatusService](#statusservice)
  - [Avhengigheter](#avhengigheter-5)
  - [Krav som sjekkes](#krav-som-sjekkes)
  - [Metoder](#metoder-5)
- [DatabaseService](#databaseservice)
  - [Avhengigheter](#avhengigheter-6)
  - [Metoder](#metoder-6)
  - [Metrikker som inkrementeres ved updateSentKrav()](#metrikker-som-inkrementeres-ved-updatesentkrav)
- [RapportService](#rapportservice)
  - [Avhengigheter](#avhengigheter-7)
  - [Egenskaper](#egenskaper)
  - [Metoder](#metoder-7)
  - [Dataklasse: RapportObjekt](#dataklasse-rapportobjekt)

---

## SkeService

**Pakke:** `service`
**Fil:** [`SkeService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt)

Hoved-orkestratoren for hele behandlingsløpet. Koordinerer alle andre services og styrer den overordnede flyten i `handleNewKrav()`.

### Avhengigheter

| Avhengighet          | Formål                                          |
|----------------------|-------------------------------------------------|
| [`FtpService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/FtpService.kt)         | Henter og validerer filer fra SFTP              |
| [`LineValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidator.kt)      | Validerer enkeltlinjer i hver fil               |
| [`StatusService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StatusService.kt)      | Oppdaterer mottaksstatus fra SKE                |
| [`OpprettKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/OpprettKravService.kt) | Sender nye krav til SKE                         |
| [`EndreKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/EndreKravService.kt)   | Sender endringer til SKE                        |
| [`StoppKravService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StoppKravService.kt)   | Sender avskrivinger til SKE                     |
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt)    | Lagrer og henter krav fra databasen             |
| [`SkeClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt)          | Henter SKE-kravidentifikator via avstemming-API |
| [`SlackService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SlackService.kt)       | Sender feilmeldinger til Slack                  |

### Viktige egenskaper

| Egenskap  | Type      | Beskrivelse                                                                                                         |
|-----------|-----------|---------------------------------------------------------------------------------------------------------------------|
| `haltRun` | `Boolean` | Settes til `true` dersom en fil inneholder ≥ 1000 kravlinjer. Blokkerer neste kjøring og resettes etter fullføring. |

### Metoder

#### `handleNewKrav()`
Hovedfunksjonen som kjøres periodisk (default hvert 5. time) 

Rekkefølge:
1. Returnerer tidlig dersom `haltRun == true`
2. Kaller `resendKrav()` – oppdaterer status og resender ventende krav
3. Kaller `sendNewFilesToSKE()` – henter og behandler nye filer
4. Venter 5 sekunder, deretter ny runde med `resendKrav()`
5. Sender alle akkumulerte feil til Slack
6. Resetter `haltRun` dersom den var satt

#### `resendKrav()` *(privat)*
1. Kaller `StatusService.getMottaksStatus()` for å oppdatere status på krav i tilstandene `KRAV_SENDT` og `MOTTATT_UNDER_BEHANDLING`
2. Henter alle krav med status `KRAV_IKKE_SENDT` og sender dem på nytt

#### `sendNewFilesToSKE()` *(privat)*
Itererer over alle validerte filer fra `FtpService` og kaller `processFile()` for hver fil. Etter at alle filer er behandlet (og minst én fil ble funnet):
1. Kaller `updateSkeKravidentifikatorForEndringerAndStopp()` – henter kravidentifikatorer for alle usendte endringer/stopp
2. Sender alle usente krav via `sendKrav()`

#### `processFile(fil)` *(privat)*
1. Kjører linjevalidering via `LineValidator.validateNewLines()`
2. Lagrer gyldige og ugyldige linjer i databasen
3. Flytter filen til `/outbound`

#### `updateSkeKravidentifikatorForEndringerAndStopp()` *(privat)*
Kjøres én gang per `handleNewKrav()`-runde, etter at alle filer er prosessert. Henter alle usente krav av typen `ENDRING_RENTE`, `ENDRING_HOVEDSTOL` og `STOPP_KRAV` med status `KRAV_IKKE_SENDT`. For hvert krav:
1. Sjekker om SKE-kravidentifikator allerede finnes i DB – bruker den i så fall direkte
2. Dersom ikke: kaller `getKravidentifikatorFromSkatt()` for å spørre SKE sitt avstemming-API
3. Dersom kravidentifikator ble funnet: oppdaterer kravet i DB
4. Dersom kravidentifikator ikke ble funnet:
   - Oppdaterer status på kravet
   - For 404-svar: kaller `handle404FromAvstemming()` for tilpasset Slack-varsling og feillogging
   - For øvrige feilkoder (403, 500 osv.): legger til i resultat-listen som håndteres normalt av `handleErrors()`

#### `getKravidentifikatorFromSkatt(krav)` *(privat)*
Kaller `SkeClient.getSkeKravidentifikator()` med `referansenummerGammelSak`, leser respons-body én gang og kaller `defineStatus()` på den. Returnerer et `RequestResult` med:
- `kravidentifikator`: hentet fra `AvstemmingResponse` ved suksess, ellers tom streng
- `status`: `UKJENT_FEIL` dersom HTTP 200 men kravidentifikator er tom; ellers status fra `defineStatus()`

#### `handle404FromAvstemming(requestResult, krav, slackErrorsHandled)` *(privat)*
Håndterer 404-svar fra SKEs avstemming-API. Bruker `slackErrorsHandled`-settet for å sikre at det kun sendes én Slack-varsling per `saksnummerNAV`, selv om samme sak dukker opp i flere kravlinjer. Bygger en egendefinert `FeilResponse` med saksnummer og `referansenummerGammelSak` i `detail`-feltet, slik at feilen kan følges opp manuelt. Kaller alltid `handleError()` for å lagre feilmelding i DB.

#### `handleError(requestResult, feilResponse)` *(privat)*
Hjelpemetode som:
1. Legger til Slack-feil (kun dersom `feilResponse` er ikke-null)
2. Kaller `saveErrorMessage()` uansett – lagrer feilmelding i DB

#### `checkKravDateForAlert()`
Kjøres hvert 24. time. Henter alle krav i status `KRAV_SENDT`/`MOTTATT_UNDER_BEHANDLING` og varsler på Slack for hvert krav der `tidspunktSendt` er mer enn 24 timer siden.

---

## FtpService

**Pakke:** `service`
**Fil:** [`FtpService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/FtpService.kt)

Håndterer all kommunikasjon med onprem SFTP-server der Oppdrag-Z (og snart Arena, Pesys og Infotrygd) legger sine filer med innkrevingsoppdrag.

### Avhengigheter

| Avhengighet                                                                                    | Formål                                |
|------------------------------------------------------------------------------------------------|---------------------------------------|
| [`SftpConfig`](../../../src/main/kotlin/no/nav/sokos/ske/krav/config/SftpConfig.kt)            | Oppretter og lukker SFTP-sesjon/kanal |
| [`FileValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/FileValidator.kt)  | Validerer filen som er lastet ned     |
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt) | Lagrer filvalideringsfeil             |

### Mapper-enum: `Directories`

| Verdi      | Sti                  | Beskrivelse                                  |
|------------|----------------------|----------------------------------------------|
| `INBOUND`  | `/inbound`           | Mappen nye filer legges i                    |
| `OUTBOUND` | `/outbound`          | Filer flyttes hit etter vellykket behandling |
| `FAILED`   | `/inbound/feilfiler` | Filer flyttes hit ved filvalideringsfeil     |

### Dataklasse: [`FtpFil`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/FtpService.kt)

Holder nedlastet filinnhold etter vellykket filvalidering:

| Felt         | Type                                                                                        | Beskrivelse                            |
|--------------|---------------------------------------------------------------------------------------------|----------------------------------------|
| `name`       | `String`                                                                                    | Filnavnet                              |
| `content`    | `List<String>`                                                                              | Råinnholdet linje for linje            |
| `kravLinjer` | [`List<KravLinje>`](../../../src/main/kotlin/no/nav/sokos/ske/krav/copybook/FixedRecord.kt) | Parsede kravlinjer fra `FileValidator` |

### Metoder

#### `getValidatedFiles()`
Laster ned alle filer fra `/inbound`, kjører `FileValidator` på hver fil og returnerer en liste med [`FtpFil`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/FtpService.kt) for alle filer som passerte validering. Filer som feiler flyttes til `/inbound/feilfiler` og feilen lagres i `filvalideringsfeil`-tabellen.

#### `moveFile(fileName, from, to)`
Flytter en fil mellom to `Directories` via SFTP `rename`. Logger og kaster `SftpException` ved feil.

#### `listFiles(directory)`
Lister filnavn i angitt mappe. Filtrerer bort kataloger (`isDir`).

---

## OpprettKravService

**Pakke:** `service`
**Fil:** [`OpprettKravService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/OpprettKravService.kt)

Sender krav av typen `NYTT_KRAV` til SKE.

### Avhengigheter

| Avhengighet                                                                                    | Formål                                       |
|------------------------------------------------------------------------------------------------|----------------------------------------------|
| [`SkeClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt)              | HTTP POST til `/innkrevingsoppdrag`          |
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt) | Oppdaterer krav med resultater etter sending |

### Metoder

#### `sendAllOpprettKrav(kravList)`
Itererer over listen og sender hvert krav til SKE. Bryter løkken dersom `CircuitBreakerException` eller `CallNotPermittedException` kastes. Oppdaterer databasen med alle resultater etter at løkken er ferdig.

#### `sendOpprettKrav(krav)` *(privat)*
1. Bygger request via [`createOpprettKravRequest(krav)`](SKE_requests_og_feilhandtering.md#11-opprett-krav--post-innkrevingsoppdrag)
2. Kaller `SkeClient.opprettKrav()`
3. Leser responsen én gang med `bodyAsText()` og lagrer resultatet i `responseBody`
4. Kaller [`defineStatus(responseBody, response.status)`](SKE_requests_og_feilhandtering.md#22-http-feilkoder-og-statuser) som tolker body-teksten og returnerer `(Status, FeilResponse?)`
5. Dersom HTTP-status er 2xx: deserialiserer `responseBody` til `OpprettInnkrevingsOppdragResponse` og henter ut `kravidentifikator`; ellers settes kravidentifikator til tom streng
6. Returnerer [`RequestResult`](../../../src/main/kotlin/no/nav/sokos/ske/krav/util/RequestResult.kt) med `responseBody`, `httpStatusCode`, `status`, `feilResponse` og `kravidentifikator` (brukes til å lagre SKEs identifikator i databasen)

> **Merk:** Responsen leses bare én gang (steg 3). Både statustolking (steg 4) og deserialisering av suksessrespons (steg 5) opererer på den allerede innleste strengen, og unngår dermed dobbel konsumering av HTTP-strømmen.

---

## EndreKravService

**Pakke:** `service`
**Fil:** [`EndreKravService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/EndreKravService.kt)

Sender krav av typen `ENDRING_RENTE` og `ENDRING_HOVEDSTOL` til SKE. Fordi applikasjonen alltid sender til begge endepunkter for én endring, sender denne tjenesten **alltid to requests per endring**.

### Avhengigheter

| Avhengighet                                                                                    | Formål                                                                                 |
|------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| [`SkeClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt)              | HTTP PUT til `/innkrevingsoppdrag/{id}/renter` og `/innkrevingsoppdrag/{id}/hovedstol` |
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt) | Oppdaterer krav med resultater etter sending                                           |

### Metoder

#### `sendAllEndreKrav(kravList)`
1. Grupperer krav etter `kravidentifikatorSKE + saksnummerNAV` – siden hvert krav i databasen splittes i to rader (`ENDRING_RENTE` og `ENDRING_HOVEDSTOL`), vil én logisk endring ha to rader med samme nøkkel
2. Prosesserer én gruppe om gangen via `processKravGroup()`
3. Bryter løkken ved Circuit Breaker-feil
4. Oppdaterer databasen med alle resultater

#### `processKravGroup(gruppeAvKrav)` *(privat)*
1. Bestemmer kravidentifikator via [`createKravidentifikatorPair()`](SKE_requests_og_feilhandtering.md#15-valg-av-kravidentifikator-ved-endre-og-stopp) (SKE sin identifikator brukes dersom tilgjengelig, ellers `referansenummerGammelSak`)
2. Sender én request per krav i gruppen (typisk 2: rente + hovedstol) via `sendEndreKrav()`. For hvert kall: responsen leses én gang med `bodyAsText()`, deretter kalles [`defineStatus(responseBody, response.status)`](SKE_requests_og_feilhandtering.md#22-http-feilkoder-og-statuser) på den innleste strengen
3. Konformerer statusene via `getConformedResponses()`

#### `getConformedResponses(resultater)` *(privat)*
Dersom de to requestene for én endring returnerer ulike HTTP-statuser, konformeres de til én felles status etter denne prioriteten:

| Prioritet  | HTTP-status              | Årsak                      |
|------------|--------------------------|----------------------------|
| 1 (høyest) | 404 Not Found            | Kravet eksisterer ikke     |
| 2          | 422 Unprocessable Entity | Valideringsfeil er kritisk |
| 3          | 409 Conflict             | Forretningsregel-konflikt  |
| 4 (lavest) | Annet                    | Settes til `UKJENT_STATUS` |

---

## StoppKravService

**Pakke:** `service`
**Fil:** [`StoppKravService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StoppKravService.kt)

Sender krav av typen `STOPP_KRAV` til SKE. Logikken er tilsvarende `OpprettKravService`.

### Avhengigheter

| Avhengighet                                                                                    | Formål                                         |
|------------------------------------------------------------------------------------------------|------------------------------------------------|
| [`SkeClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt)              | HTTP POST til `/innkrevingsoppdrag/avskriving` |
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt) | Oppdaterer krav med resultater etter sending   |

### Metoder

#### `sendAllStoppKrav(kravList)`
Itererer over listen og sender hvert krav. Bryter løkken ved Circuit Breaker-feil. Oppdaterer databasen etter ferdig løkke.

#### `sendStoppKrav(krav)` *(privat)*
1. Bestemmer kravidentifikator via [`createKravidentifikatorPair()`](SKE_requests_og_feilhandtering.md#15-valg-av-kravidentifikator-ved-endre-og-stopp) (SKE-identifikator eller `referansenummerGammelSak`)
2. Bygger [`AvskrivingRequest`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/requests/AvskrivingRequest.kt) via [`createStoppKravRequest()`](SKE_requests_og_feilhandtering.md#14-stopp-krav--post-innkrevingsoppdragavskriving)
3. Kaller `SkeClient.stoppKrav()`
4. Leser responsen én gang med `bodyAsText()` og lagrer resultatet i `responseBody`
5. Kaller [`defineStatus(responseBody, response.status)`](SKE_requests_og_feilhandtering.md#22-http-feilkoder-og-statuser) som tolker body-teksten og returnerer `(Status, FeilResponse?)`
6. Returnerer [`RequestResult`](../../../src/main/kotlin/no/nav/sokos/ske/krav/util/RequestResult.kt) med `responseBody`, `httpStatusCode`, `status` og `feilResponse`

> **Merk:** Som i `sendOpprettKrav` leses responsen bare én gang (steg 4), og den innleste strengen gjenbrukes for statustolking (steg 5).

---

## StatusService

**Pakke:** `service`
**Fil:** [`StatusService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/StatusService.kt)

Sjekker og oppdaterer mottaksstatus for krav som er sendt til SKE men ikke endelig avklart.

### Avhengigheter

| Avhengighet                                                                                    | Formål                                   |
|------------------------------------------------------------------------------------------------|------------------------------------------|
| [`SkeClient`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SkeClient.kt)              | GET mottaksstatus og GET valideringsfeil |
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt) | Henter krav og oppdaterer status         |
| [`SlackService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/client/SlackService.kt)        | Varsler ved feil og valideringsfeil      |

### Krav som sjekkes

Kun krav med statusene `KRAV_SENDT` eller `MOTTATT_UNDER_BEHANDLING` hentes ut for statussjekk.

### Metoder

#### `getMottaksStatus()`
Trigges av `SkeService` i starten og slutten av `handleNewKrav()`, samt ved manuelt kall på `GET /api/hentStatus`.

For hvert krav:
1. Bestemmer kravidentifikator via [`createKravidentifikatorPair()`](SKE_requests_og_feilhandtering.md#15-valg-av-kravidentifikator-ved-endre-og-stopp)
2. Kaller `GET /innkrevingsoppdrag/{id}/mottaksstatus`
3. Oppdaterer status i databasen
4. Dersom ny status er `VALIDERINGSFEIL` – kaller `handleValideringsFeil()`
5. Bryter løkken dersom Circuit Breaker er åpen (kaster exception)

#### `handleValideringsFeil(kravIdentifikatorPair, krav)` *(privat)*
1. Kaller `GET /innkrevingsoppdrag/{id}/valideringsfeil`
2. Deserialiserer [`ValideringsFeilResponse`](../../../src/main/kotlin/no/nav/sokos/ske/krav/dto/ske/responses/FeilResponses.kt)
3. Lagrer hver valideringsfeil som en rad i `feilmelding`-tabellen
4. Varsler Slack med header `"Asynk valideringsfeil"`

---

## DatabaseService

**Pakke:** `service`
**Fil:** [`DatabaseService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt)

Abstraksjonslag mellom serviceklassene og repositoriene. Alle databaseoperasjoner går gjennom denne klassen.

### Avhengigheter

| Avhengighet                                                                                                                 | Formål                                         |
|-----------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| [`KravRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/KravRepository.kt)                             | CRUD mot `krav`-tabellen                       |
| [`FeilmeldingRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FeilmeldingRepository.kt)               | CRUD mot `feilmelding`-tabellen                |
| [`FilValideringsfeilRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FilValideringsfeilRepository.kt) | CRUD mot `filvalideringsfeil`-tabellen         |
| `Metrics`                                                                                                                   | Inkrementerer Prometheus-metrikker ved sending |

### Metoder

| Metode                                                                 | Beskrivelse                                                                                                                                |
|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `getSkeKravidentifikator(navref)`                                      | Slår opp SKE-kravidentifikator i databasen. Prøver først med `navref`, deretter med forrige referansenummer (`getPreviousReferansenummer`) |
| `saveAllNewKrav(kravLinjer, filnavn)`                                  | Lagrer alle validerte kravlinjer fra en fil                                                                                                |
| `saveLineValidationError(filnavn, kravlinje, feilmelding)`             | Lagrer linjevalideringsfeil i `filvalideringsfeil`                                                                                         |
| `saveFileValidationError(filnavn, feilmelding)`                        | Lagrer filvalideringsfeil i `filvalideringsfeil`                                                                                           |
| `updateSentKrav(results)`                                              | Oppdaterer krav i DB med status og SKE-kravidentifikator etter sending. Oppdaterer Prometheus-metrikker                                    |
| `getAllKravForStatusCheck()`                                           | Returnerer krav med status `KRAV_SENDT` eller `MOTTATT_UNDER_BEHANDLING`                                                                   |
| `getAllKravForAvstemming()`                                            | Returnerer krav med feilstatuser til bruk i rapportvisningen                                                                               |
| `getAllKravForResending()`                                             | Returnerer krav med status `KRAV_IKKE_SENDT`                                                                                               |
| `getAllUnsentKrav()`                                                   | Returnerer krav med status `KRAV_IKKE_SENDT` (brukes etter innlesing av ny fil)                                                            |
| `getAllUnsentEndringerAndStopp()`                                      | Returnerer krav med status `KRAV_IKKE_SENDT` og kravtype `ENDRING_RENTE`, `ENDRING_HOVEDSTOL` eller `STOPP_KRAV` (brukes for å hente SKE-kravidentifikator via avstemming-API)             |
| `updateStatus(mottakStatus, corrId)`                                   | Oppdaterer status på krav identifisert med `corrId`                                                                                        |
| `updateEndringWithSkeKravIdentifikator(saksnummer, kravidentifikator)` | Lagrer SKEs kravidentifikator på endringskrav                                                                                              |
| `updateStatusForAvstemtKravToReported(kravId)`                         | Markerer et krav som rapportert i avstemmingsvisningen                                                                                     |
| `getFileValidationMessage(filNavn)`                                    | Henter filvalideringsfeil for en gitt fil                                                                                                  |

### Metrikker som inkrementeres ved `updateSentKrav()`

| Metrikk                   | Beskrivelse                                        |
|---------------------------|----------------------------------------------------|
| `numberOfKravSent`        | Totalt antall krav sendt                           |
| `numberOfKravFeilet`      | Antall krav med ikke-2xx respons                   |
| `numberOfNyeKrav`         | Antall `NYTT_KRAV` sendt                           |
| `numberOfEndringerAvKrav` | Antall `ENDRING_RENTE` + `ENDRING_HOVEDSTOL` sendt |
| `numberOfStoppAvKrav`     | Antall `STOPP_KRAV` sendt                          |

---

## RapportService

**Pakke:** `service`
**Fil:** [`RapportService.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt)
**Annotasjon:** `@Frontend` – skal kun brukes fra routing-laget ([`AvstemmingRouting.kt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/api/AvstemmingRouting.kt))

Produserer data til webgrensesnittet for funksjonelle feil

### Avhengigheter

| Avhengighet             | Formål                                     |
|-------------------------|--------------------------------------------|
| [`DatabaseService`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/DatabaseService.kt)       | Henter krav for rapport                    |
| [`FeilmeldingRepository`](../../../src/main/kotlin/no/nav/sokos/ske/krav/repository/FeilmeldingRepository.kt) | Henter feilmeldinger tilknyttet hvert krav |

### Egenskaper

| Egenskap               | Beskrivelse                                                                                                                                                                               |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kravSomSkalAvstemmes` | Lazy-evaluert liste av [`RapportObjekt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt) for krav med feilstatuser (brukt i `/rapporter/avstemming`)            |
| `kravSomSkalResendes`  | Lazy-evaluert liste av [`RapportObjekt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt) for krav med status `KRAV_IKKE_SENDT` (brukt i `/rapporter/resending`) |

### Metoder

#### `oppdaterStatusTilRapportert(kravId)`
Markerer et krav som rapportert. Kalles via `POST /rapporter/avstemming/update`.

### Dataklasse: [`RapportObjekt`](../../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt)

Representerer ett krav i rapportvisningen:

| Felt                        | Beskrivelse                                                                                       |
|-----------------------------|---------------------------------------------------------------------------------------------------|
| `kravID`                    | Internt database-ID                                                                               |
| `filnavn`                   | Kildefil kravet kom fra                                                                           |
| `linjenummer`               | Linjenummer i kildefilen                                                                          |
| `vedtaksId`                 | NAVs saksnummer                                                                                   |
| `vedtaksDato`               | Vedtaksdato                                                                                       |
| `fagsystemId`               | Fagsystem-ID                                                                                      |
| `kravkode`                  | Kravkode                                                                                          |
| `kodeHjemmel`               | Hjemmelkode                                                                                       |
| `status`                    | Nåværende status                                                                                  |
| `stonadsType`               | Mappet [`StonadsType`](../../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt)-enum |
| `saksnummerNAV`             | NAVs saksnummer                                                                                   |
| `referansenummerGammelSak`  | Ref. gammel sak ved endringer                                                                     |
| `belop`                     | Kravbeløp                                                                                         |
| `periodeFOM` / `periodeTOM` | Tilbakekrevingsperiode                                                                            |
| `feilmeldinger`             | Liste av feilmeldingstekster fra `feilmelding`-tabellen                                           |
| `tidspunktSisteStatus`      | Formatert tidspunkt for siste statusendring                                                       |

Feilmeldinger hentes kun for krav som ikke har status `VALIDERINGSFEIL_AV_LINJE_I_FIL` (disse har ingen rader i `feilmelding`-tabellen).

`RapportObjekt.CsvBuilder.buildCSV()` produserer en CSV-streng som kan lastes ned fra `/rapporter/avstemming/CSVdownload`.
