package nl.guido.foodtracker.feature.camera.read

/** Checks for shop barcodes (EAN-13, EAN-8, UPC-A, UPC-E), so misread codes are never looked up. */
object Barcodes {

    /** True when [code] is a shop barcode whose last digit (the check digit) is right. */
    fun isValid(code: String): Boolean {
        if (code.isEmpty() || !code.all { it.isDigit() }) return false
        return when (code.length) {
            8 -> checkDigitOk(code) || upcEToUpcA(code)?.let(::checkDigitOk) == true
            12, 13 -> checkDigitOk(code)
            else -> false
        }
    }

    /** GTIN rule: from the right, digits alternate weight 3 and 1; the total must end in 0. */
    private fun checkDigitOk(code: String): Boolean {
        val sum = code.reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 1) 3 else 1 }.sum()
        return sum % 10 == 0
    }

    /** Expands an 8-digit UPC-E code to its 12-digit UPC-A form. */
    internal fun upcEToUpcA(code: String): String? {
        if (code.length != 8 || code[0] !in "01") return null
        val d = code.substring(1, 7)
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return "${code[0]}$body${code[7]}"
    }
}
