# Migration File Structure

```sql
-- V1__initial_schema.sql

CREATE TABLE krav (
    id                    BIGSERIAL PRIMARY KEY,
    filnavn               VARCHAR(255)   NOT NULL,
    linjenummer           INT            NOT NULL,
    saksnummer_nav        VARCHAR(50)    NOT NULL,
    kravidentifikator_ske VARCHAR(100)   NOT NULL DEFAULT '',
    belop                 DOUBLE PRECISION,
    vedtaksdato           DATE,
    gjelder_id            VARCHAR(20),
    periode_fom           VARCHAR(8),
    periode_tom           VARCHAR(8),
    kravkode              VARCHAR(20),
    kode_hjemmel          VARCHAR(10),
    kode_arsak            VARCHAR(20),
    belop_rente           DOUBLE PRECISION,
    fremtidig_ytelse      DOUBLE PRECISION,
    utbetaldato           DATE,
    fagsystem_id          VARCHAR(50),
    status                VARCHAR(100)   NOT NULL,
    kravtype              VARCHAR(50),
    corr_id               VARCHAR(100)   NOT NULL,
    tidspunkt_sendt       TIMESTAMP,
    tidspunkt_siste_status TIMESTAMP,
    tidspunkt_opprettet   TIMESTAMP      NOT NULL DEFAULT NOW(),
    avsender              VARCHAR(50)
);

CREATE INDEX idx_krav_status ON krav(status);
CREATE INDEX idx_krav_corr_id ON krav(corr_id);
CREATE INDEX idx_krav_saksnummer_nav ON krav(saksnummer_nav);

CREATE TABLE feilmelding (
    id                    BIGSERIAL PRIMARY KEY,
    krav_id               BIGINT         NOT NULL REFERENCES krav(id) ON DELETE CASCADE,
    corr_id               VARCHAR(100),
    saksnummer_nav        VARCHAR(50),
    kravidentifikator_ske VARCHAR(100),
    error                 TEXT,
    melding               TEXT,
    nav_request           TEXT,
    ske_response          TEXT,
    tidspunkt_opprettet   TIMESTAMP      NOT NULL DEFAULT NOW(),
    rapporter             BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_feilmelding_krav_id ON feilmelding(krav_id);

CREATE TABLE filvalideringsfeil (
    id                  BIGSERIAL PRIMARY KEY,
    filnavn             VARCHAR(255)   NOT NULL,
    linjenummer         INT,
    saksnummer_nav      VARCHAR(50),
    kravlinje           TEXT,
    feilmelding         TEXT,
    tidspunkt_opprettet TIMESTAMP      NOT NULL DEFAULT NOW(),
    rapporter           BOOLEAN        NOT NULL DEFAULT TRUE
);
```
