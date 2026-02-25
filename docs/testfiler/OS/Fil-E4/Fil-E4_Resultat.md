# Forventet resultat av kjøring av Fil-E3.txt

Denne filen inneholder kun stopp (av 5 krav opprettet i Fil-E1.txt) og skal ikke få noen feil.


##  Krav
```select linjenummer, filnavn, saksnummer_nav, referansenummergammelsak, belop, belop_rente, fremtidig_ytelse, vedtaksdato,utbetaldato,gjelder_id, periode_fom, periode_tom, kravkode, kode_hjemmel, transaksjonsdato, enhet_bosted, enhet_behandlende, fagsystem_id, kravtype, status, tilleggsfrist, avsender from krav where filnavn = 'Fil-E3.txt' and  tidspunkt_opprettet > '2026-02-24 15:00:00'``` (bytt ut timestamp med det som passer, eller bruk DATE(now()))
Se [sokos_ske_krav_public_krav-Fil-E4.xml](sokos_ske_krav_public_krav-Fil-E4.xml)

| linjenummer | filnavn    | saksnummer_nav     | referansenummergammelsak | belop | belop_rente | fremtidig_ytelse | vedtaksdato | utbetaldato | gjelder_id  | periode_fom | periode_tom | kravkode | kode_hjemmel | transaksjonsdato | enhet_bosted | enhet_behandlende | fagsystem_id      | kravtype   | status         | tilleggsfrist | avsender |
|-------------|------------|--------------------|--------------------------|-------|-------------|------------------|-------------|-------------|-------------|-------------|-------------|----------|--------------|------------------|--------------|-------------------|-------------------|------------|----------------|---------------|----------|
| 1           | Fil-E4.txt | 9248919240409-E101 | 9248919240409-E101       | 0     | 0           | 0.00             | 2023-12-20  | 2023-12-18  | 68877300327 | 20240110    | 20240321    | BA OR    | T            |                  | 4402         | 4819              | Fil-E4-20240409-1 | STOPP_KRAV | RESKONTROFOERT | null          | OB04     |
| 2           | Fil-E4.txt | 7421107240409-E102 | 7421107240409-E102       | 0     | 0           | 0.00             | 2023-12-20  | 2023-12-18  | 07811499913 | 20240110    | 20240321    | BS OM    | T            |                  | 4403         | 4819              | Fil-E4-20240409-2 | STOPP_KRAV | RESKONTROFOERT | null          | OB04     |
| 3           | Fil-E4.txt | 2267919240409-E103 | 2267919240409-E103       | 0     | 0           | 0.00             | 2023-12-20  | 2023-12-18  | 31855199931 | 20240110    | 20240321    | BS OP    | T            |                  | 4405         | 4819              | Fil-E4-20240409-3 | STOPP_KRAV | RESKONTROFOERT | null          | OB04     |
| 4           | Fil-E4.txt | 4664490240409-E104 | 4664490240409-E104       | 0     | 0           | 0.00             | 2023-12-20  | 2023-12-18  | 13881098606 | 20240110    | 20240321    | BS PN    | T            |                  | 4407         | 4819              | Fil-E4-20240409-4 | STOPP_KRAV | RESKONTROFOERT | null          | OB04     |
| 5           | Fil-E4.txt | 7665538240409-E105 | 7665538240409-E105       | 0     | 0           | 0.00             | 2023-12-20  | 2023-12-18  | 17857397649 | 20240110    | 20240321    | BS PP    | T            |                  | 4410         | 4819              | Fil-E4-20240409-5 | STOPP_KRAV | RESKONTROFOERT | null          | OB04     |

