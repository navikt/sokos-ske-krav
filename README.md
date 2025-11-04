# sokos-ske-krav

# 1. Funksjonelle Krav

Applikasjonen er en tjeneste som sender tilbakekrevingskrav til Skatteetatens nye REST tjeneste, som på sikt skal
ertstatte PAK.  
Den henter flatfiler fra filmottakserveren, mapper de om til objekter, og sender kravene
ihht [SKE sin kontrakt](https://app.swaggerhub.com/apis/skatteetaten/oppdragsinnkreving-api/).
SKE sender tilbake en *kravidentifikator* som kobles til NAV sitt saksnummer for kravet og blir så lagret i database.
Oppbygninen av flatfilene er dokumentert
i [confluence](https://confluence.adeo.no/pages/viewpage.action?pageId=176706565)

# 2. Utviklingsmiljø

### Forutsetninger

* Java 21
* Gradle
* [Kotest](https://plugins.jetbrains.com/plugin/14080-kotest) plugin for å kjøre Kotest tester
* [vault](https://github.com/navikt/utvikling/blob/main/docs/teknisk/Vault.md) for å kjøre `setupLocalEnvironment.sh`
* [jq](https://github.com/stedolan/jq) for å kjøre `setupLocalEnvironment.sh`

### Bygge prosjekt

`./gradlew build shadowjar`

### Lokal utvikling

NB! Du må ha [naisdevice](https://docs.nais.io/device/) kjørende på maskinen.

For å kjøre applikasjonen må du gjøre følgende:

- Kjør scriptet [setupLocalEnvironment.sh](setupLocalEnvironment.sh)
     ```
     chmod 755 setupLocalEnvironment.sh && ./setupLocalEnvironment.sh
     ```                                
  Denne vil opprette [default.properties](defaults.properties) med alle environment variabler (bortsett fra
  POSTGRES_USERNAME og POSTGRES_PASSWORD, som må hentes manuelt fra vault) du trenger for å kjøre
  applikasjonen som er definert i [PropertiesConfig](src/main/kotlin/sokos/ske/krav/config/PropertiesConfig.kt).

### SFTP tilkobling

Etter at ```setupLocalEnvironment.sh``` er kjørt, vil den opprette en ```privKey``` fil. Den burde legges inn der man oppbevarer ssh nøkler, f.eks ```.ssh```.
Scriptet vil hente brukernavn og passord til ```defaults.properties``` i form av variablene ```SKE_SFTP_USERNAME``` og ```SKE_SFTP_PASSWORD```.

FileZilla er en god klient for å koble seg til SFTP. Bruk login with key file.
Host og port finner man i [PropertiesConfig](https://github.com/navikt/sokos-ske-krav/blob/main/src/main/kotlin/no/nav/sokos/ske/krav/config/PropertiesConfig.kt#L45-L46)

På MacOS kan den be om en ```.ppk``` nøkkel. Isåfall la FileZilla automatisk konvertere privatnøkkelen til .ppk og referer til denne.

For å teste manuelt i dev når man er logget på, legger man en testfil i ``ìnbound`` mappen. Kjøring kan trigges med endepunktet [https://sokos-ske-krav.intern.dev.nav.no/api/hentNye](https://sokos-ske-krav.intern.dev.nav.no/api/hentNye)

### Alarmer

Applikasjonen bruker [Grafana Alerting](https://grafana.nav.cloud.nais.io/alerting/) for overvåkning og varsling.
Dette er konfigurert via NAIS sin [alerting-integrasjon](https://doc.nais.io/observability/alerts).

Alarmene overvåker metrics som:

- HTTP-feilrater
- JVM-metrikker

Varsler blir sendt til følgende Slack-kanaler:

- Dev-miljø: [#team-mob-alerts-dev](https://nav-it.slack.com/archives/C042SF2FEQM)
- Prod-miljø: [#team-mob-alerts-prod](https://nav-it.slack.com/archives/C042ESY71GX)

# 3. Programvarearkitektur

```mermaid
stateDiagram-v2
    direction LR
    state mottak {
        direction LR
        lesfil --> validerFil
        validerFil --> Skriv_statusfil: valideringfeilet
        validerFil --> valider_alle_records: valideringOk
        valider_alle_records --> lagAlarm: validering_av_linj_feilet
        valider_alle_records --> SendKrav: validerin_OK
    }
state sendKrav {
direction LR
SendKrav --> OpprettNyttKrav: IkkeStop_IkkeEndring
SendKrav --> SendEndring: IkkeStopp_harGammelref
SendKrav --> SendStopp: HovedStolEr_0,0
OpprettNyttKrav --> [BehandleResponse]
SendEndring --> [BehandleResponse]
SendStopp --> [BehandleResponse]
}
```

```mermaid
stateDiagram-v2
    direction LR
[BehandleResponse] --> responseOk
[BehandleResponse] --> responseIkkeOk
state BehandleResponse{
direction LR
responseOk --> LagreKrav
responseIkkeOk --> LoggFeil
LoggFeil --> lagAlarm

}
```

```mermaid
stateDiagram-v2
    direction LR
    state mottak {
        direction LR
        lesfil --> validerFil
        validerFil --> Skriv_statusfil: valideringfeilet
        validerFil --> valider_alle_records: valideringOk
        valider_alle_records --> lagAlarm: validering_av_linj_feilet
        valider_alle_records --> SendKrav: validerin_OK
    }
state sendKrav {
direction LR
SendKrav --> OpprettNyttKrav: IkkeStop_IkkeEndring
SendKrav --> SendEndring: IkkeStopp_harGammelref
SendKrav --> SendStopp: HovedStolEr_0,0
OpprettNyttKrav --> [BehandleResponse]
SendEndring --> [BehandleResponse]
SendStopp --> [BehandleResponse]
}
```

```mermaid
stateDiagram-v2
    direction LR
[BehandleResponse] --> responseOk
[BehandleResponse] --> responseIkkeOk
state BehandleResponse{
direction LR
responseOk --> LagreKrav
responseIkkeOk --> LoggFeil
LoggFeil --> lagAlarm

}
```

# 4. Deployment

Distribusjon av tjenesten er gjort med bruk av Github Actions.
[sokos-ske-krav CI / CD](https://github.com/navikt/sokos-ske-krav/actions)

Push/merge til master branche vil teste, bygge og deploye til produksjonsmiljø og testmiljø.

# 5. Drift og støtte

### Logging

TODO

### Grafana

[prod-fss](https://grafana.nav.cloud.nais.io/goto/KzX7VOkDg?orgId=1)

# 6. Henvendelser og tilgang

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på Github.\
Interne henvendelser kan sendes via Slack i kanalen `#utbetaling`