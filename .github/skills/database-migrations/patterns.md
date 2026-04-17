# Best Practices & Migration Patterns

## Best Practices

### Primary Keys

```sql
-- Use BIGSERIAL for auto-incrementing primary keys
id BIGSERIAL PRIMARY KEY,
```

### Timestamps

```sql
-- Always include created timestamp; add updated_at only if rows are updated in place
tidspunkt_opprettet TIMESTAMP NOT NULL DEFAULT NOW(),
tidspunkt_sendt     TIMESTAMP,          -- nullable: only set when sent
```

### Indexes

```sql
-- Index foreign keys
CREATE INDEX idx_feilmelding_krav_id ON feilmelding(krav_id);

-- Index heavily filtered columns
CREATE INDEX idx_krav_status ON krav(status);
CREATE INDEX idx_krav_corr_id ON krav(corr_id);

-- Composite indexes for multi-column queries
CREATE INDEX idx_krav_saksnummer_fom ON krav(saksnummer_nav, periode_fom);
```

### Constraints

```sql
-- Foreign keys with ON DELETE CASCADE
krav_id BIGINT NOT NULL REFERENCES krav(id) ON DELETE CASCADE,

-- Check constraints
CONSTRAINT check_valid_status CHECK (status IN ('KRAV_IKKE_SENDT', 'KRAV_SENDT', ...)),

-- Unique constraints
CONSTRAINT unique_corr_id UNIQUE (corr_id)
```

### Data Types

```sql
VARCHAR(n)        -- For strings with known max length
TEXT              -- For strings with unknown length (request/response bodies)
BIGINT            -- For large numbers
DOUBLE PRECISION  -- For amounts (belop, belopRente)
TIMESTAMP         -- For date/time
DATE              -- For dates only (vedtaksDato, periodeFOM/TOM as VARCHAR because of fixed-width format)
BOOLEAN           -- For flags (rapporter)
BIGSERIAL         -- For auto-incrementing IDs
```

## Migration Patterns

### Adding a Column

```sql
-- V5__add_tilleggsfrist.sql

ALTER TABLE krav
ADD COLUMN tilleggsfrist DATE;
```

### Adding a Table with Foreign Key

```sql
-- V6__create_ny_tabell.sql

CREATE TABLE ny_tabell (
    id                  BIGSERIAL PRIMARY KEY,
    krav_id             BIGINT    NOT NULL REFERENCES krav(id) ON DELETE CASCADE,
    data                TEXT,
    tidspunkt_opprettet TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ny_tabell_krav_id ON ny_tabell(krav_id);
```

### Altering a Column

```sql
-- V7__alter_column_length.sql

ALTER TABLE krav
ALTER COLUMN kravidentifikator_ske TYPE VARCHAR(200);
```
