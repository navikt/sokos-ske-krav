# Drift og test 

## SFTP tilkobling

Etter at `setupLocalEnvironment.sh` er kjørt, vil den opprette en `privKey` fil. Den burde legges inn der man oppbevarer ssh nøkler, f.eks `.ssh`.
Scriptet vil hente brukernavn og passord til `defaults.properties` i form av variablene `SKE_SFTP_USERNAME` og `SKE_SFTP_PASSWORD`.

FileZilla er en god klient for å koble seg til SFTP. Bruk `login with key file`.
Host og port finner man i [PropertiesConfig](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/config/PropertiesConfig.kt#L45-L46)

På MacOS kan den be om en `.ppk` nøkkel. Isåfall la FileZilla automatisk konvertere privatnøkkelen til .ppk og referer til denne.

## Testfiler på FTP

FTP filer for testing kan man finne [her](../testfiler). De er i copybook format som stormaskin/COBOL opererer med.  
Testfilene er navngitt Fil-A, Fil-B... Fil-E6. Filer med navn Fil-Ex inneholder endringer og stopp på krav som blir opprettet med Fil...A-Fil-D. Noen av filene vil gi asynkrone valideringsfeil og det er hensikten. Forventet resultat er dokumentert i (`docs/testfiler/Testfiler_forklaring.pdf`)

Disse kan brukes for testing, men vi må første erstatte saksnummerene med nye så vi ikke sender inn duplikater.
Dette gjøres med et python skript (`docs/scripts/ErstattSaksnummer.py`).

- Installer python3 (MACOS og brew: `brew install python`) (Linux: `sudo apt install python3`)

- Kjør f.eks `python3 ErstattSaksnummer.py Fil-A.txt` (PS: script og fil må være i samme mappe, ellers må du endre stien i scriptet)
- Logg inn på SFTP serveren ved å følge instruksjonene over.
- Filen legges i `/inbound` mappen
- Vi har to måter å trigge innlesing av filen på:

  1. Enten trigging av endepunktet [hentNye](https://sokos-ske-krav.intern.dev.nav.no/api/hentNye). Dette kan gjøres med en klient som Bruno.
    Vi ha med en `Authorization` token i headeren. En sånn kan genereres [her](https://azure-token-generator.intern.dev.nav.no/api/m2m?aud=dev-fss:okonomi:sokos-ske-krav)
  2. Eller restarte den kjørende pod'en via [nais console](https://console.nav.cloud.nais.io/team/okonomi/dev-fss/app/sokos-ske-krav). Trykk på `Restart app`.

## Alerts og tolkning av testresultater

Slack er stedet man først burde ta en titt. [#team-best-slackbot-dev](https://nav-it.slack.com/archives/C07P8SX6FTN) vil inneholde funksjonelle alerts/feil.
[#team-mob-alerts-dev](https://nav-it.slack.com/archives/C042SF2FEQM) kan man sjekke for andre tekniske feil.
Prod-utgavene av kanalene burde også regelmessig sjekkes, men prodfeil oppdages generelt fort av noen fra teamet.

## Database

Her kan man også titte angående ting relatert til drift og test. `Feilmelding` tabellen vil være et naturlig sted å starte. 
Det kan være handy å lage seg noen aliaser for pålogging til vault for å hente brukernavn og passord til databasen.

For eksempel:

`alias vaultlogin='gcloud auth login && vault login -method=oidc -no-print'`
`alias vaultskekravdev='vault read postgresql/preprod-fss/creds/sokos-ske-krav-user'`

Tilsvarende kan gjøres for feilsøking i prod. Da må man i tillegg huske på å åpne post-gres prod gatewayen i naisdevice, så skal man bli promptet med JIT side der man oppgir grunn og hvor lenge 
man trenger tilgang.
    
