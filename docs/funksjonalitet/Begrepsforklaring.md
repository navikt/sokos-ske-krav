### Generelle overordnet
| Begrep | Betyr        |
|:-------|:-------------|
| SKE    | Skatteetaten |

### Knyttet til Fil
| Begrep    | Betyr                                               |
|:----------|:----------------------------------------------------|
| Header    | Første linje i innfil, brukes kun til filvalidering |
| KravLinje | De enkelte krav som skal overføres skatteetaten     |
| Footer    | Siste linje i innfil, brukes kun til filvalidering  |

### Avsendere
Kravfiler kan komme fra ulike fagsystemer. Applikasjonen skiller mellom følgende avsendere:

| Avsender    | Beskrivelse                                              |
|:------------|:---------------------------------------------------------|
| `OB04`      | Oppdrag/Z – det primære fagsystemet som sender kravfiler |
| `ARENA`     | Arena fagsystem                                          |
| `PESYS`     | Pensjonssystemet (PESYS)                                 |
| `INFOTRYGD` | Infotrygd fagsystem                                      |

> **Merk:** Valideringsreglene for utbetalingsdato er strengere for `OB04` enn for de øvrige avsenderne. For `ARENA`, `PESYS` og `INFOTRYGD` er tom/ugyldig utbetalingsdato akseptert.

### Knyttet til Krav
| Begrep                   | Betyr i Nav                                             | Betyr i Skatt                   | Kommentar                                                                                                                            |
|:-------------------------|:--------------------------------------------------------|:--------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------|
| SaksnummerNav            | Saksnummer fra økonomikjernen                           | Oppdragivers kravidentifikator  | Må være unik for hvert krav                                                                                                          |
| GjelderID                | Personnummer/organisasjonsnummer                        | Skyldner                        | Må være unik for hvert krav                                                                                                          |
| KravidentifikatorSke     | Skatts kravidentifikator for kravet                     | Kravidentifikator               | Mottas fra skatt ved nytt krav                                                                                                       |
| OppdragsgiversReferanse  | Fagsystem ID                                            | Oppdragsgivers referanse        | Fagsystemet som opprettet transaksjonen                                                                                              |
| ReferansenummerGammelSak | Gammelt saksnummer ved endring                          |                                 |                                                                                                                                      |
| Kravkode                 | Ytelse kravet gjelder                                   | StønadstypeKode                 | f.eks: "PE AP" for "Alderspensjon" mappes med hjemmelskode til Kravtype                                                              |
| Hjemmelskode             | Hjemmelkode                                             | StoenadstypeKode                | Lovhjemmel. f.eks: "TA" for §22-16 i folketrygdloven                                                                                 |
| StonadsType              | StønadsType                                             | Kravtype                        | Mappet basert på kravkode og hjemmelskode                                                                                            |
| Vedtaksdato              | Vedtaksdato                                             | FastsettelsesDato               | Datoen da tilbakekrevingsvedtaket ble opprettet                                                                                      |
| UtbetalDato              | Utbetalingsdato                                         | ForeldelsesFristensUtgangspunkt | Datoen da feilutbetalingen ble utbetalt                                                                                              |
| FremtidigYtelse          | Fremtidig ytelse tilgjengelig for avregning             | YtelseForAvregningBeloep        | Total ytelse tilgjengelig for avregning, om noe. Benyttes av innkrevingsmyndigheten ved "avregning" som innkrevingstiltak.           |
| Periode FOM og TOM       | Utbetalingsperioden tilbakekrevingsvedtaket gjelder for | Tilbakekrevingsperiode          | Tidsperioden hvor det ble foretatt utbetalinger som senere er avdekket som feilaktige, og som det derfor kreves tilbakebetaling for. |
| Hovedstol                | Beløp/Hovedstol                                         | Hovedstol                       | Beløpet som skal kreves inn og er feltet "beløp" i kravlinja                                                                         |
| BelopRente               | Rentebeløp                                              | Rentebeløp                      |                                                                                                                                      |
| Kravtype                 | Kravtype                                                | NA                              | Dette er et begrep i koden for å identifisere kravet som nytt, endring eller stopp                                                   |
| NyttKrav                 | Opprettelse av krav                                     | OpprettKrav                     |                                                                                                                                      |
| EndreKrav                | Endring av krav                                         | Endre Krav                      | Innebærer endring av Hovedstol og Endring av rente                                                                                   |
| StoppKrav                | Avskrive et krav                                        | Avskriv Krav                    | Avslutter innkreving                                                                                                                 |




