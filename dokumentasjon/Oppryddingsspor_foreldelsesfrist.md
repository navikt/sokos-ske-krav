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

OS (`avsender = OB04`) kan samtidig sende en `STOPP` (nullperiode) for samme sak. Denne OS-stoppen lagres med både `kravtype = IGNORERES` og `status = IGNORERES` for sporbarhet, men sendes ikke til SKE. `STOPP` fra andre avsendere følger ordinær flyt, også når det finnes en historisk foreldelsesfrist-feil.

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
    M -->|OS| I[Lagre som IGNORERES]
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

Oppryddingssporet følger en referansekjede med ett opprinnelig krav, ett oppryddingskrav og senere oppfølgingskrav:

1. Det opprinnelige, avviste kravet har `saksnummer = A`.
2. Oppryddingskravet kommer inn som `ENDRING` med et nytt `saksnummer = B` og `referansenummerGammelSak = A`. Det lagres som `NYTT_KRAV` og sendes til SKE med `oppdragsgiversKravIdentifikator = B`.
3. En senere oppfølging får et nytt `saksnummer = C` og `referansenummerGammelSak = B`.

Senere oppfølginger, som C, følger ordinær endrings- eller stopprute. De er ikke oppryddingskrav fordi referansen er B, ikke den opprinnelige avviste saken A. Hvis B ikke er effectuert når C behandles, håndteres eventuelle SKE-feil gjennom tjenestens ordinære feil- og avstemmingsflyt.

STOPP i selve oppryddingsfilen fra OS lagres med både `kravtype = IGNORERES` og `status = IGNORERES`, og holdes utenfor sending og resend.

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
        SkeService->>DB: Lagre som IGNORERES
        Note over SkeService: Ingen SKE-kall utføres
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
| STOPP fra `OB04` | Ja | Lagres med både `kravtype = IGNORERES` og `status = IGNORERES`; ingen utsending til SKE. |
| STOPP fra annen avsender | Ja | Lagres og sendes som ordinær `STOPP_KRAV`. |
| ENDRING eller STOPP | Nei, eller tidligere opprydding er løst | Normal flyt er uendret. |
| NYTT_KRAV | Ikke vurdert | Normal flyt er uendret. |

## Observability og drift

`SkeService` beregner oppryddingsreferansene ved å bruke `FeilmeldingRepository`. `KravRepository` bruker deretter det ferdige settet når krav og linjevalideringsfeil lagres i én transaksjon. Oppslaget støttes av indeksen `idx_feilmelding_saksnummer_nav`. Det skrives kun aggregert ordinær logg uten saksnummer eller andre personopplysninger. En ignorert oppryddings-`STOPP` fra `OB04` er synlig i `krav` med typen `IGNORERES`, men gir ikke SKE-kall.

Sporet er avgrenset til det eksakte meldingsfragmentet over. Hvis SKE endrer feilmeldingsteksten, vil kravene følge ordinær flyt til oppslagsregelen oppdateres.

## Testdekning

Database-fixturen [`OppryddingForeldelsesfrist.sql`](../src/test/resources/SQLscript/feilmeldinger/OppryddingForeldelsesfrist.sql) inneholder både relevante foreldelsesfrist-feil og en nærliggende, men ikke-matchet feilmelding.

Testene verifiserer:

- oppslag med treff, ikke-treff og ukjent saksnummer
- ENDRING som lagres som én `NYTT_KRAV`
- STOPP fra `OB04` som lagres med `kravtype = IGNORERES` uten utsending til SKE
- STOPP fra andre avsendere som lagres som ordinær `STOPP_KRAV`
- SFTP-innlesing og utsending av oppryddings-ENDRING via opprett-krav-ruten
- at øvrige ENDRING-er i samme fil fortsatt bruker ordinær endringsrute
- at en tidligere foreldelsesfrist-feil ikke lenger utløser oppryddingssporet etter vellykket opprettelse med SKE-kravidentifikator
- at en senere `ENDRING` da lagres som `ENDRING_HOVEDSTOL` og `ENDRING_RENTE`, og at en senere `STOPP` lagres og sendes som `STOPP_KRAV`
- at senere oppfølgingskrav følger ordinær endrings- og stopprute
