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
**Fil:** `SkeService.kt`

Hoved-orkestratoren for hele behandlingsløpet. Koordinerer alle andre services og styrer den overordnede flyten i `handleNewKrav()`.

### Avhengigheter

| Avhengighet          | Formål                                          |
|----------------------|-------------------------------------------------|
| `FtpService`         | Henter og validerer filer fra SFTP              |
| `LineValidator`      | Validerer enkeltlinjer i hver fil               |
| `StatusService`      | Oppdaterer mottaksstatus fra SKE                |
| `OpprettKravService` | Sender nye krav til SKE                         |
| `EndreKravService`   | Sender endringer til SKE                        |
| `StoppKravService`   | Sender avskrivinger til SKE                     |
| `DatabaseService`    | Lagrer og henter krav fra databasen             |
| `SkeClient`          | Henter SKE-kravidentifikator via avstemming-API |
| `SlackService`       | Sender feilmeldinger til Slack                  |

### Viktige egenskaper

| Egenskap  | Type      | Beskrivelse                                                                                                         |
|-----------|-----------|---------------------------------------------------------------------------------------------------------------------|
| `haltRun` | `Boolean` | Settes til `true` dersom en fil inneholder ≥ 1000 kravlinjer. Blokkerer neste kjøring og resettes etter fullføring. |

### Metoder

#### `handleNewKrav()`
Hovedfunksjonen som kjøres periodisk (default hvert 5. time) og ved manuelt kall på `GET /api/hentNye`.

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
Itererer over alle validerte filer fra `FtpService`. For hver fil:
1. Kaller `processFile()` – validerer linjer, lagrer i DB, flytter fil til `/outbound`, henter kravidentifikatorer for endringer/stopp
2. Sender alle usente krav via `sendKrav()`

#### `processFile(fil)` *(privat)*
1. Kjører linjevalidering via `LineValidator.validateNewLines()`
2. Lagrer gyldige og ugyldige linjer i databasen
3. Flytter filen til `/outbound`
4. For krav som er endringer eller stopp: slår opp kravidentifikator i DB, og hvis ikke funnet, spør SKE sitt avstemming-API. Varsler på Slack dersom identifikator ikke kan finnes.

#### `checkKravDateForAlert()`
Kjøres hvert 24. time. Henter alle krav i status `KRAV_SENDT`/`MOTTATT_UNDER_BEHANDLING` og varsler på Slack for hvert krav der `tidspunktSendt` er mer enn 24 timer siden.

---

## FtpService

**Pakke:** `service`
**Fil:** `FtpService.kt`

Håndterer all kommunikasjon med SKE sin SFTP-server.

### Avhengigheter

| Avhengighet       | Formål                                |
|-------------------|---------------------------------------|
| `SftpConfig`      | Oppretter og lukker SFTP-sesjon/kanal |
| `FileValidator`   | Validerer filen som er lastet ned     |
| `DatabaseService` | Lagrer filvalideringsfeil             |

### Mapper-enum: `Directories`

| Verdi      | Sti                  | Beskrivelse                                  |
|------------|----------------------|----------------------------------------------|
| `INBOUND`  | `/inbound`           | Mappen nye filer legges i                    |
| `OUTBOUND` | `/outbound`          | Filer flyttes hit etter vellykket behandling |
| `FAILED`   | `/inbound/feilfiler` | Filer flyttes hit ved filvalideringsfeil     |

### Dataklasse: `FtpFil`

Holder nedlastet filinnhold etter vellykket filvalidering:

| Felt         | Type              | Beskrivelse                            |
|--------------|-------------------|----------------------------------------|
| `name`       | `String`          | Filnavnet                              |
| `content`    | `List<String>`    | Råinnholdet linje for linje            |
| `kravLinjer` | `List<KravLinje>` | Parsede kravlinjer fra `FileValidator` |

### Metoder

#### `getValidatedFiles()`
Laster ned alle filer fra `/inbound`, kjører `FileValidator` på hver fil og returnerer en liste med `FtpFil` for alle filer som passerte validering. Filer som feiler flyttes til `/inbound/feilfiler` og feilen lagres i `filvalideringsfeil`-tabellen.

#### `moveFile(fileName, from, to)`
Flytter en fil mellom to `Directories` via SFTP `rename`. Logger og kaster `SftpException` ved feil.

#### `listFiles(directory)`
Lister filnavn i angitt mappe. Filtrerer bort kataloger (`isDir`).

