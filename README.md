# sokos-ske-krav

* [1. Funksjonelle krav](#1-funksjonelle-krav)
* [2. Utviklingsmiljø](#2-utviklingsmiljø)
* [3. Dokumentasjon](#3-dokumentasjon)
* [4. Deployment](#4-deployment)
* [5. Autentisering](#5-autentisering)
* [6. Drift og støtte](#6-drift-og-støtte)
* [7. Henvendelser](#7-henvendelser)

# 1. Funksjonelle Krav

Applikasjonen er en tjeneste som sender tilbakekrevingskrav til Skatteetatens nye REST tjeneste, som på sikt skal
ertstatte PAK.
Den henter flatfiler fra filmottakserveren, mapper de om til objekter, og sender kravene
ihht [SKE sin kontrakt](https://app.swaggerhub.com/apis/skatteetaten/oppdragsinnkreving-api/).
Oppbygningen av flatfilene er dokumentert
i [Confluence](https://confluence.adeo.no/pages/viewpage.action?pageId=176706565)

# 2. Utviklingsmiljø

### Forutsetninger

* Java 25
* Gradle
* [Kotest](https://plugins.jetbrains.com/plugin/14080-kotest) plugin for å kjøre Kotest tester
* [vault](https://github.com/navikt/utvikling/blob/main/docs/teknisk/Vault.md) for å kjøre `setupLocalEnvironment.sh`
* [jq](https://github.com/stedolan/jq) for å kjøre `setupLocalEnvironment.sh`

### Lokal utvikling

NB! Du må ha [naisdevice](https://docs.nais.io/device/) kjørende på maskinen.

For å kjøre applikasjonen må du gjøre følgende:

- Kjør scriptet [setupLocalEnvironment.sh](setupLocalEnvironment.sh)
     ```
     chmod 755 setupLocalEnvironment.sh && ./setupLocalEnvironment.sh
     ```                                
  Denne vil opprette [defaults.properties](defaults.properties) med alle environment variabler du trenger for å kjøre
  applikasjonen som er definert i [PropertiesConfig](src/main/kotlin/no/nav/sokos/ske/krav/config/PropertiesConfig.kt).

# 3. Dokumentasjon

### Funksjonalitet
| Dokument                                                                        | Beskrivelse                                             |
|---------------------------------------------------------------------------------|---------------------------------------------------------|
| [Overordnet beskrivelse](dokumentasjon/beskrivelse/overordnet/Overordnet_beskrivelse.md) | Hva applikasjonen gjør, aktører, flyt og kravtyper      |
| [Funksjonell flyt](dokumentasjon/arkitektur/Funksjonell_flyt.md)                         | Detaljerte sekvens- og flytdiagrammer                   |
| [Systemarkitektur](dokumentasjon/arkitektur/Systemarkitektur.md)                         | Komponent- og infrastrukturdiagrammer                   |
| [Beskrivelse](dokumentasjon/funksjonalitet/Beskrivelse.md)                               | Detaljert beskrivelse av input, behandling og kravtyper |
| [Begrepsforklaring](dokumentasjon/funksjonalitet/Begrepsforklaring.md)                   | Ordliste – begreper i NAV vs. SKE-kontekst              |
| [Stønadstyper](dokumentasjon/funksjonalitet/Stonadstyper.md)                             | Fullstendig kravkode + hjemmelkode-mappingtabell        |
| [Databasestruktur](dokumentasjon/funksjonalitet/Databasestruktur.md)                     | Tabeller, kolonner og migrasjonshistorikk               |

### Løsningsbeskrivelse
| Dokument                                                                                       | Beskrivelse                                       |
|------------------------------------------------------------------------------------------------|---------------------------------------------------|
| [Klassebeskrivelser](dokumentasjon/beskrivelse/overordnet/Klassebeskrivelser.md)                        | Ansvar og oppgaver for alle klasser               |
| [Serviceklasser](dokumentasjon/beskrivelse/detaljert/Serviceklasser.md)                                 | Detaljert beskrivelse av alle serviceklasser      |
| [Validering](dokumentasjon/beskrivelse/detaljert/Validering.md)                                         | Detaljerte valideringsregler (fil og linje)       |
| [SKE-requests og feilhåndtering](dokumentasjon/beskrivelse/detaljert/SKE_requests_og_feilhandtering.md) | Request-oppbygging og HTTP-feilhåndtering mot SKE |

### Drift og testing
| Dokument                                           | Beskrivelse                                           |
|----------------------------------------------------|-------------------------------------------------------|
| [Drift](dokumentasjon/Drift.md)                             | BAU-oppgaver, nyttige SQL-kommandoer og Vault-aliaser |
| [Feilretting Guide](dokumentasjon/Feilretting_Guide.md)     | Feilscenarioer, årsaker og tiltak                     |
| [Manuell testing](dokumentasjon/testing/Manuell_testing.md) | Steg-for-steg guide for manuell testing mot dev       |

# 4. Deployment

Distribusjon av tjenesten er gjort med bruk av Github Actions.
[sokos-ske-krav CI / CD](https://github.com/navikt/sokos-ske-krav/actions)

Push/merge til main branch direkte er ikke mulig. Det må opprettes PR og godkjennes før merge til main branch.
Når PR er merged til main branch vil Github Actions bygge og deploye til dev-fss og prod-fss.
Har også mulighet for å deploye manuelt til testmiljø ved å deploye en PR.

# 5. Autentisering
Applikasjonen bruker to autentiseringsmekanismer:
- **[AzureAD](https://docs.nais.io/security/auth/azure-ad/)** – beskytter API-endepunktene (`/api/*`). Krever et gyldig Azure AD JWT-token i `Authorization`-headeren.
- **Basic Auth** – beskytter webgrensesnittet for rapporter (`/rapporter/*`). Brukernavn og passord er konfigurert via miljøvariabler.

# 6. Drift og støtte

Se [Drift](dokumentasjon/Drift.md) for BAU-oppgaver og nyttige kommandoer, [Feilretting Guide](dokumentasjon/Feilretting_Guide.md) for feilscenarioer, og [Manuell testing](dokumentasjon/testing/Manuell_testing.md) for testing mot dev-miljøet.

### Logging

Feilmeldinger og infomeldinger som ikke inneholder sensitive data logges til [Grafana Loki](https://docs.nais.io/observability/logging/#grafana-loki).  
Sensitive meldinger logges til [Team Logs](https://doc.nais.io/observability/logging/how-to/team-logs/).

### Kubectl

For dev-fss:

```shell script
kubectl config use-context dev-fss
kubectl get pods -n okonomi | grep sokos-ske-krav
kubectl logs -f sokos-ske-krav-<POD-ID> --namespace okonomi -c sokos-ske-krav
```

For prod-fss:

```shell script
kubectl config use-context prod-fss
kubectl get pods -n okonomi | grep sokos-ske-krav
kubectl logs -f sokos-ske-krav-<POD-ID> --namespace okonomi -c sokos-ske-krav
```

### Alarmer

Applikasjonen bruker [Grafana Alerting](https://grafana.nav.cloud.nais.io/alerting/) for overvåkning og varsling.
Dette er konfigurert via NAIS sin [alerting-integrasjon](https://doc.nais.io/observability/alerts).

Alarmene overvåker metrics som:

- HTTP-feilrater
- JVM-metrikker

Varsler blir sendt til følgende Slack-kanaler:

- Dev-miljø: [#team-mob-alerts-dev](https://nav-it.slack.com/archives/C042SF2FEQM)
- Prod-miljø: [#team-mob-alerts-prod](https://nav-it.slack.com/archives/C042ESY71GX)

### Grafana

[sokos-ske-krav](https://grafana.nav.cloud.nais.io/goto/KzX7VOkDg?orgId=1)

# 7. Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på Github.
Interne henvendelser kan sendes via Slack i kanalen [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP)