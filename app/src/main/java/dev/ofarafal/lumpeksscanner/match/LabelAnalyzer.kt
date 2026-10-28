package dev.ofarafal.lumpeksscanner.match

import kotlin.math.abs

enum class FiberQuality { PREMIUM, NEUTRAL, POOR }

data class Fiber(val key: String, val displayName: String, val quality: FiberQuality)

data class FiberHit(val fiber: Fiber, val percent: Int?)

enum class CompositionVerdict { PLUS, MINUS, NEUTRAL, UNKNOWN }

data class LabelInfo(
    val fibers: List<FiberHit>,
    val verdict: CompositionVerdict,
    val country: String?,
)

/** Analiza składu (włókna + procenty) i kraju produkcji z tekstu OCR. Wielojęzyczne: EN/FR/IT/DE/ES/NL/PL. */
object LabelAnalyzer {

    private val fiberDictionary: List<Pair<Fiber, Set<String>>> = listOf(
        Fiber("cashmere", "kaszmir", FiberQuality.PREMIUM) to
            setOf("cashmere", "cachemire", "kaschmir", "kaszmir", "cachemir", "cashmir", "kasjmier"),
        Fiber("merino", "wełna merino", FiberQuality.PREMIUM) to
            setOf("merino", "merinos", "merinowolle", "merinowol"),
        Fiber("silk", "jedwab", FiberQuality.PREMIUM) to
            setOf("silk", "soie", "seta", "seide", "jedwab", "seda", "zijde"),
        Fiber("linen", "len", FiberQuality.PREMIUM) to
            setOf("linen", "lin", "lino", "leinen", "len", "linnen", "lniany"),
        Fiber("alpaca", "alpaka", FiberQuality.PREMIUM) to
            setOf("alpaca", "alpaga", "alpaka"),
        Fiber("mohair", "moher", FiberQuality.PREMIUM) to
            setOf("mohair", "moher", "mohar"),
        Fiber("wool", "wełna", FiberQuality.NEUTRAL) to
            setOf("wool", "laine", "lana", "wolle", "welna", "wol"),
        Fiber("cotton", "bawełna", FiberQuality.NEUTRAL) to
            setOf("cotton", "coton", "cotone", "baumwolle", "bawelna", "algodon", "katoen"),
        Fiber("leather", "skóra", FiberQuality.NEUTRAL) to
            setOf("leather", "cuir", "pelle", "leder", "skora", "piel", "leer"),
        Fiber("viscose", "wiskoza", FiberQuality.NEUTRAL) to
            setOf("viscose", "viscosa", "viskose", "wiskoza", "rayon"),
        Fiber("lyocell", "lyocell", FiberQuality.NEUTRAL) to
            setOf("lyocell", "tencel", "modal"),
        Fiber("polyester", "poliester", FiberQuality.POOR) to
            setOf("polyester", "polyesther", "poliestere", "poliester", "polyestere", "polyesteri"),
        Fiber("acrylic", "akryl", FiberQuality.POOR) to
            setOf("acrylic", "acrylique", "acrilico", "acryl", "akryl", "poliakryl", "acrilica"),
        Fiber("polyamide", "poliamid", FiberQuality.POOR) to
            setOf("polyamide", "poliammide", "polyamid", "poliamid", "nylon", "poliamida"),
        Fiber("elastane", "elastan", FiberQuality.NEUTRAL) to
            setOf("elastane", "elasthan", "elastan", "spandex", "lycra", "elastam", "elastano"),
    )

    private val percentRegex = Regex("(\\d{1,3})\\s*%")
    private val wordRegex = Regex("[a-z]+")

    private val countryPrefix = Regex(
        "(made in|fabrique en|fabrique au|fabrique aux|fabrique a|prodotto in|fabbricato in|" +
            "hergestellt in|wyprodukowano w|hecho en|fabricado en|fabricado em|vervaardigd in|gemaakt in)" +
            "\\s+([a-z]+(?:\\s+[a-z]+){0,2})"
    )

