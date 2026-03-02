# Detaljert beskrivelse – SKE-requests og feilhåndtering

---

## 1. Oppbygging av requests mot SKE

### 1.1 Opprett krav – `POST /innkrevingsoppdrag`

Bygges av `createOpprettKravRequest()` i `CreateRequests.kt`.

| Felt i request | Kilde i NAV-modellen | Merknad |
|---|---|---|
| `kravtype` | `StonadsType.getStonadstype(kravkode, kodeHjemmel)` | Mappes fra kravkode + hjemmelkode |
| `skyldner.identifikatortype` | `gjelderId` | `ORGANISASJON` dersom ID starter med `00`, ellers `PERSON` |
| `skyldner.identifikator` | `gjelderId` | Dersom organisasjon strippes de to første tegnene (`00`) |
| `hovedstol.beloep` | `belop` | Avrundet til nærmeste heltall (Long) |
| `hovedstol.valuta` | – | Alltid `NOK` |
| `renteBeloep[].beloep` | `belopRente` | Kun inkludert dersom beloep > 0 |
| `renteBeloep[].renterIlagtDato` | `vedtaksDato` | |
| `renteBeloep[].rentetype` | – | Alltid `STRAFFERENTE` |
| `oppdragsgiversReferanse` | `fagsystemId` | |
| `oppdragsgiversKravidentifikator` | `saksnummerNAV` | |
| `fastsettelsesdato` | `vedtaksDato` | |
| `foreldelsesfristensUtgangspunkt` | `utbetalDato` | Settes kun dersom: ingen tilleggsfrist er satt, dato er gyldig, dato er ulik vedtaksdato, og dato er før vedtaksdato |
| `tilleggsfristEtterForeldelsesloven` | `tilleggsfrist` | Nullable – settes kun dersom feltet er utfylt. Dersom tilleggsfrist er satt, settes foreldelsesfristensUtgangspunkt til null |
| `tilleggsinformasjon.tilbakekrevingsperiode.fom` | `periodeFOM` | |
| `tilleggsinformasjon.tilbakekrevingsperiode.tom` | `periodeTOM` | |
| `tilleggsinformasjon.ytelserForAvregning.beloep` | `fremtidigYtelse` | Kun inkludert dersom beloep > 0 |

### 1.2 Endre rente – `PUT /innkrevingsoppdrag/{id}/renter`

Bygges av `createEndreRenteRequest()`.

| Felt | Kilde | Merknad |
|---|---|---|
| `renter[].beloep` | `belopRente` | Avrundet til Long |
| `renter[].renterIlagtDato` | `vedtaksDato` | |
| `renter[].rentetype` | – | Alltid `STRAFFERENTE` |
| `renter[].valuta` | – | Alltid `NOK` |

### 1.3 Endre hovedstol – `PUT /innkrevingsoppdrag/{id}/hovedstol`

Bygges av `createEndreHovedstolRequest()`.

| Felt | Kilde | Merknad |
|---|---|---|
| `hovedstol.beloep` | `belop` | Avrundet til Long |
| `hovedstol.valuta` | – | Alltid `NOK` |

### 1.4 Stopp krav – `POST /innkrevingsoppdrag/avskriving`

Bygges av `createStoppKravRequest()`.

| Felt | Kilde | Merknad |
|---|---|---|
| `kravidentifikator` | SKEs kravidentifikator eller NAV-saksnummer | Se avsnitt om kravidentifikatorvalg |
| `kravidentifikatortype` | `SKATTEETATENS_KRAVIDENTIFIKATOR` eller `OPPDRAGSGIVERS_KRAVIDENTIFIKATOR` | Se avsnitt om kravidentifikatorvalg |

### 1.5 Valg av kravidentifikator ved endre og stopp

Logikken i `createKravidentifikatorPair()`:

1. Dersom `kravidentifikatorSKE` er utfylt i databasen → bruk denne med type `SKATTEETATENS_KRAVIDENTIFIKATOR`
2. Dersom `kravidentifikatorSKE` er tom og kravet ikke er et nytt krav → fall tilbake til `referansenummerGammelSak` med type `OPPDRAGSGIVERS_KRAVIDENTIFIKATOR`

---

## 2. Feilhåndtering per kravtype

### 2.1 Statuskonformering for endringer (`EndreKravService`)

Fordi hver endring alltid sender to requests (rente og hovedstol), kan de returnere ulike statuser. Dersom statusene er ulike konformeres de etter denne prioritetsrekkefølgen:

| Prioritet | HTTP-status | Årsak |
|---|---|---|
| 1 (høyest) | 404 Not Found | Kravet eksisterer ikke – begge bør speile dette |
| 2 | 422 Unprocessable Entity | Valideringsfeil er kritisk |
| 3 | 409 Conflict | Forretningskonflikter |
| 4 (lavest) | Annet | Settes til `UKJENT_STATUS` |

### 2.2 HTTP-feilkoder og statuser

Alle kall mot SKE returnerer et `RequestResult`. Funksjonen `defineStatus()` mapper HTTP-statuskode og SKE-feiltype til intern `Status`-enum:

