package com.charles.cruiseapp.data.decks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeckCatalog(
    val version: Int = 1,
    val updatedAt: String = "",
    val ships: List<ShipEntry> = emptyList()
)

@Serializable
data class ShipEntry(
    val id: String,
    val displayName: String,
    val line: String = "",
    @SerialName("class") val shipClass: String = "",
    val aliases: List<String> = emptyList(),
    val deckCount: Int = 0,
    val imageBase: String = "",
    val thumb: String? = null,
    val decks: List<DeckInfo> = emptyList(),
    val attribution: String = "",
    val externalUrl: String? = null
)

@Serializable
data class DeckInfo(
    val number: Int,
    val name: String,
    val file: String,
    val width: Int = 1600,
    val height: Int = 2400,
    val bytes: Long = 0,
    val license: String = "CC0-1.0"
)

fun ShipEntry.bestImageUrl(deck: DeckInfo): String {
    val base = imageBase.trimEnd('/')
    return "$base/${deck.file}"
}

fun DeckCatalog.findBestMatch(query: String): ShipEntry? {
    val q = normalizeShipName(query)
    if (q.isBlank()) return null
    // Exact id or displayName
    ships.find { normalizeShipName(it.displayName) == q }?.let { return it }
    ships.find { normalizeShipName(it.id) == q }?.let { return it }
    // Alias exact
    ships.find { e -> e.aliases.any { normalizeShipName(it) == q } }?.let { return it }
    // Contains
    ships.find { normalizeShipName(it.displayName).contains(q) || q.contains(normalizeShipName(it.displayName)) }?.let { return it }
    ships.find { e -> e.aliases.any { normalizeShipName(it).contains(q) || q.contains(normalizeShipName(it)) } }?.let { return it }
    // Levenshtein-ish simple: smallest distance
    var best: ShipEntry? = null
    var bestDist = Int.MAX_VALUE
    for (s in ships) {
        val d1 = levenshtein(q, normalizeShipName(s.displayName))
        val d2 = s.aliases.minOfOrNull { levenshtein(q, normalizeShipName(it)) } ?: 99
        val d = minOf(d1, d2)
        if (d < bestDist && d <= 3) { bestDist = d; best = s }
    }
    return best
}

fun normalizeShipName(s: String): String = s.lowercase().trim()
    .replace(Regex("[^a-z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length+1){ IntArray(b.length+1) }
    for (i in 0..a.length) dp[i][0]=i
    for (j in 0..b.length) dp[0][j]=j
    for (i in 1..a.length) for (j in 1..b.length) {
        dp[i][j] = if (a[i-1]==b[j-1]) dp[i-1][j-1] else 1+minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
    }
    return dp[a.length][b.length]
}
