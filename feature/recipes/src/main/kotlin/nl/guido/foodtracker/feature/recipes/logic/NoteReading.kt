package nl.guido.foodtracker.feature.recipes.logic

/** One line of text the text reader found in one camera frame, with its height on the picture (y grows downwards). */
data class SeenLine(val text: String, val top: Float, val bottom: Float) {
    val centerY: Float get() = (top + bottom) / 2f
}

/** A note line read over many frames: the most-seen version first, then the others. */
data class ReadLine(val text: String, val seen: Int, val versions: List<String>)

/**
 * Reads a handwritten note from the live camera (round 4, H), the way the barcode scanner reads
 * barcodes: every frame is offered, and the same line seen in many frames is counted as one.
 * Handwriting is read a bit differently from frame to frame, so for each line the version seen
 * most often wins. The longer the phone is held over the note, the better the result.
 *
 * Lines are matched between frames by how alike their text is, so moving the phone is fine. The
 * order on the note comes from which line was above which in the frames that showed both.
 */
class NoteReading(
    /** A line shows once it was seen in this many frames, so a one-off misreading never does. */
    private val minSeen: Int = 2,
    private val maxLines: Int = 80,
) {
    private class Group(var id: Int) {
        val versions = LinkedHashMap<String, Int>()
        var seen = 0
        var lastFrame = 0
        val best: String get() = versions.maxBy { it.value }.key
        /** The most-seen versions as match keys, to compare new lines with. */
        var keys: List<String> = emptyList()

        fun add(text: String) {
            versions[text] = (versions[text] ?: 0) + 1
            if (versions.size > MAX_VERSIONS) versions.remove(versions.minBy { it.value }.key)
            keys = versions.entries.sortedByDescending { it.value }.take(COMPARE_VERSIONS).map { matchKey(it.key) }
        }
    }

    private val groups = mutableListOf<Group>()
    /** above[a to b] = frames in which group a was above group b. */
    private val above = HashMap<Pair<Int, Int>, Int>()
    private var frame = 0
    private var nextId = 0

    /** Takes one frame's lines. */
    fun offer(lines: List<SeenLine>) {
        frame++
        val usable = lines.map { it.copy(text = it.text.trim().replace(Regex("\\s+"), " ")) }
            .filter { line -> line.text.count { it.isLetterOrDigit() } >= 2 }
        if (usable.isEmpty()) return

        // Pair each line with the most alike group, best pairs first, one line per group.
        val keys = usable.map { matchKey(it.text) }
        val pairs = buildList {
            keys.forEachIndexed { li, key ->
                groups.forEachIndexed { gi, g ->
                    val sim = g.keys.maxOf { similarity(key, it) }
                    if (sim >= MATCH_AT) add(Triple(li, gi, sim))
                }
            }
        }.sortedByDescending { it.third }
        val groupOf = arrayOfNulls<Group>(usable.size)
        val taken = HashSet<Int>()
        for ((li, gi, _) in pairs) {
            if (groupOf[li] != null || gi in taken) continue
            groupOf[li] = groups[gi]
            taken += gi
        }
        usable.forEachIndexed { i, line ->
            val g = groupOf[i] ?: Group(nextId++).also { groups += it; groupOf[i] = it }
            g.add(line.text)
            g.seen++
            g.lastFrame = frame
        }
        // Which line was above which, in this frame.
        for (a in usable.indices) for (b in usable.indices) {
            if (a != b && usable[a].centerY < usable[b].centerY) {
                val key = groupOf[a]!!.id to groupOf[b]!!.id
                above[key] = (above[key] ?: 0) + 1
            }
        }
        prune()
    }

    /** The lines read so far, in the order they are on the note. */
    val lines: List<ReadLine>
        get() {
            val shown = groups.filter { it.seen >= minSeen }
            val score = shown.associate { g ->
                g.id to shown.sumOf { o ->
                    if (o === g) 0 else (above[g.id to o.id] ?: 0).compareTo(above[o.id to g.id] ?: 0)
                }
            }
            return shown.sortedByDescending { score.getValue(it.id) }.map { g ->
                ReadLine(g.best, g.seen, g.versions.entries.sortedByDescending { it.value }.map { it.key })
            }
        }

    fun clear() {
        groups.clear()
        above.clear()
        frame = 0
    }

    /** Lines seen only once long ago are noise; the list never grows past [maxLines]. */
    private fun prune() {
        groups.removeAll { it.seen < minSeen && frame - it.lastFrame > STALE_FRAMES }
        if (groups.size > maxLines) {
            groups.sortByDescending { it.seen }
            groups.subList(maxLines, groups.size).clear()
        }
    }

    private companion object {
        const val MATCH_AT = 0.7
        const val STALE_FRAMES = 30
        const val MAX_VERSIONS = 12
        const val COMPARE_VERSIONS = 3
    }
}

/** Lower case letters and digits only, so "300g Kip," and "300 g kip" look the same. */
internal fun matchKey(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("\\s+"), " ").trim()

/** 1.0 for the same text, 0.0 for nothing alike (edit distance against the longer one). */
internal fun similarity(a: String, b: String): Double {
    if (a == b) return 1.0
    val longest = maxOf(a.length, b.length)
    if (longest == 0) return 1.0
    return 1.0 - editDistance(a, b).toDouble() / longest
}

internal fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[b.length]
}
