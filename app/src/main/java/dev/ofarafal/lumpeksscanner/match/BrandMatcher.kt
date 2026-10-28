package dev.ofarafal.lumpeksscanner.match

import dev.ofarafal.lumpeksscanner.data.Brand
import kotlin.math.abs

data class BrandMatch(val brand: Brand, val alias: String, val distance: Int)

/**
 * Dopasowanie marki po aliasach:
 *  - bez wielkości liter i znaków diakrytycznych,
 *  - odporne na rozbicie/sklejenie słów przez OCR ("LEVI'S" / "LEVIS", "MAX MARA" / "MAXMARA"),
 *  - tolerancja literówek OCR: aliasy do 5 znaków dokładnie, 6–8 znaków 1 błąd, dłuższe 2 błędy.
 * Przy kilku trafieniach wygrywa najdłuższy alias ("Weekend Max Mara" > "Max Mara").
 */
class BrandMatcher(brands: List<Brand>) {

    private class Entry(val brand: Brand, val alias: String, val words: Int, val compact: String)

    private val entries: List<Entry> = brands.flatMap { brand ->
        (brand.aliases + brand.name)
            .map { TextNorm.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .map { alias -> Entry(brand, alias, alias.split(' ').size, alias.replace(" ", "")) }
    }

    fun match(text: String): BrandMatch? {
        val tokens = TextNorm.normalize(text).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        var best: BrandMatch? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (entry in entries) {
            val tolerance = tolerance(entry.compact.length)
            var found = Int.MAX_VALUE
            val minWindow = maxOf(1, entry.words - 1)
            val maxWindow = entry.words + 1
            for (n in minWindow..maxWindow) {
                if (n > tokens.size) break
                for (i in 0..tokens.size - n) {
                    val window = tokens.subList(i, i + n).joinToString("")
                    val distance = when {
                        window == entry.compact -> 0
                        tolerance == 0 -> continue
                        abs(window.length - entry.compact.length) > tolerance -> continue
                        else -> TextNorm.levenshtein(window, entry.compact)
                    }
                    if (distance < found) found = distance
                }
            }
            if (found <= tolerance) {
                val score = entry.compact.length * 10.0 - found * 15.0
                if (score > bestScore) {
                    bestScore = score
                    best = BrandMatch(entry.brand, entry.alias, found)
                }
            }
        }
        return best
    }

    private fun tolerance(length: Int): Int = when {
        length <= 5 -> 0
        length <= 8 -> 1
        else -> 2
    }
}
