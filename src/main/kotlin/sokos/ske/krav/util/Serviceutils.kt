package sokos.ske.krav.util

import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.service.NYTT_KRAV

fun createKravidentifikatorPair(it: KravTable,): Pair<String, Kravidentifikatortype> {
    var kravIdentifikator = it.saksnummerSKE
    var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR

    if (kravIdentifikator.isEmpty() && it.kravtype != NYTT_KRAV) {
        kravIdentifikator = it.referanseNummerGammelSak
        kravIdentifikatorType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
    }
    return Pair(kravIdentifikator, kravIdentifikatorType)
}


fun getNewFnr(fnrListe: List<String>, fnrIter: ListIterator<String>): String {
    var iter = fnrIter
    if (!iter.hasNext()) {
        iter = fnrListe.listIterator(0)
    }
    return iter.next()
}

