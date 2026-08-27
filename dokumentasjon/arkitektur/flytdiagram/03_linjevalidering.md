# 3. Linjevalidering

Validerer hver enkelt kravlinje mot SKEs synkrone valideringsregler og interne forretningsregler.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 20, 'rankSpacing': 30}}}%%
flowchart LR
    Input[Liste med KravLinje] --> ForEach[For hver kravlinje]

    ForEach --> V1{Feil i validering?}
    V1 -->|Feil| Invalid[Valideringsfeil]
    V1 -->|OK| Valid[Kravlinje godkjent]

    Valid --> Next[Status: KRAV_IKKE_SENDT]
    Invalid --> Next2[Status: VALIDERINGSFEIL]

    style Input fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style ForEach fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style V1 fill:#fff3b0,stroke:#e6d476,color:#000
    
    style Valid fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Invalid fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Next fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Next2 fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Valideringsregler

| Felt | Regel |
|------|-------|
| vedtaksDato | Kan ikke være i fremtiden |
| utbetalingsDato | Må være før vedtaksDato (tom verdi tillatt for Arena/Pesys/Infotrygd) |
| periodeFOM/TOM | FOM ≤ TOM, TOM < første dag neste måned |
| tilleggsfrist | Ikke eldre enn 10 måneder fra i dag |
| gjelderId | Kan ikke være blank |
| beløp (hovedstol) | Kan ikke være negativt |
| saksnummerNAV | Må matche `^[a-zA-Z0-9-/]+$` |
| kravKode + kodeHjemmel | Må finnes i `StonadsType`-mapping |
| referansenummerGammelSak | Påkrevd for stopp, gyldig format for endring/stopp |
| fagsystemId | Påkrevd for avsender OB04 |

## Viktig

- Ugyldige linjer lagres likevel i databasen (for sporbarhet), men sendes aldri til SKE
- Gyldige og ugyldige linjer fra samme fil behandles uavhengig
