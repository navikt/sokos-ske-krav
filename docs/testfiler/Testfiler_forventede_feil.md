# Testfiler forklaring

## FIL-A

| Case       | Forventet resultat                                                      | pnr/org       | Kravtype                                       | Stønadstype | Hjemmelskode |
|------------|-------------------------------------------------------------------------|---------------|------------------------------------------------|-------------|--------------|
| Død person | Skal få valideringsfeil. Krav kan ikke rettes mot død skydner           | 6586640023120 | TILBAKEKREVING_FORSKUTTERTE_DAGPENGER          | FO FT       | T            |


---

## FIL-B

| Case                                            | Forventet resultat                                  | pnr/org      | Kravtype                          | Stønadstype | Hjemmelskode |
|-------------------------------------------------|-----------------------------------------------------|--------------|-----------------------------------|-------------|--------------|
| Person som ikke eksisterer i part (eller tenor) | Får feil - Person eksiterer ikke                    | 29527141786  | TILBAKEKREVING_SYKEPENGER         | KT SP       | T            |
| Person                                          | Får feil, kravtypen kan ikke mottas på org          | 01828896640  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| D-nummer                                        | Får feil, kravtypen kan ikke mottas på org          | 45864500173  | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |

---

## FIL-C

| Case                    | Forventet resultat                          | pnr/org     | Kravtype                          | Stønadstype | Hjemmelskode |
|-------------------------|---------------------------------------------|-------------|-----------------------------------|-------------|--------------|
| Org hovedenhet          | Får feil, kravtypen kan ikke mottas på org  | 00313999912 | TILBAKEKREVING_OVERGANGSSTOENAD   | EF OG       | T            |
| Org underenhet          | Får feil, kravtypen kan ikke mottas på org  | 00311984578 | TILBAKEKREVING_SYKEPENGER         | KT SP       | T            |
| Org slettet             | Får asynkron feil, organisasjon finnes ikke | 00210167722 | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |
| Org slettet             | Får feil, kravtypen kan ikke mottas på org  | 00313753018 | TILBAKEKREVING_SVANGERSKAPSPENGER | FASV T      | T            |
| Org opphørt             | Får feil, kravtypen kan ikke mottas på org  | 00315064112 | TILBAKEKREVING_SVANGERSKAPSPENGER | FASV T      | T            |
| Org som ikke eksisterer | Får asynkron feil, organisasjon finnes ikke | 00999999999 | TILBAKEKREVING_LOENNSKOMPENSASJON | LK RF       | T            |

---
