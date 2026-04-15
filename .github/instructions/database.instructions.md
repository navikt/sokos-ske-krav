---
applyTo: "**/db/migration/**/*.sql"
---

# Database Migration Standards (Flyway)

Standarder for databasemigrasjoner med Flyway: navnekonvensjoner, sikre endringer og idempotente skript.

## Schema Overview

Three tables managed by Flyway migrations in `src/main/resources/db/migration/`:

| Table | Purpose |
|---|---|
| `krav` | One row per claim line; tracks `status`, `kravtype`, `corr_id`, `kravidentifikator_ske` |
| `feilmelding` | HTTP error responses from SKE, linked to a `krav` via `krav_id` |
| `filvalideringsfeil` | File-level and line-level validation failures (not linked to a specific krav) |

## Migration File Naming

Follow Flyway naming convention: `V{version}__{description}.sql`

### Examples

```
V1__initial_schema.sql
V2__add_status_column.sql
V3__add_user_indexes.sql
V4__alter_table_constraints.sql
```

### Rules

- Version numbers must be sequential (1, 2, 3, ...)
- Use double underscore `__` between version and description
- Description should be lowercase with underscores
- **NEVER modify existing migrations** — always create new ones

## Migration File Structure

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

## Kotlin Integration

`PostgresDataSource` runs Flyway migrations at startup using the `adminUser` role (Vault-managed in non-local environments):

```kotlin
object PostgresDataSource {
    fun migrate() {
        dataSource(role = postgresConfig.adminUser).use { migrate(it) }
    }

    fun migrate(dataSource: HikariDataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${postgresConfig.adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }
}

// Called at startup only in non-local environments:
if (!PropertiesConfig.isLocal) {
    PostgresDataSource.migrate()
}
```

## Testing Migrations

Migrations run automatically in `DBListener` via TestContainers PostgreSQL 16:

```kotlin
object DBListener : TestListener {
    init {
        PropertiesConfig.load(ApplicationConfig("application-test.conf"))
    }

    val dataSource: HikariDataSource by lazy {
        container.toDataSource { maximumPoolSize = 10 }.also {
            PostgresDataSource.migrate(it) // runs Flyway against test container
        }
    }

    fun loadInitScript(name: String) {
        dataSource // ensure migrations ran before seeding
        ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
    }

    fun clearDB() {
        dataSource.transaction { session ->
            val tables = mutableListOf<String>()
            session.list(
                queryOf("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history'"),
            ) { rs -> tables += rs.string("tablename") }
            if (tables.isNotEmpty()) {
                session.execute(queryOf("TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE"))
            }
        }
    }
}
```

SQL fixture files live under `src/test/resources/SQLscript/`. Load them in tests:

```kotlin
DBListener.clearDB()
DBListener.loadInitScript("SQLscript/krav/ToNyeKrav.sql")
```

## PostgreSQL Query Optimization

### EXPLAIN ANALYZE

Always analyze new or changed queries:

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM krav
WHERE status IN ('KRAV_IKKE_SENDT', '409_KRAV_ER_IKKE_RESKONTROFØRT_RESEND')
ORDER BY id;
```

Red flags: `Seq Scan` on large tables, high discrepancy between estimated/actual rows.

### Large Table Migrations

```sql
-- Add column with default (instant in PostgreSQL 11+)
ALTER TABLE krav ADD COLUMN ny_kolonne BOOLEAN DEFAULT false;

-- Standard index in migration
CREATE INDEX idx_ny ON krav(ny_kolonne);

-- Use CREATE INDEX CONCURRENTLY only in its own dedicated migration
-- with no other statements in the file.
```

## Boundaries

### ✅ Always

- Follow `V{n}__{description}.sql` naming
- Add indexes for all foreign keys and frequently filtered columns
- Include `tidspunkt_opprettet TIMESTAMP NOT NULL DEFAULT NOW()`
- Use `BIGSERIAL` for primary keys
- Test migrations locally with `DBListener` before pushing

### ⚠️ Ask First

- Schema changes affecting multiple tables
- Dropping columns or tables
- Changing primary keys or foreign key constraints
- Large data migrations (use batching)

### 🚫 Never

- Modify existing migration files
- Skip version numbers
- Use single underscore in naming (`V1_initial` → wrong, use `V1__initial`)
- Deploy untested migrations to production
- Run DDL outside of Flyway (including in application code)
