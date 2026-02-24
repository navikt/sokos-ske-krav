# Forventet resultat av kjøring av Fil-A.txt

 NB! Denne filen skal motta en asynkron valideringsfeil. Dvs at du må trigge sending to ganger slik at mottaksstatus blir kalt. Request og respons fra asynkron valideringsfeil vil IKKE lagres i Feilmelding. 


## Feil 
| Case       | Forventet resultat                                                      | pnr/org       | Kravtype                                       | Stønadstype | Hjemmelskode |
|------------|-------------------------------------------------------------------------|---------------|------------------------------------------------|-------------|--------------|
| Død person | Skal få valideringsfeil. Krav kan ikke rettes mot død skydner           | 6586640023120 | TILBAKEKREVING_FORSKUTTERTE_DAGPENGER          | FO FT       | T            |
                            
  
##  Krav
```select filnavn, saksnummer_nav, referansenummergammelsak, belop, belop_rente, fremtidig_ytelse, vedtaksdato,utbetaldato,gjelder_id, periode_fom, periode_tom, kravkode, kode_hjemmel, transaksjonsdato, enhet_bosted, enhet_behandlende, fagsystem_id, kravtype, status, tilleggsfrist, avsender from krav where filnavn = 'Fil-A.txt' and  tidspunkt_opprettet > '2026-02-24 13:00:00'``` (bytt ut timestamp med det som passer)


| filnavn   | saksnummer_nav | referansenummergammelsak | belop | belop_rente | fremtidig_ytelse | vedtaksdato                | utbetaldato                | gjelder_id  | periode_fom | periode_tom | kravkode | kode_hjemmel | transaksjonsdato | enhet_bosted | enhet_behandlende | fagsystem_id    | kravtype   | status            | tilleggsfrist | avsender |
|-----------|----------------|--------------------------|-------|-------------|------------------|----------------------------|----------------------------|-------------|-------------|-------------|----------|--------------|------------------|--------------|-------------------|-----------------|------------|-------------------|---------------|----------|
| Fil-A.txt | 5080825        |                          | 10000 | 10          | 0.00             | 2024-03-18 00:00:00.000000 | 2023-12-20 00:00:00.000000 | 65866400231 | 20230601    | 20230930    | FO FT    | FT           | 20240320         | 4416         | 4819              | Fil-A-Test-001  | NYTT_KRAV  | VALIDERINGSFEIL   | null          | OB04     |
| Fil-A.txt | 6040915        |                          | 8000  | 2000        | 150000.00        | 2024-03-18 00:00:00.000000 | 2023-12-20 00:00:00.000000 | 02817398173 | 20230601    | 20230930    | PE UT    | EU           | 20240320         | 4402         | 4819              | Fil-A-Test-002  | NYTT_KRAV  | RESKONTROFOERT    | null          | OB04     |
| Fil-A.txt | 9871724        |                          | 6500  | 56          | 350500.00        | 2024-03-18 00:00:00.000000 | 2023-12-20 00:00:00.000000 | 06895898935 | 20230601    | 20230930    | PE UT    | TA           | 20240320         | 4407         | 4819              | Fil-A-Test-003  | NYTT_KRAV  | RESKONTROFOERT    | null          | OB04     |
| Fil-A.txt | 4667544        |                          | 4897  | 150         | 0.00             | 2024-03-18 00:00:00.000000 | 2023-12-20 00:00:00.000000 | 21876498895 | 20230601    | 20230930    | FR SN    | T            | 20240320         | 4402         | 4819              | Fil-A-Test-004  | NYTT_KRAV  | RESKONTROFOERT    | null          | OB04     |
| Fil-A.txt | 5664645        |                          | 35478 | 900         | 0.00             | 2024-03-18 00:00:00.000000 | 2023-12-20 00:00:00.000000 | 12916999982 | 20230601    | 20230930    | KS KS    | T            | 20240320         | 4403         | 4819              | Fil-A-Test-005  | NYTT_KRAV  | RESKONTROFOERT    | null          | OB04     |

## Feilmelding
 ```select saksnummer_nav, error, melding, nav_request, ske_response from feilmelding where saksnummer_nav in (select krav.saksnummer_nav from krav where filnavn = 'Fil-A.txt' and DATE(tidspunkt_opprettet) = DATE(now()))```

| saksnummer_nav | error           | melding                              | nav_request | ske_response |
|----------------|-----------------|--------------------------------------|-------------|--------------|
| 5080825        | PERSON_ER_DOED  | Person med fødselsdato=658664 er død |             |              |



