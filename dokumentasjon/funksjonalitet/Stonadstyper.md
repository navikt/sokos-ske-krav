# Stønadstyper og kravkode-mappinger

Oversikt over alle stønadstypene som sokos-ske-krav støtter, og hvordan de mappes fra kravkode + hjemmelkode til SKEs kravtype-identifikator.

## Bakgrunn

NAV bruker kombinasjonen av **kravkode** og **hjemmelkode** for å identifisere hvilken stønad et krav gjelder. Skatteetaten bruker derimot ett enkelt felt kalt **kravtype** (eller `StønadstypeKode`). Applikasjonen mapper derfor kravkode + hjemmelkode til en intern [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt)-enum, som igjen brukes som kravtype i SKE-requests.

> **Merk:** Av historiske årsaker heter klassen [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt) i koden, men den tilsvarer det SKE kaller `kravtype`. Begrepet `kravtype` i koden brukes separat for å angi om et krav er nytt/endring/stopp.

Dersom en kombinasjon av kravkode og hjemmelkode ikke finnes i tabellen nedenfor, vil kravet feile med `VALIDERINGSFEIL_AV_LINJE_I_FIL` og en Slack-alarm sendes. Nye stønadstyper må koordineres med SKE og legges inn i [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt)-enumen.

## Mapping-tabell

| StonadsType (intern)                             | Kravkode | Hjemmelkode |
|--------------------------------------------------|----------|-------------|
| TILBAKEKREVING_ALDERSPENSJON                     | PE AP    | T           |
| TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER           | AAP AAP  | T           |
| TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER           | AE AA    | AT          |
| TILBAKEKREVING_ATTFOERINGSPENGER                 | AE AP    | AT          |
| TILBAKEKREVING_ATTFOERINGSYTELSER                | AE AY    | AT          |
| TILBAKEKREVING_AVTALEFESTET_PENSJON              | PE XP    | T           |
| TILBAKEKREVING_AVTALEFESTET_PENSJON_PRIVATSEKTOR | PE AF    | T           |
| TILBAKEKREVING_BARNETRYGD                        | BA OR    | T           |
| TILBAKEKREVING_BARNEPENSJON                      | PE BP    | T           |
| TILBAKEKREVING_DAGPENGER                         | DP DP    | T           |
| TILBAKEKREVING_DAGPENGER                         | AE DP    | AT          |
| TILBAKEKREVING_ENGANGSSTOENAD_VED_FOEDSEL        | FA FE    | T           |
| TILBAKEKREVING_FORELDREPENGER                    | FA FØ    | T           |
| TILBAKEKREVING_FORSKUTTERTE_DAGPENGER            | FO FT    | FT          |
| TILBAKEKREVING_GAMMEL_YRKESSKADEPENSJON          | PE GY    | T           |
| TILBAKEKREVING_GJENLEVENDE_PENSJON               | PE GP    | T           |
| TILBAKEKREVING_GJENLEVENDE_PENSJON_AVREGNING     | PE GP    | TA          |
| TILBAKEKREVING_KOMPENSASJON_NAERING_OG_FRILANS   | FR SN    | T           |
| TILBAKEKREVING_KONTANTSTOETTE                    | KS KS    | T           |
| TILBAKEKREVING_KRIGSPENSJON                      | PE KP    | T           |
| TILBAKEKREVING_LOENNSKOMPENSASJON                | LK RF    | T           |
| TILBAKEKREVING_MOBILITETSFREMMENDE_STOENADER     | AE MS    | AT          |
| TILBAKEKREVING_OMSORGSPENGER                     | BS OM    | T           |
| TILBAKEKREVING_OMSTILLINGSSTOENAD                | OM OM    | T           |
| TILBAKEKREVING_OMSTILLINGSSTOENAD_ETTEROPPGJOER  | OM OM    | EO          |
| TILBAKEKREVING_OPPLAERINGSPENGER                 | BS OP    | T           |
| TILBAKEKREVING_OVERGANGSSTOENAD                  | EF OG    | T           |
| TILBAKEKREVING_PERMITTERINGSPENGER_KORONA        | LK LK    | T           |
| TILBAKEKREVING_PLEIEPENGER_BARN                  | BS PN    | T           |
| TILBAKEKREVING_PLEIEPENGER_NAERSTAAENDE          | BS PP    | T           |
| TILBAKEKREVING_SPESIALUTBETALING                 | AE SU    | AT          |
| TILBAKEKREVING_STOENAD_TIL_BARNETILSYN           | EF BT    | T           |
| TILBAKEKREVING_SUPPLERENDE_STOENAD_ALDERSPENSJON | SU AP    | T           |
| TILBAKEKREVING_SUPPLERENDE_STOENAD_UFOEREPENSJON | SU UF    | T           |
| TILBAKEKREVING_SVANGERSKAPSPENGER                | FA SV    | T           |
| TILBAKEKREVING_SYKEPENGER                        | KT SP    | T           |
| TILBAKEKREVING_TIDLIGERE_FAMILIEPLEIER_PENSJON   | PE FP    | T           |
| TILBAKEKREVING_TILLEGGSTOENAD                    | TS TS    | T           |
| TILBAKEKREVING_TILLEGGSTOENAD                    | AE TA    | AT          |
| TILBAKEKREVING_TILLEGGSTOENAD                    | AE TT    | AT          |
| TILBAKEKREVING_TILLEGGSTOENADER                  | AE TS    | AT          |
| TILBAKEKREVING_TILTAKSPENGER                     | TP TP    | T           |
| TILBAKEKREVING_TILTAKSPENGER                     | AE IS    | AT          |
| TILBAKEKREVING_UFOEREPENSJON                     | PE UP    | T           |
| TILBAKEKREVING_UFOERETRYGD                       | PE UT    | T           |
| TILBAKEKREVING_UFOERETRYGD_AVREGNING             | PE UT    | TA          |
| TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER         | PE UT    | EU          |
| TILBAKEKREVING_UNGDOMSPROGRAMYTELSEN             | UNG      | T           |
| TILBAKEKREVING_UTDANNINGSSTOENAD                 | EF UT    | T           |

## Legge til ny stønadstype

1. Koordiner med SKE at de har lagt inn stønadstypekoden på sin side
2. Legg til en ny entry i [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt)-enumen i [`StonadsType.kt`](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt) med riktig kravkode og hjemmelkode
3. Oppdater denne tabellen
4. Test manuelt ved å sende inn en fil med den nye kravkoden (se [Manuell testing](../testing/Manuell_testing.md))

