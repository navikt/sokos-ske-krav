# Forventet resultat av kjøring av Fil-B.txt

5 nye krav, hvor 3 får feil. 1 av disse feilene er asynkron


## Feil
| Case                                            | Forventet resultat                                  | pnr/org      | Kravtype                          | Stønadstype | Hjemmelskode |
|-------------------------------------------------|-----------------------------------------------------|--------------|-----------------------------------|-------------|--------------|
| Person som ikke eksisterer i part (eller tenor) | Får feil - Person eksiterer ikke                    | 29527141786  | TILBAKEKREVING_SYKEPENGER         | KT SP       | T            |
| Person                                          | Får feil, kravtypen kan ikke mottas på org          | 01828896640  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| D-nummer                                        | Får feil, kravtypen kan ikke mottas på org          | 45864500173  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |


##  Krav
```select linjenummer, filnavn, referansenummergammelsak, belop, belop_rente, fremtidig_ytelse, vedtaksdato,utbetaldato,gjelder_id, periode_fom, periode_tom, kravkode, kode_hjemmel, transaksjonsdato, enhet_bosted, enhet_behandlende, fagsystem_id, kravtype, status, tilleggsfrist, avsender from krav where filnavn = 'Fil-B.txt' and DATE(tidspunkt_opprettet) = DATE(now()))```      
Se [sokos_ske_krav_public_krav-Fil-B.xml](sokos_ske_krav_public_krav-Fil-B.xml)


| linjenummer | filnavn   | referansenummergammelsak | belop | belop_rente | fremtidig_ytelse | vedtaksdato             | utbetaldato             | gjelder_id  | periode_fom | periode_tom | kravkode | kode_hjemmel | transaksjonsdato | enhet_bosted | enhet_behandlende | fagsystem_id   | kravtype   | status              | tilleggsfrist | avsender |
|-------------|-----------|--------------------------|-------|-------------|------------------|-------------------------|-------------------------|-------------|-------------|-------------|----------|--------------|------------------|--------------|-------------------|----------------|------------|---------------------|---------------|----------|
| 1           | Fil-B.txt |                          | 2500  | 100         | 0.00             | 2024-03-18 00:00:00.000 | 2024-01-07 00:00:00.000 | 29527141786 | 20230501    | 20230930    | KT SP    | T            | 20240320         | 4416         | 4819              | Fil-B-Test-001 | NYTT_KRAV  | VALIDERINGSFEIL     | null          | OB04     |
| 2           | Fil-B.txt |                          | 12562 | 100         | 100100.00        | 2024-03-18 00:00:00.000 | 2024-01-07 00:00:00.000 | 01828896640 | 20230501    | 20230930    | LK RF    | T            | 20240320         | 4402         | 4819              | Fil-B-Test-002 | NYTT_KRAV  | 422_VALIDERINGSFEIL | null          | OB04     |
| 3           | Fil-B.txt |                          | 13000 | 100         | 0.00             | 2024-03-18 00:00:00.000 | 2024-01-07 00:00:00.000 | 42827200551 | 20230501    | 20230930    | EF UT    | T            | 20240320         | 4407         | 4819              | Fil-B-Test-003 | NYTT_KRAV  | RESKONTROFOERT      | null          | OB04     |
| 4           | Fil-B.txt |                          | 7000  | 100         | 0.00             | 2024-03-18 00:00:00.000 | 2024-01-07 00:00:00.000 | 45864500173 | 20230501    | 20230930    | LK RF    | T            | 20240320         | 4402         | 4819              | Fil-B-Test-004 | NYTT_KRAV  | 422_VALIDERINGSFEIL | null          | OB04     |
| 5           | Fil-B.txt |                          | 3980  | 100         | 250250.00        | 2024-03-18 00:00:00.000 | 2024-01-07 00:00:00.000 | 46918300929 | 20230501    | 20230930    | BA OR    | T            | 20240320         | 4403         | 4819              | Fil-B-Test-005 | NYTT_KRAV  | RESKONTROFOERT      | null          | OB04     |



## Feilmelding
```select error, melding, ske_response from feilmelding where saksnummer_nav in (select krav.saksnummer_nav from krav where filnavn = 'Fil-B.txt' and DATE(tidspunkt_opprettet) = DATE(now()))``` 
Se [sokos_ske_krav_public_feilmelding-Fil-B.xml](sokos_ske_krav_public_feilmelding-Fil-B.xml)


| error                  | melding                                                                                                                                        | ske_response                                                                                                                                                                                                                                                                                                                                                                                           |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 422                    | Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON | `{"type":"tag:skatteetaten.no,2026:innkreving:innkrevingsoppdrag:kravtype-gjelder-kun-for-organisasjon","title":"Kravtype gjelder kun for organisasjon","status":422,"detail":"Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON","instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag"}` |
| 422                    | Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON | `{"type":"tag:skatteetaten.no,2026:innkreving:innkrevingsoppdrag:kravtype-gjelder-kun-for-organisasjon","title":"Kravtype gjelder kun for organisasjon","status":422,"detail":"Ugyldig identifikatortype=PERSON. Innkrevingsoppdrag med kravtype=TILBAKEKREVING_LOENNSKOMPENSASJON kan bare ha identifikatortype=ORGANISASJON","instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag"}` |
| PERSON_EKSISTERER_IKKE | Person med fødselsdato=295271 eksisterer ikke                                                                                                  |                                                                                                                                                                                                                                                                                                                                                                                                        |