    private val countries: Map<String, String> = buildMap {
        fun add(name: String, vararg keys: String) = keys.forEach { put(it, name) }
        add("Włochy", "italy", "italia", "italie", "italien", "wlochy", "wloszech")
        add("Chiny", "china", "chine", "cina", "chiny", "chinach", "prc", "p r c", "p r")
        add("Bangladesz", "bangladesh", "bangladesz", "bangladeszu")
        add("Turcja", "turkey", "turkiye", "turquie", "turchia", "turcja", "turcji", "turkei")
        add("Portugalia", "portugal", "portogallo", "portugalia", "portugalii")
        add("Wielka Brytania", "uk", "u k", "united kingdom", "england", "great britain", "britain",
            "royaume uni", "regno unito", "wielkiej brytanii", "anglii")
        add("Szkocja", "scotland", "ecosse", "scozia", "szkocji")
        add("Irlandia", "ireland", "irlande", "irlanda", "irlandii")
        add("Francja", "france", "francia", "frankreich", "francja", "francji")
        add("Niemcy", "germany", "allemagne", "germania", "deutschland", "niemcy", "niemczech")
        add("Polska", "poland", "pologne", "polonia", "polen", "polska", "polsce", "pl")
        add("Hiszpania", "spain", "espagne", "spagna", "espana", "hiszpania", "hiszpanii")
        add("Indie", "india", "inde", "indie", "indien", "indiach")
        add("Wietnam", "vietnam", "viet nam", "wietnam", "wietnamie")
        add("Kambodża", "cambodia", "cambodge", "cambogia", "kambodza", "kambodzy")
        add("Pakistan", "pakistan", "pakistanie")
        add("Indonezja", "indonesia", "indonesie", "indonezja", "indonezji")
        add("Sri Lanka", "sri lanka")
        add("Mjanma", "myanmar", "burma", "birmanie")
        add("Rumunia", "romania", "roumanie", "rumanien", "rumunia", "rumunii")
        add("Bułgaria", "bulgaria", "bulgarie", "bulgarien", "bulgarii")
        add("Tunezja", "tunisia", "tunisie", "tunezja", "tunezji")
        add("Maroko", "morocco", "maroc", "marocco", "marokko", "maroko")
        add("Mongolia", "mongolia", "mongolie", "mongolii")
        add("Peru", "peru", "perou")
        add("Japonia", "japan", "japon", "giappone", "japonia", "japonii")
        add("USA", "usa", "u s a", "united states", "etats unis", "stati uniti", "stanach zjednoczonych")
        add("Litwa", "lithuania", "lituanie", "litwa", "litwie")
        add("Macedonia Płn.", "macedonia", "north macedonia", "macedoine")
        add("Albania", "albania", "albanie", "albanii")
        add("Egipt", "egypt", "egypte", "egitto", "egipt", "egipcie")
    }

    fun analyze(rawText: String): LabelInfo {
        val lines = rawText.lines().map { TextNorm.fold(it).replace(Regex("[^a-z0-9%]+"), " ") }

        val hits = mutableListOf<FiberHit>()
        for (line in lines) {
            val percents = percentRegex.findAll(line).map { it.range.first to it.groupValues[1].toInt() }.toList()
            for (word in wordRegex.findAll(line)) {
                val fiber = fiberDictionary.firstOrNull { word.value in it.second }?.first ?: continue
                val percent = percents
                    .filter { it.second in 1..100 && abs(it.first - word.range.first) <= 25 }
                    .minByOrNull { abs(it.first - word.range.first) }
                    ?.second
                hits += FiberHit(fiber, percent)
            }
        }

        var fibers = hits
            .groupBy { it.fiber.key }
            .map { (_, list) -> list.firstOrNull { it.percent != null } ?: list.first() }
        if (fibers.any { it.fiber.key == "merino" }) fibers = fibers.filterNot { it.fiber.key == "wool" }

        return LabelInfo(
            fibers = fibers.sortedByDescending { it.percent ?: 0 },
            verdict = verdict(fibers),
            country = detectCountry(lines.joinToString(" ")),
        )
    }

    private fun verdict(fibers: List<FiberHit>): CompositionVerdict {
        if (fibers.isEmpty()) return CompositionVerdict.UNKNOWN
        if (fibers.any { it.fiber.quality == FiberQuality.PREMIUM }) return CompositionVerdict.PLUS
        val onlySynthetic = fibers.all { it.fiber.key == "polyester" || it.fiber.key == "acrylic" }
        val fullSynthetic = fibers.any {
            (it.fiber.key == "polyester" || it.fiber.key == "acrylic") && it.percent == 100
        }
        return if (onlySynthetic || fullSynthetic) CompositionVerdict.MINUS else CompositionVerdict.NEUTRAL
    }

    private fun detectCountry(text: String): String? {
        val normalized = text.replace("%", " ").replace(Regex("\\s+"), " ")
        val match = countryPrefix.find(normalized) ?: return null
        val words = match.groupValues[2].split(' ')
        for (n in words.size downTo 1) {
            countries[words.take(n).joinToString(" ")]?.let { return it }
        }
        return words.first().replaceFirstChar { it.uppercase() }
    }
}
