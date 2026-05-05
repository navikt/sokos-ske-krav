# Kotlin Integration & Testing

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
