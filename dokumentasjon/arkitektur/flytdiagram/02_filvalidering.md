# 2. Filvalidering

Verifiserer at filen er konsistent – at header, footer og innhold stemmer overens.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Input[ParseResult.Success] --> AntallCheck{Antall linjer<br/>== footer.antall?}

    AntallCheck -->|Nei| Feil1[Feil: Avvik i antall]
    AntallCheck -->|Ja| SumCheck{Sum beløp<br/>== footer.sum?}

    SumCheck -->|Nei| Feil2[Feil: Avvik i sum]
    SumCheck -->|Ja| DatoCheck{header.dato<br/>== footer.dato?}

    DatoCheck -->|Nei| Feil3[Feil: Dato-avvik]
    DatoCheck -->|Ja| OB04Check{OB04: har<br/>fagsystemId?}

    OB04Check -->|Nei| Feil4[Feil: FagsystemId mangler]
    OB04Check -->|Ja| OK[Fil godkjent]

    Feil1 --> Avvist[Avvist<br/>Flyttes til /feilfiler]
    Feil2 --> Avvist
    Feil3 --> Avvist
    Feil4 --> Avvist

    style Input fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style AntallCheck fill:#fff3b0,stroke:#e6d476,color:#000
    style SumCheck fill:#fff3b0,stroke:#e6d476,color:#000
    style DatoCheck fill:#fff3b0,stroke:#e6d476,color:#000
    style OB04Check fill:#fff3b0,stroke:#e6d476,color:#000
    style OK fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Feil1 fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Feil2 fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Feil3 fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Feil4 fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Avvist fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Valideringsregler

| Regel | Sjekk |
|-------|-------|
| Antall | Antall kravlinjer i filen == `footer.antallTransaksjoner` |
| Sum | `sum(belop + belopRente)` for alle linjer == `footer.sumAlleTransaksjoner` |
| Dato | `header.transaksjonsDato` == `footer.transaksjonTimestamp` |
| FagsystemId | Alle kravlinjer fra avsender OB04 må ha `fagsystemId` |