---

## OpprettKravService

**Pakke:** `service`
**Fil:** `OpprettKravService.kt`

Sender krav av typen `NYTT_KRAV` til SKE.

### Avhengigheter

| Avhengighet       | Formål                                       |
|-------------------|----------------------------------------------|
| `SkeClient`       | HTTP POST til `/innkrevingsoppdrag`          |
| `DatabaseService` | Oppdaterer krav med resultater etter sending |

### Metoder

#### `sendAllOpprettKrav(kravList)`
Itererer over listen og sender hvert krav til SKE. Bryter løkken dersom `CircuitBreakerException` eller `CallNotPermittedException` kastes. Oppdaterer databasen med alle resultater etter at løkken er ferdig.

#### `sendOpprettKrav(krav)` *(privat)*
1. Bygger request via `createOpprettKravRequest(krav)` (se [SKE-requests](SKE_requests_og_feilhandtering.md))
2. Kaller `SkeClient.opprettKrav()`
3. Returnerer `RequestResult` med kravidentifikator fra SKE-responsen (brukes til å lagre SKEs identifikator i databasen)

---

## EndreKravService

**Pakke:** `service`
**Fil:** `EndreKravService.kt`

Sender krav av typen `ENDRING_RENTE` og `ENDRING_HOVEDSTOL` til SKE. Fordi applikasjonen alltid sender til begge endepunkter for én endring, sender denne tjenesten **alltid to requests per endring**.

### Avhengigheter

| Avhengighet       | Formål                                                                                 |
|-------------------|----------------------------------------------------------------------------------------|
| `SkeClient`       | HTTP PUT til `/innkrevingsoppdrag/{id}/renter` og `/innkrevingsoppdrag/{id}/hovedstol` |
| `DatabaseService` | Oppdaterer krav med resultater etter sending                                           |

### Metoder

#### `sendAllEndreKrav(kravList)`
1. Grupperer krav etter `kravidentifikatorSKE + saksnummerNAV` – siden hvert krav i databasen splittes i to rader (`ENDRING_RENTE` og `ENDRING_HOVEDSTOL`), vil én logisk endring ha to rader med samme nøkkel
2. Prosesserer én gruppe om gangen via `processKravGroup()`
3. Bryter løkken ved Circuit Breaker-feil
4. Oppdaterer databasen med alle resultater

#### `processKravGroup(gruppeAvKrav)` *(privat)*
1. Bestemmer kravidentifikator via `createKravidentifikatorPair()` (SKE sin identifikator brukes dersom tilgjengelig, ellers `referansenummerGammelSak`)
2. Sender én request per krav i gruppen (typisk 2: rente + hovedstol)
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
**Fil:** `StoppKravService.kt`

Sender krav av typen `STOPP_KRAV` til SKE. Logikken er tilsvarende `OpprettKravService`.

### Avhengigheter

| Avhengighet       | Formål                                         |
|-------------------|------------------------------------------------|
| `SkeClient`       | HTTP POST til `/innkrevingsoppdrag/avskriving` |
| `DatabaseService` | Oppdaterer krav med resultater etter sending   |

### Metoder

#### `sendAllStoppKrav(kravList)`
Itererer over listen og sender hvert krav. Bryter løkken ved Circuit Breaker-feil. Oppdaterer databasen etter ferdig løkke.

#### `sendStoppKrav(krav)` *(privat)*
1. Bestemmer kravidentifikator via `createKravidentifikatorPair()` (SKE-identifikator eller `referansenummerGammelSak`)
2. Bygger `AvskrivingRequest` via `createStoppKravRequest()`
3. Kaller `SkeClient.stoppKrav()`
4. Returnerer `RequestResult`

---

## StatusService

**Pakke:** `service`
**Fil:** `StatusService.kt`

Sjekker og oppdaterer mottaksstatus for krav som er sendt til SKE men ikke endelig avklart.

### Avhengigheter

| Avhengighet       | Formål                                   |
|-------------------|------------------------------------------|
| `SkeClient`       | GET mottaksstatus og GET valideringsfeil |
| `DatabaseService` | Henter krav og oppdaterer status         |
| `SlackService`    | Varsler ved feil og valideringsfeil      |

### Krav som sjekkes

Kun krav med statusene `KRAV_SENDT` eller `MOTTATT_UNDER_BEHANDLING` hentes ut for statussjekk.

### Metoder

