# Manuell testing av applikasjonen

## SFTP tilkobling

Etter at `setupLocalEnvironment.sh` er kjørt, vil den opprette en `privKey` fil. Den burde legges inn der man oppbevarer ssh nøkler, f.eks `.ssh`.
Scriptet vil hente brukernavn og passord til `defaults.properties` i form av variablene `SKE_SFTP_USERNAME` og `SKE_SFTP_PASSWORD`.

FileZilla er en god klient for å koble seg til SFTP. Bruk `login with key file`.
Host og port finner man i [PropertiesConfig](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/config/PropertiesConfig.kt#L45-L46)
Noen ganger vil FileZilla ikke kunne koble seg til SFTP (den vil stå og "spinne") og dette løses som regel ved å kjøre kommandoen `'mv "/run/user/1000/keyring/ssh" "/run/user/1000/keyring/sshrem"'`

På MacOS og Linux kan den be om en `.ppk` nøkkel. Isåfall la FileZilla automatisk konvertere privatnøkkelen til .ppk og referer til denne.

### Trigge innlesing av fil
Vi har tre måter å trigge innlesing av filen på:
1. Trigging av endepunktet [hentNye](https://sokos-ske-krav.intern.dev.nav.no/api/hentNye). Dette kan gjøres med en klient som Bruno.
   Vi må ha med en `Authorization`-token i headeren. En slik kan genereres [her](https://azure-token-generator.intern.dev.nav.no/api/m2m?aud=dev-fss:okonomi:sokos-ske-krav)
2. Restarte den kjørende pod'en via [nais console](https://console.nav.cloud.nais.io/team/okonomi/dev-fss/app/sokos-ske-krav). Trykk på `Restart app`.
   Hvis du skal teste en spesiell branch kan du bruke [workflowen for manuell deploy til dev](https://github.com/navikt/sokos-ske-krav/actions/workflows/manual-deploy.yaml)
3. Kjøre applikasjonen lokalt

**Alltid trigg innlesing to ganger** slik at kravene får korrekt mottaksstatus (og asynkrone valideringsfeil blir hentet)

## Testfiler på SFTP

SFTP filer for testing kan man finne [her](testfiler). De er i copybook format som stormaskin/COBOL opererer med.
For å kunne bruke disse til testing må først må man erstatte saksnummerene med nye så vi ikke sender inn duplikater.

- Endring av saksnummer gjøres med pythonscriptet `docs/testing/scripts/ErstattSaksnummer.py`
- Kopiering av saksnummer til referansenummer gjøres med pythonscriptet `docs/testing/scripts/KopierSaksnummer.py`
- Installer python3 (MACOS og brew: `brew install python`) (Linux: `sudo apt install python3`)
- Kopier scriptene og filene til en mappe lokalt på din maskin

### Les disse instruksjonene nøye!

Noen av filene vil gi valideringsfeil og det er litt av hensikten. Forventede feil er dokumentert i `.md`-filen som følger med hver testfil.


### Test av nye krav
Testfilene er navngitt Fil-A, Fil-B, og Fil-C og organisert i mapper med testfil og resultater:
- [Fil-A](testfiler/OS/Fil-A)
  - Resultatfil: [Fil-A_Resultat.md](testfiler/OS/Fil-A/Fil-A_Resultat.md)
- [Fil-B](testfiler/OS/Fil-B)
  - Resultatfil: [Fil-B_Resultat.md](testfiler/OS/Fil-B/Fil-B_Resultat.md)
- [Fil-C](testfiler/OS/Fil-C)
  - Resultatfil: [Fil-C_Resultat.md](testfiler/OS/Fil-C/Fil-C_Resultat.md)
  

- Kopier filer og script (`docs/testing/scripts/ErstattSaksnummer.py`) til en mappe lokalt på din maskin
- For Fil-A, Fil-B og Fil-C kjør `python3 ErstattSaksnummer.py Fil-x.txt` Hvor x er A..C
- [Logg inn på SFTP serveren](#sftp-tilkobling)
- Legg én fil i `/inbound` mappen og [trigg innlesing](#trigge-innlesing-av-fil)
- Sjekk resultatet, og fortsett til neste fil

### Test av endringer av krav
Testfilene er navngitt Fil-E1, Fil-E2, Fil-E3, og Fil-E4 og organisert i mapper med testfil og resultater:
- [Fil-E1](testfiler/OS/Fil-E1)
  - Fil E1 oppretter krav som E2, E3 og E4 skal sende inn endringer på
  - Resultat: [Fil-E1_Resultat.md](testfiler/OS/Fil-E1/Fil-E1_Resultat.md)
  - Kommandoer:
    - `python3 ErstattSaksnummer.py Fil-E1.txt`
- [Fil-E2](testfiler/OS/Fil-E2)
  - Endrer alle kravene som E1 opprettet
  - Resultat: [Fil-E2_Resultat.md](testfiler/OS/Fil-E2/Fil-E2_Resultat.md)
  - Kommandoer:
    - `python3 ErstattSaksnummer.py Fil-E2.txt`
    -  `python3 KopierSaksnummer.py Fil-E1.txt Fil-E2.txt`
- [Fil-E3](testfiler/OS/Fil-E3)
  - Endrer alle kravene som E1 opprettet
  - Resultat: [Fil-E3_Resultat.md](testfiler/OS/Fil-E3/Fil-E3_Resultat.md)
  - Kommandoer:
    - `python3 ErstattSaksnummer.py Fil-E3.txt`
    -  `python3 KopierSaksnummer.py Fil-E2.txt Fil-E3.txt`
- [Fil-E4](testfiler/OS/Fil-E4)
  - Stopper 5 av kravene som E1 opprettet
  - Resultat: [Fil-E4_Resultat.md](testfiler/OS/Fil-E4/Fil-E4_Resultat.md)
  - Kommandoer:
    - `python3 KopierSaksnummer.py Fil-E1.txt Fil-E4.txt`
              

Saksnumrene på kravene ender med Exxx der xxx er siffer og representerer filnummeret og linjennummeret i filen slik at det er lett å sammenligne

- Legg E1 i `/inbound` mappen og [trigg innlesing](#trigge-innlesing-av-fil)
- Sjekk resultatet, og fortsett til neste fil
                                       

## Tolkning av testresultater
Databasedump (med SQL kommandoer) av hva resultatene skal være i databasen etter innlesning av filene er dokumentert i resultatfilene slik at du kan gjøre en grundig sammenligning.

## Testfiler for andre avsendere

Mappene for Arena, Pesys og Infotrygd under `testfiler/` er foreløpig tomme. Det finnes per i dag ingen dedikerte testfiler for disse avsenderne. Dersom du skal teste integrasjoner mot disse fagsystemene må testfiler opprettes manuelt.

> **Merk:** For `ARENA`, `PESYS` og `INFOTRYGD` er tom/ugyldig utbetalingsdato akseptert. Dette er den viktigste valideringsforskjellen sammenlignet med `OB04`-filer, og bør verifiseres ved manuell testing.

