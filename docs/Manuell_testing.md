# Manuell testing av applikasjonen

## SFTP tilkobling

Etter at `setupLocalEnvironment.sh` er kjørt, vil den opprette en `privKey` fil. Den burde legges inn der man oppbevarer ssh nøkler, f.eks `.ssh`.
Scriptet vil hente brukernavn og passord til `defaults.properties` i form av variablene `SKE_SFTP_USERNAME` og `SKE_SFTP_PASSWORD`.

FileZilla er en god klient for å koble seg til SFTP. Bruk `login with key file`.
Host og port finner man i [PropertiesConfig](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/config/PropertiesConfig.kt#L45-L46)
Noen ganger vil FileZilla ikke kunne koble seg til SFTP (den vil stå og "spinne") og dette løses som regel ved å kjøre kommandoen `'mv "/run/user/1000/keyring/ssh" "/run/user/1000/keyring/sshrem"'`

På MacOS og Linux kan den be om en `.ppk` nøkkel. Isåfall la FileZilla automatisk konvertere privatnøkkelen til .ppk og referer til denne.

## Testfiler på FTP

FTP filer for testing kan man finne [her](../testfiler). De er i copybook format som stormaskin/COBOL opererer med.
For å kunne bruke disse til testing men først må man erstatte saksnummerene med nye så vi ikke sender inn duplikater.
Noen av filene vil gi asynkrone valideringsfeil og det er litt av hensikten. Forventede feil er dokumentert i (`docs/testfiler/Testfiler_forventede_feil.md`).   


### Les disse ins                                                                                                                                                                                                                                                                                                                                                                                       truksjonene nøye!!
### Test av nye krav
Testfilene er navngitt Fil-A, Fil-B...
Endring av saksnummer gjøres med pythonscript (`docs/scripts/ErstattSaksnummer.py`).

- Installer python3 (MACOS og brew: `brew install python`) (Linux: `sudo apt install python3`)
- Kopier filer og script til en mappe lokalt på din maskin
- For Fil-A.. Fil-D kjør `python3 ErstattSaksnummer.py Fil-x.txt` Hvor x er A..D
- Kjør f.eks `python3 ErstattSaksnummer.py Fil-A.txt` (PS: script og fil må være i samme mappe, ellers må du endre stien i scriptet)
- Logg inn på SFTP serveren ved å følge instruksjonene over.
- Filen legges i `/inbound` mappen 

### Test av endringer av krav
Filer med navn Fil-E2, E3 og E4 inneholder endringer og stopp på kraven som opprettes i E1. E4 er stopp, mens E2 endrer på E1, og E3 endrer på E2 (dobbel endring)
Saksnumrene ender med Exxx der xxx er siffer og representerer filnummeret og linjennummeret i filen slik at det er lett å sammenligne
- Først må du kjøre `python3 ErstattSaksnummer.py Fil-E1.txt`, `python3 ErstattSaksnummer.py Fil-E2.txt`, `python3 ErstattSaksnummer.py Fil-E3.txt` for å gi E1-E3 nye saksnummer (Exxx beholdes). Du trenger ikke å gjøre dette for Fil-E4, da den skal stoppe kravet i Fil-E1 og dermed ikke trenger et nytt saksnummer.
- Deretter kjører du `python3 KopierSaksnummer.py Fil-E1.txt Fil-E2.txt`, `python3 KopierSaksnummer.py Fil-E2.txt Fil-E3.txt`, og til slutt `python3 KopierSaksnummer.py Fil-E1.txt Fil-E4.txt` for å kopiere de nye saksnumrene til feltet for Referansenummergammelsak.
- Legg én fil i `/inbound` mappen og trigg innlesing
- Sjekk resultatet, og fortsett til neste fil
                                       
### Triggere innlesing av fil
- Vi har to måter å trigge innlesing av filen på:
  1. Enten trigging av endepunktet [hentNye](https://sokos-ske-krav.intern.dev.nav.no/api/hentNye). Dette kan gjøres med en klient som Bruno.
    Vi ha med en `Authorization` token i headeren. En sånn kan genereres [her](https://azure-token-generator.intern.dev.nav.no/api/m2m?aud=dev-fss:okonomi:sokos-ske-krav)
  2. Eller restarte den kjørende pod'en via [nais console](https://console.nav.cloud.nais.io/team/okonomi/dev-fss/app/sokos-ske-krav). Trykk på `Restart app`.
- Alltid trigg innlesing to ganger slik at kravene får korrekt mottaksstatus (og vi henter asynkrone valideringsfeil)

## Tolkning av testresultater
Logg inn i databasen og sjekk at de som ikke skal ha feil har status 'MOTTATT_UNDER_BEHANDLING' eller 'RESKONTROFOERT'.  
Sjekk deretter at kravene som skal motta valideringsfeil har blitt lagret med korrekt statuskode i henhold til feilen som SKE skal returnere, og sjekk deretter at dette er lagret korrekt i Feilmelding tabellen. Se [SKE dokumentasjon for valideringsregler](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler).     
Feil som gjelder filvalidering vil ha status 'VALIDERINGSFEIL_AV_LINJE_I_FIL'.   
For kravene som skal få feil vil det komme en alert i Slackkanalen #team-best-slackbot-dev.   
Databasedump (med SQL kommandoer) av hva resultatene skal være i databasen etter innlesning av filene er dokumentert i (`docs/testfiler/OS/Fil-A_Resultat.md`),   (`docs/testfiler/OS/Fil-B_Resultat.md`) osv slik at du kan gjøre en grundig sammenligning.
    
