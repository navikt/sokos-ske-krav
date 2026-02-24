# Testfiler forklaring

## FIL-A

| Case       | Forventet resultat                                                      | pnr/org       | Kravtype                                       | Stønadstype | Hjemmelskode |
|------------|-------------------------------------------------------------------------|---------------|------------------------------------------------|-------------|--------------|
| Død person | Skal få valideringsfeil. Krav kan ikke rettes mot død skydner           | 6586640023120 | TILBAKEKREVING_FORSKUTTERTE_DAGPENGER          | FO FT       | T            |
  
### I database: 


---

## FIL-B

| Case                                            | Forventet resultat                                  | pnr/org      | Kravtype                          | Stønadstype | Hjemmelskode |
|-------------------------------------------------|-----------------------------------------------------|--------------|-----------------------------------|-------------|--------------|
| Person som ikke eksisterer i part (eller tenor) | Får feil - Person eksiterer ikke                    | 29527141786  | TILBAKEKREVING_SYKEPENGER         | KT SP       | T            |
| Person                                          | Får feil, kravtypen kan ikke mottas på org          | 01828896640  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| D-nummer                                        | Får feil, kravtypen kan ikke mottas på org          | 45864500173  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |

---

## FIL-C

| Case                    | Forventet resultat                         | pnr/org   | Kravtype                          | Stønadstype | Hjemmelskode |
|-------------------------|--------------------------------------------|-----------|-----------------------------------|-------------|--------------|
| Org hovedenhet          | Får feil, kravtypen kan ikke mottas på org | 313999912 | TILBAKEKREVING_OVERGANGSSTOENAD   | EF OG       | T            |
| Org underenhet          | Får feil, kravtypen kan ikke mottas på org | 311984578 | TILBAKEKREVING_SYKEPENGER         | KT SP       | T            |
| Org slettet             | Får feil, organisasjon finnes ikke         | 210167722 | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| Org slettet             | Får feil, kravtypen kan ikke mottas på org | 313753018 | TILBAKEKREVING_SVANGERSKAPSPENGER | FASV T      | T            |
| Org opphørt             | Får asynkron feil                          | 212074462 | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| Org opphørt             | Får feil, kravtypen kan ikke mottas på org | 315064112 | TILBAKEKREVING_SVANGERSKAPSPENGER | FASV T      | T            |
| Org som ikke eksisterer | Får feil, organisasjon finnes ikke         | 999999999 | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |

---

## FIL-D

| Case        | Forventet resultat                                          | pnr/org | Kravtype                                                | Stønadstype | Hjemmelskode |
|-------------|-------------------------------------------------------------|---------|---------------------------------------------------------|-------------|--------------|
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_GJENLEVENDE_PENSJON__AVREGNING           | PE GP       | TA           |
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_GJENLEVENDE_PENSJON                      | PE GP       | T            |
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_UFOEREPENSJON                            | PE UP       | T            |
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_UFOEREPENSJON_UTBETALT TIL FEIL MOTTAKER | PE UP       | C            |
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_UFOERETRYGD                              | PE UT       | T            |
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER                | PE UT       | EU           |
| Ny kravtype | Skal gå igjennom ok, Ny kravtype som ikke lå inne sist test |         | TILBAKEKREVING_UFOERETRYGD_AVREGNING                    | PE UT       | TA           |

