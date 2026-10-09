package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.LocalDate

/** A Monday, so weeks line up neatly. */
internal val MONDAY: LocalDate = LocalDate.of(2026, 10, 5)

internal val PROFILE = UserProfile(
    id = "u", name = "Test", birthYear = 1990, sex = Sex.MALE, heightCm = 180,
    activity = ActivityLevel.LIGHT, startWeightKg = 85.0, targetWeightKg = 78.0, weeklyPaceKg = 0.5,
)

internal fun weighIn(date: LocalDate, kg: Double) = WeighIn(id = date.toString(), userId = "u", date = date, kg = kg)

/** Weekly weigh-ins on Mondays, starting [weeks] weeks before [MONDAY]. */
internal fun weeklyWeighIns(vararg kgs: Double): List<WeighIn> =
    kgs.mapIndexed { i, kg -> weighIn(MONDAY.minusWeeks((kgs.size - 1 - i).toLong()), kg) }

/** The same intake every day from [from] up to (not including) [to]. */
internal fun dailyIntake(from: LocalDate, to: LocalDate, kcal: Double): List<DayTotal> =
    generateSequence(from) { it.plusDays(1) }.takeWhile { it < to }.map { DayTotal(it, kcal) }.toList()
