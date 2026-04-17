---
name: repository
description: "Repository-mønster med Kotlin object og Connection/TransactionalSession extension-funksjoner, DBUtils-transaksjoner og ResultSet-mapping"
---

# Repository & Database Access Patterns

Repository- og databaseaksessmønstre: object-repositories med Connection-extensions, DBUtils-transaksjoner, ResultSet-mapping og feilhåndtering.

> All database access goes through `DBUtils.asyncTransaction {}` (suspending) or `DBUtils.transaction {}` (blocking). Repositories are Kotlin `object`s with extension functions on `Connection` or `TransactionalSession`.

## DataSource

`PostgresDataSource` is a singleton `object` using HikariCP. In non-local environments it integrates with Vault for credentials:

```kotlin
object PostgresDataSource {
    val dataSource: HikariDataSource by lazy { dataSource() }

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
```

## Transactions via DBUtils

Always use `DBUtils.asyncTransaction {}` (suspending) or `DBUtils.transaction {}` (blocking). Never use bare JDBC connections directly.

```kotlin
// Suspending (use in coroutine context / service layer)
dataSource.asyncTransaction { session ->
    KravRepository.run { session.insertAllNewKrav(kravLinjer, filnavn) }
}

// Blocking (use in sync contexts)
dataSource.transaction { session ->
    KravRepository.run { session.updateStatus(corrId, status) }
}
```

## Repository Pattern

Repositories are Kotlin `object`s with extension functions on `Connection` (raw JDBC) or `TransactionalSession` (kotliquery). Use `RepositoryExtensions` helpers:

```kotlin
object KravRepository {
    fun Connection.getAllUnsentKrav() =
        executeSelect(
            """select * from krav where status = ?""",
            Status.KRAV_IKKE_SENDT.value,
        ).toKrav()

    fun Connection.updateSentKrav(
        corrId: String,
        kravidentifikatorSKE: String,
        status: String,
    ) = executeUpdate(
        """update krav set kravidentifikator_ske = ?, status = ?, tidspunkt_sendt = now() where corr_id = ?""",
        kravidentifikatorSKE,
        status,
        corrId,
    )

    // For kotliquery TransactionalSession (e.g. when returnGeneratedKey is needed)
    fun getKravTableIdFromCorrelationId(
        tx: TransactionalSession,
        corrID: String,
    ): Long =
        tx.single(
            queryOf("select id from krav where corr_id = ?", corrID)
                .map { row -> row.long("id") }
                .asSingle,
        ) ?: throw IllegalStateException("Krav med corrId $corrID ikke funnet")
}
```

## ResultSet Mapping

Centralise `ResultSet` → domain mapping in `RepositoryMappers.kt` using `getColumn<T>()`:

```kotlin
fun ResultSet.toKrav() =
    toList {
        Krav(
            kravId = getColumn("id"),
            saksnummerNAV = getColumn("saksnummer_nav"),
            status = getColumn("status"),
            kravtype = getColumn("kravtype"),
            corrId = getColumn("corr_id"),
            kravidentifikatorSKE = getColumn("kravidentifikator_ske"),
            // ... remaining fields
        )
    }

private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    buildList {
        while (next()) { add(mapper()) }
    }
```

## Error Handling

Wrap bare `Connection` usage with `useAndHandleErrors`:

```kotlin
dataSource.connection.useAndHandleErrors { con ->
    con.getAllUnsentKrav()
}
```

## Boundaries

### ✅ Always

- Use `DBUtils.asyncTransaction {}` / `DBUtils.transaction {}` — never bare `Connection` in service code
- Use `object` + extension functions for repositories
- Map `ResultSet` → domain in `RepositoryMappers.kt` using `getColumn<T>()`
- Wrap `Connection` usage with `useAndHandleErrors`

### 🚫 Never

- Skip Flyway migrations for schema changes
- Use bare JDBC connections directly in service code
- Scatter ResultSet mapping logic outside `RepositoryMappers.kt`
