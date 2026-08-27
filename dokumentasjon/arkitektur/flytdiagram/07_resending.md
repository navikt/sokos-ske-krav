# 7. Resending

Krav som feilet med retrybare statuser plukkes opp og sendes på nytt.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Start[Hent krav for resending] --> Query[(SELECT krav WHERE status IN<br/>retrybare feilstatuser)]

    Query --> HasKrav{Finnes krav å resende?}
    HasKrav -->|Nei| Done([Ingen resending nødvendig])
    HasKrav -->|Ja| Send[Send krav til SKE]

    Send --> Result{Resultat?}
    Result -->|Suksess| NewStatus[(Status → KRAV_SENDT)]
    Result -->|Feil igjen| KeepStatus[(Behold feilstatus<br/>prøves igjen neste kjøring)]

    subgraph Resend_Timing["Kjøremønster i handleNewKrav"]
        R1[1. resendKrav] --> NewFiles[2. Behandle nye filer]
        NewFiles --> Delay[3. delay 5s]
        Delay --> R2[4. resendKrav igjen]
    end

    style Start fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Query fill:#b4d7ff,stroke:#7baed4,color:#000
    style HasKrav fill:#fff3b0,stroke:#e6d476,color:#000
    style Done fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Send fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Result fill:#fff3b0,stroke:#e6d476,color:#000
    style NewStatus fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style KeepStatus fill:#ffc4c4,stroke:#e69a9a,color:#000
    style R1 fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style NewFiles fill:#b4d7ff,stroke:#7baed4,color:#000
    style Delay fill:#fff3b0,stroke:#e6d476,color:#000
    style R2 fill:#ffe0f0,stroke:#e6a8c8,color:#000
```

## Retrybare statuser

Krav med disse statusene plukkes opp for automatisk resending:

| Status | Årsak |
|--------|-------|
| `HTTP409_*_RESEND` | Konflikt som kan løses ved retry |
| `HTTP500_*` | Serverfeil hos SKE |
| `HTTP503_*` | SKE midlertidig utilgjengelig |

## Stagnerende krav

Krav som har vært forsøkt resendt i over 24 timer uten suksess fanges opp av en separat jobb (`checkForStangendeKrav`) som kjører daglig. Disse krever manuell oppfølging.
