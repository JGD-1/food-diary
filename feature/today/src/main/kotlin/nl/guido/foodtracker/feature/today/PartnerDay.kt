package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.SharedDay

/**
 * The partner's day on the kcal meter (round 10): only their kcal total, and only when they chose
 * "Show my day to my partner". [fraction] is their share of their own target (null without one).
 */
internal data class PartnerDay(val name: String?, val kcal: Int, val fraction: Float?)

/** The first household member who shared something for this day; nothing for days they logged nothing. */
internal fun partnerDay(totals: List<SharedDay>, members: List<HouseholdMember>): PartnerDay? {
    val total = totals.firstOrNull { it.kcal > 0 } ?: return null
    val name = members.firstOrNull { it.userId == total.userId }?.displayName?.trim()?.takeIf { it.isNotEmpty() }
    // First name only, as on "<name> dished up for you".
    return PartnerDay(name?.substringBefore(' '), total.kcal, total.fraction)
}
