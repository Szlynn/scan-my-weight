package io.github.szlynn.scanmyweight.ocr

/**
 * Parses raw text from ML Kit Text Recognition into individual scale metrics.
 *
 * The scale (SBS 5020) uses a 7-segment display. ML Kit may misread certain characters:
 *   - 'O' instead of '0'
 *   - 'l' or 'I' instead of '1'
 *   - 'S' instead of '5'
 *   - decimal point may be surrounded by extra whitespace
 *
 * The display layout:
 *   - Centre panel: weight value followed by "kg"
 *   - Right panel: label (BF / BW / BM) followed by a percentage value and "%"
 */
object OcrParser {

    /**
     * All metrics extracted from a single OCR frame.
     * A null field means the metric was not found in this frame.
     */
    data class ParseResult(
        val weightKg: Double? = null,
        val bf: Double? = null,
        val bw: Double? = null,
        val bm: Double? = null,
    )

    // Normalise common 7-segment misreads before applying regex.
    private fun normalise(text: String): String = text
        .replace('O', '0')
        .replace('o', '0')
        .replace('l', '1')
        .replace('I', '1')
        .replace('S', '5')
        .replace('s', '5')
        // Collapse whitespace around a decimal point: "60 . 6" → "60.6"
        .replace(Regex("""\s*\.\s*"""), ".")

    /**
     * Regex for a decimal number: one or more digits, optional decimal part.
     * Accepts values like "60.6", "606", "35.6".
     */
    private val NUMBER = """(\d+(?:\.\d+)?)"""

    private val weightRegex = Regex("""$NUMBER\s*kg""", RegexOption.IGNORE_CASE)
    private val bfRegex = Regex("""BF\s*$NUMBER\s*%""", RegexOption.IGNORE_CASE)
    private val bwRegex = Regex("""BW\s*$NUMBER\s*%""", RegexOption.IGNORE_CASE)
    private val bmRegex = Regex("""BM\s*$NUMBER\s*%""", RegexOption.IGNORE_CASE)

    /**
     * Parses [rawText] (the full concatenated text from one ML Kit frame) and returns
     * whatever metrics could be extracted. Any metric not found is null.
     */
    fun parse(rawText: String): ParseResult {
        val text = normalise(rawText)
        return ParseResult(
            weightKg = weightRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull(),
            bf = bfRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull(),
            bw = bwRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull(),
            bm = bmRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull(),
        )
    }
}
