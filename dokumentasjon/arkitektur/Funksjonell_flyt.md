# Funksjonell flyt
Beskrivelse av de viktigste prosessene i sokos-ske-krav, med sekvensdiagrammer.
## Oversikt over kjøreløkken

Applikasjonen kjører to periodiske jobber:

| Jobb                                                                                         | Intervall                        | Funksjon                                                            |
|----------------------------------------------------------------------------------------------|----------------------------------|---------------------------------------------------------------------|
| [`handleNewKrav`](../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt)         | Konfigurerbart (default 5 timer) | Henter nye filer fra SFTP, validerer, lagrer og sender krav til SKE |
| [`checkKravDateForAlert`](../../src/main/kotlin/no/nav/sokos/ske/krav/service/SkeService.kt) | Hvert 24. time                   | Varsler på Slack dersom krav har stått ubehandlet for lenge         |

---
## 1. Hovedflyt – behandling av nye kravfiler
```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Scheduler (Coroutine)
    participant SkeService
    participant StatusService
    participant FtpService
    participant FileValidator
    participant LineValidator
    participant DB as DatabaseService (PostgreSQL)
    participant SKE as SKE REST-API (via SkeClient)
    participant Slack
    Scheduler->>SkeService: handleNewKrav()
    note over SkeService: Steg 1 – Resend ventende krav
    SkeService->>StatusService: getMottaksStatus()
    StatusService->>SKE: GET mottaksstatus for alle uavklarte krav
    SKE-->>StatusService: MottaksStatusResponse
    StatusService->>DB: updateStatus()
    SkeService->>DB: getAllKravForResending()
    DB-->>SkeService: Krav som skal resendes
    SkeService->>SKE: Send krav (opprett / endre / stopp)
    SKE-->>SkeService: Respons
    SkeService->>DB: updateSentKrav()
    note over SkeService: Steg 2 – Hent og behandle nye filer
    SkeService->>FtpService: getValidatedFiles()
    FtpService->>FtpService: downloadFiles() via SFTP
    FtpService->>FileValidator: validateFile(innhold, filnavn)
    FileValidator->>FileValidator: Sjekk header/footer, antall og sum
    alt Fil er ugyldig
        FileValidator->>Slack: addError() / sendErrors()
        FileValidator-->>FtpService: ValidationResult.Error
        FtpService->>FtpService: moveFile() til /inbound/feilfiler
        FtpService->>DB: insertFileValideringsfeil()
    else Fil er gyldig
        FileValidator-->>FtpService: ValidationResult.Success(kravLinjer)
        FtpService-->>SkeService: FtpFil(navn, innhold, kravLinjer)
    end
    loop For hver gyldig fil
        SkeService->>LineValidator: validateNewLines(fil, DB)
        LineValidator->>DB: Sjekk duplikater og forretningsregler
        alt Linje er ugyldig
            LineValidator->>DB: insertLineFilValideringsfeil()
        end
        SkeService->>DB: saveAllNewKrav(validerteLinjer)
        SkeService->>FtpService: moveFile() til /outbound
        SkeService->>SKE: Hent SKE-kravidentifikator for endringer/stopp
        SkeService->>DB: getAllUnsentKrav()
        DB-->>SkeService: Usente krav
        SkeService->>SKE: sendKrav() – opprett + endre + stopp
        SKE-->>SkeService: Responser
        SkeService->>DB: updateSentKrav()
    end
    note over SkeService: Steg 3 – Resend igjen etter sending
    SkeService->>StatusService: getMottaksStatus()
    SkeService->>DB: getAllKravForResending()
    SkeService->>SKE: Resend krav
    SkeService->>DB: updateSentKrav()
    SkeService->>Slack: sendErrors()
```
---
## 2. Sending av krav til SKE
Krav sendes i tre varianter avhengig av `kravtype`:

```mermaid
flowchart TD
    A[Liste med usente krav] --> B{kravtype?}
    B -->|NYTT_KRAV| C[OpprettKravService]
    B -->|ENDRING_RENTE / ENDRING_HOVEDSTOL| D[EndreKravService]
    B -->|STOPP_KRAV| E[StoppKravService]
    C --> F[POST /innkrevingsoppdrag]
    D --> G1[PUT /innkrevingsoppdrag/renter]
    D --> G2[PUT /innkrevingsoppdrag/hovedstol]
    E --> H[POST /innkrevingsoppdrag/avskriving]
    F --> I{HTTP-respons}
    G1 --> I
    G2 --> I
    H --> I
    I -->|2xx| J[Status: KRAV_SENDT + lagre SKE-kravidentifikator]
    I -->|4xx / 5xx| K[Status: HTTP-feilkode + lagre i feilmelding-tabell]
    I -->|Circuit Breaker OPEN| L[Stopp videre sending – vent på reset]
    J --> M[DB: updateSentKrav]
    K --> M
```
### Statuskoder

