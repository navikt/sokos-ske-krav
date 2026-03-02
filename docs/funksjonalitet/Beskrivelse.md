# Input
OS/Z legger flatfiler (fixed record) av innkrevingsoppdrag på en FTP server én gang i døgnet (cirka kl 23). Oppbygninen av flatfilene er dokumentert detaljert
i [Confluence](https://confluence.adeo.no/pages/viewpage.action?pageId=176706565)  
Filen består av en header, n antall linjer av krav, og en footer. I footeren legger OS/Z på informasjon om hvor mange krav som finnes i filen, og samlet beløp for alle kravene.
### Validering
Filen blir i applikasjonen validert ved å bekrefte at informasjonen i footeren stemmer overens med antall innleste krav samt beregnet beløp. Valideringsreglene for filvalidering finnes i [Filevalidator.kt](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/sokos/ske/krav/validation/FileValidator.kt).  
Dersom filvalidering feiler vil en alarm sendes til en slack kanal, og filen flyttes til et eget område på filserveren.


Kravene parses til objektet "Kravlinjer" og hver linje blir så validert. Valideringsreglene for linjevalidering finnes i [LineValidator.kt](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/sokos/ske/krav/validation/LineValidator.kt).    
Dersom linjevalidering feiler vil informasjon om linje og fil lagres i databasetabellen "Valideringsfeil", en alarm sendes til en slack kanal, og linjen vil ikke bli forsøkt overført til Skatteetaten.


# Behandling

            
### Databasen
Databasen har tre tabeller:
- Krav
  - Inneholder innlest og validert data fra filen
- Feilmelding
  - Feil fra skatteetaten 
- Filvalideringsfeil
  - Feil fra filvalideringen
 
 Databasen brukes som en state machine gjennom programmet.  
 
### Flyt 
Alle kravlinjene som er blitt validert uten feil lagres i databasen med statusen "KRAV_IKKE_SENDT". Krav som feiler linjevalidering lagres med status "VALIDERINGSFEIL_AV_LINJE_I_FIL" og vil ikke bli sendt videre. Kun krav med status `KRAV_IKKE_SENDT` hentes ut for sending.  
Når kravene er lagret blir filen på FTP server flyttet fra /inbound til /outbound.  
Etter at kravene er lagret hentes ut alle krav som er endringer eller avskrivinger og vi prøver å finne den *originale* kravidentifikatoren og oppdaterer disse kravene i databasen med den.

Deretter blir alle krav med status KRAV_IKKE_SENDT overført til Skatteetaten og får status KRAV_SENDT. Statusen oppdateres til det vi får fra Skatteetaten når vi kaller hent mottaksstatus (MOTTATT_UNDER_BEHANDLING og så RESKONTROFOERT eller MIGRERT). Når et krav har fått status RESKONTROFOERT eller MIGRERT er "jobben vår" ferdig.

> **Merk:** Statuskoden `KRAV_INNLEST_FRA_FIL` er definert i koden som en fallback i `insertAllNewKrav`, men brukes aldri i praksis – `LineValidator` setter alltid status eksplisitt til enten `KRAV_IKKE_SENDT` eller `VALIDERINGSFEIL_AV_LINJE_I_FIL` før kravene lagres.

## Type krav
Det finnes tre typer krav:
- Nytt krav
- Endring
- Avskrivning/stopp

*Endringer* og *avskrivinger* har i flatfilen utfyllt "Referanse gammel sak". Dette er fordi i Nav-verden anses endringer og avskrivinger å være nye vedtak, og for sporbarhet så fylles dette feltet ut.
        

## Endringer
Skatteetaten har designet løsningen slik at endringer skal sendes inn til to forskjellige endepunkt: Endre hovedstol, og endre rente. Problemet er at denne applikasjonen ikke vet *hva* som har endret seg når vi får inn en endring. Den vet kun at *noe* har endret seg. Så hver endring vil *alltid* sendes til begge endepunktene. Dette gjør at i databasen vil vi ha to linjer i Kravtabellen for hver endring: Én med kravtype "ENDRING_HOVEDSTOL", og én med kravtype "ENDRING_RENTE".  Og fordi vi har designet databasen slik at hver innsending har én linje ville vi uansett ha måtte "splitte" kravet. 


### Saksnummer og kravidentifikator
I Nav bruker vi et *saksnummer* som starter med "OB04". Skatteetaten bruker en *kravidentifikator* som de genererer. Når vi sender inn et *nytt* krav vil vi få kravidentifikatoren i responsen. Vi bruker **alltid** deres kravidentifikator dersom vi har den.


Dersom det er første endring vil "referansenummer gammel sak" være lik det originale saksnummeret.  
Dersom det er *andre* endring vil "referansenummer gammel sak" være lik saksnummeret i den *første endringen*.  
Skatteetaten har derimot inget forhold tilvali dette nye saksnummeret ettersom de på sin side anser det som samme sak og det skal derfor ha samme kravidentifikator.

### Dobbel endring på migrerte krav
I **PAK** har de historisk sett brukt Nav sitt *saksnummer*, og som en del av moderniseringen har de "migrert" krav for 1 år tilbake i tid hvor disse har da fått en kravidentifikator.
Det finnes da tilfeller hvor verken de eller vi vil kunne finne kravidentifikatoren

``` 
1. Original: 
    Saksnummer: 123
    Referansenummer gammel sak: Blank
    Vi sender: 123
2. Første endring:
    Saksummer: 456
    Referansenummer gammel sak: 123
    Vi bruker "123" for å finne det originale kravet i databasen og henter ut kravidentifikatoren fra Skatteetaten og bruker den når vi sender endringen.
3. Andre endring:
    Saksnummer: 789
    Referansenummer gammel sak: 456
        Hvis originalkravet har gått gjennom várt system (Det vil si, etter 10. Oktober 2024) så kan vi finne det originale saksnummeret i vår database
        Hvis vi ikke fant det så vil vi spørre mot ett av Skatteetatens endepunkter ("avstemming") og hvis dette er et migrert krav så vil vi få deres kravidentifikator.
        Hvis det ikke er et migrert krav så vil vi få en feilmelding om at de ikke finner kravet og dette må da følges opp manuelt. 
```
 Dette scenarioet kaller vi "dobbel endring på et migrert krav". Når dette skjer vil vi få en alarm i Slackkanalen #team-best-slackbot-prod og det vil følges opp manuelt av noen på fagsiden

### Kravkode + hjemmelkode = Stønadstype   (kravtype)
SKE bruker "kravtype" som identifikator for hvilken stønad kravet gjelder. I Nav bruker vi kravkode og hjemmelkode. Kombinasjoner av kravkode + hjemmelkode mappes derfor til stønadstype på vår side. Se [fullstendig oversikt over alle stønadstyper og kravkoder](Stonadstyper.md).    
Dersom Nav oppretter nye kravtyper må vi i Utbetalingsseksjonen koordinere med SKE for at de skal oppdatere mappingen sin.  
Vi validerer på vår side at kravtypen finnes, så disse vil feile i validering.  
PS. Av historiske årsaker heter "kravtype" "stønadstype" i vår domenemodell, og "kravtype" betyr typen krav (ny/endring/stopp)