#  Info
Dette er ment som en guide for utviklere for å feilsøke og feilrette.

I de aller fleste tilfeller skal vi kun rapporerte feilen til produkteier/produktleder, og så vil de håndtere videre kommunikasjon med fagressurser og Skatteetaten.
Alle feil, med unntak av validering som skjer på vår side, vil vises på [denne siden](https://sokos-ske-krav.intern.nav.no/rapporter/avstemming) med informasjon som trengs for å feilsøke og rapportere. Produkteier og teknisk domenespesialist har tilgang til denne siden.



De vanligste feilene er:
* Synkrone valideringsfeil ( [dokumentasjon Nav spesifikke regler](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/oppdragsgiverspesifikke-valideringsregler/nav#synkrone-valideringsregler) , [dokumentasjon fellesregler](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler)  )
    * Kravtype finnes ikke for organisasjon/person
    * "Dobbel endring på migrert krav"
    * Det finnes et krav med samme saksnummer
    * Skatt har ikke lagt inn en kravtype som er definert i systemet vårt
* Asynkron valideringsfeil ( [dokumentasjon](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/oppdragsgiverspesifikke-valideringsregler/nav#asynkrone-valideringsregler) )
    * Person er død
    * Organisasjon er opphørt
* Det "stanger" mellom Skatt og PAK og krav blir ikke reskontroførte
* Feil i kommunikasjon med SKE
* Feil i rutiner for fagsystemene. For eksempel at de sender inn krav med feil dato, eller at de sender inn det samme kravet to ganger
 
### Hvor og hvordan finne informasjon
- Slackkanal for funksjonelle feil: #team-best-slackbot-prod
- Slackkanal for tekniske feil: #team-mob-alerts-prod
- [Grafana Loki logs](https://grafana.nav.cloud.nais.io/goto/ffees1emmaghsa?orgId=1)
- [GCP (sikker) logs](https://cloudlogging.app.goo.gl/xarQ8g9swFiCtgpt5)

I #team-best-slackbot-prod sendes informasjon om funksjonelle feil, dvs hvis et krav feiler i validering hos oss eller blir avvist av skatt. Slackmeldingen vil inneholde all informasjon som du som utvikler trenger for å rapportere videre eller for å undersøke nærmere. Det sendes **aldri** personopplysninger.    
I #team-mob-alerts-prod kommer det alerts når en logger.error(..) linje i koden blir truffet og det skjer når det er noe *teknisk* feil, som feil i databasekommunikasjon eller feil i dekoding av json til objekt. Exception meldinger logges *kun* til sikker logg (GCP)

## Dobbel endring på migrert krav
Problem: Nye krav som ikke har gått gjennom denne applikasjonen vil ikke ha en kravidentifikator (som er det skatt bruker i sine systemer). Når vi sender inn en endring på et krav så må vi sende med deres kravidentifikator.  
Skatteetaten migrerte derfor krav for hele 2024 slik at de har en kravidentifikator.
Dette løste problemet med *første* endring av et krav siden vi bruker "referansenummergammelsak" til å finne kravidentifikatoren via deres "Avstemming API". Men når vi får inn en endring på et krav som allerede er endret OG ikke har gått gjennom denne applikasjonen så har vi ingen måte å finne kravidentifikatoren på.   
En visuell forklaring:

| Type krav | Saksnummer nav | Referansenummer gammel sak | Kravidentifikator                                                                                               |
|:----------|:---------------|:---------------------------|:----------------------------------------------------------------------------------------------------------------|
| Nytt      | A              |                            | 123                                                                                                             |
| Endring   | B              | A                          | Avstemming API: Bruker A for å finne kravidentifikator                                                          |
| Endring   | C              | B                          | Kan ikke bruke B i Avstemming, for SKE har inget krav med saksnummer B. Kan dermed ikke finne kravidentifikator |

### Løsning
Rapporter til produkteier/produktleder. Dette må håndteres manuelt og en Jira sak i Skatt sitt system vil opprettes.

## Synkrone og asynkrone valideringsfeil
Vår linjevalidering validerer ihht til Skatteetatens valideringsregler for datoer, formater, osv så synkrone valideringsfeil på dette forekommer ikke med mindre Skatt har endret noe på sin side.   
Alle tilfeller av synkrone og asynkrone valideringsfeil må følges opp manuelt og rapporteres til produkteier/produktleder.

## Krav blir ikke reskontroførte
Vi har en alert for om et krav er blitt forsøkt resendt i over 24 timer slik at dette kan fanges opp. Dette skjer som regel fordi det "stanger" mot PAK på deres side og utviklere hos Skattetaten må involveres. Kontakt de i Slackkanalen #utbetaling-tilbakekreving-fi. Det løser seg som regel i løpet av 1-2 dager.

## Feil i kommunikasjon med SKE
Applikasjonen benytter seg av en circuit breaker, så ved kommunikasjon/serverfeil vil det komme en melding i #team-mob-alerts-prod. Hvis du oppdager feil i kommunikasjonen bør du undersøke logger og database, og rapportere dette til Skatteetaten i Slackkanalen #utbetaling-tilbakekreving-fi. Hent ut corr-id fra Kravtabellen for et krav som feilet i oversending slik at skatteetaten kan bruke den for å finne hendelsen i sine logger.

Når vi sender inn krav og får httpstatus 500 eller 503 som svar vil disse kravene bli forsøkt resendt siden 500 og 503 er som regel forbigående problemer. Http 409 med feilmelding "innkrevingsoppdrag-er-ikke-reskontrofoert" vil også bli resendt siden at et krav ikke er reskontroført også som regel er et forbigånde problem.  
Resten av feilene i 400- og 500-serien må håndteres manuelt, og når årsaken er løst må utvikler logge seg inn i databasen og sette statusen på de aktuelle kravene til "KRAV_IKKE_SENDT" for å trigge en resending.   Man må først informere produktleder om at man må gjøre dette og få tillatelse, og så må man opprette en oppgave i Jira.


## Feil i rutiner for fagsystemene
Det har forekommet at fagsystemene sender inn krav som allerede er sendt inn tidligere. Dette vil Skatteetaten avvise og det må følges opp av produktleder/produkteier

# Valideringsregler
Det finnes to typer validering: Filvalidering, og Linjevalidering. Filvalidering validerer at Oppdrag-Z har generert filen korrekt, og det har per dags dato aldri forekommet at
dette feiler. Linjevalidering validerer at kravlinjene er i henhold til våre interne regler samt Skatteetatens regler og at de kan sendes til Skatteetaten.

### Filvalidering
Fil blir validert  FileValidator.kt  
Filen vi mottar inneholder en header og en footer med tilleggsinformasjon. Footeren inneholder antall transaksjoner ("kravlinjer"), sum alle transksjoner (hovedstol + rentebeløp), og transaksjonsdato. Headeren inneholder også transaksjonsdato.  
Det finnes tre valideringsregler:
* Antall linjer må stemme ( lastLine.antallTransaksjoner må være lik kravLinjer.size)
* Sum alle transaksjoner må stemme (kravLinjer.sumOf { it.belop + it.belopRente } må være lik lastLine.sumAlleTransaksjoner)
* Transaksjonsdato i header og footer må være lik

Dersom filvalidering feiler vil en melding bli sendt til Slack og filen vil flyttes til FTP directorien inbound/feilfiler. Filen vil ikke bli behandlet videre (ingen krav vil bli sendt) da den anses å være korrupt og dette må håndteres manuelt.


### Linjevalidering
Enkeltlinjer blir validert i Linevalidator.kt i henhold til [Skatteetatens regler](https://skatteetaten.github.io/beta-apier/innkrevingsoppdrag/felles-valideringsregler]).

* Saksnummer er på ugyldig format (må være "^[a-zA-Z0-9-/]+$")
* Vedtaksdato kan ikke være i fremtiden
* Kravtype er ikke definert for oversending til skatt
    * Det vil si, det kan for eksempel finnes en ny kravkode som skatt ikke vet om, eller så har skatt på sin side lagt inn håndtering men utviklerne i Nav har ikke fått beskjed og har ikke fått lagt inn mappingen
* Refnummer gammel sak er på ugyldig format (må være "^[a-zA-Z0-9-/]+$")
* Perioden FOM-TOM:
    * FOM-dato kan ikke være etter TOM (dvs FOM kan være lik TOM)
    * TOM-dato kan være frem i tid, men ikke lenger frem enn inneværende måned
* Utbetalingsdato må vœre før vedtaksdato

Begrepet "må være i fremtiden" betyr at TOM dato må være senere enn dagens dato. "Må være i fortid" betyr at FOM dato må være tidligere enn dagens dato.