| Status                           | Beskrivelse                                                                                                                                                         |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `KRAV_IKKE_SENDT`                | Initial status for gyldige krav etter innlesing. Brukes også ved automatisk resending etter feil                                                                    |
| `KRAV_SENDT`                     | Krav er sendt til SKE og venter på statusbekreftelse                                                                                                                |
| `MOTTATT_UNDER_BEHANDLING`       | SKE har bekreftet mottak, kravet er til behandling                                                                                                                  |
| `RESKONTROFOERT`                 | SKE har reskontroført kravet – endelig status, ingen videre behandling                                                                                              |
| `MIGRERT`                        | Migrert krav er godkjent av SKE – endelig status                                                                                                                    |
| `VALIDERINGSFEIL_AV_LINJE_I_FIL` | Linjen feilet intern validering og vil ikke sendes til SKE                                                                                                          |
| `VALIDERINGSFEIL_MOTTAKSSTATUS`  | SKE returnerte valideringsfeil ved statussjekk                                                                                                                      |
| `HTTP4xx` / `HTTP5xx`            | Ulike HTTP-feilstatuser fra SKE (se detaljert tabell i [SKE_requests_og_feilhandtering.md](../beskrivelse/detaljert/SKE_requests_og_feilhandtering.md))             |
| `UKJENT_FEIL`                    | Ukjent feiltype                                                                                                                                                     |
| `UKJENT_STATUS`                  | Statusen kunne ikke bestemmes (brukes bl.a. ved statuskonformering i [`EndreKravService`](../../src/main/kotlin/no/nav/sokos/ske/krav/service/EndreKravService.kt)) |
| `KRAV_INNLEST_FRA_FIL`           | Definert som fallback i `insertAllNewKrav`, men brukes **aldri i praksis** siden `LineValidator` alltid setter status eksplisitt                                    |
---
## 3. Filvalidering
```mermaid
flowchart TD
    A[Fil lastes ned fra SFTP] --> B[FileValidator.validateFile]
    B --> C{Kan filen parses?}
    C -->|Nei – parse-feil| ERR1[Valideringsfeil: Parse-unntak]
    C -->|Ja| D[Parse header – KontrollLinjeHeader]
    D --> E[Parse kravlinjer – KravLinje x N]
    E --> F[Parse footer – KontrollLinjeFooter]
    F --> G{Antall krav = footer.antall?}
    G -->|Nei| ERR2[Valideringsfeil: Feil i antall]
    G -->|Ja| H{Sum belop = footer.sum?}
    H -->|Nei| ERR3[Valideringsfeil: Feil i sum]
    H -->|Ja| I{Dato header = dato footer?}
    I -->|Nei| ERR4[Valideringsfeil: Dato-avvik]
    I -->|Ja| OK[ValidationResult.Success]
    ERR1 --> SEND[Samle alle feil – Slack + DB + flytt til /feilfiler]
    ERR2 --> SEND
    ERR3 --> SEND
    ERR4 --> SEND
    OK --> LV[LineValidator.validateNewLines]
    LV --> DB[Lagre gyldige linjer i krav-tabellen / ugyldige i filvalideringsfeil]
```
---
## 4. Statussjekk og reskontroføring
```mermaid
sequenceDiagram
    autonumber
    participant SkeService
    participant StatusService
    participant SKE as SKE REST-API
    participant DB as DatabaseService
    participant Slack
    SkeService->>StatusService: getMottaksStatus()
    StatusService->>DB: getAllKravForStatusCheck()
    note right of DB: Krav med status KRAV_SENDT / MOTTATT_UNDER_BEHANDLING
    loop For hvert krav
        StatusService->>SKE: GET /mottaksstatus
        alt Suksess
            SKE-->>StatusService: MottaksStatusResponse
            StatusService->>DB: updateStatus(nyStatus)
            alt nyStatus == VALIDERINGSFEIL
                StatusService->>SKE: GET /valideringsfeil
                SKE-->>StatusService: ValideringsFeilResponse
                StatusService->>DB: Lagre i feilmelding-tabell
                StatusService->>Slack: addError()
            end
        else Feil fra SKE
            StatusService->>Slack: addError()
        else Circuit Breaker OPEN
            StatusService-->>SkeService: Avbryt løkken
        end
    end
    StatusService->>Slack: sendErrors()
```
---
## 5. Circuit Breaker
```mermaid
stateDiagram-v2
    [*] --> CLOSED : Applikasjonsstart
    CLOSED --> OPEN : Forste kall feiler (failureRateThreshold=100%)
    OPEN --> HALF_OPEN : Etter waitDuration (default 4 timer)
    HALF_OPEN --> CLOSED : Testanrop lykkes
    HALF_OPEN --> OPEN : Testanrop feiler
    CLOSED : CLOSED – Normale kall tillates
    OPEN : OPEN – Alle kall blokkeres, sending stoppes
    HALF_OPEN : HALF_OPEN – 1 testanrop tillates
```
Når Circuit Breaker er **OPEN** stopper applikasjonen all videre sending i inneværende kjøring. Neste planlagte kjøring starter med ny sjekk av status.

---
## 6. Rapport og manuell oppfølging
```mermaid
flowchart LR
    A[Fagressurs] -->|Browser| B["/rapporter/avstemming"]
    A -->|Browser| C["/rapporter/resending"]
    B --> D[RapportService – kravSomSkalAvstemmes]
    C --> E[RapportService – kravSomSkalResendes]
    D --> F[(PostgreSQL – krav + feilmelding)]
    E --> F
    B --> G{Marker som rapportert}
    G -->|POST /update| H[oppdaterStatusTilRapportert]
    H --> F
    B --> I{Last ned CSV}
    I -->|POST /CSVdownload| J[CSV-fil til browser]
```
Rapportvisningen brukes til:
- **Avstemming** – følge opp krav med feil-statuser som krever manuell handling (via [`RapportService.kravSomSkalAvstemmes`](../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt))
- **Resending** – se hvilke krav som er i kø for automatisk resending (via [`RapportService.kravSomSkalResendes`](../../src/main/kotlin/no/nav/sokos/ske/krav/service/RapportService.kt))
