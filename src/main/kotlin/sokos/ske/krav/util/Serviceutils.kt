package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype

fun createKravidentifikatorPair(it: KravLinje, skeKravident: String): Pair<String, Kravidentifikatortype> {
    var kravIdentifikator = skeKravident
    var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR

    if (kravIdentifikator.isEmpty() && !it.isNyttKrav()) {
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

