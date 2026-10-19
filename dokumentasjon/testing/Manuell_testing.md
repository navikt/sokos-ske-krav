# Manuell testing av applikasjonen

## SFTP tilkobling

Etter at `setupLocalEnvironment.sh` er kjørt, vil den opprette en `privateKey` fil. Den burde legges inn der man oppbevarer ssh nøkler, f.eks `.ssh`.
Scriptet vil hente brukernavn og passord til `defaults.properties` i form av variablene `SKE_SFTP_USERNAME` og `SKE_SFTP_PASSWORD`.

FileZilla er en god klient for å koble seg til SFTP. Bruk `login with key file`.
Host og port finner man i [PropertiesConfig](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/config/PropertiesConfig.kt#L45-L46)
Noen ganger vil FileZilla ikke kunne koble seg til SFTP (den vil stå og "spinne") og dette løses som regel ved å kjøre kommandoen `'mv "/run/user/1000/keyring/ssh" "/run/user/1000/keyring/sshrem"'`

På MacOS og Linux kan den be om en `.ppk` nøkkel. Isåfall la FileZilla automatisk konvertere privatnøkkelen til .ppk og referer til denne.

### Trigge innlesing av fil
Vi har to måter å trigge innlesing av filen på:
1. Kjøre applikasjonen lokalt
2. Restarte den kjørende pod'en via [nais console](https://console.nav.cloud.nais.io/team/okonomi/dev-fss/app/sokos-ske-krav). Trykk på `Restart app`.
   Hvis du skal teste en spesiell branch kan du bruke [workflowen for manuell deploy til dev](https://github.com/navikt/sokos-ske-krav/actions/workflows/manual-deploy.yaml)

**Alltid trigg innlesing to ganger** slik at kravene får korrekt mottaksstatus (og asynkrone valideringsfeil blir hentet)

## Testfiler på SFTP

SFTP filer for testing kan man finne [her](testfiler). De er i copybook format som stormaskin/COBOL opererer med.
For å kunne bruke disse til testing må først må man erstatte saksnummerene med nye så vi ikke sender inn duplikater.

- Endring av saksnummer gjøres med pythonscriptet `dokumentasjon/testing/scripts/ErstattSaksnummer.py`
- Kopiering av saksnummer til referansenummer gjøres med pythonscriptet `dokumentasjon/testing/scripts/KopierSaksnummer.py`
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
  

- Kopier filer og script (`dokumentasjon/testing/scripts/ErstattSaksnummer.py`) til en mappe lokalt på din maskin
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

### Test av oppryddingskjede og krav som venter på opprydding

Denne testen dekker hele kjeden der et opprinnelig krav avvises på foreldelsesfrist, et oppryddingskrav opprettes, og senere oppfølging må vente til oppryddingskravet har en SKE-kravidentifikator.

Testfilene ligger i [`testfiler/OS/Oppryddingskjede`](testfiler/OS/Oppryddingskjede):

| Fil | Rolle | Forventet referanse |
| --- | --- | --- |
| `OriginalKrav_A.txt` | Originalt krav | `saksnummer = A` |
| `Oppryddingskrav_B.txt` | Oppryddingskrav | `saksnummer = B`, `referansenummerGammelSak = A` |
| `Oppfolging_C.txt` | Første oppfølging | `saksnummer = C`, `referansenummerGammelSak = B` |
| `Oppfolging_D.txt` | Senere oppfølging | `saksnummer = D`, `referansenummerGammelSak = C` |

#### Forberedelse

1. Kopier de fire filene til en lokal arbeidsmappe. Ikke last opp malfilene direkte til dev.
2. Bruk scriptet ditt for å gi alle saksnummer nye, unike verdier og for å sette referansenummer gammel sak. Behold disse koblingene etter regenerering:
   - `B.referansenummerGammelSak = A.saksnummer`
   - `C.referansenummerGammelSak = B.saksnummer`
   - `D.referansenummerGammelSak = C.saksnummer`
3. Kontroller spesielt begge kravlinjene med gammelreferanse i hver fil. I malfilene er det linje 2 og 7. De skal referere til tilsvarende saksnummer fra forrige ledd, ikke til originalen A.
4. Gi filene unike filnavn før opplasting, for eksempel med dato og initialer. Filnavn brukes i databasen og gjør det mulig å avgrense SQL-oppslagene.
5. Sørg for at A fortsatt gir SKE-feilen `foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.` B må derimot være en gyldig korrigering, normalt ved at tilleggsfrist settes av regenereringsscriptet. Uten denne forskjellen testes ikke oppryddingssporet.

#### Del 1: Opprett foreldelsesfrist-feilen for A

1. Last opp `OriginalKrav_A.txt` til SFTP-serverens `/inbound`.
2. Trigger innlesing og sending i dev. Trigger deretter én gang til for å hente mottaksstatus, som beskrevet i [Trigge innlesing av fil](#trigge-innlesing-av-fil).
3. Verifiser i databasen at raden for A har en feilmelding med forventet foreldelsesfrist-tekst. Sjekk også at A ikke har fått en SKE-kravidentifikator.

   ```sql
   select k.saksnummer_nav, k.referansenummergammelsak, k.kravtype, k.status,
          k.kravidentifikator_ske, f.error, f.melding
   from krav k
   left join feilmelding f on f.krav_id = k.id
   where k.filnavn = '<navn-på-A-filen>'
   order by k.id;
   ```

4. I loggene skal du se at filen er lest og at SKE-svaret for A ble behandlet som en feil. Ikke gå videre dersom feilmeldingen ikke er den spesifikke foreldelsesfrist-feilen.

#### Del 2: Les inn B, C og D uten å sende dem

1. Slå av utsending av krav i dev, slik at B, C og D lagres men ikke får SKE-kall. Bruk den vanlige feature-toggle-/driftsrutinen for `isSendKravEnabled`; ikke endre database-status manuelt.
2. Last opp `Oppryddingskrav_B.txt` til `/inbound` og trigger innlesing. B skal lagres som `NYTT_KRAV`, med `saksnummer_nav = B` og `referansenummergammelsak = A`.
3. Last opp `Oppfolging_C.txt` og trigger innlesing. C-radene som refererer til B skal få `PENDING_ENDRING_HOVEDSTOL`, `PENDING_ENDRING_RENTE` eller `PENDING_STOPP` etter kravtype.
4. Last opp `Oppfolging_D.txt` og trigger innlesing. D-radene som refererer til C skal få tilsvarende `PENDING_*`-status. Dette verifiserer at ventingen følger hele referansekjeden, ikke bare ett ledd.
5. Verifiser databasen. Bytt ut plassholderne med de regenererte saksnumrene:

   ```sql
   select id, filnavn, saksnummer_nav, referansenummergammelsak, kravtype,
          status, kravidentifikator_ske
   from krav
   where saksnummer_nav in ('<B>', '<C>', '<D>')
      or referansenummergammelsak in ('<A>', '<B>', '<C>')
   order by id;
   ```

   Forventet resultat:

   - B har `kravtype = NYTT_KRAV`, mangler `kravidentifikator_ske` og er ikke `PENDING_*`.
   - C har `referansenummergammelsak = B` og `PENDING_*`-status.
   - D har `referansenummergammelsak = C` og `PENDING_*`-status.
   - C og D finnes ikke i ordinær resend-kø.

6. Kontroller at B-, C- og D-filene er flyttet til `/outbound`. For C og D skal Slack også ha mottatt varsel om krav som venter på opprydding.

#### Del 3: Send B og frigjør C/D

1. Slå på utsending av krav igjen og trigger behandling. B skal sendes via opprett-krav-ruten med `oppdragsgiversKravIdentifikator = B`.
2. Trigger én gang til for mottaksstatus, og kontroller at B nå har en ikke-blank `kravidentifikator_ske`.

   ```sql
   select saksnummer_nav, referansenummergammelsak, kravtype, status,
          kravidentifikator_ske
   from krav
   where saksnummer_nav = '<B>';
   ```

3. Åpne rapporten **Krav som venter på opprydding** under `/rapporter` og frigjør én C-rad og én D-rad. Begynn gjerne med D for å verifisere at frigivelse følger referansekjeden tilbake til B.
4. Kontroller direkte etter frigivelse:

   ```sql
   select id, saksnummer_nav, referansenummergammelsak, kravtype, status,
          kravidentifikator_ske
   from krav
   where saksnummer_nav in ('<C>', '<D>');
   ```

   Den frigjorte raden skal ha `status = KRAV_IKKE_SENDT` og samme `kravidentifikator_ske` som B. Rader som ikke ble frigitt skal fortsatt ha `PENDING_*`.

5. Trigger sending på nytt. Den frigjorte C- eller D-raden skal sendes i ordinær endrings- eller stopprute. Trigger deretter én gang til for mottaksstatus.
6. Kontroller loggene for:
   - filnavnene for A, B, C og D ved innlesing
   - SKE-feilen for A
   - at B sendes som opprett krav
   - Slack-varsel for C og D
   - operatørens frigivelse fra rapporten
   - ordinær utsending av den frigjorte raden

7. Frigi eventuelle gjenværende C-/D-rader og trigger sending på nytt, slik at testdata ikke blir stående i `PENDING_*`.
                                       

## Tolkning av testresultater
Databasedump (med SQL kommandoer) av hva resultatene skal være i databasen etter innlesning av filene er dokumentert i resultatfilene slik at du kan gjøre en grundig sammenligning.

## Testfiler for andre avsendere

Mappene for Arena, Pesys og Infotrygd under `testfiler/` er foreløpig tomme. Det finnes per i dag ingen dedikerte testfiler for disse avsenderne. Dersom du skal teste integrasjoner mot disse fagsystemene må testfiler opprettes manuelt.

> **Merk:** For alle avsendere aksepteres tom utbetalingsdato, og feltet `foreldelsesfristensUtgangspunkt` utelates da fra requesten til SKE. Ugyldige eller feilformaterte utbetalingsdatoer aksepteres ikke og skal gi linjevalideringsfeil.
>
> For `ARENA`, `PESYS` og `INFOTRYGD` aksepteres også tom/blank `fagsystemId` – feltet utelates da fra requesten til SKE.
>
> Begge disse punktene bør verifiseres ved manuell testing.
