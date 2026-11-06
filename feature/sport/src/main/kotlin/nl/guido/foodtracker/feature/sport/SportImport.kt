package nl.guido.foodtracker.feature.sport

import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.SportEntry
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** One exercise session as another app (Garmin, Sportcity, …) put it in Health Connect. */
internal data class ImportedSession(
    val recordId: String,
    val start: Instant,
    val end: Instant,
    val startOffset: ZoneOffset?,
    /** The session's own title, e.g. "Ochtendloop"; may be empty. */
    val title: String?,
    /** A readable name for its exercise type, e.g. "Hardlopen". */
    val typeName: String,
    /** Calories the other app counted for the session itself (active), or null when it saved none. */
    val activeKcal: Double?,
    /** Total calories burned in that time (includes resting), used only when there is no active number. */
    val totalKcal: Double?,
)

internal object SportImport {
    /** How far back an import looks. */
    const val DAYS_BACK = 30L

    /**
     * The sport line for a session, or null when it should be skipped: already imported (also when
     * deleted since), or no kcal known. Active calories are used because the day's target already
     * includes resting burn; total calories only when the app saved nothing else.
     */
    fun toEntry(session: ImportedSession, userId: Id, alreadyImported: Set<String>, zone: ZoneId = ZoneId.systemDefault()): SportEntry? {
        if (session.recordId in alreadyImported) return null
        val kcal = (session.activeKcal ?: session.totalKcal)?.roundToInt()?.coerceAtMost(SportEntry.MAX_KCAL) ?: return null
        if (kcal < 1) return null
        val minutes = ChronoUnit.MINUTES.between(session.start, session.end).toInt().takeIf { it > 0 }
        val date = session.start.atZone(session.startOffset ?: zone).toLocalDate()
        val name = session.title?.trim()?.takeIf { it.isNotEmpty() } ?: session.typeName
        return SportEntry(
            id = SportEntry.importedId(session.recordId),
            userId = userId,
            date = date,
            name = name.take(60),
            kcal = kcal,
            createdAt = session.start,
            minutes = minutes,
            externalId = session.recordId,
        )
    }
}
