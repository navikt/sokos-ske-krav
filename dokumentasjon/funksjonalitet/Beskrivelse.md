# Funksjonalitetsbeskrivelse

## Input

Fagsystemet OS/Z legger flatfiler (fixed record-format) av innkrevingsoppdrag på en SFTP-server én gang i døgnet (rundt kl. 23). Filen består av:
- **Header** – første linje, brukes kun til filvalidering
- **Kravlinjer** – n antall krav som skal overføres til Skatteetaten
- **Footer** – siste linje, inneholder antall transaksjoner og samlet beløp                    

Oppbygninen av flatfilene er dokumentert detaljert i [Confluence](https://confluence.adeo.no/pages/viewpage.action?pageId=176706565)

## Validering

### Filvalidering
Filen valideres ved å bekrefte at informasjonen i footeren stemmer overens med antall innleste krav og beregnet beløp. Valideringsreglene finnes i [`FileValidator.kt`](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/validation/FileValidator.kt).

Dersom filvalidering feiler vil en alarm sendes til Slack og filen flyttes til `/inbound/feilfiler`. Ingen krav fra filen behandles videre.

### Linjevalidering
Kravene parses til [`KravLinje`](../../src/main/kotlin/no/nav/sokos/ske/krav/copybook/FixedRecord.kt)-objekter og hver linje valideres individuelt. Valideringsreglene finnes i [`LineValidator.kt`](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/validation/LineValidator.kt).

Dersom linjevalidering feiler lagres informasjon om linjen i databasetabellen `filvalideringsfeil`, en alarm sendes til Slack, og linjen sendes **ikke** videre til Skatteetaten. Øvrige gyldige linjer i filen behandles normalt.

Se [detaljert valideringsdokumentasjon](../beskrivelse/detaljert/Validering.md) for fullstendig oversikt over alle valideringsregler.

## Behandling

### Databasen
Databasen har tre tabeller:
- **`krav`** – inneholder innlest og validert data fra filene
- **`feilmelding`** – feil returnert fra Skatteetaten
- **`filvalideringsfeil`** – feil fra fil- og linjevalidering

Databasen brukes som en state machine gjennom hele behandlingsløpet. Se [Databasestruktur](Databasestruktur.md) for kolonner og migrasjonshistorikk.

### Flyt
1. Gyldige kravlinjer lagres i databasen med status `KRAV_IKKE_SENDT`
2. Krav som feiler linjevalidering lagres med status `VALIDERINGSFEIL_AV_LINJE_I_FIL` og sendes ikke videre
3. Filen flyttes fra `/inbound` til `/outbound` på SFTP-serveren
4. For endringer og avskrivinger hentes den originale kravidentifikatoren fra SKE og lagres i databasen
5. Alle krav med status `KRAV_IKKE_SENDT` sendes til Skatteetaten og får status `KRAV_SENDT`
6. Statusen oppdateres til `MOTTATT_UNDER_BEHANDLING` og deretter til `RESKONTROFOERT` eller `MIGRERT` basert på svar fra Skatteetaten

Når et krav har fått status `RESKONTROFOERT` eller `MIGRERT` er behandlingen ferdig.

> **Merk:** Statuskoden `KRAV_INNLEST_FRA_FIL` er definert i koden som en fallback, men brukes aldri i praksis – `LineValidator` setter alltid status eksplisitt til enten `KRAV_IKKE_SENDT` eller `VALIDERINGSFEIL_AV_LINJE_I_FIL`.

## Kravtyper

Det finnes tre typer krav:

| Kravtype          | Beskrivelse                       |
|-------------------|-----------------------------------|
| Nytt krav         | Nytt tilbakekrevingsvedtak        |
| Endring           | Endring av eksisterende krav      |
| Avskrivning/stopp | Avslutter innkrevingen av et krav |

*Endringer* og *avskrivinger* har utfylt feltet "Referanse gammel sak" i flatfilen. Dette er fordi NAV anser endringer og avskrivinger som nye vedtak, og feltet sikrer sporbarhet.

## Endringer

Skatteetaten krever at endringer sendes til to separate endepunkter: ett for endring av hovedstol og ett for endring av rente. Applikasjonen vet ikke *hva* som har endret seg – kun at *noe* har endret seg. Derfor sendes hver endring **alltid** til begge endepunktene.

Dette betyr at det i databasen opprettes **to rader** per endring: én med kravtype `ENDRING_HOVEDSTOL` og én med kravtype `ENDRING_RENTE`.

### Saksnummer og kravidentifikator
NAV bruker et *saksnummer* (f.eks. starter med "OB04"). Skatteetaten bruker en *kravidentifikator* som de genererer selv. Når vi sender inn et *nytt* krav får vi kravidentifikatoren i responsen. Vi bruker **alltid** SKEs kravidentifikator dersom vi har den.

- Første endring: `referansenummer gammel sak` = originalt saksnummer
- Andre endring: `referansenummer gammel sak` = saksnummeret fra første endring

Skatteetaten anser dette som samme sak og forventer at samme kravidentifikator brukes.

### Dobbel endring på migrerte krav
Skatteetaten migrerte krav for å tildele kravidentifikatorer til eldre saker. Dette løser problemet med *første* endring på et migrert krav, men ved *andre* endring på et krav som ikke har gått gjennom dette systemet, kan vi mangle kravidentifikatoren:

| Steg           | Saksnummer | Ref. gammel sak | Hva skjer                                                                                             |
|----------------|------------|-----------------|-------------------------------------------------------------------------------------------------------|
| Nytt krav      | 123        | –               | Sendes, SKE gir kravidentifikator                                                                     |
| Første endring | 456        | 123             | Vi slår opp "123" i DB eller hos SKE (avstemming-API) og finner kravidentifikatoren                   |
| Andre endring  | 789        | 456             | Hvis krav 456 aldri gikk gjennom dette systemet kan vi ikke finne kravidentifikatoren → alarm i Slack |

Dette scenarioet kalles «dobbel endring på et migrert krav». Det varsles i Slackkanalen `#team-best-slackbot-prod` og følges opp manuelt av fagsiden. Se [Feilretting_Guide.md](../Feilretting_Guide.md) for mer detaljer.

## Kravkode + hjemmelkode = Stønadstype

SKE bruker *kravtype* som identifikator for hvilken stønad kravet gjelder. NAV bruker *kravkode* og *hjemmelkode*. Applikasjonen mapper kombinasjonen til en intern [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt)-enum som igjen brukes som kravtype i SKE-requester.

> **Merk:** Av historiske årsaker heter klassen [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt) i koden, men tilsvarer det SKE kaller `kravtype`. Begrepet `kravtype` i koden brukes separat for å angi om et krav er nytt/endring/stopp.

Se [fullstendig oversikt over alle stønadstyper og kravkoder](Stonadstyper.md). Dersom NAV oppretter nye kravkoder må dette koordineres med SKE, og den nye kombinasjonen legges inn i [`StonadsType`](../../src/main/kotlin/no/nav/sokos/ske/krav/domain/StonadsType.kt)-enumen.
