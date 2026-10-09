package nl.guido.foodtracker.core.model

import java.util.UUID

/** Every stored thing has a random UUID as text, so phones can create rows offline without clashing. */
typealias Id = String

fun newId(): Id = UUID.randomUUID().toString()
