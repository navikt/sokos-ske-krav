# Begrepsforklaring

## Generelt

| Begrep | Betyr        |
|:-------|:-------------|
| SKE    | Skatteetaten |

## Knyttet til fil

| Begrep                                                                             | Betyr                                               |
|:-----------------------------------------------------------------------------------|:----------------------------------------------------|
| Header                                                                             | Første linje i innfil, brukes kun til filvalidering |
| [`KravLinje`](../../src/main/kotlin/no/nav/sokos/ske/krav/copybook/FixedRecord.kt) | De enkelte krav som skal overføres skatteetaten     |
| Footer                                                                             | Siste linje i innfil, brukes kun til filvalidering  |

## Avsendere

Kravfiler kan komme fra ulike fagsystemer. Applikasjonen skiller mellom følgende avsendere:

| Avsender    | Beskrivelse                                              |
|:------------|:---------------------------------------------------------|
| `OB04`      | Oppdrag/Z – det primære fagsystemet som sender kravfiler |
| `ARENA`     | Arena fagsystem                                          |
| `PESYS`     | Pensjonssystemet (PESYS)                                 |
| `INFOTRYGD` | Infotrygd fagsystem                                      |

> **Merk:** Valideringsreglene for utbetalingsdato er strengere for `OB04` enn for de øvrige avsenderne. For `ARENA`, `PESYS` og `INFOTRYGD` er tom/ugyldig utbetalingsdato akseptert.

## Knyttet til krav

| Begrep                                                                             | Betyr i Nav                                             | Betyr i Skatt                   | Kommentar                                                                                                            |
|:-----------------------------------------------------------------------------------|:--------------------------------------------------------|:--------------------------------|:---------------------------------------------------------------------------------------------------------------------|
| SaksnummerNav                                                                      | Saksnummer fra økonomikjernen                           | Oppdragivers kravidentifikator  | Må være unik for hvert krav                                                                                          |
| GjelderID                                                                          | Personnummer/organisasjonsnummer                        | Skyldner                        | Må være unik for hvert krav                                                                                          |
| KravidentifikatorSke                                                               | Skatteetatens kravidentifikator for kravet              | Kravidentifikator               | Mottas fra Skatteetaten ved nytt krav                                                                                |
| OppdragsgiversReferanse                                                            | Fagsystem ID                                            | Oppdragsgivers referanse        | Fagsystemet som opprettet transaksjonen                                                                              |
| ReferansenummerGammelSak                                                           | Gammelt saksnummer ved endring                          |                                 |                                                                                                                      |
| Kravkode                                                                           | Ytelse kravet gjelder                                   | StønadstypeKode                 | f.eks. "PE AP" for "Alderspensjon" mappes med hjemmelskode til Kravtype                                              |
| Hjemmelskode                                                                       | Hjemmelkode                                             | StønadstypeKode                 | Lovhjemmel, f.eks. "TA" for §22-16 i folketrygdloven                                                                 |
| [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt) | Stønadstype                                             | Kravtype                        | Mappet basert på kravkode og hjemmelskode                                                                            |
| Vedtaksdato                                                                        | Vedtaksdato                                             | FastsettelsesDato               | Datoen da tilbakekrevingsvedtaket ble opprettet                                                                      |
| UtbetalDato                                                                        | Utbetalingsdato                                         | ForeldelsesFristensUtgangspunkt | Datoen da feilutbetalingen ble utbetalt                                                                              |
| FremtidigYtelse                                                                    | Fremtidig ytelse tilgjengelig for avregning             | YtelseForAvregningBeloep        | Total ytelse tilgjengelig for avregning. Benyttes ved "avregning" som innkrevingstiltak                              |
| Periode FOM og TOM                                                                 | Utbetalingsperioden tilbakekrevingsvedtaket gjelder for | Tilbakekrevingsperiode          | Tidsperioden hvor det ble foretatt utbetalinger som er avdekket som feilaktige og som det kreves tilbakebetaling for |
| Hovedstol                                                                          | Beløp/Hovedstol                                         | Hovedstol                       | Beløpet som skal kreves inn – feltet `belop` i kravlinja                                                             |
| BelopRente                                                                         | Rentebeløp                                              | Rentebeløp                      |                                                                                                                      |
| Kravtype                                                                           | Kravtype                                                | NA                              | Begrep i koden for å identifisere kravet som nytt, endring eller stopp                                               |
| NyttKrav                                                                           | Opprettelse av krav                                     | OpprettKrav                     |                                                                                                                      |
| EndreKrav                                                                          | Endring av krav                                         | Endre krav                      | Innebærer endring av Hovedstol og endring av rente                                                                   |
| StoppKrav                                                                          | Avskrive et krav                                        | Avskriv krav                    | Avslutter innkreving                                                                                                 |
