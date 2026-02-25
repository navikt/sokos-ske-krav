# Drift
 
## Nyttige lenker

[Vault prod SFTP informasjon] (https://vault.adeo.no/ui/vault/secrets/kv%2Fprod%2Ffss/show/sokos-ske-krav/okonomi/sftp) (Du må først åpne po-utbetaling-prod gatwayen i naisconsole)
Slack kanaler: #team-best-slackbot-prod, #utbetaling-tilbakekreving-fi
Alias for .zshrc eller .bashrc:
- `alias vaultlogin='gcloud auth login && vault login -method=oidc -no-print'`
- `alias vaultskekravprod='vault read postgresql/prod-fss/creds/sokos-ske-krav-user'`
- `alias vaultskekravdev='vault read postgresql/preprod-fss/creds/sokos-ske-krav-user'`
- `alias py='python3'
For å logge inn i prod database (prod-pg.intern.nav.no) må du åpne po-utbetaling-prod gatwayen i naisconsole. I JIT access begrunnelsen skriver du hvorfor du skal ha tilgang, og helst med referanse til en Jira sak
          

## Typiske BAU oppgaver

### Finne informasjon om et gitt krav
Typisk scenario er at et krav ikke er kommet frem til Skatteetaten eller ikke er blitt reskontroført. Det vil opprettes en Jirasak med informasjon om kravet og du vil typisk bli gitt et saksnummer. Du kan så logge inn i prod database og finne kravet ved å sjekke SaksnummerNav i tabellen Krav:
`select * from krav where saksnummer_nav = `
Hvis det har skjedd noe feil kan du finne feilmelding i Feilmelding:
`select * from feilmelding where saksnummer_nav = `

### Resende et krav
Dette er nødvendig når det har gått noe galt med et krav, og så har det blitt rettet opp i. For eksempel hvis det feilet i validering, eller hvis tjenesten til Skatteetaten var nede. Da må verdien i statuskolonnen settes til "KRAV_IKKE_SENDT" for at kravet skal bli sendt på nytt. Dette gjøres ved å kjøre følgende SQL-kommando:
`update krav set status = 'KRAV_IKKE_SENDT' where saksnummer_nav = ` 
Alternativt kan du gjøre det i GUI for å minimere risikoen for feil.
Kravet vil så plukkes opp og resendes neste gang scheduleringen kjører.
Husk at du alltid må ha tillatelse fra prosjektleder, samt en Jirasak før du gjør endringer i database. Dette er pga etterlevelseskrav.



## Typiske videreutvikliongsoppgaver
- Legge ny kravkoder
- Legge til nye felter
- Endre navn på parametere til Skatteetaten

## Ting som kan gå galt 
### Funksjonelle feil
De aller fleste feilene er på grunn av feil i valideringsregler mot Skatteetaten, samt asynkron validering. Se liste over valideringsregler [her](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler).  
Feil vil bli sendt som alert til slackkanalen #team-best-slackbot-prod med informasjon om hva som gikk galt, samt filnavn og dato. 
De aller fleste feilene er forårsaket av endringer på krav som ikke er blitt reskontroført (eller såkalte "dobbel endring på migrerte krav"), og krav på organisasjoner som er opphørt. Disse sakene håndteres av produkteier som må igangsette en manuell rutine direkte med Skatteetaten. 
Av feilene som dukker opp i #team-best-slackbot-prod er det ingenting som kan løses av en utvikler.
Mer detaljer om feilen, inkludert fullstendig request og response, finnes i Feilmelding tabellen

### Tekniske feil
Hvis Skatteetaten sin tjeneste er nede eller det oppstår andre problemer i kommunikasjon med Skatteetaten vil alert komme i #team-mob-alerts-prod. Feilen meldes til Skatteetaten i slackkanalen #utbetaling-tilbakekreving-fi.  
Når feilen er løst må vi trigge resending av kravene. Dette gjøres ved å manuelt sette status på kravet til "KRAV_IKKE_SENDT". Man må først informere produktleder om at man må gjøre dette og få tillatelse, og så må man opprette en oppgave i Jira.

        
## Testing
Se [manuell testing](Manuell_testing.md) for detaljert beskrivelse av hvordan å teste.

### Når MÅ du teste manuelt? 
Husk at å "teste manuelt" betyr å faktisk teste mot Skatteetaten. Når kontrakten endrer seg må du *alltid* teste manuelt.
- Ved nye major versjoner av viktige rammeverk som Jsch (SFTP) og Ktor
- Når det legges inn nye kravkoder (du må manuelt lage en fil med de nye kravkodene)
- Når det legges inn nye felter (du må manuelt lage en fil med de nye feltene)

### Når BØR du teste manuelt?
- Ved endring i logikk for parsing (slik at du kan sjekke database) (vi vil ikke vite om vi sender inn feil data så det lønner seg å sjekke det manuelt)
- Ved endring i logikk for alarmer og logging
- Når det kommer nye valideringsregler
- Når eksisterende valideringsregler endres
