# 6. Statusoppfølging

Poller SKE for mottaksstatus på krav som er sendt men ikke ferdigbehandlet.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Input[Krav med status<br/>KRAV_SENDT / MOTTATT] --> Poll[GET /mottaksstatus<br/>per krav]

    Poll --> Response{Mottaksstatus?}

    Response -->|RESKONTROFOERT| Final1[(Status → RESKONTROFOERT<br/>Ferdigbehandlet)]
    Response -->|MIGRERT| Final2[(Status → MIGRERT<br/>Ferdigbehandlet)]
    Response -->|UNDER BEHANDLING| Vent[(Status uendret<br/>Sjekkes neste kjøring)]
    Response -->|VALIDERINGSFEIL| ValFeil[Hent detaljer fra SKE]

    ValFeil --> GetFeil[GET /valideringsfeil]
    GetFeil --> SaveFeil[(Lagre i feilmelding-tabell)]

    style Input fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Poll fill:#b4d7ff,stroke:#7baed4,color:#000
    style Response fill:#fff3b0,stroke:#e6d476,color:#000
    style Final1 fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Final2 fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Vent fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style ValFeil fill:#ffc4c4,stroke:#e69a9a,color:#000
    style GetFeil fill:#ffc4c4,stroke:#e69a9a,color:#000
    style SaveFeil fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Statuslivssyklus

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000', 'fontSize': '14px'}}}%%
stateDiagram-v2
    state "KRAV IKKE SENDT" as KIS
    state "KRAV SENDT" as KS
    state "UNDER BEHANDLING" as MUB
    state "RESKONTROFOERT" as RESK
    state "MIGRERT" as MIG
    state "VALIDERINGSFEIL" as VAL
    state "HTTP FEIL" as FEIL

    [*] --> KIS : Innlest fra fil
    KIS --> KS : Sendt til SKE (2xx)
    KS --> MUB : SKE bekrefter mottak
    MUB --> RESK : Reskontroført
    MUB --> MIG : Migrert godkjent
    KS --> VAL : Asynk valideringsfeil
    KIS --> FEIL : Sending feilet
    FEIL --> KIS : Resending
    RESK --> [*]
    MIG --> [*]
```

## Endelige statuser (ingen videre behandling)

- `RESKONTROFOERT` – kravet er innkrevd
- `MIGRERT` – migrert krav er akseptert
- `VALIDERINGSFEIL_AV_LINJE_I_FIL` – intern valideringsfeil, sendes aldri
- `VALIDERINGSFEIL` – SKE avviste kravet asynkront
