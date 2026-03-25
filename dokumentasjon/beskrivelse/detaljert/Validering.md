# Detaljert beskrivelse – validering

Dokumentasjonen dekker alle valideringsregler som kjøres i sokos-ske-krav, delt inn i filvalidering og linjevalidering.

---

## 1. Filvalidering ([`FileValidator`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/FileValidator.kt))

Filvalidering kjøres på hele filen umiddelbart etter nedlasting fra SFTP. Filen er bygget opp av tre seksjoner:

- **Header** ([`KontrollLinjeHeader`](../../../src/main/kotlin/no/nav/sokos/ske/krav/copybook/FixedRecord.kt)) – første linje, brukes kun til filvalidering
- **Kravlinjer** ([`KravLinje`](../../../src/main/kotlin/no/nav/sokos/ske/krav/copybook/FixedRecord.kt)) – n antall datarader
- **Footer** ([`KontrollLinjeFooter`](../../../src/main/kotlin/no/nav/sokos/ske/krav/copybook/FixedRecord.kt)) – siste linje, inneholder kontrollsummer

### Regler

| Regel          | Beskrivelse                                                                              | Feilmelding                                                     |
|----------------|------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| Parsbar fil    | Filen må kunne parses uten unntak                                                        | `"Exception i parsing av fil"`                                  |
| Antall stemmer | `footer.antallTransaksjoner` må være lik faktisk antall kravlinjer                       | `"Antall krav stemmer ikke med antallet i siste linje"`         |
| Sum stemmer    | Summen av `belop + belopRente` for alle linjer må være lik `footer.sumAlleTransaksjoner` | `"Sum alle linjer stemmer ikke med sum i siste linje"`          |
| Dato stemmer   | `header.transaksjonsDato` må være lik `footer.transaksjonTimestamp`                      | `"Dato sendt er avvikende mellom første og siste linje fra OS"` |

### Ved feil

- Alle feilmeldinger samles og sendes til Slack
- Filen flyttes fra `/inbound` til `/inbound/feilfiler`
- Feilen lagres i `filvalideringsfeil`-tabellen
- Ingen kravlinjer fra filen behandles videre

---

## 2. Linjevalidering ([`LineValidationRules`](../../../src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidationRules.kt))

Linjevalidering kjøres på hver enkelt kravlinje etter at filvalideringen er godkjent. Valideringsreglene speiler SKEs synkrone valideringsregler (se [SKE sin dokumentasjon](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler)).

En spesialverdi brukes som sentinel-dato for feilformaterte datoer: `21240101` (år 2124). Alle datoer som ikke lar seg parse returneres som denne verdien for å gi meningsfylte feilmeldinger fremfor unntak.

### 2.1 Vedtaksdato

Feltet tilsvarer `fastsettelsesdato` hos SKE.

| Regel | Betingelse | Feilmelding |
|---|---|---|
| Gyldig format | Datoen kan parses som `yyyyMMdd` | `"Vedtaksdato er feil formattert i fil"` |
| Ikke i fremtiden | `vedtaksdato <= i dag` | `"Vedtaksdato kan ikke være i fremtiden"` |

### 2.2 Utbetalingsdato

Feltet tilsvarer `foreldelsesfristensUtgangspunkt` hos SKE.

| Regel | Betingelse | Feilmelding |
|---|---|---|
| Gyldig format (kun OB04) | Dersom avsender er `OB04` MÅ datoen kunne parses | `"Utbetalingsdato er feil formattert i fil"` |
| Tom tillatt for andre avsendere | For `ARENA`, `PESYS` og `INFOTRYGD` er tom/ugyldig dato akseptert | – |
| Før vedtaksdato | `utbetalingsdato < vedtaksdato` | `"Utbetalingsdato må være tidligere enn vedtaksdato"` |

### 2.3 Periode (FOM og TOM)

| Regel                         | Betingelse                              | Feilmelding                                           |
|-------------------------------|-----------------------------------------|-------------------------------------------------------|
| Gyldig format FOM             | FOM kan parses som `yyyyMMdd`           | `"FOM er feil formattert i fil"`                      |
| Gyldig format TOM             | TOM kan parses som `yyyyMMdd`           | `"TOM er feil formattert i fil"`                      |
| FOM ikke etter TOM            | `periodeFOM <= periodeTOM`              | `"Periode FOM kan ikke være etter TOM"`               |
| TOM ikke for langt frem i tid | `periodeTOM < første dag i neste måned` | `"Periode TOM kan ikke være etter inneværende måned"` |

### 2.4 Tilleggsfristdato

| Regel           | Betingelse                                         | Feilmelding                                                                             |
|-----------------|----------------------------------------------------|-----------------------------------------------------------------------------------------|
| Gyldig format   | Datoen kan parses                                  | `"Tilleggsfristdato er feil formattert i fil"`                                          |
| Ikke for gammel | `tilleggsfrist >= i dag - 10 måneder`              | `"Tilleggsfristdato kan ikke være lengre tilbake i tid enn 10 måneder fra dagens dato"` |
| Valgfri         | Feltet er nullable – ingen validering dersom blank | –                                                                                       |

### 2.5 Saksnummer

| Regel         | Betingelse                       | Feilmelding                             |
|---------------|----------------------------------|-----------------------------------------|
| Gyldig format | Matcher regex `^[a-zA-Z0-9-/]+$` | `"Saksnummer er feil formattert i fil"` |

### 2.6 ReferanseNummerGammelSak

| Regel                                   | Betingelse                                                                          | Feilmelding                                           |
|-----------------------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------|
| Gyldig format (kun for endringer/stopp) | Dersom kravet IKKE er et nytt krav, må referansen matche samme regex som saksnummer | `"ReferanseNummerGammelSak er feil formattert i fil"` |
| Ikke validert for nye krav              | For `isOpprettKrav() == true` hoppes denne valideringen over                        | –                                                     |

### 2.7 Kravtype (kravkode + hjemmelkode)

| Regel                | Betingelse                                                                                                                                              | Feilmelding                                                 |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| Kombinasjonen finnes | [`StonadsType.getStonadstype(kravkode, kodeHjemmel)`](../../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt) må returnere en kjent verdi | `"Kravtype finnes ikke definert for oversending til skatt"` |

### 2.8 FagsystemId (oppdragsgiversReferanse)

`fagsystemId` er **ikke** underlagt linjevalidering. Feltet er valgfritt – det aksepteres at det er tomt eller blankt, og det medfører ingen feil. Dersom `fagsystemId` er tom, utelates feltet `oppdragsgiversReferanse` helt fra requesten mot SKE.

### Ved linjevalideringsfeil

- Linjen får status `VALIDERINGSFEIL_AV_LINJE_I_FIL`
- Feilen lagres i `filvalideringsfeil`-tabellen
- Alle feil samles og sendes til Slack etter at alle linjer er behandlet
- Øvrige gyldige linjer i filen får status `KRAV_IKKE_SENDT` og behandles normalt

### Klassifisering av kravlinjer

Kravtypen utledes fra selve kravlinjens innhold, ikke fra et eksplisitt felt:

| Logikk                                               | Kravtype                              |
|------------------------------------------------------|---------------------------------------|
| `belop == 0`                                         | `STOPP_KRAV`                          |
| `referansenummerGammelSak` er utfylt og `belop != 0` | `ENDRING_RENTE` + `ENDRING_HOVEDSTOL` |
| Ingen av de over                                     | `NYTT_KRAV`                           |

For endringer opprettes to rader i databasen – én med `ENDRING_RENTE` og én med `ENDRING_HOVEDSTOL`.
