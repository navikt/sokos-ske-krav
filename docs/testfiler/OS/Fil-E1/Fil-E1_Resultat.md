# Forventet resultat av kjøring av Fil-E1.txt

Denne filen skal kun opprette nye krav, uten feil

##  Krav
```select filnavn, saksnummer_nav, referansenummergammelsak, belop, belop_rente, fremtidig_ytelse, vedtaksdato,utbetaldato,gjelder_id, periode_fom, periode_tom, kravkode, kode_hjemmel, transaksjonsdato, enhet_bosted, enhet_behandlende, fagsystem_id, kravtype, status, tilleggsfrist, avsender from krav where filnavn = 'Fil-E1.txt' and  tidspunkt_opprettet > '2026-02-24 13:00:00'``` (bytt ut timestamp med det som passer, eller bruk DATE(now()))


## Feilmelding
```select saksnummer_nav, error, melding, ske_response from feilmelding where saksnummer_nav in (select krav.saksnummer_nav from krav where filnavn = 'Fil-C.txt' and DATE(tidspunkt_opprettet) = DATE(now()))``` (eller bruk timestamp)

