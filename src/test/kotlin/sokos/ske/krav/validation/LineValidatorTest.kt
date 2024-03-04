package sokos.ske.krav.validation

import io.kotest.core.spec.style.FunSpec
import sokos.ske.krav.domain.nav.KravLinje
import java.math.BigDecimal
import java.time.LocalDate

class LineValidatorTest : FunSpec({

    test("validerer med riktig innhold i saksnummer").config(enabled = false) {
     val kravLinje = KravLinje( 1,"abc123-ASADA/234asAGFH", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     //LineValidator.validateSaksnr(kravLinje, "fil.txt") shouldBe true
    }

    test("validerer med $,€,#,!,§,%,&,\\,(,) i saksnummer skal alle feile").config(enabled = false) {
     val kravLinje1 = KravLinje( 1,"abc12$3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje2 = KravLinje( 1,"abc12€3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje3 = KravLinje( 1,"abc12#3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje4 = KravLinje( 1,"abc12!3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje5 = KravLinje( 1,"abc12§3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje6 = KravLinje( 1,"abc12%3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje7 = KravLinje( 1,"abc12&3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje8 = KravLinje( 1,"abc12\\3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje9 = KravLinje( 1,"abc12(3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
     val kravLinje10 = KravLinje( 1,"abc12)3-/", BigDecimal(1.0), LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", BigDecimal(1.0), BigDecimal(0.0), "", "")
//     LineValidator.validateSaksnr(kravLinje1, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje2, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje3, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje4, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje5, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje6, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje7, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje8, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje9, "fil.txt") shouldBe false
//     LineValidator.validateSaksnr(kravLinje10, "fil.txt") shouldBe false
    }
})
