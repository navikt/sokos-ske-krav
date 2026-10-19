# Oppryddingsspor for foreldelsesfristens utgangspunkt

## Innhold

- [Formål](#formål)
- [Beslutningsflyt ved innlesing](#beslutningsflyt-ved-innlesing)
- [Utsending til SKE](#utsending-til-ske)
- [Avgrensning og oppslagsregel](#avgrensning-og-oppslagsregel)
- [Effekt](#effekt)
- [Observability og drift](#observability-og-drift)
- [Testdekning](#testdekning)

## Formål

Vedtaksløsningene kan sende en korrigering som `ENDRING` etter at det opprinnelige kravet ble avvist av SKE fordi `foreldelsesfristensUtgangspunkt` var for langt tilbake i tid. Korrigeringen har tilleggsfrist, men SKE kan ikke endre et krav som aldri ble opprettet. Derfor behandles denne avgrensede kategorien som et nytt krav.

OS (`avsender = OB04`) kan samtidig sende en `STOPP` (nullperiode) for samme sak. Denne OS-stoppen skal ikke lagres eller sendes til SKE. `STOPP` fra andre avsendere følger ordinær flyt, også når det finnes en historisk foreldelsesfrist-feil.

## Beslutningsflyt ved innlesing

```mermaid
flowchart TD
    A[Gyldig kravlinje fra fil] --> B{Normal type?}
    B -->|NYTT_KRAV| N[Lagre som NYTT_KRAV]
    B -->|ENDRING eller STOPP| C[Les referansenummerGammelSak]
    C --> D{Finnes foreldelsesfrist-feil med samme saksnummer?}
    D -->|Nei eller tidligere opprydding er sendt| E[Normalt løp]
    D -->|Ja| G{Innkommende type?}
    G -->|ENDRING| H[Lagre én rad som NYTT_KRAV]
    G -->|STOPP| M{Avsender?}
    M -->|OS| I[Ignorer linjen]
    M -->|Andre| K[Lagre STOPP_KRAV]
    E -->|ENDRING| J[Lagre ENDRING]
    E -->|STOPP| K[Lagre STOPP_KRAV]
    N --> L[Flytt fil til outbound]
    H --> L
    I --> L
    J --> L
    K --> L
```

Oppslaget gjøres bare for linjer som allerede er klassifisert som `ENDRING` eller `STOPP`. Vanlige nye krav slår ikke opp i `feilmelding` og påvirkes ikke av oppryddingssporet.

### Referansekjede for opprydding

Oppryddingssporet følger en referansekjede med ett opprinnelig krav, ett oppryddingskrav og vilkårlig mange senere oppfølgingskrav:

1. Det opprinnelige, avviste kravet har `saksnummer = A`.
2. Oppryddingskravet kommer inn som `ENDRING` med et nytt `saksnummer = B` og `referansenummerGammelSak = A`. Det lagres som `NYTT_KRAV` og sendes til SKE med `oppdragsgiversKravIdentifikator = B`.
3. En senere oppfølging får et nytt `saksnummer = C` og `referansenummerGammelSak = B`. Videre oppfølging kan fortsette kjeden, for eksempel `D` med `referansenummerGammelSak = C`.

Hvis oppryddingskravet `B` mangler SKE-kravidentifikator, lagres alle senere krav i kjeden, inkludert `C` og `D`, med en avventende status i stedet for å følge ordinær utsending: `PENDING_ENDRING_HOVEDSTOL`, `PENDING_ENDRING_RENTE` eller `PENDING_STOPP`. Dette hindrer at et krav sendes til SKE før det oppryddende kravet er opprettet. En avventende status kan frigjøres manuelt i frontend når `B` har fått en SKE-kravidentifikator.

STOPP i selve oppryddingsfilen følger fortsatt den vanlige OS-regelen og ignoreres. Det opprettes en Slack-alarm, og operatøren kan se og frigjøre hvert avventende krav fra **Krav som venter på opprydding** under `/rapporter`. Frigivelse er bare mulig etter at `B` har fått en SKE-kravidentifikator; uansett hvilket ledd i kjeden som frigjøres, følges referansene tilbake til `B`, identifikatoren fra `B` kopieres til det frigjorte kravet, status settes til `KRAV_IKKE_SENDT`, og kravet går gjennom tjenestens vanlige resend-løp.

## Utsending til SKE

```mermaid
sequenceDiagram
    autonumber
    participant OS as Fil på SFTP
    participant SkeService
    participant DB as PostgreSQL
    participant SKE as SKE API

    OS->>SkeService: ENDRING eller STOPP med referansenummerGammelSak
    SkeService->>DB: FeilmeldingRepository: søk etter "foreldelsesfrist" feil med saksnummer_nav og meldingsfragment
    alt Treff: ENDRING
        DB-->>SkeService: Foreldelsesfrist-feil funnet
        SkeService->>DB: Lagre som NYTT_KRAV
        SkeService->>SKE: POST opprett krav
        SKE-->>SkeService: Respons
        SkeService->>DB: Oppdater status og SKE-kravidentifikator
    else Treff: STOPP fra OB04
        DB-->>SkeService: Foreldelsesfrist-feil funnet
        Note over SkeService: Ingen rad lagres og ingen SKE-kall utføres
    else Treff: STOPP fra annen avsender
        DB-->>SkeService: Foreldelsesfrist-feil funnet
        SkeService->>SkeService: Følg ordinær stopprute
    else Ikke treff
        DB-->>SkeService: Ingen relevant historisk feil
        SkeService->>SkeService: Følg ordinær endrings- eller stopprute
    end
```

## Avgrensning og oppslagsregel

Ved innlesing samler `SkeService` først distinkte `referansenummerGammelSak` for linjer som normalt klassifiseres som `ENDRING` eller `STOPP`. Referansene slås deretter opp samlet mot `feilmelding.saksnummer_nav` via `FeilmeldingRepository`. Dette gir ett databaseoppslag per fil i stedet for ett oppslag per linje.

Linjen går i oppryddingssporet bare når det finnes en historisk feilmelding hvor `melding` inneholder:

```
foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.
```

En slik feilmelding regnes som **løst** når et relatert krav har fått en ikke-blank SKE-kravidentifikator etter vellykket opprettelse. Et krav er relatert når det enten har samme `saksnummer_nav` som feilmeldingen, eller har dette saksnummeret som `referansenummerGammelSak`. Løste feilmeldinger beholdes for historikk og rapportering, men brukes ikke videre til å klassifisere nye linjer som opprydding. Dermed følger senere reelle `ENDRING`- og `STOPP`-linjer ordinær flyt.

## Effekt

| Innkommende type | Treff i uløst historisk feilmelding | Resultat |
| --- | --- | --- |
| ENDRING | Ja | Én rad med `kravtype = NYTT_KRAV`; sendes via opprett-krav-endepunktet. |
| STOPP fra `OB04` | Ja | Ignoreres; ingen database-rad og ingen utsending til SKE. |
| STOPP fra annen avsender | Ja | Lagres og sendes som ordinær `STOPP_KRAV`. |
| ENDRING eller STOPP | Nei, eller tidligere opprydding er løst | Normal flyt er uendret. |
| NYTT_KRAV | Ikke vurdert | Normal flyt er uendret. |

## Observability og drift

`SkeService` beregner oppryddingsreferansene ved å bruke `FeilmeldingRepository`. `KravRepository` bruker deretter det ferdige settet når krav og linjevalideringsfeil lagres i én transaksjon. Oppslaget støttes av indeksen `idx_feilmelding_saksnummer_nav`. Det skrives kun aggregert ordinær logg uten saksnummer eller andre personopplysninger. En ignorert oppryddings-`STOPP` fra `OB04` er derfor ikke synlig som krav, feilmelding eller SKE-kall; dette er forventet oppførsel.

Sporet er avgrenset til det eksakte meldingsfragmentet over. Hvis SKE endrer feilmeldingsteksten, vil kravene følge ordinær flyt til oppslagsregelen oppdateres.

## Testdekning

Database-fixturen [`OppryddingForeldelsesfrist.sql`](../src/test/resources/SQLscript/feilmeldinger/OppryddingForeldelsesfrist.sql) inneholder både relevante foreldelsesfrist-feil og en nærliggende, men ikke-matchet feilmelding.

Testene verifiserer:

- oppslag med treff, ikke-treff og ukjent saksnummer
- ENDRING som lagres som én `NYTT_KRAV`
- STOPP fra `OB04` som ikke lagres
- STOPP fra andre avsendere som lagres som ordinær `STOPP_KRAV`
- SFTP-innlesing og utsending av oppryddings-ENDRING via opprett-krav-ruten
- at øvrige ENDRING-er i samme fil fortsatt bruker ordinær endringsrute
- at en tidligere foreldelsesfrist-feil ikke lenger utløser oppryddingssporet etter vellykket opprettelse med SKE-kravidentifikator
- at en senere `ENDRING` da lagres som `ENDRING_HOVEDSTOL` og `ENDRING_RENTE`, og at en senere `STOPP` lagres og sendes som `STOPP_KRAV`
- at avhengige oppfølgingskrav lagres med `PENDING_*`-status og må frigjøres manuelt