#### `getMottaksStatus()`
Trigges av `SkeService` i starten og slutten av `handleNewKrav()`, samt ved manuelt kall på `GET /api/hentStatus`.

For hvert krav:
1. Bestemmer kravidentifikator via `createKravidentifikatorPair()`
2. Kaller `GET /innkrevingsoppdrag/{id}/mottaksstatus`
3. Oppdaterer status i databasen
4. Dersom ny status er `VALIDERINGSFEIL` – kaller `handleValideringsFeil()`
5. Bryter løkken dersom Circuit Breaker er åpen (kaster exception)

#### `handleValideringsFeil(kravIdentifikatorPair, krav)` *(privat)*
1. Kaller `GET /innkrevingsoppdrag/{id}/valideringsfeil`
2. Deserialiserer `ValideringsFeilResponse`
3. Lagrer hver valideringsfeil som en rad i `feilmelding`-tabellen
4. Varsler Slack med header `"Asynk valideringsfeil"`

---

## DatabaseService

**Pakke:** `service`
**Fil:** `DatabaseService.kt`

Abstraksjonssjikt mellom serviceklassene og repositoriene. Alle databaseoperasjoner går gjennom denne klassen.

### Avhengigheter

| Avhengighet                    | Formål                                         |
|--------------------------------|------------------------------------------------|
| `KravRepository`               | CRUD mot `krav`-tabellen                       |
| `FeilmeldingRepository`        | CRUD mot `feilmelding`-tabellen                |
| `FilValideringsfeilRepository` | CRUD mot `filvalideringsfeil`-tabellen         |
| `Metrics`                      | Inkrementerer Prometheus-metrikker ved sending |

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
**Fil:** `RapportService.kt`
**Annotasjon:** `@Frontend` – skal kun brukes fra routing-laget (`AvstemmingRouting.kt`)

Produserer data til webgrensesnittet for avstemmings- og resendingsrapporter.

### Avhengigheter

| Avhengighet             | Formål                                     |
|-------------------------|--------------------------------------------|
| `DatabaseService`       | Henter krav for rapport                    |
| `FeilmeldingRepository` | Henter feilmeldinger tilknyttet hvert krav |

### Egenskaper

| Egenskap               | Beskrivelse                                                                                                   |
|------------------------|---------------------------------------------------------------------------------------------------------------|
| `kravSomSkalAvstemmes` | Lazy-evaluert liste av `RapportObjekt` for krav med feilstatuser (brukt i `/rapporter/avstemming`)            |
| `kravSomSkalResendes`  | Lazy-evaluert liste av `RapportObjekt` for krav med status `KRAV_IKKE_SENDT` (brukt i `/rapporter/resending`) |

### Metoder

#### `oppdaterStatusTilRapportert(kravId)`
Markerer et krav som rapportert. Kalles via `POST /rapporter/avstemming/update`.

### Dataklasse: `RapportObjekt`

Representerer ett krav i rapportvisningen:

| Felt                        | Beskrivelse                                             |
|-----------------------------|---------------------------------------------------------|
| `kravID`                    | Internt database-ID                                     |
| `filnavn`                   | Kildefil kravet kom fra                                 |
| `linjenummer`               | Linjenummer i kildefilen                                |
| `vedtaksId`                 | NAVs saksnummer                                         |
| `vedtaksDato`               | Vedtaksdato                                             |
| `fagsystemId`               | Fagsystem-ID                                            |
| `kravkode`                  | Kravkode                                                |
| `kodeHjemmel`               | Hjemmelkode                                             |
| `status`                    | Nåværende status                                        |
| `stonadsType`               | Mappet `StonadsType`-enum                               |
| `saksnummerNAV`             | NAVs saksnummer                                         |
| `referansenummerGammelSak`  | Ref. gammel sak ved endringer                           |
| `belop`                     | Kravbeløp                                               |
| `periodeFOM` / `periodeTOM` | Tilbakekrevingsperiode                                  |
| `feilmeldinger`             | Liste av feilmeldingstekster fra `feilmelding`-tabellen |
| `tidspunktSisteStatus`      | Formatert tidspunkt for siste statusendring             |

Feilmeldinger hentes kun for krav som ikke har status `VALIDERINGSFEIL_AV_LINJE_I_FIL` (disse har ingen rader i `feilmelding`-tabellen).

`RapportObjekt.CsvBuilder.buildCSV()` produserer en CSV-streng som kan lastes ned fra `/rapporter/avstemming/CSVdownload`.

