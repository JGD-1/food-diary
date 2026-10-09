package nl.guido.foodtracker.feature.food.data

/** Barcodes Open Food Facts didn't know, so rescanning the same one doesn't ask again for 10 minutes. */
internal class BarcodeMemory(private val clock: () -> Long = System::currentTimeMillis) {
    private val missed = mutableMapOf<String, Long>()

    @Synchronized fun remember(code: String) { missed[code] = clock() }

    @Synchronized fun recentlyMissed(code: String): Boolean =
        missed[code]?.let { clock() - it < TEN_MINUTES } ?: false

    companion object {
        private const val TEN_MINUTES = 10 * 60_000L

        /** Digits only; a 12-digit UPC is also tried as the 13-digit EAN with a leading 0. */
        fun variants(raw: String): List<String> {
            val digits = raw.filter { it.isDigit() }
            if (digits.length < 6) return emptyList()
            return if (digits.length == 12) listOf("0$digits", digits) else listOf(digits)
        }
    }
}
