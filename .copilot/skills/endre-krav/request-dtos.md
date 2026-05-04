# Request DTOs & SKE Endpoints

## Kravtype Constants

Defined in `SkeService.kt`:

```kotlin
const val NYTT_KRAV = "NYTT_KRAV"
const val ENDRING_RENTE = "ENDRING_RENTE"
const val ENDRING_HOVEDSTOL = "ENDRING_HOVEDSTOL"
const val STOPP_KRAV = "STOPP_KRAV"
```

## Request DTOs

```kotlin
// EndringRequest.kt
@Serializable
data class NyHovedStolRequest(
    val hovedstol: HovedstolBeloep,
)

@Serializable
data class EndreRenteBeloepRequest(
    val renter: List<RenteBeloep>,
)

// Common.kt (shared)
@Serializable
data class HovedstolBeloep(val valuta: Valuta = Valuta.NOK, val beloep: Long)

@Serializable
data class RenteBeloep(
    val valuta: Valuta = Valuta.NOK,
    val beloep: Long,
    val renterIlagtDato: LocalDate,
    val rentetype: String = "STRAFFERENTE",
)
```

## Request Creation

```kotlin
// CreateRequests.kt
fun createEndreRenteRequest(krav: Krav) =
    EndreRenteBeloepRequest(createRenteBelop(krav))

fun createEndreHovedstolRequest(krav: Krav) =
    NyHovedStolRequest(HovedstolBeloep(beloep = krav.belop.roundToLong()))

private fun createRenteBelop(krav: Krav) = listOf(
    RenteBeloep(
        beloep = krav.belopRente.roundToLong(),
        renterIlagtDato = krav.vedtaksDato.toKotlinLocalDate(),
    ),
)
```

## KravidentifikatorType & Lookup

```kotlin
// Common.kt
enum class KravidentifikatorType(val value: String) {
    SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR"),
    OPPDRAGSGIVERSKRAVIDENTIFIKATOR("OPPDRAGSGIVERS_KRAVIDENTIFIKATOR"),
}

// Serviceutils.kt — determines which identifier to use
fun createKravidentifikatorPair(it: Krav): Pair<String, KravidentifikatorType> {
    var kravIdentifikator = it.kravidentifikatorSKE
    var kravIdentifikatorType = KravidentifikatorType.SKATTEETATENSKRAVIDENTIFIKATOR

    if (kravIdentifikator.isEmpty() && it.kravtype != NYTT_KRAV) {
        kravIdentifikator = it.referansenummerGammelSak
        kravIdentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
    }
    return Pair(kravIdentifikator, kravIdentifikatorType)
}
```

If the krav already has a `kravidentifikatorSKE` (assigned by SKE), that is used. Otherwise, `referansenummerGammelSak` (NAV's own identifier) is used with the `OPPDRAGSGIVERS_KRAVIDENTIFIKATOR` type.

## SkeClient Endpoints

```kotlin
private const val ENDRE_RENTER = "innkrevingsoppdrag/%s/renter?kravidentifikatortype=%s"
private const val ENDRE_HOVESTOL = "innkrevingsoppdrag/%s/hovedstol?kravidentifikatortype=%s"
```

Both use HTTP PUT via the `doPut()` helper with Maskinporten bearer token, `Klientid`, `Korrelasjonsid`, and `kravidentifikator` headers.

| Operation | Method | Path | Request DTO |
|---|---|---|---|
| Endre rente | PUT | `innkrevingsoppdrag/{id}/renter?kravidentifikatortype={type}` | `EndreRenteBeloepRequest` |
| Endre hovedstol | PUT | `innkrevingsoppdrag/{id}/hovedstol?kravidentifikatortype={type}` | `NyHovedStolRequest` |
