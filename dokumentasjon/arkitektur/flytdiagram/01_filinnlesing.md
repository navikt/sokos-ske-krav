# 1. Filinnlesing

Henter flatfiler fra SFTP-server og parser fixed-width copybook-format til strukturerte objekter.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    SFTP[(SFTP /inbound)] --> List[List filer]
    List --> Download[Last ned filinnhold]
    Download --> Parse[FileParser]

    Parse --> Header[Header<br/>avsender, dato]
    Parse --> Krav[KravLinje x N<br/>saksnr, beløp, datoer, kode m.m]
    Parse --> Footer[Footer<br/>antall, sum, dato]

    Header --> Result[ParseResult.Success]
    Krav --> Result
    Footer --> Result

    Parse -->|Parse-feil| Error[ParseResult.Error<br/>Flyttes til /feilfiler]

    style SFTP fill:#b4d7ff,stroke:#7baed4,color:#000
    style List fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Download fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Parse fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Header fill:#fff3b0,stroke:#e6d476,color:#000
    style Krav fill:#fff3b0,stroke:#e6d476,color:#000
    style Footer fill:#fff3b0,stroke:#e6d476,color:#000
    style Result fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Error fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Nøkkelpunkter

- Filene er fixed-width (byte-offsets per felt) fra OS, Arena, Infotrygd, Pesys
- Hver fil har nøyaktig én header-linje, N kravlinjer, og én footer-linje
- Filer som ikke kan parses flyttes til `/inbound/feilfiler` og behandles ikke videre
