package dev.ofarafal.lumpeksscanner.data

import org.json.JSONObject

enum class Rating {
    GREEN, YELLOW, RED;

    companion object {
        fun from(value: String): Rating = when (value.trim().lowercase()) {
            "green" -> GREEN
            "yellow" -> YELLOW
            else -> RED
        }
    }
}

data class Brand(
    val name: String,
    val aliases: List<String>,
    val country: String,
    val rating: Rating,
    val note: String,
    val counterfeitWarning: Boolean,
    /** Opcjonalna krótka wskazówka, np. "bierz Polo/oxford, Made in USA". */
    val tip: String? = null,
)

data class BrandDatabase(
    val version: Int,
    val updated: String,
    val brands: List<Brand>,
) {
    companion object {
        fun parse(json: String): BrandDatabase {
            val root = JSONObject(json)
            val array = root.getJSONArray("brands")
            val brands = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val aliasArray = o.optJSONArray("aliases")
                val aliases = if (aliasArray == null) {
                    emptyList()
                } else {
                    (0 until aliasArray.length()).map { aliasArray.getString(it) }
                }
                Brand(
                    name = o.getString("name"),
                    aliases = aliases,
                    country = o.optString("country", ""),
                    rating = Rating.from(o.getString("rating")),
                    note = o.optString("note", ""),
                    counterfeitWarning = o.optBoolean("counterfeit_warning", false),
                    tip = o.optString("tip", "").trim().ifEmpty { null },
                )
            }
            return BrandDatabase(
                version = root.getInt("version"),
                updated = root.optString("updated", ""),
                brands = brands,
            )
        }
    }
}
