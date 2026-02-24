# Forventet resultat av kjøring av Fil-B.txt

NB! Denne filen skal motta en asynkron valideringsfeil. Dvs at du må trigge sending to ganger slik at mottaksstatus blir kalt. Request og respons fra asynkron valideringsfeil vil IKKE lagres i Feilmelding.

## Feil
| Case                                            | Forventet resultat                                  | pnr/org      | Kravtype                          | Stønadstype | Hjemmelskode |
|-------------------------------------------------|-----------------------------------------------------|--------------|-----------------------------------|-------------|--------------|
| Person som ikke eksisterer i part (eller tenor) | Får feil - Person eksiterer ikke                    | 29527141786  | TILBAKEKREVING_SYKEPENGER         | KT SP       | T            |
| Person                                          | Får feil, kravtypen kan ikke mottas på org          | 01828896640  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| D-nummer                                        | Får feil, kravtypen kan ikke mottas på org          | 45864500173  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |


##  Krav
```select filnavn, saksnummer_nav, referansenummergammelsak, belop, belop_rente, fremtidig_ytelse, vedtaksdato,utbetaldato,gjelder_id, periode_fom, periode_tom, kravkode, kode_hjemmel, transaksjonsdato, enhet_bosted, enhet_behandlende, fagsystem_id, kravtype, status, tilleggsfrist, avsender from krav where filnavn = 'Fil-B.txt' and  tidspunkt_opprettet > '2026-02-24 13:00:00'``` (bytt ut timestamp med det som passer)

| filnavn   | saksnummer_nav | referansenummergammelsak | belop | belop_rente | fremtidig_ytelse | vedtaksdato                | utbetaldato                | gjelder_id  | periode_fom | periode_tom | kravkode | kode_hjemmel | transaksjonsdato | enhet_bosted | enhet_behandlende | fagsystem_id   | kravtype  | status              | tilleggsfrist | avsender |
|-----------|----------------|--------------------------|-------|-------------|------------------|----------------------------|----------------------------|-------------|-------------|-------------|----------|--------------|------------------|--------------|-------------------|----------------|-----------|---------------------|---------------|----------|
| Fil-B.txt | 2146407nr      |                          | 12562 | 100         | 100100.00        | 2024-03-18 00:00:00.000000 | 2024-01-07 00:00:00.000000 | 01828896640 | 20230501    | 20230930    | LK RF    | T            | 20240320         | 4402         | 4819              | Fil-B-Test-002 | NYTT_KRAV | 422_VALIDERINGSFEIL | null          | OB04     |
| Fil-B.txt | 1432637        |                          | 7000  | 100         | 0.00             | 2024-03-18 00:00:00.000000 | 2024-01-07 00:00:00.000000 | 45864500173 | 20230501    | 20230930    | LK RF    | T            | 20240320         | 4402         | 4819              | Fil-B-Test-004 | NYTT_KRAV | 422_VALIDERINGSFEIL | null          | OB04     |
| Fil-B.txt | 4873465kke     |                          | 2500  | 100         | 0.00             | 2024-03-18 00:00:00.000000 | 2024-01-07 00:00:00.000000 | 29527141786 | 20230501    | 20230930    | KT SP    | T            | 20240320         | 4416         | 4819              | Fil-B-Test-001 | NYTT_KRAV | VALIDERINGSFEIL     | null          | OB04     |
| Fil-B.txt | 7776149        |                          | 13000 | 100         | 0.00             | 2024-03-18 00:00:00.000000 | 2024-01-07 00:00:00.000000 | 42827200551 | 20230501    | 20230930    | EF UT    | T            | 20240320         | 4407         | 4819              | Fil-B-Test-003 | NYTT_KRAV | RESKONTROFOERT      | null          | OB04     |
| Fil-B.txt | 9015040Doed    |                          | 3980  | 100         | 250250.00        | 2024-03-18 00:00:00.000000 | 2024-01-07 00:00:00.000000 | 46918300929 | 20230501    | 20230930    | BA OR    | T            | 20240320         | 4403         | 4819              | Fil-B-Test-005 | NYTT_KRAV | RESKONTROFOERT      | null          | OB04     |


## Feilmelding
```select saksnummer_nav, error, melding, ske_response from feilmelding where saksnummer_nav in (select krav.saksnummer_nav from krav where filnavn = 'Fil-B.txt' and DATE(tidspunkt_opprettet) = DATE(now()))``` (eller bruk timestamp)

| saksnummer_nav | error                  | melding                                                                                                                                       | ske_response |
|----------------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| 2146407nr      | 422                    | Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON | `{"type":"tag:skatteetaten.no,2026:innkreving:innkrevingsoppdrag:kravtype-gjelder-kun-for-organisasjon","title":"Kravtype gjelder kun for organisasjon","status":422,"detail":"Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON","instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag"}` |
| 1432637        | 422                    | Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON | `{"type":"tag:skatteetaten.no,2026:innkreving:innkrevingsoppdrag:kravtype-gjelder-kun-for-organisasjon","title":"Kravtype gjelder kun for organisasjon","status":422,"detail":"Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON","instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag"}` |
| 4873465kke     | PERSON_EKSISTERER_IKKE | Person med fødselsdato=295271 eksisterer ikke                                                                                                 |              |
