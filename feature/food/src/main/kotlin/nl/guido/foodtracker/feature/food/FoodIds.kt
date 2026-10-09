package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.Id
import java.util.UUID

/** Name-based ids (UUID v3), so both phones give the same food the same id (see decisions.md). */
internal fun nevoId(code: String): Id = UUID.nameUUIDFromBytes("nevo:$code".toByteArray()).toString()

internal fun offId(barcode: String): Id = UUID.nameUUIDFromBytes("off:$barcode".toByteArray()).toString()
