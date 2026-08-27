# 4. Lagring

Etter validering lagres alle kravlinjer i databasen. Filen flyttes til `/outbound`.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Input[Validerte kravlinjer<br/>gyldige + ugyldige] --> Insert[(INSERT krav<br/>status: KRAV_IKKE_SENDT)]
    Input --> InsertFeil[(INSERT filvalideringsfeil)]

    Insert --> MoveFile[Flytt fil til /outbound]

    MoveFile --> BigCheck{≥ 1000 linjer?}
    BigCheck -->|Ja| Halt[haltRun = true<br/>Blokkerer neste kjøring]
    BigCheck -->|Nei| Done[Klar for sending]

    Insert --> Lookup[Hent kravidentifikator<br/>for endringer/stopp]

    subgraph Identifikator-oppslag["Oppslag av SKE-kravidentifikator"]
        Lookup --> DBLookup{Finnes i<br/>vår database?}
        DBLookup -->|Ja| UseLocal[Bruk lagret identifikator]
        DBLookup -->|Nei| AskSKE[Spør SKE]
        AskSKE --> Found{Fant identifikator?}
        Found -->|Ja| SaveIdent[Lagre på kravet]
        Found -->|Nei| Mark404[Status: HTTP404]
    end

    style Input fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Insert fill:#b4d7ff,stroke:#7baed4,color:#000
    style InsertFeil fill:#ffc4c4,stroke:#e69a9a,color:#000
    style MoveFile fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style BigCheck fill:#fff3b0,stroke:#e6d476,color:#000
    style Halt fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Done fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Lookup fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style DBLookup fill:#fff3b0,stroke:#e6d476,color:#000
    style UseLocal fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style AskSKE fill:#b4d7ff,stroke:#7baed4,color:#000
    style Found fill:#fff3b0,stroke:#e6d476,color:#000
    style SaveIdent fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Mark404 fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Nøkkelpunkter

- Alle linjer (gyldige og ugyldige) lagres for sporbarhet
- `haltRun`-mekanismen forhindrer at neste scheduler-kjøring starter ved store filer
- For endringer og stopp trengs SKEs `kravidentifikator` – den slås opp lokalt først, deretter mot SKE
