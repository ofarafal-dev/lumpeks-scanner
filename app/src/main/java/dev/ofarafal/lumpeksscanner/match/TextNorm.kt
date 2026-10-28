package dev.ofarafal.lumpeksscanner.match

import java.text.Normalizer

object TextNorm {
    private val combiningMarks = Regex("\\p{Mn}+")

    /** Małe litery, bez polskich/francuskich znaków diakrytycznych. */
    fun fold(text: String): String {
        val lower = text.lowercase().replace('ł', 'l')
        return combiningMarks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
    }

    /** fold() + wszystko poza [a-z0-9] zamienione na spację. */
    fun normalize(text: String): String =
        fold(text).replace(Regex("[^a-z0-9]+"), " ").trim()

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = curr
            curr = t
        }
        return prev[b.length]
    }
}
