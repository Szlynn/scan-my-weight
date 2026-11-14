package io.github.szlynn.scanmyweight.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrParserTest {

    // region Weight parsing

    @Test
    fun `parses clean weight`() {
        val result = OcrParser.parse("60.6 kg")
        assertEquals(60.6, result.weightKg)
    }

    @Test
    fun `parses weight without decimal`() {
        val result = OcrParser.parse("80 kg")
        assertEquals(80.0, result.weightKg)
    }

    @Test
    fun `parses weight with OCR zero misread as O`() {
        val result = OcrParser.parse("6O.6 kg")
        assertEquals(60.6, result.weightKg)
    }

    @Test
    fun `parses weight with spaced decimal point`() {
        val result = OcrParser.parse("60 . 6 kg")
        assertEquals(60.6, result.weightKg)
    }

    @Test
    fun `parses weight with S misread for 5`() {
        val result = OcrParser.parse("S5.0 kg")
        assertEquals(55.0, result.weightKg)
    }

    @Test
    fun `returns null weight when kg not present`() {
        val result = OcrParser.parse("60.6")
        assertNull(result.weightKg)
    }

    @Test
    fun `rejects suffix match from comma-separated token`() {
        // "60,6 kg" after comma normalisation becomes "60.6 kg" — valid, should parse
        val result = OcrParser.parse("60,6 kg")
        assertEquals(60.6, result.weightKg)
    }

    @Test
    fun `rejects suffix match from double-decimal token`() {
        // "60.6.6 kg" is not a valid number — must not match "6.6 kg" or "60.6 kg"
        val result = OcrParser.parse("60.6.6 kg")
        assertNull(result.weightKg)
    }

    @Test
    fun `rejects suffix match from double-comma token`() {
        // "60,6,6 kg" partially normalises to "60.6,6 kg" — trailing ",6" must not match
        val result = OcrParser.parse("60,6,6 kg")
        assertNull(result.weightKg)
    }

    @Test
    fun `rejects partial digit suffix match`() {
        // "1606 kg" should not match as "60.6" or "606" mid-token
        val result = OcrParser.parse("1606 kg")
        assertEquals(1606.0, result.weightKg)
    }

    // endregion

    // region BF parsing

    @Test
    fun `parses clean BF`() {
        val result = OcrParser.parse("BF 35.6%")
        assertEquals(35.6, result.bf)
    }

    @Test
    fun `parses BF with no space before percent`() {
        val result = OcrParser.parse("BF35.6%")
        assertEquals(35.6, result.bf)
    }

    @Test
    fun `parses BF with OCR misreads`() {
        val result = OcrParser.parse("BF 3S.6%")
        assertEquals(35.6, result.bf)
    }

    @Test
    fun `returns null BF when label missing`() {
        val result = OcrParser.parse("35.6%")
        assertNull(result.bf)
    }

    // endregion

    // region BW parsing

    @Test
    fun `parses clean BW`() {
        val result = OcrParser.parse("BW 44.2%")
        assertEquals(44.2, result.bw)
    }

    @Test
    fun `returns null BW when label missing`() {
        val result = OcrParser.parse("44.2%")
        assertNull(result.bw)
    }

    // endregion

    // region BM parsing

    @Test
    fun `parses clean BM`() {
        val result = OcrParser.parse("BM 31.3%")
        assertEquals(31.3, result.bm)
    }

    @Test
    fun `returns null BM when label missing`() {
        val result = OcrParser.parse("31.3%")
        assertNull(result.bm)
    }

    // endregion

    // region Full frame parsing

    @Test
    fun `parses full BF frame`() {
        val result = OcrParser.parse("Code Age\n1 35\n152 cm\n60.6 kg\nBF 35.6%")
        assertEquals(60.6, result.weightKg)
        assertEquals(35.6, result.bf)
        assertNull(result.bw)
        assertNull(result.bm)
    }

    @Test
    fun `parses full BW frame`() {
        val result = OcrParser.parse("Code Age\n1 35\n152 cm\n60.6 kg\nBW 44.2%")
        assertEquals(60.6, result.weightKg)
        assertNull(result.bf)
        assertEquals(44.2, result.bw)
        assertNull(result.bm)
    }

    @Test
    fun `parses full BM frame`() {
        val result = OcrParser.parse("Code Age\n1 35\n152 cm\n60.6 kg\nBM 31.3%")
        assertEquals(60.6, result.weightKg)
        assertNull(result.bf)
        assertNull(result.bw)
        assertEquals(31.3, result.bm)
    }

    @Test
    fun `returns all nulls for irrelevant text`() {
        val result = OcrParser.parse("Code Age\n1 35\n152 cm")
        assertNull(result.weightKg)
        assertNull(result.bf)
        assertNull(result.bw)
        assertNull(result.bm)
    }

    // endregion

    private fun assertEquals(expected: Double, actual: Double?) {
        assertEquals(expected, actual ?: Double.NaN, 0.001)
    }
}
