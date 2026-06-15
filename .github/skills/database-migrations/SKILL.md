---
name: database-migrations
description: "Flyway-migrasjoner for PostgreSQL: navnekonvensjoner (V{n}__desc.sql), skjema for krav/feilmelding/filvalideringsfeil, DBListener-testing med TestContainers"
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

Follow Flyway naming convention: `V{major}.{minor}.{patch}__{description}.sql`

### Examples

```
V1.0.0__create_tables.sql
V1.0.1__ny_kolonne_rapporter_i_feilmelding.sql
V1.0.2__ny_kolonne_rapporter_i_valideringsfeil.sql
V1.0.3__rename_valideringsfeil_til_filvalidateringsfeil.sql
```

### Rules

- Version numbers use semantic versioning (`major.minor.patch`)
- Use double underscore `__` between version and description
- Description should be lowercase with underscores
- **NEVER modify existing migrations** — always create new ones

## Sub-files

- See [schema.md](schema.md) for the full CREATE TABLE statements (migration file structure).
- See [patterns.md](patterns.md) for best practices (primary keys, timestamps, indexes, constraints, data types) and migration patterns (adding columns, tables, altering columns).
- See [kotlin-integration.md](kotlin-integration.md) for Kotlin integration (PostgresDataSource), testing migrations (DBListener, TestContainers), and PostgreSQL query optimization.

## Boundaries

### ✅ Always

- Follow `V{major}.{minor}.{patch}__{description}.sql` naming
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
- Use single underscore in naming (`V1.0.0_initial` → wrong, use `V1.0.0__initial`)
- Deploy untested migrations to production
- Run DDL outside of Flyway (including in application code)