| HTTP-status | SKE error type | Intern status |
|---|---|---|
| 2xx | – | `KRAV_SENDT` |
| 400 | `ugyldig-kravidentifikator` | `HTTP400_UGYLDIG_KRAVIDENTIFIKATOR` |
| 400 | `ugyldig-tilleggsinformasjon` | `HTTP400_UGYLDIG_TILLEGGSINFORMASJON` |
| 400 | annet | `HTTP400_UGYLDIG_FORESPORSEL` |
| 401 | – | `HTTP401_FEIL_AUTENTISERING` |
| 403 | – | `HTTP403_INGEN_TILGANG` |
| 404 | `innkrevingsoppdrag-eksisterer-ikke` | `HTTP404_FANT_IKKE_SAKSREF` |
| 404 | `innkrevingsoppdrag-er-ikke-reskontrofoert` | `HTTP404_KRAV_ER_IKKE_RESKONTROFORT` |
| 404 | annet | `HTTP404_ANNEN_IKKE_FUNNET` |
| 406 | – | `HTTP406_FEIL_MEDIETYPE` |
| 409 | `innkrevingsoppdrag-er-ikke-reskontrofoert` | `HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND` |
| 409 | `avskrevet-innkrevingsoppdrag-kan-ikke-endres` | `HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES` |
| 409 | `avskrevet-innkrevingsoppdrag-kan-ikke-avskrives` | `HTTP409_AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES` |
| 409 | `innkrevingsoppdrag-er-avskrevet` / `...-er-allerede-avskrevet` | `HTTP409_KRAV_ER_AVSKREVET` |
| 409 | `oppdragsgivers-kravidentifikator-eksisterer-allerede` | `HTTP409_KRAVIDENTIFIKATOR_EKSISTERER` |
| 409 | annet | `HTTP409_ANNEN_KONFLIKT` |
| 422 | – | `HTTP422_VALIDERINGSFEIL` |
| 500 | – | `HTTP500_INTERN_TJENERFEIL` |
| 503 | – | `HTTP503_UTILGJENGELIG_TJENESTE` |
| 300–399 | – | `HTTP300_REDIRECTION_FEIL` |
| 400–499 | annet | `HTTP400_ANNEN_KLIENT_FEIL` |
| 500–599 | annet | `HTTP500_ANNEN_SERVER_FEIL` |
| annet | – | `UKJENT_FEIL` |

### 2.3 Lagring av feilmeldinger

For alle kall som returnerer feil (ikke 2xx) lagres en rad i `feilmelding`-tabellen med:

| Kolonne | Innhold |
|---|---|
| `krav_id` | Internt krav-ID hentet via correlation-ID |
| `corr_id` | Correlation-ID for sporing på tvers av systemer |
| `saksnummer_nav` | NAVs saksnummer |
| `kravidentifikator_ske` | SKEs kravidentifikator (tom dersom vi ikke har den) |
| `error` | HTTP-statuskode som tekst |
| `melding` | `detail`-feltet fra SKEs `FeilResponse`, eller rå respons-body ved parse-feil |
| `nav_request` | JSON-serialisert request-body |
| `ske_response` | Rå HTTP-respons-body fra SKE |

En `FeilResponse` fra SKE har følgende format:

```json
{
  "type": "innkrevingsoppdrag-eksisterer-ikke",
  "title": "Innkrevingsoppdrag eksisterer ikke",
  "status": 404,
  "detail": "Fant ikke innkrevingsoppdrag med oppgitt kravidentifikator",
  "instance": "/innkrevingsoppdrag/ABC123/mottaksstatus"
}
```

Dersom responsen ikke lar seg parse til `FeilResponse` lagres en egendefinert feilmelding med `type = "egendefinert"`.

---

## 3. Asynkrone valideringsfeil fra SKE (`StatusService`)

SKE validerer krav asynkront etter mottak. Dersom et krav returnerer status `VALIDERINGSFEIL` ved polling av mottaksstatus, hentes detaljene:

1. `GET /innkrevingsoppdrag/{id}/valideringsfeil`
2. Responsen deserialiseres til `ValideringsFeilResponse`:

```json
{
  "valideringsfeil": [
    {
      "error": "ugyldig-periode",
      "message": "Tilbakekrevingsperioden er ugyldig"
    }
  ]
}
```

3. Hver valideringsfeil lagres som en rad i `feilmelding`-tabellen
4. Feilen varsles til Slack med header `"Asynk valideringsfeil"`

---

## 4. Circuit Breaker-feilhåndtering

Dersom `CircuitBreakerManager` kaster `CircuitBreakerException` eller `CallNotPermittedException`:

- For `OpprettKravService` og `StoppKravService`: `break` ut av sending-løkken – ingen flere krav sendes i denne kjøringen
- For `EndreKravService`: `break` ut av gruppe-løkken

Krav som ikke ble sendt beholder status `KRAV_IKKE_SENDT` og resendes automatisk i neste kjøring.

---

## 5. Stor-fil-sperre

Dersom en fil inneholder 1000 eller flere kravlinjer etter validering, settes `haltRun = true` i `SkeService`. Dette:

- Blokkerer neste planlagte kjøring (`handleNewKrav` returnerer tidlig)
- Logges som `"***Stor fil. Blokkerer kjøring***"`
- Resettes automatisk etter at neste kjøring er fullført (`haltRun = false`)

---

## 6. Varsler om krav som venter for lenge (`checkKravDateForAlert`)

Kjøres hvert 24. time. Henter alle krav med status som venter på svar fra SKE, og filtrerer ut de som har `tidspunktSendt` mer enn 24 timer tilbake i tid. For hvert slikt krav sendes en Slack-melding med:

- Saksnummer
- Antall dager kravet har ventet
- Nåværende status
- Tidspunkt for opprinnelig sending

---

## 7. Slack-varsling – konsolidering

`SlackService` samler feil i minnet per fil og per feiltype. Dersom mer enn 5 feil av samme type er registrert for en fil, erstattes de med én melding:

> `"N av samme type feil: <feiltype>. Sjekk avstemming"`

Dette hindrer at Slack-kanalen oversvømmes ved store filer med mange like feil.
