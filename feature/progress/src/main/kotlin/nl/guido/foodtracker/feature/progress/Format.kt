package nl.guido.foodtracker.feature.progress

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Number and date formatting for this module, in the phone's language (so Dutch gets "78,4"). */
internal object Format {
    /** A weight with one decimal: "78.4". */
    fun kg(value: Double, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.0", DecimalFormatSymbols(locale)).format(value)

    /** A small weight or rate with up to two decimals, no sign: "0.15", "0.2". */
    fun kgSmall(value: Double, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.0#", DecimalFormatSymbols(locale)).format(abs(value))

    /** A change with a sign, using a real minus: "−0.4", "+0.2". */
    fun kgSigned(value: Double, locale: Locale = Locale.getDefault()): String {
        val text = kg(abs(value), locale)
        return when {
            text == kg(0.0, locale) -> text
            value < 0 -> "−$text"
            else -> "+$text"
        }
    }

    /** Whole kcal with thousands separators: "2,108". */
    fun kcal(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(value.roundToInt())

    /** "19 July". */
    fun dayMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMMM", locale).format(date)

    /** "5–11 Oct", or "28 Sep – 4 Oct" across a month boundary. */
    fun weekRange(from: LocalDate, to: LocalDate, locale: Locale = Locale.getDefault()): String {
        val short = DateTimeFormatter.ofPattern("d MMM", locale)
        return if (from.month == to.month) {
            "${from.dayOfMonth}–${short.format(to)}"
        } else {
            "${short.format(from)} – ${short.format(to)}"
        }
    }

    /** "March", or "March 2028" when it isn't within the coming twelve months. */
    fun monthHint(month: YearMonth, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        val monthsAway = (month.year - today.year) * 12 + (month.monthValue - today.monthValue)
        return if (monthsAway < 12) name else "$name ${month.year}"
    }

    fun monthShort(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)

    fun monthFull(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
}
